package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakeConnection
import com.spartanlabs.testing.support.webtools.udp.FakePeriodicSchedule
import com.spartanlabs.webtools.udp.Admission
import com.spartanlabs.webtools.udp.HandshakeCoordinator
import com.spartanlabs.webtools.udp.ReliableMessageTooLargeException
import com.spartanlabs.webtools.udp.ReliableWindowFullException
import com.spartanlabs.webtools.udp.ReliableWireFormat
import com.spartanlabs.webtools.udp.TransportWireFormat
import com.spartanlabs.webtools.udp.UdpChannel
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 2 - the reliable-channel seam wired into HandshakeCoordinator (§3.3/§3.4 of the
// Stage-3 plan), over recording fakes and a synchronous dispatch, so no socket or thread
// is involved. Mirrors HandshakeCoordinatorTest's shape for the reliable plane specifically.
@Tag("component")
class HandshakeCoordinatorReliableTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val originA = InetSocketAddress(loopback, 40101)
    private val originB = InetSocketAddress(loopback, 40102)

    private val keepAliveSchedule = FakePeriodicSchedule()
    private val probeSchedule = FakePeriodicSchedule()
    private val retransmitSchedule = FakePeriodicSchedule()

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
        keepAliveSchedule = keepAliveSchedule,
        probeSchedule = probeSchedule,
        retransmitSchedule = retransmitSchedule,
        reliableMaxMessageBytes = reliableMaxMessageBytes,
    )

    private fun reliableFrame(seq: Int = 0, payload: ByteArray = byteArrayOf(1)): ByteArray =
        ReliableWireFormat.reliableDataDatagram(seq = seq, ack = 0xFFFF, ackBitfield = 0, payload = payload)

    @Test
    fun `an inbound 0xA0 from a registered origin creates exactly one engine and arms exactly one schedule`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")

        assertTrue(coordinator.accept(originA, reliableFrame(seq = 0), "").isSuccess)
        assertTrue(coordinator.accept(originA, reliableFrame(seq = 1), "").isSuccess)

        assertEquals(1, retransmitSchedule.scheduleCalls.count { it.key == originA })
    }

    @Test
    fun `its payload reaches the bound reliable handler and not the bytes text handler`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val reliableReceived = mutableListOf<ByteArray>()
        val bytesReceived = mutableListOf<ByteArray>()
        coordinator.bindReliable(originA) { reliableReceived += it }
        coordinator.bindBytes(originA, bytesReceived::add)

        coordinator.accept(originA, reliableFrame(payload = byteArrayOf(9)), "")

        assertEquals(listOf(listOf<Byte>(9)), reliableReceived.map { it.toList() })
        assertTrue(bytesReceived.isEmpty())
    }

    @Test
    fun `the text bytes handler still receives 0x90 traffic while a reliable handler is bound`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val reliableReceived = mutableListOf<ByteArray>()
        val bytesReceived = mutableListOf<ByteArray>()
        coordinator.bindReliable(originA) { reliableReceived += it }
        coordinator.bindBytes(originA, bytesReceived::add)

        val framed = TransportWireFormat.unreliableDatagram(byteArrayOf(5))
        coordinator.accept(originA, framed, "")

        assertEquals(listOf(listOf<Byte>(5)), bytesReceived.map { it.toList() })
        assertTrue(reliableReceived.isEmpty())
    }

    @Test
    fun `an inbound 0xA0 from an unregistered origin creates no engine and no schedule`() {
        val coordinator = newCoordinator()

        assertTrue(coordinator.accept(originA, reliableFrame(), "").isSuccess)

        assertTrue(retransmitSchedule.scheduleCalls.isEmpty())
    }

    @Test
    fun `sendReliable to an unregistered peer fails with IllegalStateException`() {
        val coordinator = newCoordinator()

        val result = coordinator.sendReliable(originA, byteArrayOf(1))

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `an oversize sendReliable fails with ReliableMessageTooLargeException and puts nothing on the wire`() {
        var sent = 0
        val coordinator = newCoordinator(reliableMaxMessageBytes = 4, sender = { _, _ -> sent++; Result.success(Unit) })
        coordinator.accept(originA, "Iam alice")
        sent = 0 // discard the REGISTERED reply the handshake above just sent

        val result = coordinator.sendReliable(originA, ByteArray(5))

        assertTrue(result.isFailure)
        assertIs<ReliableMessageTooLargeException>(result.exceptionOrNull())
        assertEquals(0, sent, "nothing must reach the wire for an oversize reliable send")
    }

    @Test
    fun `a window-full sendReliable reports the engine's actual window size, never a spurious 0`() {
        // §12 B3: reliableEngineFor(reg) is bound to a val first and its windowSize reported,
        // rather than re-reading reg.reliable?.windowSize ?: 0 - which a concurrent deregister
        // or supersede could null between the send and the failure. No seam exists to override
        // ReliableChannelEngine's default windowSize (256) via HandshakeCoordinator.
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        val windowSize = 256
        repeat(windowSize) { i -> assertTrue(coordinator.sendReliable(originA, byteArrayOf(i.toByte())).isSuccess) }

        val result = coordinator.sendReliable(originA, byteArrayOf(1))

        val failure = assertIs<ReliableWindowFullException>(result.exceptionOrNull())
        assertEquals(windowSize, failure.inFlight, "must report the engine's real window size, not a spurious 0")
    }

    @Test
    fun `deregister cancels the tick and closes the engine`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        coordinator.accept(originA, reliableFrame(), "")

        coordinator.deregister(originA)

        assertEquals(listOf(originA), retransmitSchedule.cancels)

        // A fresh registration under the same origin gets a fresh engine (sequence restarts at 0).
        coordinator.accept(originA, "Iam alice")
        val received = mutableListOf<ByteArray>()
        coordinator.bindReliable(originA) { received += it }
        assertTrue(coordinator.accept(originA, reliableFrame(seq = 0, payload = byteArrayOf(7)), "").isSuccess)
        assertEquals(listOf(listOf<Byte>(7)), received.map { it.toList() })
    }

    @Test
    fun `a same-name supersede resets the channel - the new registration gets a fresh engine`() {
        val coordinator = newCoordinator()
        coordinator.accept(originA, "Iam alice")
        coordinator.accept(originA, reliableFrame(seq = 0), "")
        coordinator.accept(originA, reliableFrame(seq = 1), "")

        // Supersede: same name "alice", new origin.
        coordinator.accept(originB, "Iam alice")

        assertEquals(listOf(originA), retransmitSchedule.cancels)
        val received = mutableListOf<ByteArray>()
        coordinator.bindReliable(originB) { received += it }
        // seq 0 on a fresh engine must deliver immediately - a stale reorder-buffer expectation
        // (next seq 2, carried over from the superseded registration) would instead buffer it.
        assertTrue(coordinator.accept(originB, reliableFrame(seq = 0, payload = byteArrayOf(3)), "").isSuccess)
        assertEquals(listOf(listOf<Byte>(3)), received.map { it.toList() })
    }

    // --- reliable broadcast fold (actuateAllReliable / broadcastReliable), mirroring
    // HandshakeCoordinatorTest's coverage of actuateAll / broadcast / terminateAll ---

    @Test
    fun `actuateAllReliable and broadcastReliable are no-op successes with no registrations`() {
        val coordinator = newCoordinator()

        assertTrue(coordinator.actuateAllReliable { }.isSuccess)
        assertTrue(coordinator.broadcastReliable(byteArrayOf(1)).isSuccess)
    }

    @Test
    fun `one peer's full window does not stop broadcastReliable reaching the other peer - the non-short-circuiting fold`() {
        val sentTo = mutableListOf<InetSocketAddress>()
        val coordinator = newCoordinator(sender = { _, to -> sentTo += to; Result.success(Unit) })
        // originA registers first - Registrations.snapshot() is oldest-first, so this is the
        // registration broadcastReliable's fold visits first (mirrors
        // MultiConnectionUDPReliableBroadcastE2ETest's "stalled handshakes first" setup).
        coordinator.accept(originA, "Iam alice")
        coordinator.accept(originB, "Iam bob")
        val windowSize = 256
        repeat(windowSize) { i -> assertTrue(coordinator.sendReliable(originA, byteArrayOf(i.toByte())).isSuccess) }
        sentTo.clear()

        val result = coordinator.broadcastReliable(byteArrayOf(9))

        // broadcastReliable's fold evaluates each peer's sendReliable into a val before flatMap
        // (never short-circuiting, unlike broadcast's), so originA's window-full failure must not
        // stop originB from being attempted and sent to.
        assertTrue(result.isFailure, "the fold must surface originA's window-full failure")
        val failure = assertIs<ReliableWindowFullException>(result.exceptionOrNull())
        assertEquals(windowSize, failure.inFlight, "must report originA's real window size")
        assertTrue(originB in sentTo, "originB must still be attempted/sent despite originA's window being full")
    }
}
