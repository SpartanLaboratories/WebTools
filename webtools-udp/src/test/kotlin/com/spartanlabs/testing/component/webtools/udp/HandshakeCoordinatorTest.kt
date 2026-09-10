package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakeConnection
import com.spartanlabs.testing.support.webtools.udp.FakePeriodicSchedule
import com.spartanlabs.webtools.udp.Admission
import com.spartanlabs.webtools.udp.ClientChannel
import com.spartanlabs.webtools.udp.HandshakeCoordinator
import com.spartanlabs.webtools.udp.HandshakeProtocol
import com.spartanlabs.webtools.udp.UDPConnection
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 2 - the handshake state machine + inbound router in isolation. Its collaborators
// (connection factory, byte sink, registration callback, dispatch) are recording fakes and
// the dispatch is synchronous, so no socket or thread is involved.
@Tag("component")
class HandshakeCoordinatorTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val originA = InetSocketAddress(loopback, 40001)
    private val originB = InetSocketAddress(loopback, 40002)

    private val created = mutableListOf<Pair<String, InetSocketAddress>>()
    private val createdConnections = mutableListOf<FakeConnection>()
    private val createdChannels = mutableListOf<ClientChannel>()
    private val sent = mutableListOf<Pair<String, InetSocketAddress>>()
    private val sentBytes = mutableListOf<Pair<ByteArray, InetSocketAddress>>()
    private val registeredNames = mutableListOf<String>()
    private val disconnects = mutableListOf<Pair<String, com.spartanlabs.webtools.udp.DisconnectReason>>()
    private var sendResult: Result<Unit> = Result.success(Unit)
    private var idleTimeoutMillis: Long = 0L
    private val keepAliveSchedule = FakePeriodicSchedule()
    private val probeSchedule = FakePeriodicSchedule()

    private val admitCalls = mutableListOf<Triple<String, InetSocketAddress, String>>()
    private var admissionToReturn: Admission = Admission.Admitted
    private var admitThrows = false

    private val recordingAdmit: (name: String, peer: InetSocketAddress, credential: String) -> Admission =
        { name, peer, credential ->
            admitCalls += Triple(name, peer, credential)
            if (admitThrows) error("admit boom")
            admissionToReturn
        }

    private var connectionFactory: (name: String, peer: InetSocketAddress) -> FakeConnection =
        { name, peer -> FakeConnection(name, peer) }

    private fun newCoordinator() = HandshakeCoordinator(
        newConnection = { name, peer, channel ->
            created += name to peer
            createdChannels += channel
            connectionFactory(name, peer).also { createdConnections += it }
        },
        sender = { bytes, to ->
            sent += String(bytes, Charsets.UTF_8) to to
            sentBytes += bytes to to
            sendResult
        },
        onRegistered = { registeredNames += it.name },
        admit = recordingAdmit,
        dispatch = { it() },
        onDisconnect = { connection, reason -> disconnects += connection.name to reason },
        idleTimeoutMillis = idleTimeoutMillis,
        keepAliveSchedule = keepAliveSchedule,
        probeSchedule = probeSchedule,
    )

    private fun HandshakeCoordinator.registerClients(count: Int) {
        repeat(count) { accept(InetSocketAddress(loopback, 41000 + it), "Iam client$it") }
    }

    @Test
    fun `a first Iam registers, replies REGISTERED to the origin, and notifies`() {
        val coordinator = newCoordinator()

        assertTrue(coordinator.accept(originA, "Iam alice").isSuccess)

        assertEquals(1, coordinator.size)
        assertEquals("alice" to originA, created.single())
        assertEquals("REGISTERED" to originA, sent.single())
        assertEquals(listOf("alice"), registeredNames)
    }

    @Test
    fun `newConnection is passed the coordinator itself as the ClientChannel`() {
        val coordinator = newCoordinator()

        coordinator.accept(originA, "Iam alice")

        assertEquals(coordinator, createdChannels.single())
    }

    @Test
    fun `a retransmit from the same origin repeats REGISTERED and does not re-register`() {
        val coordinator = newCoordinator()

        coordinator.accept(originA, "Iam alice")
        coordinator.accept(originA, "Iam alice")

        assertEquals(1, coordinator.size)
        assertEquals(1, created.size)
        assertEquals(listOf("alice"), registeredNames, "onRegistered must fire once, not per retransmit")
        assertEquals(listOf("REGISTERED" to originA, "REGISTERED" to originA), sent)
    }

    @Test
    fun `the credential slot is claimed and only tokens past it are ignored`() {
        val coordinator = newCoordinator()

        assertTrue(coordinator.accept(originA, "Iam carol 10.0.0.9 junk").isSuccess)

        assertEquals("carol", created.single().first)
        assertEquals(Triple("carol", originA, "10.0.0.9"), admitCalls.single())
        assertEquals(1, HandshakeProtocol.extraTokenCount("Iam carol 10.0.0.9 junk".split(' ')))
        assertEquals(1, coordinator.size)
    }

    @Test
    fun `admit receives the parsed credential`() {
        val coordinator = newCoordinator()

        coordinator.accept(originA, "Iam alice tok123")

        assertEquals(Triple("alice", originA, "tok123"), admitCalls.last())
    }

    @Test
    fun `admit receives an empty credential for a bare Iam`() {
        val coordinator = newCoordinator()

        coordinator.accept(originA, "Iam alice")

        assertEquals(Triple("alice", originA, ""), admitCalls.single())
    }

    @Test
    fun `a refused handshake registers nothing, sends REFUSED, and reports success`() {
        val coordinator = newCoordinator()
        admissionToReturn = Admission.Refused("nope")

        assertTrue(coordinator.accept(originA, "Iam alice").isSuccess)

        assertTrue(created.isEmpty())
        assertEquals(0, coordinator.size)
        assertEquals(listOf("REFUSED nope" to originA), sent)
        assertTrue(registeredNames.isEmpty())
    }

    @Test
    fun `a refusal reason with spaces round-trips onto the wire`() {
        val coordinator = newCoordinator()
        admissionToReturn = Admission.Refused("over capacity now")

        coordinator.accept(originA, "Iam alice")

        assertEquals(listOf("REFUSED over capacity now" to originA), sent)
    }

    @Test
    fun `a refused newcomer does not evict the incumbent`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        sent.clear()
        admissionToReturn = Admission.Refused("x")

        coordinator.accept(originB, "Iam alice")

        assertEquals(1, coordinator.size)
        assertEquals(originA, coordinator.snapshot().single().origin)
        assertTrue(disconnects.isEmpty())
        assertEquals(1, created.size)
        sent.clear()
        coordinator.broadcast("ping")
        assertEquals(listOf("ping" to originA), sent)
    }

    @Test
    fun `an admitted newcomer under an existing name still supersedes`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        admitCalls.clear()

        coordinator.accept(originB, "Iam alice")

        assertEquals(
            listOf("alice" to com.spartanlabs.webtools.udp.DisconnectReason.SUPERSEDED),
            disconnects,
        )
        assertEquals(Triple("alice", originB, ""), admitCalls.single())
    }

    @Test
    fun `a retransmit from a registered origin does not re-consult admit`() {
        val coordinator = newCoordinator()

        coordinator.accept(originA, "Iam alice")
        coordinator.accept(originA, "Iam alice")

        assertEquals(1, admitCalls.size)
    }

    @Test
    fun `a throwing admit drops the handshake with no reply and no registration`() {
        val coordinator = newCoordinator()
        admitThrows = true

        assertTrue(coordinator.accept(originA, "Iam alice").isFailure)

        assertEquals(0, coordinator.size)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a nameless Iam fails and registers nothing`() {
        val coordinator = newCoordinator()

        assertTrue(coordinator.accept(originA, "Iam").isFailure)

        assertEquals(0, coordinator.size)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `when the reply fails the client is still registered but onRegistered is not called`() {
        val coordinator = newCoordinator()
        sendResult = Result.failure(RuntimeException("send failed"))

        assertTrue(coordinator.accept(originA, "Iam dave").isFailure)

        assertEquals(1, coordinator.size)
        assertEquals(1, created.size)
        assertTrue(registeredNames.isEmpty(), "onRegistered runs only after a successful send")
    }

    @Test
    fun `accept routes application data to the bound handler via dispatch`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val received = mutableListOf<String>()
        coordinator.bind(originA, received::add)

        assertTrue(coordinator.accept(originA, "hello world").isSuccess)

        assertEquals(listOf("hello world"), received)
    }

    @Test
    fun `accept drops a KA datagram - no dispatch, success`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val received = mutableListOf<String>()
        coordinator.bind(originA, received::add)

        assertTrue(coordinator.accept(originA, HandshakeProtocol.KEEPALIVE_TOKEN).isSuccess)

        assertTrue(received.isEmpty())
    }

    @Test
    fun `a datagram for an unregistered origin is dropped`() {
        val coordinator = newCoordinator()

        assertTrue(coordinator.accept(originA, "hello").isSuccess)
    }

    @Test
    fun `a datagram for a registered-but-not-actuated origin is dropped`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")

        assertTrue(coordinator.accept(originA, "hello").isSuccess)
    }

    @Test
    fun `a throwing handler does not propagate out of accept`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        coordinator.bind(originA) { error("boom") }

        assertTrue(coordinator.accept(originA, "hello").isSuccess)
    }

    @Test
    fun `bind then deregister removes the registration entirely`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val received = mutableListOf<String>()
        coordinator.bind(originA, received::add)
        coordinator.deregister(originA)

        coordinator.accept(originA, "hello")
        assertTrue(received.isEmpty())
        assertEquals(0, coordinator.size)

        // A fresh Iam from the now-deregistered origin must be treated as brand-new,
        // not a retransmit - newConnection is invoked again.
        created.clear()
        coordinator.accept(originA, "Iam alice")
        assertEquals(1, created.size)
    }

    @Test
    fun `a new Iam under an existing name from a different origin supersedes the stale registration`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val stale = createdConnections.single()
        sent.clear()

        coordinator.accept(originB, "Iam alice")

        assertEquals(1, coordinator.size)
        assertEquals(1, stale.terminateCalls)
        val surviving = coordinator.snapshot().single()
        assertEquals("alice", surviving.connection.name)
        assertEquals(originB, surviving.connection.peer)

        sent.clear()
        coordinator.broadcast("ping")
        assertEquals(listOf("ping" to originB), sent)
    }

    @Test
    fun `terminating the real connection removes it from Registrations end to end`() {
        connectionFactory = { name, peer -> FakeConnection(name, peer) }
        var realConnection: UDPConnection? = null
        val coordinator = HandshakeCoordinator(
            newConnection = { name, peer, channel ->
                UDPConnection(name, peer, channel).also { realConnection = it }
            },
            sender = { bytes, to -> sent += String(bytes, Charsets.UTF_8) to to; sendResult },
            onRegistered = { registeredNames += it.name },
            admit = recordingAdmit,
            dispatch = { it() },
            onDisconnect = { connection, reason -> disconnects += connection.name to reason },
            idleTimeoutMillis = idleTimeoutMillis,
            keepAliveSchedule = keepAliveSchedule,
            probeSchedule = probeSchedule,
        )

        coordinator.accept(originA, "Iam alice")
        assertEquals(1, coordinator.size)

        assertTrue(realConnection!!.terminate().isSuccess)

        assertEquals(0, coordinator.size)
        assertTrue(coordinator.accept(originA, "hello").isSuccess)
        assertNull(coordinator.snapshot().firstOrNull { it.origin == originA })
    }

    @Test
    fun `actuate after terminate on a real connection is a silent no-op`() {
        var realConnection: UDPConnection? = null
        val coordinator = HandshakeCoordinator(
            newConnection = { name, peer, channel ->
                UDPConnection(name, peer, channel).also { realConnection = it }
            },
            sender = { bytes, to -> sent += String(bytes, Charsets.UTF_8) to to; sendResult },
            onRegistered = { registeredNames += it.name },
            admit = recordingAdmit,
            dispatch = { it() },
            onDisconnect = { connection, reason -> disconnects += connection.name to reason },
            idleTimeoutMillis = idleTimeoutMillis,
            keepAliveSchedule = keepAliveSchedule,
            probeSchedule = probeSchedule,
        )

        coordinator.accept(originA, "Iam alice")
        assertTrue(realConnection!!.terminate().isSuccess)
        assertEquals(0, coordinator.size)

        val received = mutableListOf<String>()
        assertTrue(realConnection!!.actuate(received::add).isSuccess, "actuate after terminate must not throw")

        assertEquals(0, coordinator.size)
        assertTrue(coordinator.accept(originA, "hello").isSuccess)
        assertTrue(received.isEmpty(), "a re-bound handler must never fire once the registration is gone")
    }

    @Test
    fun `terminateAll on real connections empties Registrations`() {
        val coordinator = HandshakeCoordinator(
            newConnection = { name, peer, channel -> UDPConnection(name, peer, channel) },
            sender = { bytes, to -> sent += String(bytes, Charsets.UTF_8) to to; sendResult },
            onRegistered = { registeredNames += it.name },
            admit = recordingAdmit,
            dispatch = { it() },
            onDisconnect = { connection, reason -> disconnects += connection.name to reason },
            idleTimeoutMillis = idleTimeoutMillis,
            keepAliveSchedule = keepAliveSchedule,
            probeSchedule = probeSchedule,
        )

        coordinator.accept(originA, "Iam alice")
        coordinator.accept(originB, "Iam bob")
        assertEquals(2, coordinator.size)

        assertTrue(coordinator.terminateAll().isSuccess)

        assertEquals(0, coordinator.size)
    }

    @Test
    fun `snapshot reflects registration order`() {
        val coordinator = newCoordinator()

        coordinator.accept(originA, "Iam alice")
        coordinator.accept(originB, "Iam bob")

        assertEquals(listOf("alice", "bob"), coordinator.snapshot().map { it.connection.name })
    }

    @Test
    fun `actuateAll actuates every registered connection`() {
        val coordinator = newCoordinator()
        coordinator.registerClients(3)

        assertTrue(coordinator.actuateAll { }.isSuccess)

        assertTrue(createdConnections.all { it.actuateCalls == 1 })
    }

    @Test
    fun `actuateAll reports the first actuation failure`() {
        connectionFactory = { name, peer ->
            FakeConnection(
                name,
                peer,
                actuateResult = if (name == "client1") Result.failure(RuntimeException("boom")) else Result.success(Unit),
            )
        }
        val coordinator = newCoordinator()
        coordinator.registerClients(3)

        assertTrue(coordinator.actuateAll { }.isFailure)
    }

    @Test
    fun `broadcast sends the message to every registered peer`() {
        val coordinator = newCoordinator()
        coordinator.registerClients(3)
        sent.clear()

        assertTrue(coordinator.broadcast("ping").isSuccess)

        assertEquals(List(3) { "ping" to InetSocketAddress(loopback, 41000 + it) }, sent)
    }

    @Test
    fun `broadcast reports the first send failure`() {
        val coordinator = newCoordinator()
        coordinator.registerClients(2)
        sendResult = Result.failure(RuntimeException("send failed"))

        assertTrue(coordinator.broadcast("ping").isFailure)
    }

    @Test
    fun `terminateAll terminates every connection even when one fails`() {
        connectionFactory = { name, peer ->
            FakeConnection(
                name,
                peer,
                terminateResult = if (name == "client1") Result.failure(RuntimeException("stuck")) else Result.success(Unit),
            )
        }
        val coordinator = newCoordinator()
        coordinator.registerClients(3)

        val outcome = coordinator.terminateAll()

        assertTrue(outcome.isFailure)
        assertTrue(createdConnections.all { it.terminateCalls == 1 })
    }

    @Test
    fun `accept routes application data to a bound bytes handler with the exact undecoded bytes`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val received = mutableListOf<ByteArray>()
        coordinator.bindBytes(originA, received::add)
        val payload = byteArrayOf(0x00, 0x20, 0x4B)

        assertTrue(coordinator.accept(originA, payload, String(payload, Charsets.UTF_8).trim()).isSuccess)

        assertContentEquals(payload, received.single())
    }

    @Test
    fun `a bytes payload that trims to KA is still dropped as a keepalive`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val bytesSeen = mutableListOf<ByteArray>()
        coordinator.bindBytes(originA, bytesSeen::add)
        val payload = " KA ".toByteArray(Charsets.UTF_8)

        assertTrue(coordinator.accept(originA, payload, String(payload, Charsets.UTF_8).trim()).isSuccess)

        assertTrue(bytesSeen.isEmpty())
    }

    @Test
    fun `a bytes payload whose text begins with Iam is classified as a handshake, not delivered`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val bytesSeen = mutableListOf<ByteArray>()
        coordinator.bindBytes(originA, bytesSeen::add)
        val payload = "Iam bob".toByteArray(Charsets.UTF_8)

        coordinator.accept(originB, payload, String(payload, Charsets.UTF_8).trim())

        assertTrue(bytesSeen.isEmpty())
    }

    @Test
    fun `bindBytes clears a text handler and bind clears a bytes handler`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val textSeen = mutableListOf<String>()
        val bytesSeen = mutableListOf<ByteArray>()

        coordinator.bind(originA, textSeen::add)
        coordinator.bindBytes(originA, bytesSeen::add)
        coordinator.accept(originA, "hello".toByteArray(Charsets.UTF_8), "hello")
        assertTrue(textSeen.isEmpty())
        assertEquals(1, bytesSeen.size)

        coordinator.bind(originA, textSeen::add)
        coordinator.accept(originA, "world".toByteArray(Charsets.UTF_8), "world")
        assertEquals(listOf("world"), textSeen)
        assertEquals(1, bytesSeen.size)
    }

    @Test
    fun `deliverData with no handler bound drops a binary datagram`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")

        assertTrue(coordinator.accept(originA, byteArrayOf(0x00, 0x01), " ").isSuccess)
    }

    @Test
    fun `a throwing bytes handler does not propagate out of accept`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        coordinator.bindBytes(originA) { error("boom") }

        assertTrue(coordinator.accept(originA, byteArrayOf(1, 2, 3), "x").isSuccess)
    }

    @Test
    fun `actuateAllBytes actuates every registered connection via actuateBytes`() {
        val coordinator = newCoordinator()
        coordinator.registerClients(3)

        assertTrue(coordinator.actuateAllBytes { }.isSuccess)

        assertTrue(createdConnections.all { it.lastOnBytes != null })
    }

    @Test
    fun `broadcast bytes sends the exact bytes to every registered peer at the observed origin`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam spoofer 8.8.8.8")
        sentBytes.clear()
        val payload = byteArrayOf(0x00, -0x80, 0x0A)

        assertTrue(coordinator.broadcast(payload).isSuccess)

        val (bytes, to) = sentBytes.single()
        assertContentEquals(payload, bytes)
        assertEquals(originA, to)
    }

    @Test
    fun `broadcast targets are never a payload-claimed address`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam spoofer 8.8.8.8")
        sent.clear()

        coordinator.broadcast("ping")

        assertEquals(listOf("ping" to originA), sent)
        assertFalse(sent.any { it.second.hostString == "8.8.8.8" })
    }

    // --- connection liveness (Issue #10) ---

    private val TIMEOUT = com.spartanlabs.webtools.udp.DisconnectReason.TIMEOUT
    private val SUPERSEDED = com.spartanlabs.webtools.udp.DisconnectReason.SUPERSEDED
    private val TERMINATED = com.spartanlabs.webtools.udp.DisconnectReason.TERMINATED

    @Test
    fun `accept refreshes lastInboundAt for data, KA and a retransmitted Iam when tracking is on`() {
        idleTimeoutMillis = 200L
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val reg = coordinator.snapshot().single()

        listOf("hello", HandshakeProtocol.KEEPALIVE_TOKEN, "Iam alice").forEach { datagram ->
            reg.lastInboundAt = 0L
            coordinator.accept(originA, datagram)
            assertTrue(reg.lastInboundAt > 0L, "'$datagram' must refresh lastInboundAt")
        }
    }

    @Test
    fun `accept does not touch lastInboundAt when tracking is off`() {
        idleTimeoutMillis = 0L
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val reg = coordinator.snapshot().single()
        reg.lastInboundAt = 42L

        coordinator.accept(originA, "hello")
        coordinator.accept(originA, HandshakeProtocol.KEEPALIVE_TOKEN)

        assertEquals(42L, reg.lastInboundAt)
    }

    @Test
    fun `sweepIdleConnections reports an overdue registration once and leaves it registered`() {
        idleTimeoutMillis = 200L
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val reg = coordinator.snapshot().single()
        // 1s ago in nanos - well past the 200 ms idle threshold set above (idiom reused below).
        reg.lastInboundAt = System.nanoTime() - 1_000_000_000L

        coordinator.sweepIdleConnections()
        coordinator.sweepIdleConnections()

        assertEquals(listOf("alice" to TIMEOUT), disconnects)
        assertEquals(1, coordinator.size)
        assertEquals(originA, coordinator.snapshot().single().origin)
        sent.clear()
        coordinator.broadcast("ping")
        assertEquals(listOf("ping" to originA), sent)
    }

    @Test
    fun `a KA between sweeps clears the timedOut latch and a later sweep re-reports`() {
        idleTimeoutMillis = 200L
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val reg = coordinator.snapshot().single()

        reg.lastInboundAt = System.nanoTime() - 1_000_000_000L
        coordinator.sweepIdleConnections()
        coordinator.accept(originA, HandshakeProtocol.KEEPALIVE_TOKEN)
        reg.lastInboundAt = System.nanoTime() - 1_000_000_000L
        coordinator.sweepIdleConnections()

        assertEquals(listOf("alice" to TIMEOUT, "alice" to TIMEOUT), disconnects)
    }

    @Test
    fun `a not-yet-overdue registration is not reported`() {
        idleTimeoutMillis = 10_000L
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")

        coordinator.sweepIdleConnections()

        assertTrue(disconnects.isEmpty())
    }

    @Test
    fun `sweep never reports when tracking is off`() {
        idleTimeoutMillis = 0L
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        coordinator.snapshot().single().lastInboundAt = System.nanoTime() - 10_000_000_000L

        coordinator.sweepIdleConnections()

        assertTrue(disconnects.isEmpty())
    }

    @Test
    fun `a same-name Iam from a new origin fires SUPERSEDED and not TERMINATED`() {
        idleTimeoutMillis = 200L
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")

        coordinator.accept(originB, "Iam alice")

        assertEquals(listOf("alice" to SUPERSEDED), disconnects)
    }

    @Test
    fun `a real terminate fires TERMINATED`() {
        idleTimeoutMillis = 200L
        var realConnection: UDPConnection? = null
        val coordinator = HandshakeCoordinator(
            newConnection = { name, peer, channel -> UDPConnection(name, peer, channel).also { realConnection = it } },
            sender = { bytes, to -> sent += String(bytes, Charsets.UTF_8) to to; sendResult },
            onRegistered = { registeredNames += it.name },
            admit = recordingAdmit,
            dispatch = { it() },
            onDisconnect = { connection, reason -> disconnects += connection.name to reason },
            idleTimeoutMillis = idleTimeoutMillis,
            keepAliveSchedule = keepAliveSchedule,
            probeSchedule = probeSchedule,
        )
        coordinator.accept(originA, "Iam alice")

        assertTrue(realConnection!!.terminate().isSuccess)

        assertEquals(listOf("alice" to TERMINATED), disconnects)
    }

    @Test
    fun `after stopNotifying neither terminateAll nor deregister fires anything`() {
        idleTimeoutMillis = 200L
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        coordinator.accept(originB, "Iam bob")

        coordinator.stopNotifying()
        coordinator.terminateAll()
        coordinator.deregister(originA)

        assertTrue(disconnects.isEmpty())
    }

    @Test
    fun `a throwing onDisconnect does not stop the sweep reporting other overdue registrations`() {
        idleTimeoutMillis = 200L
        val coordinator = HandshakeCoordinator(
            newConnection = { name, peer, _ -> FakeConnection(name, peer) },
            sender = { bytes, to -> sent += String(bytes, Charsets.UTF_8) to to; sendResult },
            onRegistered = { registeredNames += it.name },
            admit = recordingAdmit,
            dispatch = { it() },
            onDisconnect = { connection, reason ->
                disconnects += connection.name to reason
                if (connection.name == "client0") error("boom")
            },
            idleTimeoutMillis = idleTimeoutMillis,
            keepAliveSchedule = keepAliveSchedule,
            probeSchedule = probeSchedule,
        )
        coordinator.registerClients(3)
        val past = System.nanoTime() - 1_000_000_000L
        coordinator.snapshot().forEach { it.lastInboundAt = past }

        coordinator.sweepIdleConnections()

        assertEquals(setOf("client0", "client1", "client2"), disconnects.map { it.first }.toSet())
    }

    // --- scheduled keepalive (Issue #12) ---

    @Test
    fun `send does not touch lastOutboundAt before the first scheduleKeepAlive`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val reg = coordinator.snapshot().single()
        reg.lastOutboundAt = 7L

        coordinator.send("hi".toByteArray(Charsets.UTF_8), originA)
        coordinator.broadcast("hey")

        assertEquals(7L, reg.lastOutboundAt)
    }

    @Test
    fun `scheduleKeepAlive turns tracking on so a later send advances lastOutboundAt`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val reg = coordinator.snapshot().single()
        assertTrue(coordinator.scheduleKeepAlive(originA, 300L).isSuccess)
        assertEquals(300L, keepAliveSchedule.scheduled.getValue(originA).intervalMillis)

        reg.lastOutboundAt = 7L
        coordinator.send("hi".toByteArray(Charsets.UTF_8), originA)
        assertTrue(reg.lastOutboundAt > 7L)

        reg.lastOutboundAt = 7L
        coordinator.broadcast("hey")
        assertTrue(reg.lastOutboundAt > 7L)
    }

    @Test
    fun `the recorded tick sends exactly one KA when lastOutboundAt is in the past`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        coordinator.scheduleKeepAlive(originA, 300L)
        coordinator.snapshot().single().lastOutboundAt = System.nanoTime() - 1_000_000_000L
        sent.clear()

        keepAliveSchedule.tick(originA)

        assertEquals(listOf(HandshakeProtocol.KEEPALIVE_TOKEN to originA), sent)
    }

    @Test
    fun `the recorded tick sends nothing when lastOutboundAt is fresh`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        coordinator.scheduleKeepAlive(originA, 300L)
        coordinator.snapshot().single().lastOutboundAt = System.nanoTime()
        sent.clear()

        keepAliveSchedule.tick(originA)

        assertTrue(sent.isEmpty())
    }

    @Test
    fun `the tick is a no-op for a peer whose registration has been removed`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        coordinator.scheduleKeepAlive(originA, 300L)
        val tick = keepAliveSchedule.scheduleCalls.single().tick
        coordinator.deregister(originA)
        sent.clear()

        tick() // must not throw, must not send

        assertTrue(sent.isEmpty())
    }

    @Test
    fun `deregister cancels the keepalive schedule for that peer`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        coordinator.scheduleKeepAlive(originA, 300L)

        coordinator.deregister(originA)

        assertTrue(originA in keepAliveSchedule.cancels)
    }

    @Test
    fun `a same-name supersede cancels the stale origin's keepalive schedule`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        coordinator.scheduleKeepAlive(originA, 300L)

        coordinator.accept(originB, "Iam alice")

        assertTrue(originA in keepAliveSchedule.cancels)
    }

    @Test
    fun `shutKeepAlive shuts the schedule`() {
        val coordinator = newCoordinator()

        coordinator.shutKeepAlive()

        assertEquals(1, keepAliveSchedule.shutdownCalls)
    }

    @Test
    fun `a tick whose send fails logs rather than throws`() {
        sendResult = Result.failure(RuntimeException("send down"))
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        coordinator.scheduleKeepAlive(originA, 300L)
        coordinator.snapshot().single().lastOutboundAt = System.nanoTime() - 1_000_000_000L

        keepAliveSchedule.tick(originA) // must not throw
    }

    // --- link-quality probe (Issue #13) ---

    @Test
    fun `an inbound PING from a registered origin is answered with exactly one PONG and nothing is dispatched`() {
        val dispatched = mutableListOf<String>()
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        coordinator.bind(originA) { dispatched += it }
        sent.clear()

        assertTrue(coordinator.accept(originA, "PING 7").isSuccess)

        assertEquals(listOf("PONG 7" to originA), sent)
        assertTrue(dispatched.isEmpty())
    }

    @Test
    fun `an inbound PING from an unregistered origin is dropped with no send`() {
        val coordinator = newCoordinator()
        sent.clear()

        assertTrue(coordinator.accept(originA, "PING 1").isSuccess)

        assertTrue(sent.isEmpty())
    }

    @Test
    fun `scheduleProbe then the recorded tick sends one PING and creates the estimator`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")

        assertTrue(coordinator.scheduleProbe(originA, 300L).isSuccess)
        assertEquals(300L, probeSchedule.scheduled.getValue(originA).intervalMillis)
        sent.clear()

        probeSchedule.tick(originA)

        assertEquals(1, sent.size)
        assertTrue(HandshakeProtocol.isProbeRequest(sent.single().first), "was ${sent.single().first}")
        assertEquals(originA, sent.single().second)
        assertNotNull(coordinator.snapshot().single().linkQuality)
    }

    @Test
    fun `an inbound PONG for a live probe populates linkQualityOf`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        coordinator.scheduleProbe(originA, 300L)
        probeSchedule.tick(originA)
        val token = sent.last().first.substringAfter(' ').trim()

        assertTrue(coordinator.accept(originA, "PONG $token").isSuccess)

        assertNotNull(coordinator.linkQualityOf(originA))
    }

    @Test
    fun `scheduleProbe for an unknown peer fails`() {
        val coordinator = newCoordinator()

        assertTrue(coordinator.scheduleProbe(originA, 300L).isFailure)
    }

    @Test
    fun `deregister cancels the probe schedule for that peer`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        coordinator.scheduleProbe(originA, 300L)

        coordinator.deregister(originA)

        assertTrue(originA in probeSchedule.cancels)
    }

    @Test
    fun `a same-name supersede cancels the stale origin's probe schedule`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        coordinator.scheduleProbe(originA, 300L)

        coordinator.accept(originB, "Iam alice")

        assertTrue(originA in probeSchedule.cancels)
    }

    @Test
    fun `shutProbe shuts the probe schedule`() {
        val coordinator = newCoordinator()

        coordinator.shutProbe()

        assertEquals(1, probeSchedule.shutdownCalls)
    }
}
