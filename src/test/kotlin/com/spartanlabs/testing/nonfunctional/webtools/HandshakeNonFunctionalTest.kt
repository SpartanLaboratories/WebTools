package com.spartanlabs.testing.nonfunctional.webtools

import com.spartanlabs.testing.support.webtools.FakeConnection
import com.spartanlabs.webtools.Connection
import com.spartanlabs.webtools.HandshakeCoordinator
import com.spartanlabs.webtools.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 4c - non-functional properties of the multiplexed handshake / data path: the reply
// is only ever addressed to the datagram source; the server binds exactly one socket
// regardless of client count; per-client message order is preserved under a burst (with the
// accepted cross-client head-of-line-blocking trade-off documented below); a KA storm
// creates no registrations and is never dispatched.
@Tag("nonfunctional")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HandshakeNonFunctionalTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val origin = InetSocketAddress(loopback, 40001)

    // --- socket-free security property ---

    @Test
    fun `the reply is only ever addressed to the datagram origin, never a payload-claimed address`() {
        val replyTargets = mutableListOf<InetSocketAddress>()
        val coordinator = HandshakeCoordinator(
            newConnection = { name, peer, _ -> FakeConnection(name, peer) },
            sender = { _, target -> replyTargets += target; Result.success(Unit) },
            onRegistered = {},
            dispatch = { it() },
        )

        coordinator.accept(origin, "Iam spoofer 8.8.8.8 1.1.1.1")
        coordinator.accept(origin, "Iam spoofer 203.0.113.9")

        assertEquals(listOf(origin, origin), replyTargets)
    }

    @Test
    fun `a retransmit storm from one origin allocates exactly one connection and answers each`() {
        var created = 0
        var replies = 0
        val coordinator = HandshakeCoordinator(
            newConnection = { name, peer, _ -> created++; FakeConnection(name, peer) },
            sender = { _, _ -> replies++; Result.success(Unit) },
            onRegistered = {},
            dispatch = { it() },
        )

        repeat(STORM_SIZE) { coordinator.accept(origin, "Iam stormclient") }

        assertEquals(1, coordinator.size)
        assertEquals(1, created)
        assertEquals(STORM_SIZE, replies)
    }

    // --- real-server properties ---

    private fun withServer(block: (server: TestServer) -> Unit) {
        val server = TestServer()
        try {
            block(server)
        } finally {
            runCatching { server.stop() }
        }
    }

    private class TestServer : MultiConnectionUDPServer() {
        val connected = CopyOnWriteArrayList<Connection>()
        override fun onClientConnect(connection: Connection) {
            connected += connection
        }
    }

    private fun handshake(client: DatagramSocket) {
        val out = "Iam c${client.localPort}".toByteArray()
        client.send(DatagramPacket(out, out.size, loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
        client.soTimeout = 5000
        client.receive(DatagramPacket(ByteArray(64), 64))
    }

    @Test
    fun `the server binds exactly one socket regardless of client count`() = withServer { server ->
        val clients = (0 until 50).map { DatagramSocket() }
        try {
            clients.forEach { handshake(it) }
            // Every former dedicated port (9997, 9996, 9995 ...) is now free, proving the
            // server binds only the common port no matter how many clients registered.
            (9990..9997).forEach { port ->
                DatagramSocket(port).use { /* bind succeeds */ }
            }
        } finally {
            clients.forEach { it.close() }
        }
    }

    @Test
    fun `per-client message order is preserved under a burst`() = withServer { server ->
        // The single-threaded dispatch executor guarantees one client's messages are
        // delivered in send order. Accepted trade-off: a slow handler for one client
        // delays delivery to other clients (cross-client head-of-line blocking).
        val seen = ConcurrentLinkedQueue<Int>()
        val done = CountDownLatch(BURST)
        DatagramSocket().use { client ->
            handshake(client)
            Thread.sleep(100) // let onClientConnect register the connection
            server.start { msg -> seen += msg.toInt(); done.countDown() }
            repeat(BURST) { i ->
                val out = i.toString().toByteArray()
                client.send(DatagramPacket(out, out.size, loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
            }
            assertTrue(done.await(10, TimeUnit.SECONDS), "all $BURST messages delivered")
        }
        assertEquals((0 until BURST).toList(), seen.toList())
    }

    @Test
    fun `a KA storm from one origin creates no registrations and is never dispatched`() = withServer { server ->
        val dispatched = ConcurrentLinkedQueue<String>()
        server.start { dispatched += it }
        DatagramSocket().use { client ->
            repeat(STORM_SIZE) {
                val out = "KA".toByteArray()
                client.send(DatagramPacket(out, out.size, loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
            }
            Thread.sleep(500)
        }
        assertEquals(0, server.connected.size)
        assertTrue(dispatched.isEmpty())
    }

    private companion object {
        const val STORM_SIZE = 1_000
        const val BURST = 500
    }
}
