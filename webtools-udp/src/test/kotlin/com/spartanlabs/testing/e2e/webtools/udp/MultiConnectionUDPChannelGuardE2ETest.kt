package com.spartanlabs.testing.e2e.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.DatagramType
import com.spartanlabs.webtools.udp.DeliveryMode
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import com.spartanlabs.webtools.udp.ReliableWireFormat
import com.spartanlabs.webtools.udp.TransportWireFormat
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Level 4b - the issue's headline scenario as an application sees it (Issue #40): a simulated
 * newer-2.x peer interleaves channel-0x01 traffic with its channel-0x00 stream on both the
 * unreliable and reliable planes against a real MultiConnectionUDPServer. The application must
 * see exactly the channel-0x00 traffic, the reliable messages in order with none lost as a
 * "duplicate", and the peer must receive an ack covering the channel-0x00 reliable sequence.
 */
@Tag("e2e")
class MultiConnectionUDPChannelGuardE2ETest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private class EchoServer : MultiConnectionUDPServer() {
        val unreliable = CopyOnWriteArrayList<String>()
        val reliable = CopyOnWriteArrayList<String>()
        override fun onClientConnect(connection: Connection) {
            connection.channel(DeliveryMode.UNRELIABLE).actuate { unreliable += it }
            connection.channel(DeliveryMode.RELIABLE_ORDERED).actuateBytes { reliable += String(it, Charsets.UTF_8) }
        }
    }

    @Test
    fun `a newer-2x peer interleaving channel-0x01 with channel-0x00 on both planes is seen only on channel-0x00`() {
        val server = EchoServer()
        val socket = DatagramSocket()
        try {
            val iam = "Iam newer-2x-peer".toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(iam, iam.size, loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
            socket.soTimeout = 5_000
            socket.receive(DatagramPacket(ByteArray(64), 64)) // REGISTERED 2
            Thread.sleep(100)

            // 20 unreliable messages, alternating channel 0x01 (rejected) and channel 0x00 (delivered).
            for (i in 0 until 20) {
                val channel = if (i % 2 == 0) 0x01.toByte() else TransportWireFormat.DEFAULT_UNRELIABLE_CHANNEL
                val framed = TransportWireFormat.unreliableDatagram("u$i".toByteArray(Charsets.UTF_8), channel = channel)
                socket.send(DatagramPacket(framed, framed.size, loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
            }

            // A reliable stream: each channel-0x00 seq n is preceded by a channel-0x01 frame
            // reusing the same seq n - the guard must drop the decoy without disturbing the real
            // frame's ordering or delivery, and without the reorder buffer ever seeing seq n twice.
            val expectedReliable = (0 until 5).map { "r$it" }
            for (seq in 0 until 5) {
                val decoy = ReliableWireFormat.reliableDataDatagram(
                    seq = seq, ack = 0xFFFF, ackBitfield = 0,
                    payload = "decoy$seq".toByteArray(Charsets.UTF_8), channel = 0x01,
                )
                socket.send(DatagramPacket(decoy, decoy.size, loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT))

                val real = ReliableWireFormat.reliableDataDatagram(
                    seq = seq, ack = 0xFFFF, ackBitfield = 0, payload = "r$seq".toByteArray(Charsets.UTF_8),
                )
                socket.send(DatagramPacket(real, real.size, loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
            }

            val deadline = System.currentTimeMillis() + 3_000
            while (server.reliable.size < expectedReliable.size && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            assertEquals(expectedReliable, server.reliable.toList(), "the reliable events must arrive in order with none lost as a duplicate")

            // The server never sends reliable data here, so its acks arrive as standalone 0xA1s
            // from its retransmit tick. The last one must cover seq 4: the channel-0x00 reliable
            // plane stays fully acknowledged despite the interleaved decoys.
            socket.soTimeout = 3_000
            var lastAck = -1
            val ackDeadline = System.currentTimeMillis() + 3_000
            while (lastAck < 4 && System.currentTimeMillis() < ackDeadline) {
                val packet = DatagramPacket(ByteArray(64), 64)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    break
                }
                val bytes = packet.data.copyOf(packet.length)
                // Check the tag first: reliableAckHeaderOf decodes any 8-byte datagram.
                if (bytes.getOrNull(0) != DatagramType.RELIABLE_ACK.tag) continue
                val header = ReliableWireFormat.reliableAckHeaderOf(bytes)
                if (header != null) lastAck = header.ack
            }
            assertEquals(4, lastAck, "the peer must have received an ack covering seq 4, the last channel-0x00 reliable frame")

            val delivered20 = (1 until 20 step 2).map { "u$it" }
            assertEquals(delivered20.size, server.unreliable.size)
            assertTrue(server.unreliable.toList().sortedBy { it.removePrefix("u").toInt() } == delivered20)
        } finally {
            socket.close()
            runCatching { server.stop() }
        }
    }
}
