package com.spartanlabs.webtools.udp

/**
 * The reliable engine's send-side state: the outbound sequence buffer of
 * unacked messages, doubling as the fixed in-flight window (design doc §7.2,
 * §7.5). The ring capacity is **unified with the window size** — a slot can
 * never be [offer]ed into while its occupant is still live, because [offer]
 * refuses once [windowSize] entries are in flight. This structurally rules
 * out the classic stale-ring-slot false-ack bug (*Reliable Ordered
 * Messages*, Gaffer/Fiedler): there is no separate defensive "clear entries
 * between the previous and new highest insert seq" pass needed, because a
 * slot is never reused while occupied.
 *
 * `@Synchronized` on every method — three threads touch it: the app thread
 * via [offer], the listener thread via [onAck], and the retransmit-tick
 * thread via [dueForRetransmit].
 *
 * @param windowSize the maximum number of unacked messages in flight at once
 * @param nanoClock monotonic time source; injectable for deterministic tests
 */
internal class ReliableRetransmitBuffer(
    private val windowSize: Int = 256,
    private val nanoClock: () -> Long = System::nanoTime,
) {
    /** The outcome of [offer]. */
    sealed interface OfferResult {
        /** [seq] was assigned and the message is now in flight. */
        data class Accepted(val seq: Int) : OfferResult

        /** [windowSize] messages are already in flight; nothing was buffered. */
        data object WindowFull : OfferResult
    }

    /** An RTT sample derived from an acked entry that was never retransmitted (Karn's algorithm). */
    data class AckedSample(val seq: Int, val rttMillis: Double)

    /** One in-flight entry whose RTO has elapsed and must go back out. */
    data class DueRetransmit(val seq: Int, val payload: ByteArray)

    private class Entry(
        val seq: Int,
        val payload: ByteArray,
        val firstSentAtNanos: Long,
        var lastSentAtNanos: Long,
        var rtoMillis: Long,
        var retransmitCount: Int = 0,
    )

    // Insertion order == seq order (offer() assigns seq monotonically), which is also the
    // order dueForRetransmit() and onAck's bitfield walk want to see entries in.
    private val inFlight = LinkedHashMap<Int, Entry>()
    private var nextSeq = 0

    /**
     * Assigns the next sequence number to [payload] and puts it in flight at
     * [initialRtoMillis], or refuses if [windowSize] messages are already
     * in flight.
     * @param payload the application message to buffer for retransmit
     * @param initialRtoMillis the RTO to arm this entry's first retransmit
     * check with (the estimator's current RTO at send time)
     * @param now the current monotonic time; defaults to [nanoClock]
     * @return [OfferResult.Accepted] with the assigned seq, or [OfferResult.WindowFull]
     */
    @Synchronized
    fun offer(payload: ByteArray, initialRtoMillis: Long, now: Long = nanoClock()): OfferResult {
        if (inFlight.size >= windowSize) return OfferResult.WindowFull
        val seq = nextSeq
        nextSeq = SerialSequence.add(nextSeq, 1)
        inFlight[seq] = Entry(seq, payload, firstSentAtNanos = now, lastSentAtNanos = now, rtoMillis = initialRtoMillis)
        return OfferResult.Accepted(seq)
    }

    /**
     * Removes every in-flight entry the `(ack, ackBitfield)` pair covers, per
     * the resolved "highest sequence received at all, gaps allowed" ack
     * semantics: an exact match on [ack] itself, plus, for each set bit `n`
     * (`0..31`) of [ackBitfield], the entry at seq `ack - n - 1`.
     * @param ack the highest sequence the peer has received at all
     * @param ackBitfield bit `n` set means `(ack - n - 1)` was also received
     * @param now the current monotonic time; defaults to [nanoClock]
     * @return one [AckedSample] per removed entry that was never retransmitted
     * (Karn's algorithm) — a retransmitted entry is still removed, just not sampled
     */
    @Synchronized
    fun onAck(ack: Int, ackBitfield: Int, now: Long = nanoClock()): List<AckedSample> {
        val coveredSeqs = buildList {
            add(ack)
            for (n in 0 until ACK_BITFIELD_BITS) {
                if ((ackBitfield ushr n) and 1 != 0) add(SerialSequence.add(ack, -(n + 1)))
            }
        }
        val samples = mutableListOf<AckedSample>()
        for (seq in coveredSeqs) {
            val entry = inFlight.remove(seq) ?: continue
            if (entry.retransmitCount == 0) {
                val rttMillis = (now - entry.firstSentAtNanos) / NANOS_PER_MILLI.toDouble()
                samples += AckedSample(seq, rttMillis)
            }
        }
        return samples
    }

    /**
     * Every in-flight entry whose RTO has elapsed since it was last sent:
     * `now - lastSentAtNanos >= entry.rtoMillis`. Each returned entry's own
     * `lastSentAtNanos` is bumped to [now], its retransmit count incremented,
     * and its own `rtoMillis` doubled (capped at [rtoCapMillis]) — RFC 6298
     * §5.5 per-message backoff. The shared SRTT/RTTVAR estimator is untouched
     * here; only the caller feeds it fresh samples via [onAck].
     * @param now the current monotonic time
     * @param rtoCapMillis the ceiling the doubled per-entry RTO is clamped to
     * @return every entry now due for retransmit, in seq order
     */
    @Synchronized
    fun dueForRetransmit(now: Long, rtoCapMillis: Long): List<DueRetransmit> {
        val due = mutableListOf<DueRetransmit>()
        for (entry in inFlight.values) {
            val elapsedMillis = (now - entry.lastSentAtNanos) / NANOS_PER_MILLI
            if (elapsedMillis >= entry.rtoMillis) {
                entry.lastSentAtNanos = now
                entry.retransmitCount++
                entry.rtoMillis = (entry.rtoMillis * 2).coerceAtMost(rtoCapMillis)
                due += DueRetransmit(entry.seq, entry.payload)
            }
        }
        return due
    }

    /** Drops every in-flight entry, discarding all un-acked data. */
    @Synchronized
    fun clear() {
        inFlight.clear()
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val ACK_BITFIELD_BITS = 32
    }
}
