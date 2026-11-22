package com.spartanlabs.testing.gating.webtools.udp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.spartanlabs.testing.support.webtools.udp.FakePeriodicSchedule
import com.spartanlabs.testing.support.webtools.udp.captureLogsOf
import com.spartanlabs.webtools.udp.DeliveryMode
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import com.spartanlabs.webtools.udp.ReliableWireFormat
import com.spartanlabs.webtools.udp.TransportWireFormat
import com.spartanlabs.webtools.udp.UdpChannel
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Gating set - a fast smoke over every branch of the client listener's origin screen in one pass:
// a real client bound to an ephemeral loopback port, `peer` the socket it targets and `foreign` a
// second socket it does not. One datagram of every tag from `foreign` is dropped; the server's
// datagram that follows is the only one acted on.
@Tag("gating")
class ClientOriginScreenGatingTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    private fun fakePeer(): DatagramSocket = DatagramSocket().also { opened += it }

    private fun newClient(
        peerPort: Int,
        probe: FakePeriodicSchedule,
        retransmit: FakePeriodicSchedule,
    ): MultiConnectionUDPClient =
        MultiConnectionUDPClient(
            loopback,
            peerPort,
            MultiConnectionUDPServer.DEFAULT_RECEIVE_BUFFER_BYTES,
            FakePeriodicSchedule(),
            probe,
            retransmit,
            UdpChannel.DEFAULT_MAX_RELIABLE_MESSAGE_BYTES,
        ).also { clients += it }

    @AfterTest
    fun cleanup() {
        clients.forEach { runCatching { it.stop() } }
        opened.forEach { runCatching { it.close() } }
    }

    private fun DatagramSocket.nextBytes(timeoutMillis: Int): ByteArray? {
        soTimeout = timeoutMillis
        val packet = DatagramPacket(ByteArray(256), 256)
        return try {
            receive(packet)
            packet.data.copyOf(packet.length)
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    private fun List<ILoggingEvent>.originDrops(): Int =
        count { it.level == Level.DEBUG && it.formattedMessage.contains("Datagram from unexpected origin") }

    @Test
    fun `a datagram of every tag from a foreign socket is dropped - the server's datagram that follows is the only one acted on`() {
        val peer = fakePeer()
        val probe = FakePeriodicSchedule()
        val retransmit = FakePeriodicSchedule()
        val client = newClient(peer.localPort, probe, retransmit)
        val received = CopyOnWriteArrayList<ByteArray>()
        val reliableReceived = CopyOnWriteArrayList<ByteArray>()
        assertTrue(client.channel(DeliveryMode.UNRELIABLE).actuateBytes { received += it }.isSuccess)
        assertTrue(client.channel(DeliveryMode.RELIABLE_ORDERED).actuateBytes { reliableReceived += it }.isSuccess)
        assertTrue(client.startProbe(5_000L).isSuccess)
        probe.tick(InetSocketAddress(loopback, peer.localPort))
        val ping = assertNotNull(peer.nextBytes(2_000), "the probe tick must put a PING on the wire")
        val liveSeq = assertNotNull(TransportWireFormat.probeSequenceOf(ping))
        val foreign = fakePeer()

        val fromForeign = listOf(
            TransportWireFormat.keepaliveDatagram(),
            TransportWireFormat.probePingDatagram(99),
            TransportWireFormat.probePongDatagram(liveSeq),
            TransportWireFormat.unreliableDatagram(byteArrayOf(1)),
            ReliableWireFormat.reliableDataDatagram(seq = 0, ack = 0xFFFF, ackBitfield = 0, payload = byteArrayOf(2)),
            ReliableWireFormat.reliableAckDatagram(ack = 0, ackBitfield = 0),
            "hi".toByteArray(),
            byteArrayOf(0xA2.toByte()),
        )

        captureLogsOf(MultiConnectionUDPClient::class.java, Level.DEBUG) { events ->
            fromForeign.forEach { foreign.send(DatagramPacket(it, it.size, loopback, client.localPort)) }

            val dropDeadline = System.currentTimeMillis() + 2_000
            while (events.originDrops() != fromForeign.size && System.currentTimeMillis() < dropDeadline) {
                Thread.sleep(20)
            }

            val fromServer = TransportWireFormat.unreliableDatagram(byteArrayOf(9))
            peer.send(DatagramPacket(fromServer, fromServer.size, loopback, client.localPort))
            val deliveryDeadline = System.currentTimeMillis() + 2_000
            while (received.isEmpty() && System.currentTimeMillis() < deliveryDeadline) Thread.sleep(20)

            assertEquals(listOf(listOf<Byte>(9)), received.map { it.toList() }, "only the server's 0x90 is delivered")
            assertTrue(reliableReceived.isEmpty(), "no reliable payload is delivered")
            assertNull(client.linkQuality(), "no probe is completed")
            assertTrue(retransmit.scheduleCalls.isEmpty(), "no reliable engine is created")
            assertNull(peer.nextBytes(200), "no PONG is sent to the server")
            assertEquals(fromForeign.size, events.originDrops(), "one unexpected-origin drop per foreign datagram")
        }
    }
}
