package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakePeriodicSchedule
import com.spartanlabs.testing.support.webtools.udp.captureLogsOf
import com.spartanlabs.testing.support.webtools.udp.hasWarnContaining
import com.spartanlabs.webtools.udp.DatagramType
import com.spartanlabs.webtools.udp.DeliveryMode
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import com.spartanlabs.webtools.udp.ReliableChannelEngine
import com.spartanlabs.webtools.udp.ReliableWireFormat
import com.spartanlabs.webtools.udp.TransportWireFormat
import com.spartanlabs.webtools.udp.UdpChannel
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 2 - MultiConnectionUDPClient.channel(mode) in isolation, over a real socket against a
// loopback peer DatagramSocket (no handshake, no dispatch to a server) and FakePeriodicSchedule
// for the retransmit tick. Mirrors MultiConnectionUDPClientFramingTest's shape.
@Tag("component")
@Suppress("DEPRECATION") // exercises the still-working, now-deprecated push/actuate/send/start primitives on purpose
class MultiConnectionUDPClientReliableTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    private fun fakePeer(): DatagramSocket = DatagramSocket().also { opened += it }

    private fun newClient(
        peerPort: Int,
        retransmit: FakePeriodicSchedule = FakePeriodicSchedule(),
    ): MultiConnectionUDPClient =
        MultiConnectionUDPClient(
            loopback,
            peerPort,
            MultiConnectionUDPServer.DEFAULT_RECEIVE_BUFFER_BYTES,
            FakePeriodicSchedule(),
            FakePeriodicSchedule(),
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

    @Test
    fun `channel RELIABLE_ORDERED send puts an 0xA0 frame with the configured channel byte on the wire`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)

        assertTrue(client.channel(DeliveryMode.RELIABLE_ORDERED).send(byteArrayOf(1, 2, 3)).isSuccess)

        val onWire = peer.nextBytes(2_000)
        assertTrue(onWire != null && onWire[0] == DatagramType.RELIABLE_DATA.tag, "was ${onWire?.toList()}")
        assertEquals(ReliableWireFormat.DEFAULT_RELIABLE_CHANNEL, onWire!![1])
    }

    @Test
    fun `an inbound 0xA0 reaches the reliable handler`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val received = mutableListOf<ByteArray>()
        assertTrue(client.channel(DeliveryMode.RELIABLE_ORDERED).actuateBytes(received::add).isSuccess)

        val framed = ReliableWireFormat.reliableDataDatagram(seq = 0, ack = 0xFFFF, ackBitfield = 0, payload = byteArrayOf(9))
        peer.send(DatagramPacket(framed, framed.size, loopback, client.localPort))

        val deadline = System.currentTimeMillis() + 2_000
        while (received.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertContentEquals(byteArrayOf(9), received.single())
    }

    @Test
    fun `channel RELIABLE_ORDERED actuateBytes starts the listener when start startBytes were never called`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val received = mutableListOf<ByteArray>()

        assertTrue(client.channel(DeliveryMode.RELIABLE_ORDERED).actuateBytes(received::add).isSuccess)

        // Prove the listener is actually running by also delivering an unreliable frame - this
        // would never arrive if actuateBytes above hadn't started the read loop.
        val unreliableReceived = mutableListOf<String>()
        assertTrue(client.start(unreliableReceived::add).isSuccess)
        val framed = TransportWireFormat.unreliableDatagram("hi".toByteArray())
        peer.send(DatagramPacket(framed, framed.size, loopback, client.localPort))

        val deadline = System.currentTimeMillis() + 2_000
        while (unreliableReceived.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertContentEquals(listOf("hi"), unreliableReceived)
    }

    @Test
    fun `channel UNRELIABLE actuate delivers the trimmed UTF-8 payload byte-identical to start`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val received = mutableListOf<String>()
        assertTrue(client.channel(DeliveryMode.UNRELIABLE).actuate(received::add).isSuccess)

        val framed = TransportWireFormat.unreliableDatagram("  hello  ".toByteArray(Charsets.UTF_8))
        peer.send(DatagramPacket(framed, framed.size, loopback, client.localPort))

        val deadline = System.currentTimeMillis() + 2_000
        while (received.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(listOf("hello"), received)
    }

    @Test
    fun `channel UNRELIABLE send puts the same 0x90 frame on the wire as send`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)

        assertTrue(client.channel(DeliveryMode.UNRELIABLE).send("hi").isSuccess)

        assertContentEquals(TransportWireFormat.unreliableDatagram("hi".toByteArray()), peer.nextBytes(2_000))
    }

    @Test
    fun `calling start after channel actuate does not spawn a second listener thread`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        assertTrue(client.channel(DeliveryMode.UNRELIABLE).actuate {}.isSuccess)
        val threadsBefore = Thread.getAllStackTraces().keys.count { it.name == "mcupc-listener" }

        assertTrue(client.start {}.isSuccess)

        val threadsAfter = Thread.getAllStackTraces().keys.count { it.name == "mcupc-listener" }
        assertEquals(threadsBefore, threadsAfter, "start() after an already-running listener must not spawn a second one")
        assertEquals(1, threadsAfter)
    }

    @Test
    fun `a reliable send after stop fails with IllegalStateException`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        client.stop()

        val result = client.channel(DeliveryMode.RELIABLE_ORDERED).send(byteArrayOf(1))

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `an 0xA0 from a foreign origin mints no engine and delivers nothing - only the server endpoint may`() {
        val peer = fakePeer()
        val retransmit = FakePeriodicSchedule()
        val client = newClient(peer.localPort, retransmit)
        val received = mutableListOf<ByteArray>()
        assertTrue(client.channel(DeliveryMode.RELIABLE_ORDERED).actuateBytes(received::add).isSuccess)

        // A stray/spoofed reliable datagram from an origin other than the server this client
        // connected to (§12 B2) - the client-side counterpart of the server's findByOrigin guard.
        val foreign = fakePeer()
        val framed = ReliableWireFormat.reliableDataDatagram(seq = 0, ack = 0xFFFF, ackBitfield = 0, payload = byteArrayOf(9))
        foreign.send(DatagramPacket(framed, framed.size, loopback, client.localPort))
        Thread.sleep(300)

        assertTrue(received.isEmpty(), "a foreign origin's reliable datagram must not be delivered")
        assertTrue(retransmit.scheduleCalls.isEmpty(), "a foreign origin's reliable datagram must not mint the engine")

        // The same frame from the real server endpoint works normally.
        peer.send(DatagramPacket(framed, framed.size, loopback, client.localPort))
        val deadline = System.currentTimeMillis() + 2_000
        while (received.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertContentEquals(byteArrayOf(9), received.single())
        assertTrue(retransmit.scheduleCalls.isNotEmpty(), "the real server endpoint's datagram must mint the engine")
    }

    @Test
    fun `stop still closes the reliable engine even when the injected retransmit schedule's shutdown throws`() {
        val peer = fakePeer()
        val retransmit = FakePeriodicSchedule(throwOnShutdown = true)
        val client = newClient(peer.localPort, retransmit)
        assertTrue(client.channel(DeliveryMode.RELIABLE_ORDERED).send(byteArrayOf(1)).isSuccess)
        Thread.sleep(250) // past the 200 ms RTO floor, so the unacked send becomes retransmit-due

        captureLogsOf(ReliableChannelEngine::class.java) { events ->
            client.stop()
            assertEquals(1, retransmit.shutdownCalls, "retransmit.shutdown() must still be attempted")

            // Drives the engine's tick directly (bypassing the client's own `stopped` guard, the way
            // the real mcupc-retransmit thread would). §12 B5: if close() ran despite shutdown()
            // throwing, the retransmit buffer was cleared and this tick sends nothing; if close() was
            // skipped (the bug), the still-buffered unacked message is retransmit-due and the tick
            // logs a retransmit attempt.
            retransmit.tick(InetSocketAddress(loopback, peer.localPort))

            assertFalse(
                events.hasWarnContaining("Retransmit of reliable seq"),
                "the reliable engine must have been closed by stop() even though retransmit.shutdown() threw",
            )
        }
    }
}
