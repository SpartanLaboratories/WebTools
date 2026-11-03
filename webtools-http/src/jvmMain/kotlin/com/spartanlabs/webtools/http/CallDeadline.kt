package com.spartanlabs.webtools.http

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * One deadline for every hop of a fetch, from [timeSource] at construction.
 *
 * @param total the whole fetch's call timeout.
 * @param timeSource the clock; `TimeSource.Monotonic` in production, a manual one in tests.
 */
internal class CallDeadline(private val total: Duration, timeSource: TimeSource = TimeSource.Monotonic) {
    private val mark: TimeMark = timeSource.markNow()

    /** Time since construction. */
    fun elapsed(): Duration = mark.elapsedNow()

    /** The whole milliseconds left, never negative; below 1 means the caller must fail with `Timeout` (a timeout of 0 would lift the limit). */
    fun remainingMillis(): Long = (total - mark.elapsedNow()).inWholeMilliseconds.coerceAtLeast(0)
}
