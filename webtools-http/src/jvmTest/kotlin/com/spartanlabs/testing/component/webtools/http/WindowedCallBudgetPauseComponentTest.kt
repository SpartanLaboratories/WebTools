package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.DAY_MS
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.callAcquire
import com.spartanlabs.testing.support.webtools.http.dayWindow
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.BudgetEvent
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.PauseSource
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.WindowReading
import com.spartanlabs.webtools.http.testing.RecordingBudgetObserver
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val TEN_SECONDS_IN: Long = VirtualClock.DEFAULT_START_EPOCH_MILLIS + 10_000L

/**
 * Level 2 - pauses of `WindowedCallBudget`: `Retry-After` pauses for its length (a following call with a shorter cap is refused with `Paused`, one with
 * `INFINITE` is granted at exactly the pause's end); a reading with nothing remaining and a `resetIn` pauses for the `resetIn`, not the boundary; a
 * throttle with no hint pauses to the next boundary plus the guard; an exhausted day pauses to the next midnight plus the guard and fails waiters whose
 * cap is shorter at once; the pause is the maximum of the hints and never shortens; every lane is paused; `maxPause` caps it; `retryAfter = ZERO` pauses
 * nothing and does not fall back to the boundary; a non-throttled signal never pauses; a reading for an unknown window is ignored; the pause ends by the
 * clock; a pause moves a waiter later; `Paused` carries the winning hint's source.
 */
class WindowedCallBudgetPauseComponentTest {

    private fun throttled(configure: RateSignals.Builder.() -> Unit = {}): RateSignals = RateSignals { throttled = true; configure() }

    @Test
    fun `Retry-After pauses the budget for its length`() = runTest {
        val clock = virtualClock()
        val recording = RecordingBudgetObserver()
        budgetOn(clock, minuteWindow(10)) { observer = recording }.use { budget ->
            val permit = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            permit.complete(throttled { retryAfter = 30.seconds })
            assertEquals(30.seconds, budget.utilisation().pausedFor)

            val refused = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, 10.seconds).exceptionOrNull())
            assertEquals(30.seconds, refused.wait)
            assertEquals(ExhaustedReason.Paused, refused.reason)
            assertNull(refused.window)

