package com.spartanlabs.testing.nonfunctional.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakeConnection
import com.spartanlabs.testing.support.webtools.udp.FakePeriodicSchedule
import com.spartanlabs.webtools.udp.Admission
import com.spartanlabs.webtools.udp.HandshakeCoordinator
import com.spartanlabs.webtools.udp.ReliableMessageTooLargeException
import com.spartanlabs.webtools.udp.ReliableWindowFullException
import com.spartanlabs.webtools.udp.ReliableWireFormat
import com.spartanlabs.webtools.udp.UdpChannel
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 4c - robustness of the public reliable channel wiring (Issue #14, Stage 3): window-full
// backpressure never blocks, the size cap is enforced both per-send and at construction, one
// retransmit tick never emits more than the configured burst cap even with a full window and a
// dead peer, and neither production listener ever throws on random/malformed reliable traffic.
@Tag("nonfunctional")
class ReliableChannelNonFunctionalTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val origin = InetSocketAddress(loopback, 47001)

    private fun newCoordinator(
        reliableMaxMessageBytes: Int = UdpChannel.DEFAULT_MAX_RELIABLE_MESSAGE_BYTES,
        sender: (bytes: ByteArray, to: InetSocketAddress) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    ) = HandshakeCoordinator(
        newConnection = { name, peer, _ -> FakeConnection(name, peer) },
        sender = sender,
        onRegistered = {},
        admit = { _, _, _ -> Admission.Admitted },
        dispatch = { it() },
        onDisconnect = { _, _ -> },
        idleTimeoutMillis = 0L,
        keepAliveSchedule = FakePeriodicSchedule(),
        probeSchedule = FakePeriodicSchedule(),
        retransmitSchedule = FakePeriodicSchedule(),
        reliableMaxMessageBytes = reliableMaxMessageBytes,
    )

    @Test
    fun `a burst of windowSize plus 1 sends returns exactly one ReliableWindowFullException, never blocking`() {
        val coordinator = newCoordinator()
        coordinator.accept(origin, "Iam burst")
        val windowSize = 256

        val outcomes = (0 until windowSize + 1).map { coordinator.sendReliable(origin, byteArrayOf(1)) }

        assertEquals(windowSize, outcomes.count { it.isSuccess })
        val failures = outcomes.filter { it.isFailure }
        assertEquals(1, failures.size, "exactly one send must fail once the window is full")
        assertIs<ReliableWindowFullException>(failures.single().exceptionOrNull())
    }

    @Test
    fun `a message at the cap succeeds and cap plus 1 fails`() {
        val cap = 64
        val coordinator = newCoordinator(reliableMaxMessageBytes = cap)
        coordinator.accept(origin, "Iam capped")

        assertTrue(coordinator.sendReliable(origin, ByteArray(cap)).isSuccess, "exactly at the cap must succeed")

        val result = coordinator.sendReliable(origin, ByteArray(cap + 1))
        assertTrue(result.isFailure)
        assertIs<ReliableMessageTooLargeException>(result.exceptionOrNull())
    }

    @Test
    fun `the 8KB hard max is enforced at construction for both server and client`() {
        assertFailsWith<IllegalArgumentException> {
            object : com.spartanlabs.webtools.udp.MultiConnectionUDPServer(
                reliableMaxMessageBytes = UdpChannel.MAX_RELIABLE_MESSAGE_BYTES + 1,
            ) {
                override fun onClientConnect(connection: com.spartanlabs.webtools.udp.Connection) {}
            }
        }
        assertFailsWith<IllegalArgumentException> {
            com.spartanlabs.webtools.udp.MultiConnectionUDPClient(
                loopback,
                reliableMaxMessageBytes = UdpChannel.MAX_RELIABLE_MESSAGE_BYTES + 1,
            )
        }
    }

    @Test
    fun `one retransmit tick with 256 in flight and a dead peer emits at most 32 datagrams`() {
        var now = 1_000_000_000_000L
        val clock = { now }
        var sentToDeadPeer = 0
        val engine = com.spartanlabs.webtools.udp.ReliableChannelEngine(
            sendRaw = { sentToDeadPeer++; Result.success(Unit) },
            windowSize = 256,
            nanoClock = clock,
        )
        repeat(256) { engine.sendReliable(byteArrayOf(1)) }
        assertEquals(256, sentToDeadPeer, "the initial sends themselves")
        now += 10_000L * 1_000_000L // well past every entry's RTO - a dead peer never acked any of them
        sentToDeadPeer = 0

        engine.onRetransmitTick()

        assertTrue(
            sentToDeadPeer <= com.spartanlabs.webtools.udp.ReliableChannelEngine.MAX_RETRANSMITS_PER_TICK,
            "one tick emitted $sentToDeadPeer datagrams against a dead peer with a full window",
        )
        assertEquals(
            com.spartanlabs.webtools.udp.ReliableChannelEngine.MAX_RETRANSMITS_PER_TICK,
            sentToDeadPeer,
            "the tick should retransmit exactly the configured ceiling, not fewer, with 256 overdue entries",
        )
    }

    @Test
    fun `100k random inbound bytes fed to HandshakeCoordinator accept as 0xA0 0xA1 never throw and deliver nothing without a bound handler`() {
        val coordinator = newCoordinator()
        coordinator.accept(origin, "Iam fuzz")
        val random = Random(2026)

        repeat(100_000) {
            val tag = if (random.nextBoolean()) 0xA0.toByte() else 0xA1.toByte()
            val bytes = ByteArray(random.nextInt(0, 32)) { i -> if (i == 0) tag else random.nextInt(0, 256).toByte() }
            val result = runCatching { coordinator.accept(origin, bytes, "") }
            assertTrue(result.isSuccess, "accept must never throw on garbage reliable input, was ${bytes.toList()}")
        }
    }

    @Test
    fun `10000 channel-non-zero reliable frames from a registered origin mint no engine and never disturb one - a channel-0x00 seq 0 still delivers afterward`() {
        val retransmitSchedule = FakePeriodicSchedule()
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
            retransmitSchedule = retransmitSchedule,
            reliableMaxMessageBytes = UdpChannel.DEFAULT_MAX_RELIABLE_MESSAGE_BYTES,
        )
        coordinator.accept(origin, "Iam channel-flood")
        val random = Random(2040)
        fun flood() = repeat(5_000) {
            val channel = (1 + random.nextInt(255)).toByte()
            // Each decoy reuses seq 0, and each decoy ack names seq 0: if either leaked past the
            // guard, the real channel-0x00 seq 0 below would be treated as a duplicate.
            val frame = if (random.nextBoolean()) {
                ReliableWireFormat.reliableDataDatagram(seq = 0, ack = 0, ackBitfield = 0, payload = byteArrayOf(1), channel = channel)
            } else {
                ReliableWireFormat.reliableAckDatagram(ack = 0, ackBitfield = 0, channel = channel)
            }
            assertTrue(coordinator.accept(origin, frame, "").isSuccess)
        }

        // Phase 1 - no handler bound, so no engine exists: the flood must not mint one.
        flood()
        assertTrue(retransmitSchedule.scheduleCalls.isEmpty(), "channel-non-zero frames must mint no engine")

        // Phase 2 - bindReliable mints the engine (one schedule); a second flood must leave it
        // untouched: nothing delivered, no second engine, and seq 0 still unconsumed.
        val received = mutableListOf<ByteArray>()
        assertTrue(coordinator.bindReliable(origin) { received += it }.isSuccess)
        assertEquals(1, retransmitSchedule.scheduleCalls.size, "bindReliable mints exactly one engine")
        flood()
        assertTrue(received.isEmpty(), "no channel-non-zero frame may be delivered")
        assertEquals(1, retransmitSchedule.scheduleCalls.size, "the flood must not mint a second engine")

        val zero = ReliableWireFormat.reliableDataDatagram(seq = 0, ack = 0xFFFF, ackBitfield = 0, payload = byteArrayOf(9))
        assertTrue(coordinator.accept(origin, zero, "").isSuccess)
        assertEquals(listOf(listOf<Byte>(9)), received.map { it.toList() })
    }

    @Test
    fun `every truncation length of a channel-0x00 0xA0 or 0xA1 fed to HandshakeCoordinator accept never throws`() {
        val coordinator = newCoordinator()
        coordinator.accept(origin, "Iam trunc")

        for (tag in listOf(0xA0.toByte(), 0xA1.toByte())) {
            for (len in 0..12) {
                // Byte 1 (the channel) pinned to 0x00 (Issue #40): this loop's intent is the
                // engine's own malformed-header handling, not the channel guard - a non-zero byte 1
                // would be dropped by the guard before ever reaching the engine. See the sibling
                // test below for the channel-drop path's own never-throw property.
                val truncated = ByteArray(len) { if (it == 0) tag else if (it == 1) 0x00 else it.toByte() }
                val result = runCatching { coordinator.accept(origin, truncated, "") }
                assertTrue(result.getOrNull()?.isSuccess ?: false, "tag 0x${tag.toString(16)} length $len must not throw/fail")
            }
        }
    }

    @Test
    fun `every truncation length of a channel-0x01 0xA0 or 0xA1 fed to HandshakeCoordinator accept never throws - the channel-drop path`() {
        val coordinator = newCoordinator()
        coordinator.accept(origin, "Iam trunc-channel")

        for (tag in listOf(0xA0.toByte(), 0xA1.toByte())) {
            for (len in 0..12) {
                val truncated = ByteArray(len) { if (it == 0) tag else if (it == 1) 0x01 else it.toByte() }
                val result = runCatching { coordinator.accept(origin, truncated, "") }
                assertTrue(result.getOrNull()?.isSuccess ?: false, "tag 0x${tag.toString(16)} length $len must not throw/fail")
            }
        }
    }

    @Test
    fun `100 random garbage datagrams fed to a real client's receiveLoop never wedge it - a following real reliable frame still arrives`() {
        val peer = java.net.DatagramSocket()
        val client = com.spartanlabs.webtools.udp.MultiConnectionUDPClient(loopback, peer.localPort)
        try {
            val handshakeThread = Thread {
                val packet = java.net.DatagramPacket(ByteArray(256), 256)
                peer.soTimeout = 5_000
                peer.receive(packet)
                val clientOrigin = InetSocketAddress(packet.address, packet.port)
                val reg = "REGISTERED 2".toByteArray(Charsets.UTF_8)
                peer.send(java.net.DatagramPacket(reg, reg.size, clientOrigin.address, clientOrigin.port))
            }.apply { start() }
            assertTrue(client.handshake("fuzz").isSuccess)
            handshakeThread.join(5_000)

            val received = mutableListOf<ByteArray>()
            assertTrue(client.channel(com.spartanlabs.webtools.udp.DeliveryMode.RELIABLE_ORDERED).actuateBytes(received::add).isSuccess)

            val random = Random(11)
            val clientEndpoint = InetSocketAddress(loopback, client.localPort)
            repeat(100) {
                val bytes = ByteArray(random.nextInt(0, 65)) { random.nextInt(0, 256).toByte() }
                peer.send(java.net.DatagramPacket(bytes, bytes.size, clientEndpoint.address, clientEndpoint.port))
            }

            val realFrame = ReliableWireFormat.reliableDataDatagram(seq = 0, ack = 0xFFFF, ackBitfield = 0, payload = byteArrayOf(9))
            peer.send(java.net.DatagramPacket(realFrame, realFrame.size, clientEndpoint.address, clientEndpoint.port))

            val deadline = System.currentTimeMillis() + 3_000
            while (received.none { it.contentEquals(byteArrayOf(9)) } && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            assertTrue(received.any { it.contentEquals(byteArrayOf(9)) }, "a real reliable frame must still arrive after 100 garbage datagrams")
        } finally {
            runCatching { client.stop() }
            runCatching { peer.close() }
        }
    }
}
