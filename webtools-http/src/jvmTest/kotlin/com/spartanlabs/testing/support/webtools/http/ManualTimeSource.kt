package com.spartanlabs.testing.support.webtools.http

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A [TimeSource] whose marks read a settable [now]: a test advances the clock by assigning it, and every mark's `elapsedNow()` is
 * `now` minus the value at the mark. Nothing here sleeps.
 */
internal class ManualTimeSource : TimeSource {
    /** The current reading; assign to advance (or rewind) the clock. */
    var now: Duration = Duration.ZERO

    override fun markNow(): TimeMark {
        val at = now
        return object : TimeMark {
            override fun elapsedNow(): Duration = now - at
        }
    }
}
