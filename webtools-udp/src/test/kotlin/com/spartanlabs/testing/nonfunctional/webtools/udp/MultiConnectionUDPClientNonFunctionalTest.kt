package com.spartanlabs.testing.nonfunctional.webtools.udp

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.udp.FakePeriodicSchedule
import com.spartanlabs.testing.support.webtools.udp.captureLogsOf
import com.spartanlabs.webtools.udp.DeliveryMode
import com.spartanlabs.webtools.udp.HandshakeWireFormat
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 4c - non-functional properties of MultiConnectionUDPClient's listener-thread /
// dispatch-executor concurrency model, mirroring HandshakeNonFunctionalTest's server-side
// coverage now that the client has the same concurrency shape.
@Tag("nonfunctional")
@Suppress("DEPRECATION") // exercises the still-working, now-deprecated push/actuate/send/start primitives on purpose
class MultiConnectionUDPClientNonFunctionalTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    private fun fakePeer(): DatagramSocket = DatagramSocket().also { opened += it }

    private fun newClient(peerPort: Int): MultiConnectionUDPClient =
        MultiConnectionUDPClient(loopback, peerPort).also { clients += it }

    /**
     * A client wired to a [FakePeriodicSchedule] retransmit tick, so a test can prove the
     * reliable engine is (or is not) minted via [FakePeriodicSchedule.scheduleCalls] - the same
     * seam `MultiConnectionUDPClientReliableTest` uses. The keepalive/probe schedules stay fake
     * too, matching that file's helper, since neither is exercised here.
     */
    private fun newClientWithRetransmit(peerPort: Int, retransmit: FakePeriodicSchedule): MultiConnectionUDPClient =
        MultiConnectionUDPClient(
            loopback,
            peerPort,
            MultiConnectionUDPServer.DEFAULT_RECEIVE_BUFFER_BYTES,
            FakePeriodicSchedule(),
            FakePeriodicSchedule(),
            retransmit,
            UdpChannel.DEFAULT_MAX_RELIABLE_MESSAGE_BYTES,
        ).also { clients += it }

    private fun DatagramSocket.receiveOrigin(): InetSocketAddress {
        soTimeout = 5000
        val packet = DatagramPacket(ByteArray(64), 64)
        receive(packet)
        return InetSocketAddress(packet.address, packet.port)
    }

    private fun DatagramSocket.sendTo(target: InetSocketAddress, text: String) {
        val out = text.toByteArray(Charsets.UTF_8)
        send(DatagramPacket(out, out.size, target.address, target.port))
    }

    /** Sends [text] to [target] framed as an 0x90 unreliable application datagram. */
    private fun DatagramSocket.sendFramedTo(target: InetSocketAddress, text: String) =
        sendTo(target, TransportWireFormat.unreliableDatagram(text.toByteArray(Charsets.UTF_8)))

    /** Sends [bytes] to [target] framed as an 0x90 unreliable application datagram. */
    private fun DatagramSocket.sendFramedTo(target: InetSocketAddress, bytes: ByteArray) =
        sendTo(target, TransportWireFormat.unreliableDatagram(bytes))

    private fun handshakeSucceeds(client: MultiConnectionUDPClient, peer: DatagramSocket): InetSocketAddress {
        var origin: InetSocketAddress? = null
        val thread = Thread {
            origin = peer.receiveOrigin()
            peer.sendTo(origin!!, HandshakeWireFormat.registeredMessage())
        }.apply { start() }
        assertTrue(client.handshake("alice").isSuccess)
        thread.join(5000)
        return origin!!
    }

    @AfterTest
    fun cleanup() {
        clients.forEach { runCatching { it.stop() } }
        clients.clear()
        opened.forEach { runCatching { it.close() } }
        opened.clear()
    }

    @Test
    fun `per-session message order is preserved under a burst`() {
        // The single-threaded dispatch executor guarantees onMessage sees a burst of
        // server-sent messages in send order. Accepted trade-off (same as the server side):
        // a slow onMessage delays delivery of only this client's own subsequent messages.
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshakeSucceeds(client, peer)
        val seen = ConcurrentLinkedQueue<Int>()
        val done = CountDownLatch(BURST)
        assertTrue(
            client.start { message ->
                seen += message.toInt()
                done.countDown()
            }.isSuccess,
        )

        repeat(BURST) { i -> peer.sendFramedTo(origin, i.toString()) }
        assertTrue(done.await(10, TimeUnit.SECONDS), "all $BURST messages delivered")
        assertEquals((0 until BURST).toList(), seen.toList())
    }

    @Test
    fun `a KA storm from the peer creates no calls to onMessage and does not wedge the listener`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshakeSucceeds(client, peer)
        val dispatched = ConcurrentLinkedQueue<String>()
        val realMessageSeen = CountDownLatch(1)
        assertTrue(
            client.start { message ->
                dispatched += message
                if (message == "real-message") realMessageSeen.countDown()
            }.isSuccess,
        )

        repeat(STORM_SIZE) { peer.sendTo(origin, TransportWireFormat.keepaliveDatagram()) }
        peer.sendFramedTo(origin, "real-message")

        assertTrue(realMessageSeen.await(10, TimeUnit.SECONDS), "listener not wedged by the KA storm")
        assertEquals(listOf("real-message"), dispatched.toList())
    }

    @Test
    fun `a handler that always throws never kills the dispatch thread across many messages`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshakeSucceeds(client, peer)
        val checkSeen = CountDownLatch(1)
        assertTrue(
            client.start { message ->
                if (message == "check") {
                    checkSeen.countDown()
                } else {
                    throw IllegalStateException("always throws")
                }
            }.isSuccess,
        )

        repeat(THROWING_BURST) { i -> peer.sendFramedTo(origin, "throw-$i") }
        peer.sendFramedTo(origin, "check")

        assertTrue(checkSeen.await(10, TimeUnit.SECONDS), "dispatch thread survived $THROWING_BURST throwing messages")
    }

    @Test
    fun `per-session order preserved under a burst of binary frames via startBytes`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshakeSucceeds(client, peer)
        val seen = ConcurrentLinkedQueue<Int>()
        val done = CountDownLatch(BURST)
        assertTrue(
            client.startBytes { bytes ->
                seen += ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)
                done.countDown()
            }.isSuccess,
        )

        repeat(BURST) { i ->
            peer.sendFramedTo(origin, byteArrayOf(0x00, (i shr 8).toByte(), i.toByte()))
        }
        assertTrue(done.await(10, TimeUnit.SECONDS), "all $BURST binary frames delivered")
        assertEquals((0 until BURST).toList(), seen.toList())
    }

    @Test
    fun `a KA storm from the peer creates no calls to the bytes handler and does not wedge the listener`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshakeSucceeds(client, peer)
        val dispatched = ConcurrentLinkedQueue<ByteArray>()
        val realSeen = CountDownLatch(1)
        assertTrue(
            client.startBytes { bytes ->
                dispatched += bytes
                if (bytes.contentEquals(byteArrayOf(0x00, 0x01))) realSeen.countDown()
            }.isSuccess,
        )

        repeat(STORM_SIZE) { peer.sendTo(origin, TransportWireFormat.keepaliveDatagram()) }
        peer.sendFramedTo(origin, byteArrayOf(0x00, 0x01))

        assertTrue(realSeen.await(10, TimeUnit.SECONDS), "listener not wedged by the KA storm")
        assertEquals(1, dispatched.size)
    }

    @Test
    fun `a throwing bytes handler never kills the dispatch thread across many frames`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshakeSucceeds(client, peer)
        val checkSeen = CountDownLatch(1)
        assertTrue(
            client.startBytes { bytes ->
                if (bytes.contentEquals(byteArrayOf(0x00, 0x63))) checkSeen.countDown() else throw IllegalStateException("throws")
            }.isSuccess,
        )

        repeat(THROWING_BURST) { peer.sendFramedTo(origin, byteArrayOf(0x00, 0x01)) }
        peer.sendFramedTo(origin, byteArrayOf(0x00, 0x63))

        assertTrue(checkSeen.await(10, TimeUnit.SECONDS), "dispatch thread survived $THROWING_BURST throwing frames")
    }

    @Test
    fun `an 8 KiB binary payload round-trips whole via startBytes`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshakeSucceeds(client, peer)
        val received = ConcurrentLinkedQueue<ByteArray>()
        assertTrue(client.startBytes { received += it }.isSuccess)

        val big = ByteArray(8192) { ((it % 250) + 1).toByte() } // no 0x00, no whitespace-only trims
        peer.sendFramedTo(origin, big)

        val deadline = System.currentTimeMillis() + 10000
        while (received.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(8192, received.firstOrNull()?.size)
        assertTrue(received.first().contentEquals(big))
    }

    @Test
    fun `a ~60 KiB payload round-trips whole via startBytes - locks the documented 65507 ceiling`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshakeSucceeds(client, peer)
        val received = ConcurrentLinkedQueue<ByteArray>()
        assertTrue(client.startBytes { received += it }.isSuccess)

        val big = ByteArray(60_000) { ((it % 250) + 1).toByte() }
        peer.sendFramedTo(origin, big)

        val deadline = System.currentTimeMillis() + 10000
        while (received.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(60_000, received.firstOrNull()?.size)
        assertTrue(received.first().contentEquals(big))
    }

    private fun DatagramSocket.sendTo(target: InetSocketAddress, bytes: ByteArray) {
        send(DatagramPacket(bytes, bytes.size, target.address, target.port))
    }

    // --- Issue #40: the client-side channel-byte guard under flood, mirroring
    // FramingNonFunctionalTest's/ReliableChannelNonFunctionalTest's server-side flood coverage. ---

    @Test
    fun `a 10000-frame flood of non-zero-channel 0x90 frames is entirely dropped with one WARN each - only the channel-0x00 frames sent among them are ever delivered`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val clientEndpoint = InetSocketAddress(loopback, client.localPort)
        // CopyOnWriteArrayList: appended on the client's dispatch thread, read from the test
        // thread's poll loop below.
        val received = CopyOnWriteArrayList<Byte>()
        assertTrue(client.channel(DeliveryMode.UNRELIABLE).actuateBytes { bytes -> received += bytes[0] }.isSuccess)

        val random = Random(4040)
        val expected = mutableListOf<Byte>()
        captureLogsOf(MultiConnectionUDPClient::class.java) { events ->
            repeat(FLOOD_SIZE) { i ->
                if (i % 500 == 0) {
                    val marker = expected.size.toByte()
                    val frame = TransportWireFormat.unreliableDatagram(byteArrayOf(marker))
                    peer.send(DatagramPacket(frame, frame.size, clientEndpoint.address, clientEndpoint.port))
                    expected += marker
                } else {
                    val channel = (1 + random.nextInt(255)).toByte()
                    val frame = TransportWireFormat.unreliableDatagram(byteArrayOf(0x7F), channel = channel)
                    peer.send(DatagramPacket(frame, frame.size, clientEndpoint.address, clientEndpoint.port))
                }
            }
            val barrier = TransportWireFormat.unreliableDatagram(byteArrayOf(0x63))
            peer.send(DatagramPacket(barrier, barrier.size, clientEndpoint.address, clientEndpoint.port))
            expected += 0x63.toByte()

            val deadline = System.currentTimeMillis() + 5_000
            while (received.size < expected.size && System.currentTimeMillis() < deadline) Thread.sleep(20)

            assertEquals(
                expected, received.toList(),
                "only the channel-0x00 frames sent among the flood were ever delivered, in order",
            )
            val genuineFramesInFlood = expected.size - 1 // the trailing barrier is sent separately
            assertEquals(
                FLOOD_SIZE - genuineFramesInFlood,
                events.count { it.level.isGreaterOrEqual(Level.WARN) && it.formattedMessage.contains("on unsupported channel") },
                "one WARN for every non-zero-channel frame in the flood",
            )
        }
    }

    @Test
    fun `10000 channel-non-zero reliable frames from the server endpoint mint no engine and never disturb one once it exists - channel-0x00 seq 0 and 1 still deliver in order`() {
        val peer = fakePeer()
        val retransmit = FakePeriodicSchedule()
        val client = newClientWithRetransmit(peer.localPort, retransmit)
        val clientEndpoint = InetSocketAddress(loopback, client.localPort)
        val received = CopyOnWriteArrayList<ByteArray>()
        // Binding a reliable handler starts the listener but mints no engine on the client (unlike
        // the server's bindReliable) - see MultiConnectionUDPClientReliableTest's own note on this.
        assertTrue(client.channel(DeliveryMode.RELIABLE_ORDERED).actuateBytes { received += it }.isSuccess)

        val random = Random(4041)
        fun flood(decoySeq: Int, decoyMarker: Byte) = repeat(RELIABLE_FLOOD_PHASE_SIZE) {
            val channel = (1 + random.nextInt(255)).toByte()
            val frame = if (random.nextBoolean()) {
                ReliableWireFormat.reliableDataDatagram(
                    seq = decoySeq, ack = 0, ackBitfield = 0, payload = byteArrayOf(decoyMarker), channel = channel,
                )
            } else {
                ReliableWireFormat.reliableAckDatagram(ack = 0, ackBitfield = 0, channel = channel)
            }
            peer.send(DatagramPacket(frame, frame.size, clientEndpoint.address, clientEndpoint.port))
        }

        // Each phase reads the logback events list exactly once, after waiting on the thread-safe
        // `received` list - never while the listener thread might still be concurrently appending to
        // it, which is exactly what a bare polling read of `events` during the flood raced with (a
        // plain ArrayList, not a CopyOnWriteArrayList, threw ConcurrentModificationException under
        // that pattern). Decoys can never grow `retransmit.scheduleCalls` or `received` regardless of
        // how much of the flood the listener has drained, so those two checks are safe immediately.

        // Phase 1 - no engine exists yet. Decoys reuse seq 0, the sequence the real barrier below
        // uses: if one leaked past the guard it would occupy seq 0's reorder-buffer slot ahead of
        // the genuine frame.
        captureLogsOf(MultiConnectionUDPClient::class.java) { events ->
            flood(decoySeq = 0, decoyMarker = 0xDD.toByte())
            assertTrue(retransmit.scheduleCalls.isEmpty(), "a channel-non-zero flood before any inbound genuine frame must mint no engine")

            val realSeq0 = ReliableWireFormat.reliableDataDatagram(seq = 0, ack = 0xFFFF, ackBitfield = 0, payload = byteArrayOf(1))
            peer.send(DatagramPacket(realSeq0, realSeq0.size, clientEndpoint.address, clientEndpoint.port))
            val deadline = System.currentTimeMillis() + 5_000
            while (received.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)

            assertEquals(listOf(byteArrayOf(1).toList()), received.map { it.toList() }, "seq 0's genuine payload, not a decoy's")
            assertEquals(1, retransmit.scheduleCalls.size, "the genuine channel-0x00 frame must mint exactly one engine")
            assertEquals(
                RELIABLE_FLOOD_PHASE_SIZE,
                events.count { it.level.isGreaterOrEqual(Level.WARN) && it.formattedMessage.contains("on unsupported channel") },
                "one WARN for every phase-1 decoy - the wait above guarantees the listener has drained all of them by now",
            )
        }

        // Phase 2 - an engine now exists. Decoys reuse seq 1, the next expected sequence, so a leak
        // would corrupt the next genuine delivery instead of merely being a duplicate of seq 0.
        captureLogsOf(MultiConnectionUDPClient::class.java) { events ->
            flood(decoySeq = 1, decoyMarker = 0xEE.toByte())

            val realSeq1 = ReliableWireFormat.reliableDataDatagram(seq = 1, ack = 0xFFFF, ackBitfield = 0, payload = byteArrayOf(2))
            peer.send(DatagramPacket(realSeq1, realSeq1.size, clientEndpoint.address, clientEndpoint.port))
            val deadline = System.currentTimeMillis() + 5_000
            while (received.size < 2 && System.currentTimeMillis() < deadline) Thread.sleep(20)

            assertEquals(
                listOf(byteArrayOf(1).toList(), byteArrayOf(2).toList()),
                received.map { it.toList() },
                "seq 0 then seq 1, in order, undisturbed by either flood",
            )
            assertEquals(1, retransmit.scheduleCalls.size, "the second flood must not mint a second engine")
            assertEquals(
                RELIABLE_FLOOD_PHASE_SIZE,
                events.count { it.level.isGreaterOrEqual(Level.WARN) && it.formattedMessage.contains("on unsupported channel") },
                "one WARN for every phase-2 decoy",
            )
        }
    }

    private companion object {
        const val BURST = 500
        const val STORM_SIZE = 100
        const val THROWING_BURST = 50
        const val FLOOD_SIZE = 10_000
        const val RELIABLE_FLOOD_PHASE_SIZE = 5_000
    }
}
