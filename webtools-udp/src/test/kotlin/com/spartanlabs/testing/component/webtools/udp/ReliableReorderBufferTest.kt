package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.webtools.udp.ReliableReorderBuffer
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// Level 2 - the reliable engine's receive-side delivery cursor + bounded reorder buffer in
// isolation. Stateful (a delivery cursor), so component-only, no gating/deterministic file -
// mirrors LinkQualityTracker's precedent.
@Tag("component")
class ReliableReorderBufferTest {

    private fun payloadOf(n: Int) = byteArrayOf(n.toByte())

    @Test
    fun `ackAndBitfield is the documented sentinel before any receipt`() {
        val b = ReliableReorderBuffer(windowSize = 8)

        assertEquals(0xFFFF to 0, b.ackAndBitfield())
    }

    @Test
    fun `an in-order message is delivered immediately`() {
        val b = ReliableReorderBuffer(windowSize = 8)

        val outcome = assertIs<ReliableReorderBuffer.ReceiveOutcome.Delivered>(b.onReceive(0, payloadOf(0)))

        assertEquals(listOf(payloadOf(0).toList()), outcome.messages.map { it.toList() })
    }

    @Test
    fun `buffered-ahead messages drain once the gap is filled`() {
        val b = ReliableReorderBuffer(windowSize = 8)

        assertIs<ReliableReorderBuffer.ReceiveOutcome.Buffered>(b.onReceive(2, payloadOf(2)))
        assertIs<ReliableReorderBuffer.ReceiveOutcome.Buffered>(b.onReceive(1, payloadOf(1)))

        val outcome = assertIs<ReliableReorderBuffer.ReceiveOutcome.Delivered>(b.onReceive(0, payloadOf(0)))

        assertEquals(listOf(0, 1, 2), outcome.messages.map { it[0].toInt() })
    }

    @Test
    fun `a duplicate of an already-delivered seq is DuplicateOrOld`() {
        val b = ReliableReorderBuffer(windowSize = 8)
        b.onReceive(0, payloadOf(0))

        assertEquals(ReliableReorderBuffer.ReceiveOutcome.DuplicateOrOld, b.onReceive(0, payloadOf(0)))
    }

    @Test
    fun `a duplicate of an already-buffered seq is DuplicateOrOld`() {
        val b = ReliableReorderBuffer(windowSize = 8)
        b.onReceive(3, payloadOf(3))

        assertEquals(ReliableReorderBuffer.ReceiveOutcome.DuplicateOrOld, b.onReceive(3, payloadOf(3)))
    }

    @Test
    fun `a seq beyond the window is OutOfWindow and never buffered`() {
        val b = ReliableReorderBuffer(windowSize = 4)

        assertEquals(ReliableReorderBuffer.ReceiveOutcome.OutOfWindow, b.onReceive(10, payloadOf(10)))
        // Confirmed not buffered: the ack/bitfield still reflects nothing received.
        assertEquals(0xFFFF to 0, b.ackAndBitfield())
    }

    @Test
    fun `ackAndBitfield reflects the highest received seq and the bitfield covers buffered gaps`() {
        val b = ReliableReorderBuffer(windowSize = 8)
        b.onReceive(0, payloadOf(0)) // delivered, cursor -> 1
        b.onReceive(3, payloadOf(3)) // buffered ahead (gap at 1, 2)

        val (ack, ackBitfield) = b.ackAndBitfield()

        assertEquals(3, ack)
        // bit n set => (ack - n - 1) received: bit1 -> seq1 (delivered? no, not received) etc.
        // seq2 (bit0) not received, seq1 (bit1) not received, seq0 (bit2) delivered -> received.
        assertEquals(0, ackBitfield and 0b001, "seq 2 was never received")
        assertEquals(0, ackBitfield and 0b010, "seq 1 was never received")
        assertEquals(0b100, ackBitfield and 0b100, "seq 0 was delivered - received")
    }

    @Test
    fun `clear resets the cursor and drops every buffered entry`() {
        val b = ReliableReorderBuffer(windowSize = 8)
        b.onReceive(0, payloadOf(0))
        b.onReceive(2, payloadOf(2))

        b.clear()

        assertEquals(0xFFFF to 0, b.ackAndBitfield())
        assertIs<ReliableReorderBuffer.ReceiveOutcome.Delivered>(b.onReceive(0, payloadOf(0)))
    }
}
