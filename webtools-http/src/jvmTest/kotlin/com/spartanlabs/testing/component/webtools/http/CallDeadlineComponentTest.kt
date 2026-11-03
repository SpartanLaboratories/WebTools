package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.ManualTimeSource
import com.spartanlabs.webtools.http.CallDeadline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - the shared deadline on a manual clock: at the start `remainingMillis()` is the total in whole milliseconds; 999 us short of
 * the total it is `0`; at exactly 1 ms left it is `1`; at 1.9 ms left it is `1`; past the deadline it is `0`, never negative;
 * `elapsed()` follows the source.
 */
class CallDeadlineComponentTest {

    @Test
    fun `remainingMillis is the whole milliseconds left and never negative`() {
        val clock = ManualTimeSource()
        val deadline = CallDeadline(5.seconds, clock)
        assertEquals(5_000L, deadline.remainingMillis())
        clock.now = 5.seconds - 999.microseconds
        assertEquals(0L, deadline.remainingMillis())
        clock.now = 5.seconds - 1.milliseconds
        assertEquals(1L, deadline.remainingMillis())
        clock.now = 5.seconds - 1900.microseconds
        assertEquals(1L, deadline.remainingMillis())
        clock.now = 5.seconds
        assertEquals(0L, deadline.remainingMillis())
        clock.now = 7.seconds
        assertEquals(0L, deadline.remainingMillis())
    }

    @Test
    fun `elapsed follows the source`() {
        val clock = ManualTimeSource()
        clock.now = 2.seconds
        val deadline = CallDeadline(5.seconds, clock)
        assertEquals(0.seconds, deadline.elapsed())
        clock.now = 3500.milliseconds
        assertEquals(1500.milliseconds, deadline.elapsed())
        assertEquals(3_500L, deadline.remainingMillis())
    }
}
