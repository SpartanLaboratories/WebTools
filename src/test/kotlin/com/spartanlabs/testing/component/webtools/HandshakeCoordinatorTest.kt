package com.spartanlabs.testing.component.webtools

import com.spartanlabs.testing.support.webtools.FakeConnection
import com.spartanlabs.webtools.HandshakeCoordinator
import com.spartanlabs.webtools.HandshakeProtocol
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 2 - the handshake state machine in isolation. Its three collaborators (connection
// factory, reply sink, registration callback) are recording fakes, so no socket is bound.
@Tag("component")
class HandshakeCoordinatorTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val originA = InetSocketAddress(loopback, 40001)
    private val originB = InetSocketAddress(loopback, 40002)

    private val created = mutableListOf<Triple<String, InetSocketAddress, HandshakeProtocol.PortPair>>()
    private val replies = mutableListOf<Pair<String, InetSocketAddress>>()
    private val registeredNames = mutableListOf<String>()
    private var replyResult: Result<Unit> = Result.success(Unit)

    private fun newCoordinator() = HandshakeCoordinator(
        newConnection = { name, origin, ports ->
            created += Triple(name, origin, ports)
            FakeConnection(name, ports.sendPort, ports.receivePort, origin.address)
        },
        reply = { body, origin ->
            replies += body to origin
            replyResult
        },
        onRegistered = { registeredNames += it.name },
    )

    private fun iam(name: String, vararg extra: String) = listOf("Iam", name, *extra)

    @Test
    fun `a message whose verb is not Iam is ignored with no side effects`() {
        val coordinator = newCoordinator()

        assertTrue(coordinator.handle(originA, listOf("HELLO", "there")).isSuccess)

        assertEquals(0, coordinator.size)
        assertTrue(created.isEmpty())
        assertTrue(replies.isEmpty())
        assertTrue(registeredNames.isEmpty())
    }

    @Test
    fun `a nameless Iam fails and registers nothing`() {
        val coordinator = newCoordinator()

        assertTrue(coordinator.handle(originA, listOf("Iam")).isFailure)

        assertEquals(0, coordinator.size)
        assertTrue(created.isEmpty())
        assertTrue(replies.isEmpty())
        assertTrue(registeredNames.isEmpty())
    }

    @Test
    fun `a first Iam allocates the first port pair, replies, and notifies`() {
        val coordinator = newCoordinator()

        assertTrue(coordinator.handle(originA, iam("alice")).isSuccess)

        val expectedPorts = HandshakeProtocol.portPairFor(0)
        assertEquals(1, coordinator.size)
        assertEquals(Triple("alice", originA, expectedPorts), created.single())
        assertEquals(HandshakeProtocol.txrxonReply(expectedPorts) to originA, replies.single())
        assertEquals(listOf("alice"), registeredNames)
    }

    @Test
    fun `a retransmit from the same origin repeats the reply and does not re-register`() {
        val coordinator = newCoordinator()

        coordinator.handle(originA, iam("alice"))
        coordinator.handle(originA, iam("alice"))

        assertEquals(1, coordinator.size)
        assertEquals(1, created.size)
        assertEquals(listOf("alice"), registeredNames, "onRegistered must fire once, not per retransmit")
        assertEquals(2, replies.size)
        assertEquals(replies[0], replies[1], "the retransmit reply must be identical")
    }

    @Test
    fun `a second distinct origin gets the next port pair`() {
        val coordinator = newCoordinator()

        coordinator.handle(originA, iam("alice"))
        coordinator.handle(originB, iam("bob"))

        assertEquals(2, coordinator.size)
        assertEquals(HandshakeProtocol.portPairFor(0), created[0].third)
        assertEquals(HandshakeProtocol.portPairFor(1), created[1].third)
        assertEquals(listOf("alice", "bob"), registeredNames)
    }

    @Test
    fun `tokens after the name are ignored`() {
        val coordinator = newCoordinator()

        assertTrue(coordinator.handle(originA, iam("carol", "10.0.0.9", "junk")).isSuccess)

        assertEquals("carol", created.single().first)
        assertEquals(1, coordinator.size)
    }

    @Test
    fun `when the reply fails the client is still registered but onRegistered is not called`() {
        val coordinator = newCoordinator()
        replyResult = Result.failure(RuntimeException("send failed"))

        assertTrue(coordinator.handle(originA, iam("dave")).isFailure)

        assertEquals(1, coordinator.size, "the connection is registered before the reply is attempted")
        assertEquals(1, created.size)
        assertTrue(registeredNames.isEmpty(), "onRegistered runs only after a successful reply")
    }

    @Test
    fun `snapshot reflects registration order`() {
        val coordinator = newCoordinator()

        coordinator.handle(originA, iam("alice"))
        coordinator.handle(originB, iam("bob"))

        assertEquals(listOf("alice", "bob"), coordinator.snapshot().map { it.connection.name })
    }
}
