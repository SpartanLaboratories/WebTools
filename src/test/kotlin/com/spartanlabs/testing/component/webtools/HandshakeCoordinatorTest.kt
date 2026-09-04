package com.spartanlabs.testing.component.webtools

import com.spartanlabs.testing.support.webtools.FakeConnection
import com.spartanlabs.webtools.ClientChannel
import com.spartanlabs.webtools.HandshakeCoordinator
import com.spartanlabs.webtools.HandshakeProtocol
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `bind then unbind toggles delivery`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val received = mutableListOf<String>()
        coordinator.bind(originA, received::add)
        coordinator.unbind(originA)

        coordinator.accept(originA, "hello")

        assertTrue(received.isEmpty())
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
    fun `broadcast targets are never a payload-claimed address`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam spoofer 8.8.8.8")
        sent.clear()

        coordinator.broadcast("ping")

        assertEquals(listOf("ping" to originA), sent)
        assertFalse(sent.any { it.second.hostString == "8.8.8.8" })
    }
}
