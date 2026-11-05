package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.nowAt
import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi
import com.spartanlabs.webtools.http.LedgerClock
import com.spartanlabs.webtools.http.Pause
import com.spartanlabs.webtools.http.WallClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Level 4a - `Pause.of`, `isOver` (either axis), `remaining` (the smaller of the two), `extendedTo` (axis by axis, never shorter),
 * `restored` (the remaining length capped by `maxPause`, zero when past); `LedgerClock` keeps the elapsed reading non-decreasing
 * over a time source that steps back and reads the wall clock as given.
 */
class PauseDeterministicTest {

    /** A time source whose reading is assigned by the test, so it can step back; the pure sibling of the JVM test support's manual source. */
    private class SteppingTimeSource : TimeSource {
        var now: Duration = Duration.ZERO
        override fun markNow(): TimeMark {
            val at = now
            return object : TimeMark {
                override fun elapsedNow(): Duration = now - at
            }
        }
    }

    @OptIn(ExperimentalForInheritanceWebToolsApi::class)
    private class FixedWallClock(var wall: Long) : WallClock {
        override fun nowEpochMillis(): Long = wall
    }

    @Test
    fun `of stamps both axes from the reading`() {
        val pause = Pause.of(nowAt(1_000L, 5_000L), 3_000L)
        assertEquals(8.seconds, pause.untilElapsed)
        assertEquals(4_000L, pause.untilEpochMillis)
    }

    @Test
    fun `isOver when either axis has passed`() {
        val pause = Pause.of(nowAt(1_000L, 5_000L), 3_000L)
        assertFalse(pause.isOver(nowAt(3_999L, 7_999L)))
        assertTrue(pause.isOver(nowAt(4_000L, 7_000L)))
        assertTrue(pause.isOver(nowAt(3_000L, 8_000L)))
        assertTrue(pause.isOver(nowAt(4_000L, 8_000L)))
    }

    @Test
    fun `remaining is the smaller of the two axes and zero once over`() {
        val pause = Pause.of(nowAt(1_000L, 5_000L), 3_000L)
        assertEquals(2.seconds, pause.remaining(nowAt(2_000L, 5_500L)))
        assertEquals(1.seconds, pause.remaining(nowAt(1_000L, 7_000L)))
        assertEquals(3.seconds, pause.remaining(nowAt(1_000L, 5_000L)))
        assertEquals(Duration.ZERO, pause.remaining(nowAt(4_000L, 5_000L)))
        assertEquals(Duration.ZERO, pause.remaining(nowAt(1_000L, 8_000L)))
        assertEquals(Duration.ZERO, pause.remaining(nowAt(9_000L, 9_000L)))
    }

    @Test
    fun `extendedTo takes the later end on each axis`() {
        val a = Pause(8.seconds, 4_000L)
        val b = Pause(6.seconds, 9_000L)
        val merged = a.extendedTo(b)
        assertEquals(8.seconds, merged.untilElapsed)
        assertEquals(9_000L, merged.untilEpochMillis)
        val shorter = a.extendedTo(Pause(1.seconds, 1L))
        assertEquals(8.seconds, shorter.untilElapsed)
        assertEquals(4_000L, shorter.untilEpochMillis)
    }

    @Test
    fun `restored converts the saved end once, capped by maxPause, and is over when in the past`() {
        val now = nowAt(10_000L, 1_000L)
        val capped = Pause.restored(now, 70_000L, 30_000L)
        assertEquals(31.seconds, capped.untilElapsed)
        assertEquals(40_000L, capped.untilEpochMillis)
        val within = Pause.restored(now, 25_000L, 30_000L)
        assertEquals(16.seconds, within.untilElapsed)
        assertEquals(25_000L, within.untilEpochMillis)
        val past = Pause.restored(now, 5_000L, 30_000L)
        assertEquals(1.seconds, past.untilElapsed)
        assertEquals(10_000L, past.untilEpochMillis)
        assertTrue(past.isOver(now))
        assertEquals(Duration.ZERO, past.remaining(now))
    }

    @Test
    fun `LedgerClock keeps the elapsed reading non-decreasing over a source that steps back`() {
        val source = SteppingTimeSource()
        val wall = FixedWallClock(1_000L)
        val clock = LedgerClock(source, wall)
        assertEquals(Duration.ZERO, clock.read().elapsed)
        source.now = 5.seconds
        wall.wall = 6_000L
        val second = clock.read()
        assertEquals(5.seconds, second.elapsed)
        assertEquals(6_000L, second.wallMillis)
        source.now = 3.seconds
        wall.wall = 4_000L
        val third = clock.read()
        assertEquals(5.seconds, third.elapsed)
        assertEquals(4_000L, third.wallMillis)
        source.now = 6.seconds
        assertEquals(6.seconds, clock.read().elapsed)
        source.now = 6.seconds + 500.milliseconds
        assertEquals(6_500.milliseconds, clock.read().elapsed)
    }

    @Test
    fun `LedgerClock measures from the mark taken at construction`() {
        val source = SteppingTimeSource()
        source.now = 10.seconds
        val clock = LedgerClock(source, FixedWallClock(0L))
        assertEquals(Duration.ZERO, clock.read().elapsed)
        source.now = 12.seconds
        assertEquals(2.seconds, clock.read().elapsed)
    }
}
