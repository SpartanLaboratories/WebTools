package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.ManualTimeSource
import com.spartanlabs.testing.support.webtools.http.SteppableWallClock
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.callAcquire
import com.spartanlabs.testing.support.webtools.http.dayWindow
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.BudgetEvent
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.testing.FakeBudgetStore
import com.spartanlabs.webtools.http.testing.RecordingBudgetObserver
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - pauses, measured waits and snapshots of `WindowedCallBudget` under clock steps: a 60 s pause then a step back of an hour still ends at 60 s of
 * virtual time, the same pause then a step forward of an hour ends at once, and `pausedFor` follows both axes; a 30 s wait with a 2-hour step during it
 * reports 30 s in `Granted.waited`, never 2 h; the saved stamp equals the wall reading, a restore with the stamp 4 minutes ahead of a stepped wall keeps
 * the counts and pause, 6 minutes ahead sets `ignoredForSkew`, and a pause restores as its remaining duration capped by `maxPause`; a non-monotonic
 * `TimeSource` leaves every wait and measured wait non-negative and the elapsed reading non-decreasing; a wake that reads the wall a millisecond before
 * its slot waits the remainder.
 */
class WindowedCallBudgetClockPauseComponentTest {

    private val midnight = VirtualClock.DEFAULT_START_EPOCH_MILLIS

    private fun throttled(retryAfter: Duration): RateSignals = RateSignals { throttled = true; this.retryAfter = retryAfter }

