package com.spartanlabs.webtools.udp

/**
 * Pure, socket-free, timer-free rules for the opt-in link-quality probe: EWMA
 * smoothing of an RTT sample, RTT-variation smoothing, the windowed loss ratio,
 * and whether an unacknowledged probe is old enough to count as lost.
 * Deterministic functions of their arguments. Sibling of [Liveness] / [KeepAlive].
 */
internal object Rtt {
    // RFC 6298 gains: SRTT is smoothed with alpha = 1/8, RTTVAR with beta = 1/4.
    private const val SRTT_ALPHA = 0.125
    private const val RTTVAR_BETA = 0.25

    /** How many probe intervals an unacked probe may age before it is declared lost. */
    const val LOSS_HORIZON_INTERVALS = 3L

    /**
     * EWMA of the round-trip time: `prev + alpha * (sample - prev)` (RFC 6298 SRTT
     * update, alpha = 1/8).
     * @param previousMillis the current smoothed RTT
     * @param sampleMillis the new raw RTT measurement
     * @return the updated smoothed RTT in milliseconds
     */
    fun smoothedRttMillis(previousMillis: Double, sampleMillis: Double): Double =
        previousMillis + SRTT_ALPHA * (sampleMillis - previousMillis)

    /**
     * EWMA of `|SRTT - sample|`, the RFC 6298 RTTVAR (a jitter proxy); never
     * negative because it is a convex blend of a non-negative previous value and a
     * non-negative absolute difference.
     * @param previousVarMillis the current smoothed RTT variation
     * @param previousRttMillis the smoothed RTT the sample is compared against
     * @param sampleMillis the new raw RTT measurement
     * @return the updated smoothed RTT variation in milliseconds, `>= 0`
     */
    fun smoothedRttVarianceMillis(
        previousVarMillis: Double,
        previousRttMillis: Double,
        sampleMillis: Double,
    ): Double =
        (1 - RTTVAR_BETA) * previousVarMillis + RTTVAR_BETA * kotlin.math.abs(previousRttMillis - sampleMillis)

    /**
     * `lost / (lost + delivered)`, clamped to `0.0..1.0`; `0.0` when nothing has
     * resolved.
     * @param lost count of probes that aged past the loss horizon unanswered
     * @param delivered count of probes answered with a matching `PONG`
     * @return the loss ratio in `0.0..1.0`
     */
    fun lossRatio(lost: Int, delivered: Int): Double {
        val resolved = lost + delivered
        return if (resolved <= 0) 0.0 else (lost.toDouble() / resolved).coerceIn(0.0, 1.0)
    }

    /**
     * True if a probe sent at [sentAtNanos] is still unacknowledged and now older
     * than [LOSS_HORIZON_INTERVALS] * [intervalMillis]. Compares in milliseconds
     * so a pathological interval cannot overflow; a [nowNanos] before
     * [sentAtNanos] yields false. Always false for [intervalMillis] <= 0.
     * @param sentAtNanos monotonic `nanoTime` when the probe went out
     * @param nowNanos monotonic `nanoTime` sampled at the start of the sweep
     * @param intervalMillis the configured probe interval; <= 0 disables loss aging
     * @return true if the probe should be counted as lost now
     */
    fun isProbeLost(sentAtNanos: Long, nowNanos: Long, intervalMillis: Long): Boolean {
        if (intervalMillis <= 0L) return false
        val ageMillis = (nowNanos - sentAtNanos) / 1_000_000L
        return ageMillis >= LOSS_HORIZON_INTERVALS * intervalMillis
    }
}
