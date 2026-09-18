package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.webtools.udp.ReliableRetransmitBuffer
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 2 - the reliable engine's send-side sequence buffer / in-flight window in isolation,
// driven with a controllable nanoClock so retransmit timing is deterministic. No socket, no timer.
@Tag("component")
class ReliableRetransmitBufferTest {

    private var now = 1_000_000_000_000L
    private val clock = { now }
    private val ms = 1_000_000L

    private fun buffer(window: Int = 256) = ReliableRetransmitBuffer(windowSize = window, nanoClock = clock)

    private fun advance(millis: Long) {
        now += millis * ms
    }

    @Test
    fun `offer assigns sequence numbers starting at zero and incrementing`() {
        val b = buffer()
        val first = assertIs<ReliableRetransmitBuffer.OfferResult.Accepted>(b.offer(byteArrayOf(1), 200L))
        val second = assertIs<ReliableRetransmitBuffer.OfferResult.Accepted>(b.offer(byteArrayOf(2), 200L))

        assertEquals(0, first.seq)
        assertEquals(1, second.seq)
    }

    @Test
    fun `offer refuses once windowSize messages are in flight`() {
        val b = buffer(window = 2)
        b.offer(byteArrayOf(1), 200L)
        b.offer(byteArrayOf(2), 200L)

        assertEquals(ReliableRetransmitBuffer.OfferResult.WindowFull, b.offer(byteArrayOf(3), 200L))
    }

    @Test
    fun `onAck removes the exact-match entry and yields an RTT sample`() {
        val b = buffer()
        b.offer(byteArrayOf(1), 200L)
        advance(30)

        val samples = b.onAck(ack = 0, ackBitfield = 0)

        assertEquals(1, samples.size)
        assertEquals(0, samples.single().seq)
        assertEquals(30.0, samples.single().rttMillis, 0.001)
    }

    @Test
    fun `onAck removes every bitfield-covered entry behind ack`() {
        val b = buffer()
        b.offer(byteArrayOf(0), 200L) // seq 0
        b.offer(byteArrayOf(1), 200L) // seq 1
        b.offer(byteArrayOf(2), 200L) // seq 2
        advance(10)

        // ack = 2 (highest received); bit 0 set => seq 1 also received; bit 1 clear => seq 0 not (yet).
        val samples = b.onAck(ack = 2, ackBitfield = 0b01)

        val ackedSeqs = samples.map { it.seq }.sorted()
        assertEquals(listOf(1, 2), ackedSeqs)
        // seq 0 is still in flight - confirmed indirectly via a later ack covering only it.
        val remaining = b.onAck(ack = 0, ackBitfield = 0)
        assertEquals(listOf(0), remaining.map { it.seq })
    }

    @Test
    fun `Karn's algorithm - a retransmitted entry is removed on ack but yields no RTT sample`() {
        val b = buffer()
        b.offer(byteArrayOf(1), initialRtoMillis = 50L)
        advance(60) // past the 50 ms RTO
        val due = b.dueForRetransmit(now, rtoCapMillis = 5_000L)
        assertEquals(1, due.size, "the entry is due after its RTO elapsed")

        advance(10)
        val samples = b.onAck(ack = 0, ackBitfield = 0)

        assertTrue(samples.isEmpty(), "a retransmitted entry must not produce an RTT sample")
    }

    @Test
    fun `dueForRetransmit fires once the per-entry RTO elapses and doubles that entry's RTO, capped`() {
        val b = buffer()
        b.offer(byteArrayOf(1), initialRtoMillis = 100L)

        advance(50)
        assertTrue(b.dueForRetransmit(now, rtoCapMillis = 5_000L).isEmpty(), "not yet due")

        advance(60) // total 110 ms since send >= 100 ms RTO
        val firstDue = b.dueForRetransmit(now, rtoCapMillis = 5_000L)
        assertEquals(1, firstDue.size)

        // RTO doubled to 200 ms - not due again after only 100 ms more.
        advance(100)
        assertTrue(b.dueForRetransmit(now, rtoCapMillis = 5_000L).isEmpty(), "backed off, not due yet")

        advance(150) // total 250 ms since the last send >= the doubled 200 ms RTO
        assertEquals(1, b.dueForRetransmit(now, rtoCapMillis = 5_000L).size)
    }

    @Test
    fun `dueForRetransmit backoff is capped at rtoCapMillis`() {
        val b = buffer()
        b.offer(byteArrayOf(1), initialRtoMillis = 4_000L)

        advance(4_100)
        b.dueForRetransmit(now, rtoCapMillis = 5_000L) // backs off toward 8000, capped to 5000

        advance(4_900)
        assertTrue(b.dueForRetransmit(now, rtoCapMillis = 5_000L).isEmpty(), "capped RTO not yet elapsed")

        advance(200)
        assertEquals(1, b.dueForRetransmit(now, rtoCapMillis = 5_000L).size, "capped RTO now elapsed")
    }

    @Test
    fun `clear drops every in-flight entry and frees the window`() {
        val b = buffer(window = 2)
        b.offer(byteArrayOf(1), 200L)
        b.offer(byteArrayOf(2), 200L)
        assertEquals(ReliableRetransmitBuffer.OfferResult.WindowFull, b.offer(byteArrayOf(3), 200L))

        b.clear()

        // The cleared entries produce no ack samples...
        assertTrue(b.onAck(ack = 0, ackBitfield = 0).isEmpty())
        assertTrue(b.onAck(ack = 1, ackBitfield = 0).isEmpty())
        // ...and the window has room again.
        assertIs<ReliableRetransmitBuffer.OfferResult.Accepted>(b.offer(byteArrayOf(9), 200L))
    }
}
