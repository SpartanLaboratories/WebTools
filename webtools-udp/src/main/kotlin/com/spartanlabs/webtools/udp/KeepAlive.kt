package com.spartanlabs.webtools.udp

/**
 * Pure, socket-free, timer-free rules for the opt-in scheduled keepalive: how
 * often the scheduler wakes, and whether a keepalive is due given the last time
 * something was sent. Deterministic functions of their arguments - fully
 * unit-testable. Sibling of [Liveness].
 */
internal object KeepAlive {
    private const val MIN_POLL_MILLIS = 250L
    private const val MAX_POLL_MILLIS = 5_000L
    private const val POLLS_PER_INTERVAL = 4L

    /**
     * How long the scheduled task should wait between wake-ups for a given
     * keepalive interval: one quarter of the interval, clamped to
     * [MIN_POLL_MILLIS]..[MAX_POLL_MILLIS]. Bounding the poll period this way
     * caps how late a keepalive can go out: at most
     * `intervalMillis + pollIntervalMillis(intervalMillis)` after the last
     * outbound datagram.
     *
     * @param intervalMillis the configured output-idle keepalive interval
     * @return the poll interval in milliseconds, always within `250..5000`
     */
    fun pollIntervalMillis(intervalMillis: Long): Long =
        (intervalMillis / POLLS_PER_INTERVAL).coerceIn(MIN_POLL_MILLIS, MAX_POLL_MILLIS)

    /**
     * True if the last outbound datagram is at least [intervalMillis] old, i.e. a
     * keepalive should be sent now. Always true when [intervalMillis] <= 0.
     * Compares in milliseconds so a pathological interval cannot overflow; a
     * [nowNanos] before [lastOutboundAtNanos] (benign clock skew within one poll)
     * yields false.
     *
     * @param lastOutboundAtNanos monotonic `nanoTime` of the last outbound datagram
     * @param nowNanos monotonic `nanoTime` sampled at the start of the poll
     * @param intervalMillis the configured output-idle threshold; <= 0 forces a keepalive
     * @return true if a keepalive should be sent now
     */
    fun isDue(lastOutboundAtNanos: Long, nowNanos: Long, intervalMillis: Long): Boolean {
        if (intervalMillis <= 0L) return true
        val idleMillis = (nowNanos - lastOutboundAtNanos) / 1_000_000L
        return idleMillis >= intervalMillis
    }
}
