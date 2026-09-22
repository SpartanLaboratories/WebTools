package com.spartanlabs.testing.nonfunctional.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakeConnection
import com.spartanlabs.testing.support.webtools.udp.FakePeriodicSchedule
import com.spartanlabs.webtools.udp.Admission
import com.spartanlabs.webtools.udp.DatagramType
import com.spartanlabs.webtools.udp.HandshakeCoordinator
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import com.spartanlabs.webtools.udp.TransportWireFormat
import com.spartanlabs.webtools.udp.UdpChannel
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 4c - robustness of the framed transport (Issue #14): every first byte 0x00-0xFF
// survives an 0x90 frame intact; the framing overhead stays negligible against the
// path-MTU advisory; a flood of random/malformed/truncated datagrams never throws out of
// either listener and never leaks a control or malformed frame to a bound handler.
@Tag("nonfunctional")
@Suppress("DEPRECATION") // exercises the still-working, now-deprecated push/actuate/send/start primitives on purpose
class FramingNonFunctionalTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()
    private val origin = InetSocketAddress(InetAddress.getLoopbackAddress(), 44001)

    @AfterTest
    fun cleanup() {
        clients.forEach { runCatching { it.stop() } }
        opened.forEach { runCatching { it.close() } }
    }

    // --- frame overhead vs the path-MTU advisory ---

    @Test
    fun `framing overhead is negligible against the ~1200-byte path-MTU advisory`() {
        val payload = ByteArray(1100)
        assertEquals(payload.size + 2, TransportWireFormat.unreliableDatagram(payload).size)
        assertEquals(1, TransportWireFormat.keepaliveDatagram().size)
        assertEquals(9, TransportWireFormat.probePingDatagram(1L).size)
    }

    // --- exhaustive first-byte round-trip - the Issue #8 fix ---

    @Test
    fun `every possible first byte 0x00-0xFF survives an 0x90 frame intact`() {
        for (v in 0..0xFF) {
            val payload = byteArrayOf(v.toByte(), 0x01, 0x02)
            val framed = TransportWireFormat.unreliableDatagram(payload)
            assertContentEquals(payload, TransportWireFormat.unreliablePayloadOf(framed), "byte 0x${v.toString(16)}")
        }
    }

    @Test
    fun `a ~60 KB framed payload round-trips under the 65507-byte UDP ceiling`() {
        val payload = ByteArray(60_000) { it.toByte() }
        val framed = TransportWireFormat.unreliableDatagram(payload)
        assertTrue(framed.size <= MultiConnectionUDPServer.MAX_UDP_PAYLOAD_BYTES, "framed size ${framed.size}")
        assertContentEquals(payload, TransportWireFormat.unreliablePayloadOf(framed))
    }

    // --- fuzzing the server-side classifier ---

    private fun newCoordinator(delivered: MutableList<ByteArray>): HandshakeCoordinator {
        val coordinator = HandshakeCoordinator(
            newConnection = { name, peer, _ -> FakeConnection(name, peer) },
            sender = { _, _ -> Result.success(Unit) },
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
        coordinator.accept(origin, "Iam fuzz-target")
        coordinator.bindBytes(origin, delivered::add)
        return coordinator
    }

    @Test
    fun `100k random inbound byte arrays never throw and only ever deliver a genuine 0x90 payload`() {
        val delivered = mutableListOf<ByteArray>()
        val coordinator = newCoordinator(delivered)
        val random = Random(42)
        val expected = mutableListOf<ByteArray>()

        repeat(100_000) {
            val bytes = ByteArray(random.nextInt(0, 65)) { random.nextInt(0, 256).toByte() }
            val result = coordinator.accept(origin, bytes, "")
            assertTrue(result.isSuccess || result.isFailure, "accept must return a Result, never throw")
            if (bytes.getOrNull(0) == DatagramType.UNRELIABLE.tag && bytes.size >= 2) {
                expected += TransportWireFormat.unreliablePayloadOf(bytes)!!
            }
        }

        assertEquals(expected.size, delivered.size, "only genuine 0x90 frames were ever delivered")
        expected.indices.forEach { i -> assertContentEquals(expected[i], delivered[i]) }
    }

    @Test
    fun `a truncated 0x81, 0x82, or 0x90 datagram never throws out of HandshakeCoordinator accept`() {
        val delivered = mutableListOf<ByteArray>()
        val coordinator = newCoordinator(delivered)

        listOf(
            byteArrayOf(0x81.toByte()),
            byteArrayOf(0x81.toByte(), 0x01, 0x02),
            byteArrayOf(0x82.toByte()),
            byteArrayOf(0x82.toByte(), 0x01),
            byteArrayOf(0x90.toByte()),
        ).forEach { truncated ->
            assertTrue(
                coordinator.accept(origin, truncated, "").isSuccess,
                "truncated ${truncated.toList()} must not fail/throw",
            )
        }
        assertTrue(delivered.isEmpty(), "no truncated control/unreliable frame reaches the handler")
    }

    // --- fuzzing the client-side receiveLoop ---

    @Test
    fun `100 random garbage datagrams never wedge the client listener - a following real frame still arrives`() {
        val peer = DatagramSocket().also { opened += it }
        val client = MultiConnectionUDPClient(loopback, peer.localPort).also { clients += it }
        val handshakeThread = Thread {
            val packet = DatagramPacket(ByteArray(256), 256)
            peer.soTimeout = 5_000
            peer.receive(packet)
            val clientOrigin = InetSocketAddress(packet.address, packet.port)
            val reg = "REGISTERED 2".toByteArray(Charsets.UTF_8)
            peer.send(DatagramPacket(reg, reg.size, clientOrigin.address, clientOrigin.port))
        }.apply { start() }
        assertTrue(client.handshake("fuzz").isSuccess)
        handshakeThread.join(5_000)

        // CopyOnWriteArrayList, not a plain mutableListOf: startBytes' handler appends from the
        // client's background dispatch thread while the poll loop below reads/iterates it from
        // the test thread - an unsynchronized plain list intermittently threw ConcurrentModificationException.
        val received = CopyOnWriteArrayList<ByteArray>()
        assertTrue(client.startBytes { received += it }.isSuccess)

        val random = Random(7)
        val clientEndpoint = InetSocketAddress(loopback, client.localPort)
        repeat(100) {
            val bytes = ByteArray(random.nextInt(0, 65)) { random.nextInt(0, 256).toByte() }
            peer.send(DatagramPacket(bytes, bytes.size, clientEndpoint.address, clientEndpoint.port))
        }

        val realPayload = byteArrayOf(0x01, 0x02, 0x03)
        val framed = TransportWireFormat.unreliableDatagram(realPayload)
        peer.send(DatagramPacket(framed, framed.size, clientEndpoint.address, clientEndpoint.port))

        val deadline = System.currentTimeMillis() + 3_000
        while (received.none { it.contentEquals(realPayload) } && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertTrue(received.any { it.contentEquals(realPayload) }, "the listener survived the garbage flood")
    }
}
