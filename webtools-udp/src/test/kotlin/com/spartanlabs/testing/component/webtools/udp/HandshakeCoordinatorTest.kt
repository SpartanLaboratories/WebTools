package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakeConnection
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
    private var sendResult: Result<Unit> = Result.success(Unit)

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
        dispatch = { it() },
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
    fun `tokens after the name are ignored`() {
        val coordinator = newCoordinator()

        assertTrue(coordinator.accept(originA, "Iam carol 10.0.0.9 junk").isSuccess)

        assertEquals("carol", created.single().first)
        assertEquals(1, coordinator.size)
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
            dispatch = { it() },
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
            dispatch = { it() },
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
            dispatch = { it() },
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
}
