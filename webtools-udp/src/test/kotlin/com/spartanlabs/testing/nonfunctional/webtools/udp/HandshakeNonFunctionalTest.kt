package com.spartanlabs.testing.nonfunctional.webtools.udp

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.udp.FakeConnection
import com.spartanlabs.testing.support.webtools.udp.FakePeriodicSchedule
import com.spartanlabs.testing.support.webtools.udp.captureLogsOf
import com.spartanlabs.webtools.udp.Admission
import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.HandshakeCoordinator
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import com.spartanlabs.webtools.udp.TransportWireFormat
import com.spartanlabs.webtools.udp.UdpChannel
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 4c - non-functional properties of the multiplexed handshake / data path: the reply
// is only ever addressed to the datagram source; a hostile malformed line draws one bounded
// reply to its origin and never reaches the log; the server binds exactly one socket
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
            admit = { _, _, _ -> Admission.Admitted },
            dispatch = { it() },
            onDisconnect = { _, _ -> },
            idleTimeoutMillis = 0L,
            keepAliveSchedule = FakePeriodicSchedule(),
            probeSchedule = FakePeriodicSchedule(),
            retransmitSchedule = FakePeriodicSchedule(),
            reliableMaxMessageBytes = UdpChannel.DEFAULT_MAX_RELIABLE_MESSAGE_BYTES,
        )

        coordinator.accept(origin, "Iam spoofer 8.8.8.8 1.1.1.1")
        coordinator.accept(origin, "Iam spoofer 203.0.113.9")

        assertEquals(listOf(origin, origin), replyTargets)
    }

    @Test
    fun `a malformed Iam carrying CR-LF and a credential-like token draws one bounded reply to its origin and stays out of the log`() {
        val replies = mutableListOf<Pair<ByteArray, InetSocketAddress>>()
        val coordinator = HandshakeCoordinator(
            newConnection = { name, peer, _ -> FakeConnection(name, peer) },
            sender = { bytes, target -> replies += bytes to target; Result.success(Unit) },
            onRegistered = {},
            admit = { _, _, _ -> Admission.Admitted },
            dispatch = { it() },
            onDisconnect = { _, _ -> },
            idleTimeoutMillis = 0L,
            keepAliveSchedule = FakePeriodicSchedule(),
            probeSchedule = FakePeriodicSchedule(),
            retransmitSchedule = FakePeriodicSchedule(),
            reliableMaxMessageBytes = UdpChannel.DEFAULT_MAX_RELIABLE_MESSAGE_BYTES,
        )
        // The two spaces leave the credential slot empty, so the line is malformed. The name
        // token carries CR/LF and a forged log line; the last token stands in for an access token.
        val hostileLine = "Iam evil\r\nFORGED-LOG-LINE  sk_live_SECRET"

        captureLogsOf(HandshakeCoordinator::class.java) { events ->
            assertTrue(coordinator.accept(origin, hostileLine).isSuccess)

            val event = events.single()
            val message = event.formattedMessage
            assertEquals(Level.WARN, event.level)
            assertNull(event.throwableProxy, "no exception object is attached")
            assertTrue(message.contains("credential empty"), message)
            assertFalse(message.contains('\r'), message)
            assertFalse(message.contains('\n'), message)
            assertFalse(message.contains("FORGED-LOG-LINE"), message)
            assertFalse(message.contains("evil"), message)
            assertFalse(message.contains("sk_live_SECRET"), message)
        }

        val (bytes, target) = replies.single()
        assertEquals(origin, target)
        assertTrue(bytes.size <= 17, "the reply is ${bytes.size} bytes")
        assertEquals("REFUSED malformed", String(bytes, Charsets.UTF_8))
        assertEquals(0, coordinator.size)
    }

    @Test
    fun `a retransmit storm from one origin allocates exactly one connection and answers each`() {
        var created = 0
        var replies = 0
        val coordinator = HandshakeCoordinator(
            newConnection = { name, peer, _ -> created++; FakeConnection(name, peer) },
            sender = { _, _ -> replies++; Result.success(Unit) },
            onRegistered = {},
            admit = { _, _, _ -> Admission.Admitted },
            dispatch = { it() },
            onDisconnect = { _, _ -> },
            idleTimeoutMillis = 0L,
            keepAliveSchedule = FakePeriodicSchedule(),
            probeSchedule = FakePeriodicSchedule(),
            retransmitSchedule = FakePeriodicSchedule(),
            reliableMaxMessageBytes = UdpChannel.DEFAULT_MAX_RELIABLE_MESSAGE_BYTES,
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
                val out = TransportWireFormat.unreliableDatagram(i.toString().toByteArray(Charsets.UTF_8))
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
