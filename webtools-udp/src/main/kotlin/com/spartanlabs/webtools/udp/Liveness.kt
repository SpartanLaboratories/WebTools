package com.spartanlabs.webtools.udp

/**
 * Pure, socket-free, timer-free rules for the [MultiConnectionUDPServer] idle
 * connection sweep: whether a connection is overdue, and how often to sweep.
 * Deterministic functions of their arguments - fully unit-testable.
 */
internal object Liveness {
    /** Smallest / largest gap between idle sweeps, regardless of the threshold. */
    private const val MIN_SWEEP_MILLIS = 250L
    private const val MAX_SWEEP_MILLIS = 5_000L

    /** Sweep this many times per threshold window. */
    private const val SWEEPS_PER_WINDOW = 4L

    /**
     * True if the last inbound datagram is older than [idleTimeoutMillis].
     * Always false when [idleTimeoutMillis] <= 0 (detection disabled). Compares
     * in milliseconds so a pathological threshold cannot overflow.
     *
     * @param lastInboundAtNanos monotonic `nanoTime` of the last inbound datagram
     * @param nowNanos monotonic `nanoTime` sampled at the start of the sweep
     * @param idleTimeoutMillis the configured idle threshold; <= 0 disables detection
     * @return true if the connection has been idle for at least [idleTimeoutMillis]
     */
    fun isOverdue(lastInboundAtNanos: Long, nowNanos: Long, idleTimeoutMillis: Long): Boolean {
        if (idleTimeoutMillis <= 0L) return false
        val idleMillis = (nowNanos - lastInboundAtNanos) / 1_000_000L
        return idleMillis >= idleTimeoutMillis
    }

    /**
     * How long the scheduled sweep should wait between runs for a given
     * threshold: one quarter of the window, clamped to `250..5000` ms. Worst-case
     * detection latency is `idleTimeoutMillis + sweepIntervalMillis(idleTimeoutMillis)`.
     *
     * @param idleTimeoutMillis the configured idle threshold
     * @return the sweep interval in milliseconds, always within `250..5000`
     */
    fun sweepIntervalMillis(idleTimeoutMillis: Long): Long =
        (idleTimeoutMillis / SWEEPS_PER_WINDOW).coerceIn(MIN_SWEEP_MILLIS, MAX_SWEEP_MILLIS)
}
