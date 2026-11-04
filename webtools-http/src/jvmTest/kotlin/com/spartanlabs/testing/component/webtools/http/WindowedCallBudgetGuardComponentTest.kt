package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.SteppableWallClock
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.callAcquire
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - the boundary guard's two-cell charge through `WindowedCallBudget`: on a window of 2 per minute with a 1 s guard, two grants 0.5 s before a
 * boundary charge both periods, so a third call waits for the second boundary (a budget with a zero guard grants it at the first); a call blocked in a
 * full period is planned at the boundary plus the guard; a grant 0.5 s after a boundary charges the previous period too (shown by stepping the wall clock
 * back across the boundary: the copy fold adds the current cell's count, and the guard charge shows as the difference between a guarded and an unguarded
 * budget); a grant in the middle of a period charges one cell; the planned instant of a waiter blocked by two windows is the later of the two.
 */
class WindowedCallBudgetGuardComponentTest {

    private val halfSecondBefore = VirtualClock.DEFAULT_START_EPOCH_MILLIS - 500L

    @Test
    fun `two grants just before a boundary charge both periods so a third call waits for the second boundary`() = runTest {
        val clock = virtualClock(halfSecondBefore)
        budgetOn(clock, minuteWindow(2)) { boundaryGuard = 1.seconds }.use { budget ->
            repeat(2) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            val third = callAcquire(budget, clock)
            clock.advanceBy(Duration.ZERO)
            assertFalse(third.isCompleted)
            clock.advanceBy(500.milliseconds)
            assertEquals(2, budget.utilisation().windows.single().used, "the new period does not carry the guard charge")
            clock.advanceBy(1.minutes)
            assertFalse(third.isCompleted, "the third call was granted at the first boundary")
            clock.advanceBy(1.seconds - 1.milliseconds)
            assertFalse(third.isCompleted)
            clock.advanceBy(1.milliseconds)
            assertEquals(1.minutes + 1.5.seconds, third.completedAt)
            assertNotNull(third.permit)
        }
    }

    @Test
    fun `control - with a zero guard the third call is granted at the first boundary`() = runTest {
        val clock = virtualClock(halfSecondBefore)
        budgetOn(clock, minuteWindow(2)).use { budget ->
            repeat(2) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            val third = callAcquire(budget, clock)
            clock.advanceBy(500.milliseconds)
            assertEquals(500.milliseconds, third.completedAt)
            assertEquals(1, budget.utilisation().windows.single().used)
        }
    }

    @Test
    fun `a call blocked in a full period is planned at the boundary plus the guard`() = runTest {
        val clock = virtualClock(VirtualClock.DEFAULT_START_EPOCH_MILLIS + 10_000L)
        budgetOn(clock, minuteWindow(2)) { boundaryGuard = 1.seconds }.use { budget ->
            repeat(2) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            val refused = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull())
            assertEquals(51.seconds, refused.wait)
            assertEquals(ExhaustedReason.Window, refused.reason)
            val waiter = callAcquire(budget, clock)
            clock.advanceBy(51.seconds - 1.milliseconds)
            assertFalse(waiter.isCompleted)
            clock.advanceBy(1.milliseconds)
            assertEquals(51.seconds, waiter.completedAt)
        }
    }

    @Test
    fun `a grant just after a boundary charges the previous period too`() = runTest {
        // Observable only by looking back: the wall clock is stepped back across the boundary, which makes the previous period current again. The copy fold
        // adds the current period's count (1) to it; the guard's own charge is the extra unit a guarded budget shows over an unguarded one.
        suspend fun usedInPreviousPeriodAfterStepBack(guard: Duration): Int {
            val clock = virtualClock(VirtualClock.DEFAULT_START_EPOCH_MILLIS + 500L)
            val wall = SteppableWallClock(clock.wallClock)
            return budgetOn(clock, minuteWindow(10)) { boundaryGuard = guard; wallClock = wall }.use { budget ->
                budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
                wall.stepBy((-1).seconds)
                budget.utilisation().windows.single().used
            }
        }
        assertEquals(1, usedInPreviousPeriodAfterStepBack(Duration.ZERO))
        assertEquals(2, usedInPreviousPeriodAfterStepBack(1.seconds))
    }

    @Test
    fun `a grant in the middle of a period charges one cell`() = runTest {
        val clock = virtualClock(VirtualClock.DEFAULT_START_EPOCH_MILLIS + 30_000L)
        budgetOn(clock, minuteWindow(2)) { boundaryGuard = 1.seconds }.use { budget ->
            repeat(2) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            val third = callAcquire(budget, clock)
            clock.advanceBy(30.seconds)
            assertEquals(0, budget.utilisation().windows.single().used, "the next period carries a charge from the middle of the previous one")
            clock.advanceBy(1.seconds)
            assertEquals(31.seconds, third.completedAt)
        }
    }

    @Test
    fun `the planned instant of a waiter blocked by two windows is the later of the two`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, RateWindow("ten", 1, 10.seconds), minuteWindow(1)) { boundaryGuard = 1.seconds }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val refused = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, 30.seconds).exceptionOrNull())
            assertEquals(61.seconds, refused.wait)
            assertEquals("minute", refused.window)
            val waiter = callAcquire(budget, clock)
            clock.advanceBy(61.seconds - 1.milliseconds)
            assertFalse(waiter.isCompleted)
            clock.advanceBy(1.milliseconds)
            assertEquals(61.seconds, waiter.completedAt)
        }
    }
}
