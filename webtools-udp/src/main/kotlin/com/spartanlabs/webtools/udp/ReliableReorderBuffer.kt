package com.spartanlabs.webtools.udp

/**
 * The reliable engine's receive-side state: the in-order delivery cursor plus
 * a bounded reorder buffer for messages that arrive ahead of it (design doc
 * §7.4). Bounded ⇒ bounded receive-side memory even under pathological
 * reordering.
 *
 * "Received" status is **derived**, never double-stored: a seq is received if
 * it precedes [cursor] (already delivered) or the ring slot it maps to still
 * holds exactly that seq (still buffered). That slot-equality check is what
 * makes a stale/reused ring slot read as "not received" (a safe under-report,
 * recovered by the sender's retransmit) rather than a false positive — the
 * classic stale-ring-slot bug this design structurally avoids.
 *
 * `@Synchronized` — the listener thread calls [onReceive]; the app thread
 * (piggybacked acks) and the retransmit-tick thread (standalone acks) both
 * call [ackAndBitfield].
 *
 * @param windowSize how far ahead of [cursor] an out-of-order message may
 * still be buffered; also the ring's capacity
 */
internal class ReliableReorderBuffer(private val windowSize: Int = 256) {
    /** The outcome of [onReceive]. */
    sealed interface ReceiveOutcome {
        /** [seq] was the next in-order message; [messages] is it plus any now-contiguous drained successors. */
        data class Delivered(val messages: List<ByteArray>) : ReceiveOutcome

        /** [seq] was ahead of [cursor] but within the window; stored, not yet deliverable. */
        data object Buffered : ReceiveOutcome

        /** [seq] was already delivered or is already buffered — a duplicate/retransmit. */
        data object DuplicateOrOld : ReceiveOutcome

        /** [seq] was too far ahead of [cursor] to buffer; dropped, relying on the sender's retransmit. */
        data object OutOfWindow : ReceiveOutcome
    }

    private class Entry(val seq: Int, val payload: ByteArray)

    /** The next in-order sequence number this side expects to deliver. */
    private var cursor = 0

    /**
     * How many messages have ever actually been delivered. Guards the
     * "already delivered" check against the ambiguity of `cursor == 0`: a
     * fresh buffer and a buffer that has genuinely wrapped all the way around
     * the 16-bit space are numerically indistinguishable by `cursor` alone,
     * but [SerialSequence.lessThan] would otherwise read every "future"
     * (never-yet-sent) seq behind a pristine `cursor == 0` as "before it" -
     * i.e., already delivered. A candidate only counts as delivered if it is
     * both behind [cursor] *and* within the number of messages actually
     * delivered so far.
     */
    private var deliveredCount = 0L

    private val ring = arrayOfNulls<Entry>(windowSize)

    /** True if [seq] has genuinely already been delivered - see [deliveredCount]. */
    private fun isAlreadyDelivered(seq: Int): Boolean =
        SerialSequence.lessThan(seq, cursor) && SerialSequence.wrap(cursor - seq) <= deliveredCount

    /**
     * Accepts an inbound reliable-data message and decides deliver / buffer /
     * drop. Ack-tracking state (the ring / cursor) is updated as part of this
     * same decision, never as an afterthought conditional on the outcome
     * label, so [ackAndBitfield] always reflects exactly what is actually
     * held.
     * @param seq the message's sequence number, `0..65535`
     * @param payload the message's application bytes
     * @return the [ReceiveOutcome] — what the caller should do next
     */
    @Synchronized
    fun onReceive(seq: Int, payload: ByteArray): ReceiveOutcome = when {
        isAlreadyDelivered(seq) -> ReceiveOutcome.DuplicateOrOld
        ring[seq % windowSize]?.seq == seq -> ReceiveOutcome.DuplicateOrOld
        !SerialSequence.inWindow(seq, cursor, windowSize) -> ReceiveOutcome.OutOfWindow
        seq == cursor -> ReceiveOutcome.Delivered(deliverAndDrain(payload))
        else -> {
            ring[seq % windowSize] = Entry(seq, payload)
            ReceiveOutcome.Buffered
        }
    }

    /** Delivers [firstPayload] at the current cursor, then drains any now-contiguous buffered successors. */
    private fun deliverAndDrain(firstPayload: ByteArray): List<ByteArray> {
        val delivered = mutableListOf(firstPayload)
        cursor = SerialSequence.add(cursor, 1)
        deliveredCount++
        while (true) {
            val idx = cursor % windowSize
            val next = ring[idx] ?: break
            if (next.seq != cursor) break
            delivered += next.payload
            ring[idx] = null
            cursor = SerialSequence.add(cursor, 1)
            deliveredCount++
        }
        return delivered
    }

    /**
     * The current `(ack, ackBitfield)` pair to piggyback or send standalone:
     * `ack` is the highest sequence received at all (gaps allowed — not
     * necessarily in order), derived as the highest of `cursor - 1` (the last
     * in-order delivery) and every seq still held in the ring; bit `n` of the
     * bitfield is set when `ack - n - 1` is received (delivered or buffered).
     * Always returns a value — before the first [onReceive], `cursor == 0`
     * and the ring is empty, so this naturally yields the sentinel
     * `(0xFFFF, 0)`.
     * @return the `(ack, ackBitfield)` pair
     */
    @Synchronized
    fun ackAndBitfield(): Pair<Int, Int> {
        var highest = SerialSequence.add(cursor, -1)
        for (entry in ring) {
            if (entry != null && SerialSequence.lessThan(highest, entry.seq)) highest = entry.seq
        }
        var bitfield = 0
        for (n in 0 until ACK_BITFIELD_BITS) {
            val candidate = SerialSequence.add(highest, -(n + 1))
            val received = isAlreadyDelivered(candidate) || ring[candidate % windowSize]?.seq == candidate
            if (received) bitfield = bitfield or (1 shl n)
        }
        return highest to bitfield
    }

    /** Resets the cursor and [deliveredCount] to `0` and drops every buffered entry. */
    @Synchronized
    fun clear() {
        cursor = 0
        deliveredCount = 0L
        ring.fill(null)
    }

    private companion object {
        const val ACK_BITFIELD_BITS = 32
    }
}
