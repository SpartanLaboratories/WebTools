package com.spartanlabs.webtools.udp

/**
 * Per-endpoint link-quality accumulator for the opt-in probe. Thread-safe: the
 * probe-scheduler thread calls [beginProbe] / [sweep], the listener thread calls
 * [completeProbe], and any consumer thread calls [snapshot]; every method is
 * `@Synchronized` (contention is ~2 short calls per probe interval).
 *
 * @param nanoClock monotonic time source; injectable for deterministic tests
 * @param windowSize how many recent probes inform the loss ratio (ring capacity)
 */
internal class LinkQualityTracker(
    private val nanoClock: () -> Long = System::nanoTime,
    private val windowSize: Int = DEFAULT_WINDOW,
) {
    // IN_FLIGHT -> DELIVERED | LOST; DELIVERED and LOST are terminal.
    private enum class State { IN_FLIGHT, DELIVERED, LOST }

    private class Probe(val seq: Long, val sentAtNanos: Long) {
        var state = State.IN_FLIGHT
    }

    // Insertion order; the oldest probe is evicted first once the ring is full.
    private val ring = ArrayDeque<Probe>(windowSize)
    private var nextSeq = 0L
    private var probesSent = 0L
    private var probesDelivered = 0L

    // NaN until the first delivered sample seeds SRTT (RFC 6298 first-sample rule).
    private var srttMillis = Double.NaN
    private var rttVarMillis = 0.0

    /**
     * The probe period in milliseconds: how often a `PING` goes out, and the unit
     * the loss-aging sweep multiplies by [Rtt.LOSS_HORIZON_INTERVALS] when it asks
     * [Rtt.isProbeLost] whether an unacknowledged probe is old enough to count as
     * lost. Written exactly once, when the probe is armed
     * ([HandshakeCoordinator.scheduleProbe] / [MultiConnectionUDPClient.startProbe]),
     * before the first tick calls [beginProbe]. The initializer below is only a
     * placeholder for that window and is never the effective value of a running
     * probe. `@Volatile` so the arm-time write is visible to the `mcup?-probe`
     * sweep thread.
     */
    @Volatile
    var probeIntervalMillis: Long = HandshakeProtocol.DEFAULT_PROBE_INTERVAL_MILLIS

    /**
     * Records a new outstanding probe and returns its sequence number for the
     * `PING` token. Evicts the oldest probe once the ring is full.
     * @return the monotonically increasing sequence number of the new probe
     */
    @Synchronized
    fun beginProbe(): Long {
        val seq = nextSeq++
        ring.addLast(Probe(seq, nanoClock()))
        while (ring.size > windowSize) ring.removeFirst()
        probesSent++
        return seq
    }

    /**
     * Matches [seq] to an in-flight probe, feeds `now - sentAt` (milliseconds) into
     * the EWMA, and marks it DELIVERED. A no-op for an unknown or already-terminal
     * [seq], so a duplicate or garbage `PONG` cannot double-count.
     * @param seq the sequence number echoed back in a `PONG`
     */
    @Synchronized
    fun completeProbe(seq: Long) {
        val probe = ring.firstOrNull { it.seq == seq && it.state == State.IN_FLIGHT } ?: return
        val sampleMillis = (nanoClock() - probe.sentAtNanos) / 1_000_000.0
        if (probesDelivered == 0L) {
            // RFC 6298 first measurement: SRTT = sample, RTTVAR = sample / 2.
            srttMillis = sampleMillis
            rttVarMillis = sampleMillis / 2.0
        } else {
            // RFC 6298 orders the RTTVAR update before the SRTT update.
            rttVarMillis = Rtt.smoothedRttVarianceMillis(rttVarMillis, srttMillis, sampleMillis)
            srttMillis = Rtt.smoothedRttMillis(srttMillis, sampleMillis)
        }
        probe.state = State.DELIVERED
        probesDelivered++
    }

    /** Marks every IN_FLIGHT probe that [Rtt.isProbeLost] declares as LOST (a terminal transition). */
    @Synchronized
    fun sweep() {
        val now = nanoClock()
        ring.forEach { probe ->
            if (probe.state == State.IN_FLIGHT && Rtt.isProbeLost(probe.sentAtNanos, now, probeIntervalMillis)) {
                probe.state = State.LOST
            }
        }
    }

    /**
     * An immutable snapshot, or `null` until the first probe has been delivered
     * (so a consumer never reads an un-seeded RTT). The loss ratio is over the
     * ring's terminal (DELIVERED + LOST) probes only.
     * @return the current [LinkQuality], or `null` if nothing has resolved yet
     */
    @Synchronized
    fun snapshot(): LinkQuality? {
        if (probesDelivered == 0L) return null
        val lost = ring.count { it.state == State.LOST }
        val delivered = ring.count { it.state == State.DELIVERED }
        return LinkQuality(srttMillis, rttVarMillis, Rtt.lossRatio(lost, delivered), probesSent, probesDelivered)
    }

    private companion object {
        const val DEFAULT_WINDOW = 64
    }
}
