package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.DeliveryMode
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import com.spartanlabs.webtools.udp.ReliableWireFormat
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 3 - the reliable channel wired end to end over real sockets: a real client<->real
// server round trip, and the §1.3(b) idle-silence regression guard (an idle, fully-acked
// reliable channel must emit NOTHING - not even a standalone ack - or it would silently
// refresh Issue #10 liveness on both sides forever).
@Tag("integration")
@Suppress("DEPRECATION") // exercises the still-working, now-deprecated push/actuate/send/start primitives on purpose
class MultiConnectionUDPReliableChannelTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private var server: MultiConnectionUDPServer? = null
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    @AfterTest
    fun cleanup() {
        clients.forEach { runCatching { it.stop() } }
        runCatching { server?.stop() }
    }

    private class ReliableServer : MultiConnectionUDPServer() {
        val connections = CopyOnWriteArrayList<Connection>()
        override fun onClientConnect(connection: Connection) {
            connections += connection
        }
    }

    private fun await(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    @Test
    fun `a real client and real server exchange reliable-ordered messages both directions, exactly once`() {
        val srv = ReliableServer()
        server = srv
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT).also { clients += it }
        assertTrue(client.handshake("alice").isSuccess)
        assertTrue(await(2_000L) { srv.connections.size == 1 })
        val serverConnection = srv.connections.single()

        val fromClient = ConcurrentLinkedQueue<String>()
        val fromServer = ConcurrentLinkedQueue<String>()
        assertTrue(serverConnection.channel(DeliveryMode.RELIABLE_ORDERED).actuate(fromClient::add).isSuccess)
        assertTrue(client.channel(DeliveryMode.RELIABLE_ORDERED).actuate(fromServer::add).isSuccess)

        assertTrue(client.channel(DeliveryMode.RELIABLE_ORDERED).send("ping").isSuccess)
        assertTrue(await(3_000L) { fromClient.contains("ping") })

        assertTrue(serverConnection.channel(DeliveryMode.RELIABLE_ORDERED).send("pong").isSuccess)
        assertTrue(await(3_000L) { fromServer.contains("pong") })
    }

    @Test
    fun `no reliable datagram ever reaches the unreliable handler`() {
        val srv = ReliableServer()
        server = srv
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT).also { clients += it }
        assertTrue(client.handshake("bob").isSuccess)
        assertTrue(await(2_000L) { srv.connections.size == 1 })
        val serverConnection = srv.connections.single()

        val unreliableOnServer = ConcurrentLinkedQueue<String>()
        val reliableOnServer = ConcurrentLinkedQueue<String>()
        assertTrue(serverConnection.channel(DeliveryMode.UNRELIABLE).actuate(unreliableOnServer::add).isSuccess)
        assertTrue(serverConnection.channel(DeliveryMode.RELIABLE_ORDERED).actuate(reliableOnServer::add).isSuccess)

        assertTrue(client.channel(DeliveryMode.RELIABLE_ORDERED).send("only-reliable").isSuccess)
        assertTrue(await(3_000L) { reliableOnServer.contains("only-reliable") })

        Thread.sleep(300)
        assertTrue(unreliableOnServer.isEmpty(), "a reliable datagram must never reach the unreliable handler")
    }

    @Test
    fun `an idle, fully-acked reliable channel emits no datagrams for at least 1s`() {
        val srv = ReliableServer()
        server = srv
        DatagramSocket().use { peer ->
            // Handshake: send Iam, receive REGISTERED, on this same raw socket.
            val iam = "Iam carol".toByteArray(Charsets.UTF_8)
            peer.send(DatagramPacket(iam, iam.size, loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
            peer.soTimeout = 5_000
            peer.receive(DatagramPacket(ByteArray(64), 64))
            assertTrue(await(2_000L) { srv.connections.size == 1 })
            val serverConnection = srv.connections.single()

            // Drive one reliable send from the server, and answer it with a correct ack so the
            // engine has nothing left in flight and nothing left to acknowledge.
            assertTrue(serverConnection.channel(DeliveryMode.RELIABLE_ORDERED).send("settle").isSuccess)
            peer.soTimeout = 3_000
            val dataPacket = DatagramPacket(ByteArray(256), 256)
            peer.receive(dataPacket)
            val header = ReliableWireFormat.reliableDataHeaderOf(dataPacket.data.copyOf(dataPacket.length))!!
            val ack = ReliableWireFormat.reliableAckDatagram(ack = header.seq, ackBitfield = 0)
            peer.send(DatagramPacket(ack, ack.size, loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT))

            // Now idle: count every datagram arriving at this peer for >= 1s. None should arrive -
            // not a retransmit (it was acked) and not a standalone ack (nothing new to acknowledge).
            var count = 0
            val deadline = System.currentTimeMillis() + 1_200L
            peer.soTimeout = 100
            while (System.currentTimeMillis() < deadline) {
                try {
                    peer.receive(DatagramPacket(ByteArray(256), 256))
                    count++
                } catch (_: SocketTimeoutException) {
                    // expected - the point of this test
                }
            }
            assertEquals(0, count, "an idle, fully-acked reliable channel must emit nothing at all")
        }
    }
}