            val waiting = callAcquire(budget, clock)
            clock.advanceBy(30.seconds - 1.milliseconds)
            assertFalse(waiting.isCompleted)
            clock.advanceBy(1.milliseconds)
            assertEquals(30.seconds, waiting.completedAt)
            assertNotNull(waiting.permit)
            val paused = recording.eventsOf<BudgetEvent.Paused>().single()
            assertEquals(PauseSource.RetryAfter, paused.source)
            assertEquals(30.seconds, paused.length)
            assertEquals(VirtualClock.DEFAULT_START_EPOCH_MILLIS + 30_000L, paused.untilEpochMillis)
        }
    }

    @Test
    fun `a reading with nothing remaining and a resetIn pauses for the resetIn not the boundary`() = runTest {
        val clock = virtualClock(VirtualClock.DEFAULT_START_EPOCH_MILLIS + 5_000L)
        val recording = RecordingBudgetObserver()
        budgetOn(clock, minuteWindow(10)) { observer = recording }.use { budget ->
            val permit = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            permit.complete(throttled { reading("minute", WindowReading(0, 37.seconds)) })
            assertEquals(37.seconds, budget.utilisation().pausedFor)
            assertEquals(PauseSource.ResetIn, recording.eventsOf<BudgetEvent.Paused>().single().source)
        }
    }

    @Test
    fun `a throttle with no hint pauses to the next boundary plus the guard`() = runTest {
        val clock = virtualClock(TEN_SECONDS_IN)
        val recording = RecordingBudgetObserver()
        budgetOn(clock, minuteWindow(10)) { boundaryGuard = 1.seconds; observer = recording }.use { budget ->
            val permit = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            permit.complete(throttled())
            assertEquals(51.seconds, budget.utilisation().pausedFor)
            assertEquals(PauseSource.Boundary, recording.eventsOf<BudgetEvent.Paused>().single().source)
        }
    }

    @Test
    fun `an exhausted day pauses to the next midnight plus the guard and fails short-capped waiters at once`() = runTest {
        val clock = virtualClock(TEN_SECONDS_IN)
        budgetOn(clock, minuteWindow(2), dayWindow(100, hard = false)) { boundaryGuard = 1.seconds }.use { budget ->
            val a = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val waiter = callAcquire(budget, clock, maxWait = 1.hours)
            clock.advanceBy(Duration.ZERO)
            assertFalse(waiter.isCompleted)

            a.complete(throttled { reading("day", WindowReading(0)) })
            val expected = (DAY_MS - 10_000L).milliseconds + 1.seconds
            assertEquals(expected, budget.utilisation().pausedFor)
            clock.advanceBy(Duration.ZERO)
            val failure = assertIs<HttpFailure.BudgetExhausted>(waiter.failure, "the waiter did not fail: ${waiter.result} ${waiter.thrown}")
            assertEquals(ExhaustedReason.Paused, failure.reason)
            assertEquals(expected, failure.wait)
            assertEquals(Duration.ZERO, waiter.completedAt)
        }
    }

    @Test
    fun `the pause is the maximum of the hints and never shortens`() = runTest {
        val clock = virtualClock()
        val recording = RecordingBudgetObserver()
        budgetOn(clock, minuteWindow(10)) { observer = recording }.use { budget ->
            val a = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val b = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val c = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            a.complete(throttled { retryAfter = 30.seconds })
            b.complete(throttled { retryAfter = 10.seconds })
            assertEquals(30.seconds, budget.utilisation().pausedFor)
            assertEquals(1, recording.eventsOf<BudgetEvent.Paused>().size, "a shorter hint raised a second Paused event")
            c.complete(throttled { retryAfter = 20.seconds; reading("minute", WindowReading(0, 45.seconds)) })
            assertEquals(45.seconds, budget.utilisation().pausedFor)
            val second = recording.eventsOf<BudgetEvent.Paused>()[1]
            assertEquals(PauseSource.ResetIn, second.source)
            assertEquals(45.seconds, second.length)
        }
    }

    @Test
    fun `every lane is paused`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(10)) { laneShares = mapOf(Lane.Interactive to 0.2) }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow().complete(throttled { retryAfter = 30.seconds })
            listOf(Lane.Tracker, Lane.Interactive, Lane("batch")).forEach { lane ->
                val failure = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, lane, Duration.ZERO).exceptionOrNull(), lane.name)
                assertEquals(ExhaustedReason.Paused, failure.reason)
            }
        }
    }

    @Test
    fun `maxPause caps a hint`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(10)).use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow().complete(throttled { retryAfter = 48.hours })
            assertEquals(24.hours, budget.utilisation().pausedFor)
        }
        budgetOn(clock, minuteWindow(10)) { maxPause = 5.seconds }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow().complete(throttled { retryAfter = 30.seconds })
            assertEquals(5.seconds, budget.utilisation().pausedFor)
        }
    }

    @Test
    fun `retryAfter ZERO pauses nothing and a non-throttled signal never pauses`() = runTest {
        val clock = virtualClock(TEN_SECONDS_IN)
        val recording = RecordingBudgetObserver()
        budgetOn(clock, minuteWindow(10)) { observer = recording }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow().complete(throttled { retryAfter = Duration.ZERO })
            assertEquals(Duration.ZERO, budget.utilisation().pausedFor, "retryAfter ZERO fell back to the boundary")
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow().complete(RateSignals { retryAfter = 30.seconds; reading("minute", WindowReading(5, 30.seconds)) })
            assertEquals(Duration.ZERO, budget.utilisation().pausedFor, "a non-throttled signal paused")
            assertTrue(recording.eventsOf<BudgetEvent.Paused>().isEmpty())
            assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess)
        }
    }

    @Test
    fun `a reading for an unknown window is ignored`() = runTest {
        val clock = virtualClock()
        val recording = RecordingBudgetObserver()
        budgetOn(clock, minuteWindow(10)) { observer = recording }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow().complete(RateSignals { reading("hour", WindowReading(0, 10.seconds)) })
            assertEquals(1, budget.utilisation().windows.single().used)
            assertEquals(Duration.ZERO, budget.utilisation().pausedFor)
            assertTrue(recording.eventsOf<BudgetEvent.Reconciled>().isEmpty())
            // Throttled, the unknown reading offers no resetIn: the pause falls back to the boundary, not to the reading's 10 s.
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow().complete(throttled { reading("hour", WindowReading(0, 10.seconds)) })
            assertEquals(1.minutes, budget.utilisation().pausedFor)
            assertEquals(PauseSource.Boundary, recording.eventsOf<BudgetEvent.Paused>().single().source)
        }
    }

    @Test
    fun `the pause ends by the clock`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(10)).use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow().complete(throttled { retryAfter = 30.seconds })
            clock.advanceBy(20.seconds)
            assertEquals(10.seconds, budget.utilisation().pausedFor)
            clock.advanceBy(10.seconds)
            assertEquals(Duration.ZERO, budget.utilisation().pausedFor)
            assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess)
        }
    }

    @Test
    fun `a pause moves a waiter later to the pause's end`() = runTest {
        val clock = virtualClock(VirtualClock.DEFAULT_START_EPOCH_MILLIS + 40_000L)
        budgetOn(clock, minuteWindow(2)).use { budget ->
            val a = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val waiter = callAcquire(budget, clock)
            clock.advanceBy(5.seconds)
            assertFalse(waiter.isCompleted)
            a.complete(throttled { retryAfter = 60.seconds })
            clock.advanceBy(15.seconds)
            assertFalse(waiter.isCompleted, "the waiter was granted at its old slot inside the pause")
            clock.advanceBy(45.seconds - 1.milliseconds)
            assertFalse(waiter.isCompleted)
            clock.advanceBy(1.milliseconds)
            assertEquals(65.seconds, waiter.completedAt)
            assertNotNull(waiter.permit)
        }
    }
}
