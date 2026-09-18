package com.spartanlabs.webtools.udp

/**
 * A private per-channel RTO estimator for the reliable engine (design doc
 * D3), built on [Rtt]'s pure functions — deliberately **not** the shared
 * [LinkQualityTracker], so the reliable channel's RTT/RTO state stays its own
 * (a private estimator seeded from data acks; unify later only if the
 * reliable channel needs to publish stats).
 *
 * `@Synchronized` on every method, mirroring [LinkQualityTracker]'s
 * discipline: the listener thread (an inbound ack) and the retransmit-tick
 * thread both touch this instance.
 *
 * Karn's algorithm (skip the RTT sample if the message was retransmitted) is
 * enforced by the *caller* ([ReliableRetransmitBuffer]), not this class —
 * this class only ever sees samples it should trust.
 *
 * @param floorMillis the minimum RTO ever returned, however small SRTT/RTTVAR
 * are; a deliberate deviation below RFC 6298's 1 s default for a low-latency
 * real-time transport
 * @param capMillis the maximum RTO ever returned, so a black-holed link does
 * not push the RTO to absurdity
 */
internal class ReliableRtoEstimator(
    private val floorMillis: Long = 200L,
    private val capMillis: Long = 5_000L,
) {
    // NaN until the first sample seeds SRTT (RFC 6298 first-sample rule), mirroring LinkQualityTracker.
    private var srttMillis = Double.NaN
    private var rttVarMillis = 0.0

    /**
     * Feeds a trusted RTT sample into the estimator. The first call seeds
     * `SRTT = sample`, `RTTVAR = sample / 2` (RFC 6298's first-measurement
     * rule); every later call applies the RFC 6298 EWMA update, RTTVAR before
     * SRTT.
     * @param sampleMillis the round-trip time just measured, in milliseconds
     */
    @Synchronized
    fun onRttSample(sampleMillis: Double) {
        if (srttMillis.isNaN()) {
            srttMillis = sampleMillis
            rttVarMillis = sampleMillis / 2.0
        } else {
            rttVarMillis = Rtt.smoothedRttVarianceMillis(rttVarMillis, srttMillis, sampleMillis)
            srttMillis = Rtt.smoothedRttMillis(srttMillis, sampleMillis)
        }
    }

    /**
     * The current RTO, in milliseconds. [floorMillis] before the first sample
     * has ever seeded the estimator (there is no RTT to base a formula on
     * yet); [Rtt.rtoMillis] thereafter, clamped to `[floorMillis, capMillis]`.
     * @return the current RTO in milliseconds
     */
    @Synchronized
    fun currentRtoMillis(): Long =
        if (srttMillis.isNaN()) floorMillis
        else Rtt.rtoMillis(srttMillis, rttVarMillis, floorMillis, capMillis).toLong()
}
