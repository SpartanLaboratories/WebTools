package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.captureLogsOf
import com.spartanlabs.testing.support.webtools.udp.hasWarnContaining
import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.DeliveryMode
import com.spartanlabs.webtools.udp.HandshakeCoordinator
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import com.spartanlabs.webtools.udp.ReliableWireFormat
import com.spartanlabs.webtools.udp.TransportWireFormat
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

// Level 3 - the channel-byte guard end to end (Issue #40), against a real MultiConnectionUDPServer
// and a real MultiConnectionUDPClient over loopback. Mirrors MultiConnectionUDPServerE2ETest's
// raw-socket-peer shape for the server side, and FramingNonFunctionalTest's raw-server shape for
// the client side (webtools-udp/src/test/kotlin/com/spartanlabs/testing/nonfunctional/webtools/udp/FramingNonFunctionalTest.kt:140-149).
@Tag("integration")
class ChannelByteGuardIntegrationTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private class EchoServer : MultiConnectionUDPServer() {
        val unreliable = CopyOnWriteArrayList<ByteArray>()
        val reliable = CopyOnWriteArrayList<ByteArray>()
        override fun onClientConnect(connection: Connection) {
            connection.channel(DeliveryMode.UNRELIABLE).actuateBytes { unreliable += it }
            connection.channel(DeliveryMode.RELIABLE_ORDERED).actuateBytes { reliable += it }
        }
    }

    @Test
    fun `a real server delivers only the channel-0x00 frames and logs both channel WARNs`() {
        val server = EchoServer()
        val socket = DatagramSocket()
        try {
            val iam = "Iam channel-guard-it".toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(iam, iam.size, loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
            socket.soTimeout = 5_000
            val reply = DatagramPacket(ByteArray(64), 64)
            socket.receive(reply)
            assertContentEquals("REGISTERED 2".toByteArray(Charsets.UTF_8), reply.data.copyOf(reply.length))
            Thread.sleep(100) // let onClientConnect bind both handlers before traffic arrives

            captureLogsOf(HandshakeCoordinator::class.java) { events ->
                val u1 = TransportWireFormat.unreliableDatagram(byteArrayOf(1), channel = 0x01)
                val u2 = TransportWireFormat.unreliableDatagram(byteArrayOf(2))
                val r1 = ReliableWireFormat.reliableDataDatagram(
                    seq = 0, ack = 0xFFFF, ackBitfield = 0, payload = byteArrayOf(3), channel = 0x01,
                )
                val r2 = ReliableWireFormat.reliableDataDatagram(seq = 0, ack = 0xFFFF, ackBitfield = 0, payload = byteArrayOf(4))
                listOf(u1, u2, r1, r2).forEach { framed ->
                    socket.send(DatagramPacket(framed, framed.size, loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
                }

                val deadline = System.currentTimeMillis() + 3_000
                while ((server.unreliable.isEmpty() || server.reliable.isEmpty()) && System.currentTimeMillis() < deadline) {
                    Thread.sleep(20)
                }

                assertTrue(events.hasWarnContaining("Unreliable datagram on unsupported channel 0x1 from"))
                assertTrue(events.hasWarnContaining("Reliable datagram on unsupported channel 0x1 from"))
            }

            assertTrue(server.unreliable.size == 1 && server.unreliable[0].contentEquals(byteArrayOf(2)))
            assertTrue(server.reliable.size == 1 && server.reliable[0].contentEquals(byteArrayOf(4)))
        } finally {
            socket.close()
            runCatching { server.stop() }
        }
    }

    @Test
    fun `a real client delivers only the channel-0x00 frames and logs both channel WARNs`() {
        val rawServer = DatagramSocket()
        val client = MultiConnectionUDPClient(loopback, rawServer.localPort)
        try {
            val handshakeThread = Thread {
                val packet = DatagramPacket(ByteArray(256), 256)
                rawServer.soTimeout = 5_000
                rawServer.receive(packet)
                val clientOrigin = InetSocketAddress(packet.address, packet.port)
                val reg = "REGISTERED 2".toByteArray(Charsets.UTF_8)
                rawServer.send(DatagramPacket(reg, reg.size, clientOrigin.address, clientOrigin.port))
            }.apply { start() }
            assertTrue(client.handshake("channel-guard-it").isSuccess)
            handshakeThread.join(5_000)

            val unreliable = CopyOnWriteArrayList<ByteArray>()
            val reliable = CopyOnWriteArrayList<ByteArray>()
            assertTrue(client.channel(DeliveryMode.UNRELIABLE).actuateBytes { unreliable += it }.isSuccess)
            assertTrue(client.channel(DeliveryMode.RELIABLE_ORDERED).actuateBytes { reliable += it }.isSuccess)

            val clientEndpoint = InetSocketAddress(loopback, client.localPort)
            captureLogsOf(MultiConnectionUDPClient::class.java) { events ->
                val u1 = TransportWireFormat.unreliableDatagram(byteArrayOf(1), channel = 0x01)
                val u2 = TransportWireFormat.unreliableDatagram(byteArrayOf(2))
                val r1 = ReliableWireFormat.reliableDataDatagram(
                    seq = 0, ack = 0xFFFF, ackBitfield = 0, payload = byteArrayOf(3), channel = 0x01,
                )
                val r2 = ReliableWireFormat.reliableDataDatagram(seq = 0, ack = 0xFFFF, ackBitfield = 0, payload = byteArrayOf(4))
                listOf(u1, u2, r1, r2).forEach { framed ->
                    rawServer.send(DatagramPacket(framed, framed.size, clientEndpoint.address, clientEndpoint.port))
                }

                val deadline = System.currentTimeMillis() + 3_000
                while ((unreliable.isEmpty() || reliable.isEmpty()) && System.currentTimeMillis() < deadline) {
                    Thread.sleep(20)
                }

                assertTrue(events.hasWarnContaining("Unreliable datagram on unsupported channel 0x1 from"))
                assertTrue(events.hasWarnContaining("Reliable datagram on unsupported channel 0x1 from"))
            }

            assertTrue(unreliable.size == 1 && unreliable[0].contentEquals(byteArrayOf(2)))
            assertTrue(reliable.size == 1 && reliable[0].contentEquals(byteArrayOf(4)))
        } finally {
            runCatching { client.stop() }
            runCatching { rawServer.close() }
        }
    }
}
