package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakeConnection
import com.spartanlabs.testing.support.webtools.udp.FakePeriodicSchedule
import com.spartanlabs.webtools.udp.Admission
import com.spartanlabs.webtools.udp.HandshakeCoordinator
import com.spartanlabs.webtools.udp.ReliableWireFormat
import com.spartanlabs.webtools.udp.TransportWireFormat
import com.spartanlabs.webtools.udp.UdpChannel
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

// Level 1 - a fast pre-commit gate proving the channel-byte guard (Issue #40) exists: a
// socket-free HandshakeCoordinator smoke. No client-side smoke lives here: MultiConnectionUDPClient
// has no socket-free receive seam (its socket is a real java.net.DatagramSocket, unconditionally),
// so a client-side proof needs a real loopback socket - already covered at Level 2 by
// MultiConnectionUDPClientFramingTest's "a channel-0x01 0x90 from the peer is dropped with a WARN"
// test, which this file does not duplicate.
@Tag("gating")
class ChannelByteGuardGatingTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val origin = InetSocketAddress(loopback, 43001)

    private val retransmitSchedule = FakePeriodicSchedule()

    private fun newCoordinator() = HandshakeCoordinator(
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

    @Test
    fun `server drops a channel-0x01 0x90 and 0xA0, delivering only the channel-0x00 frame and arming no schedule`() {
        val coordinator = newCoordinator()
        coordinator.accept(origin, "Iam alice")
        val received = mutableListOf<ByteArray>()
        coordinator.bindBytes(origin, received::add)

        val nonZeroFrame = TransportWireFormat.unreliableDatagram(byteArrayOf(1), channel = 0x01)
        assertTrue(coordinator.accept(origin, nonZeroFrame, "").isSuccess)
        assertTrue(received.isEmpty(), "a channel-0x01 0x90 must not be delivered")

        val zeroFrame = TransportWireFormat.unreliableDatagram(byteArrayOf(2))
        assertTrue(coordinator.accept(origin, zeroFrame, "").isSuccess)
        assertContentEquals(byteArrayOf(2), received.single())

        val reliableNonZero = ReliableWireFormat.reliableDataDatagram(
            seq = 0, ack = 0xFFFF, ackBitfield = 0, payload = byteArrayOf(9), channel = 0x01,
        )
        assertTrue(coordinator.accept(origin, reliableNonZero, "").isSuccess)
        assertTrue(retransmitSchedule.scheduleCalls.isEmpty(), "a channel-0x01 0xA0 must arm no retransmit schedule")
    }
}
