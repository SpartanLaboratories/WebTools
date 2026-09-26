package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakePeriodicSchedule
import com.spartanlabs.testing.support.webtools.udp.captureLogsOf
import com.spartanlabs.testing.support.webtools.udp.hasWarnContaining
import com.spartanlabs.webtools.udp.DeliveryMode
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 2 - the client's send()/receiveLoop framing in isolation, over a real socket against
// a loopback peer DatagramSocket (no handshake, no dispatch to a server). Mirrors the framing
// coverage FramingComponentTest gives the server side.
@Tag("component")
@Suppress("DEPRECATION") // exercises the still-working, now-deprecated push/actuate/send/start primitives on purpose
class MultiConnectionUDPClientFramingTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    private fun fakePeer(): DatagramSocket = DatagramSocket().also { opened += it }

    private fun newClient(peerPort: Int): MultiConnectionUDPClient =
        MultiConnectionUDPClient(
            loopback,
            peerPort,
            MultiConnectionUDPServer.DEFAULT_RECEIVE_BUFFER_BYTES,
            FakePeriodicSchedule(),
            FakePeriodicSchedule(),
            FakePeriodicSchedule(),
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

    @Test
    fun `send puts an 0x90 00 frame on the wire`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)

        assertTrue(client.send("hi").isSuccess)

        assertContentEquals(TransportWireFormat.unreliableDatagram("hi".toByteArray()), peer.nextBytes(500))
    }

    @Test
    fun `an inbound 0x90 00 hi frame reaches the start text handler as hi`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val received = mutableListOf<String>()
        assertTrue(client.start(received::add).isSuccess)

        val framed = TransportWireFormat.unreliableDatagram("hi".toByteArray())
        peer.send(DatagramPacket(framed, framed.size, loopback, client.localPort))

        val deadline = System.currentTimeMillis() + 2_000
        while (received.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertContentEquals(listOf("hi"), received)
    }

    @Test
    fun `an inbound 0x90 00 frame carrying raw bytes reaches the startBytes handler exactly`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val received = mutableListOf<ByteArray>()
        assertTrue(client.startBytes(received::add).isSuccess)

        val payload = byteArrayOf(0x00, 0x4B, 0x41, -0x01)
        val framed = TransportWireFormat.unreliableDatagram(payload)
        peer.send(DatagramPacket(framed, framed.size, loopback, client.localPort))

        val deadline = System.currentTimeMillis() + 2_000
        while (received.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertContentEquals(payload, received.single())
    }

    @Test
    fun `an inbound 0x80 keepalive is dropped, never reaching the handler`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val received = mutableListOf<String>()
        assertTrue(client.start(received::add).isSuccess)

        val keepalive = TransportWireFormat.keepaliveDatagram()
        peer.send(DatagramPacket(keepalive, keepalive.size, loopback, client.localPort))
        Thread.sleep(300)

        assertTrue(received.isEmpty())
    }

    @Test
    fun `an unframed datagram after start is dropped, never reaching the handler`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val received = mutableListOf<String>()
        assertTrue(client.start(received::add).isSuccess)

        val unframed = "hi".toByteArray(Charsets.UTF_8)
        peer.send(DatagramPacket(unframed, unframed.size, loopback, client.localPort))
        Thread.sleep(300)

        assertTrue(received.isEmpty())
    }

    @Test
    fun `a channel-0x01 0x90 from the peer is dropped with a WARN - the following channel-0x00 frame is the only delivery`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val received = CopyOnWriteArrayList<ByteArray>()
        assertTrue(client.channel(DeliveryMode.UNRELIABLE).actuateBytes(received::add).isSuccess)

        captureLogsOf(MultiConnectionUDPClient::class.java) { events ->
            val nonZero = TransportWireFormat.unreliableDatagram(byteArrayOf(1), channel = 0x01)
            peer.send(DatagramPacket(nonZero, nonZero.size, loopback, client.localPort))
            val barrier = TransportWireFormat.unreliableDatagram(byteArrayOf(9))
            peer.send(DatagramPacket(barrier, barrier.size, loopback, client.localPort))

            val deadline = System.currentTimeMillis() + 2_000
            while (received.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)

            assertTrue(events.hasWarnContaining("Unreliable datagram on unsupported channel 0x1 from"))
        }
        assertEquals(1, received.size)
        assertContentEquals(byteArrayOf(9), received.single())
    }

    @Test
    fun `a channel-0x01 0x90 from a foreign socket is dropped with a WARN naming the actual sender - pins the no-origin-screen behaviour (issue 39)`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val received = CopyOnWriteArrayList<ByteArray>()
        assertTrue(client.channel(DeliveryMode.UNRELIABLE).actuateBytes(received::add).isSuccess)
        val foreign = fakePeer()

        captureLogsOf(MultiConnectionUDPClient::class.java) { events ->
            val nonZero = TransportWireFormat.unreliableDatagram(byteArrayOf(1), channel = 0x01)
            foreign.send(DatagramPacket(nonZero, nonZero.size, loopback, client.localPort))

            val deadline = System.currentTimeMillis() + 2_000
            while (!events.hasWarnContaining("on unsupported channel") && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }

            // This test intentionally pins today's behaviour: the client 0x90 branch has no
            // origin screen (#39), so the WARN names whatever socket actually sent the frame,
            // not "server". #39's own fix will change what this test asserts.
            assertTrue(
                events.hasWarnContaining("Unreliable datagram on unsupported channel 0x1 from") &&
                    events.hasWarnContaining(":" + foreign.localPort),
                "the WARN must name the actual (unscreened) sender, not \"server\"",
            )
        }
        // The WARN branch and the delivery branch are exclusive, so once the WARN is logged this
        // frame can no longer be delivered - no extra sleep is needed before the negative check.
        assertTrue(received.isEmpty(), "nothing must be delivered")
    }
}