    @Test
    fun `a pause ends at its virtual length after a step back and at once after a step forward`() = runTest {
        val clock = virtualClock()
        val wall = SteppableWallClock(clock.wallClock)
        budgetOn(clock, minuteWindow(10)) { wallClock = wall }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow().complete(throttled(60.seconds))
            wall.stepBy((-1).hours)
            assertEquals(60.seconds, budget.utilisation().pausedFor, "a step back lengthened the pause")
            clock.advanceBy(30.seconds)
            assertEquals(30.seconds, budget.utilisation().pausedFor)
            clock.advanceBy(30.seconds)
            assertEquals(Duration.ZERO, budget.utilisation().pausedFor)
            assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess)

            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow().complete(throttled(60.seconds))
            assertEquals(60.seconds, budget.utilisation().pausedFor)
            wall.stepBy(2.hours)
            assertEquals(Duration.ZERO, budget.utilisation().pausedFor, "a step forward past the pause's end did not end it")
            assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess)
        }
    }

    @Test
    fun `pausedFor follows the nearer of the two axes`() = runTest {
        val clock = virtualClock()
        val wall = SteppableWallClock(clock.wallClock)
        budgetOn(clock, minuteWindow(10)) { wallClock = wall }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow().complete(throttled(60.seconds))
            wall.stepBy(45.seconds)
            assertEquals(15.seconds, budget.utilisation().pausedFor)
            clock.advanceBy(10.seconds)
            assertEquals(5.seconds, budget.utilisation().pausedFor)
        }
    }

    @Test
    fun `a measured wait is the monotonic one whatever the wall clock does`() = runTest {
        val clock = virtualClock(midnight + 30_000L)
        val wall = SteppableWallClock(clock.wallClock)
        val recording = RecordingBudgetObserver()
        budgetOn(clock, minuteWindow(1)) { wallClock = wall; observer = recording }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val waiter = callAcquire(budget, clock)
            clock.advanceBy(10.seconds)
            wall.stepBy(2.hours)
            clock.advanceBy(20.seconds)
            assertEquals(30.seconds, waiter.completedAt)
            assertNotNull(waiter.permit)
            assertEquals(30.seconds, recording.eventsOf<BudgetEvent.Granted>().last().waited)
        }
    }

    @Test
    fun `the saved stamp is the wall reading and a restore judges skew against the stepped wall`() = runTest {
        val tenPastMidnight = midnight + 10.minutes.inWholeMilliseconds           // so a wall stepped back a few minutes is still in the same day
        val clock = virtualClock(tenPastMidnight)
        val store = FakeBudgetStore()
        budgetOn(clock, dayWindow(5)) { this.store = store }.use { budget ->
            repeat(3) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow().complete(throttled(20.minutes))
            assertEquals(Result.success(Unit), budget.flush())
        }
        val saved = store.saves.last()
        assertEquals(tenPastMidnight, saved.takenAtEpochMillis)
        assertEquals(tenPastMidnight + 20.minutes.inWholeMilliseconds, saved.pausedUntilEpochMillis)

        suspend fun restoreWithWallBehindBy(behind: Duration): Triple<BudgetEvent.Restored, Int, Duration> {
            val wall = SteppableWallClock(clock.wallClock).apply { stepBy(-behind) }
            val recording = RecordingBudgetObserver()
            return budgetOn(clock, dayWindow(5)) { this.store = store; wallClock = wall; observer = recording }.use { second ->
                val utilisation = second.utilisation()
                Triple(recording.eventsOf<BudgetEvent.Restored>().single(), utilisation.windows.single().used, utilisation.pausedFor)
            }
        }
        val (kept, keptUsed, keptPause) = restoreWithWallBehindBy(4.minutes)
        assertFalse(kept.ignoredForSkew)
        assertEquals(4, keptUsed)
        assertEquals(24.minutes, keptPause, "the pause was not restored as its remaining length against the stepped wall")
        val (ignored, ignoredUsed, ignoredPause) = restoreWithWallBehindBy(6.minutes)
        assertTrue(ignored.ignoredForSkew)
        assertEquals(4, ignoredUsed)
        assertEquals(Duration.ZERO, ignoredPause)

        val capped = SteppableWallClock(clock.wallClock).apply { stepBy((-30).hours) }
        budgetOn(clock, dayWindow(5)) { this.store = store; wallClock = capped; maxRestoreSkew = 2.hours * 24 }.use { second ->
            assertEquals(24.hours, second.utilisation().pausedFor, "a restored pause was not capped by maxPause")
        }
    }

    @Test
    fun `a non-monotonic TimeSource leaves every wait non-negative and the elapsed reading non-decreasing`() = runTest {
        val clock = virtualClock(midnight + 30_000L)
        val manual = ManualTimeSource()
        val recording = RecordingBudgetObserver()
        budgetOn(clock, minuteWindow(1)) { timeSource = manual; observer = recording }.use { budget ->
            manual.now = 100.seconds
            val first = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            first.complete(throttled(60.seconds))
            assertEquals(60.seconds, budget.utilisation().pausedFor)
            manual.now = Duration.ZERO                               // the source steps back: the elapsed reading must not
            assertEquals(60.seconds, budget.utilisation().pausedFor, "the elapsed reading went back and lengthened the pause")
            clock.advanceBy(10.seconds)
            assertEquals(50.seconds, budget.utilisation().pausedFor)

            val waiter = callAcquire(budget, clock)
            clock.advanceBy(50.seconds)
            assertNotNull(waiter.permit, "the waiter was not granted at the pause's end: ${waiter.failure}")
            val waited = recording.eventsOf<BudgetEvent.Granted>().last().waited
            assertFalse(waited.isNegative(), "a measured wait was negative: $waited")
            val refused = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull())
            assertFalse(refused.wait.isNegative())
        }
    }

    @Test
    fun `a wake that reads the wall a millisecond before its slot waits the remainder`() = runTest {
        val clock = virtualClock(midnight + 30_000L)
        val wall = SteppableWallClock(clock.wallClock)
        budgetOn(clock, minuteWindow(1)) { wallClock = wall }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val waiter = callAcquire(budget, clock)
            clock.advanceBy(20.seconds)
            wall.stepBy((-1).milliseconds)
            clock.advanceBy(10.seconds)
            assertFalse(waiter.isCompleted, "the waiter was granted a millisecond before the wall boundary")
            clock.advanceBy(1.milliseconds)
            assertEquals(30.seconds + 1.milliseconds, waiter.completedAt)
            assertNotNull(waiter.permit)
        }
    }
}
