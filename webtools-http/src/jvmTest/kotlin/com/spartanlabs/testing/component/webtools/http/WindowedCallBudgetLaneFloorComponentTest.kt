package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.callAcquire
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Level 2 - lane floors of `WindowedCallBudget`: on a window of 10 per minute with `Interactive` 0.2 (2 units) the `Tracker` lane is granted 8 and
 * its ninth waits for the boundary while two `Interactive` calls are still granted at once and a third waits; the floor is held back and never lent (an
 * idle `Interactive` lane leaves `Tracker` 8 of 10 every minute); an interactive call bypasses a 30-call tracker backlog up to its reserve and is then
 * refused with the exact wait to the boundary; a lane with a share is not starved by a saturated lane (1,000 queued on each); the rounding of a share
 * is visible through the budget (a 3-unit window reserves 1 unit for 0.2, a 7-unit window 2); an unknown lane has no floor and the capacity
 * `limit - reserves`.
 */
class WindowedCallBudgetLaneFloorComponentTest {

    @Test
    fun `the tracker lane stops at 8 of 10 and the interactive lane still gets its 2`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(10)) { laneShares = mapOf(Lane.Interactive to 0.2) }.use { budget ->
            repeat(8) { assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess, "tracker grant ${it + 1}") }
            val ninth = callAcquire(budget, clock)
            val first = callAcquire(budget, clock, lane = Lane.Interactive)
            val second = callAcquire(budget, clock, lane = Lane.Interactive)
            val third = callAcquire(budget, clock, lane = Lane.Interactive)
            clock.advanceBy(Duration.ZERO)
            assertFalse(ninth.isCompleted, "the ninth tracker call was granted into the floor")
            assertEquals(Duration.ZERO, first.completedAt)
            assertEquals(Duration.ZERO, second.completedAt)
            assertFalse(third.isCompleted)
            clock.advanceBy(1.minutes)
            assertEquals(1.minutes, ninth.completedAt)
            assertEquals(1.minutes, third.completedAt)
        }
    }

    @Test
    fun `the floor is held back and never lent to an idle lane's neighbour`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(10)) { laneShares = mapOf(Lane.Interactive to 0.2) }.use { budget ->
            repeat(3) { minute ->
                repeat(8) { assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess, "minute $minute, grant ${it + 1}") }
                val failure = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull(), "minute $minute")
                assertEquals(ExhaustedReason.Window, failure.reason)
                assertEquals(1.minutes, failure.wait)
                assertEquals(8, budget.utilisation().windows.single().used)
                clock.advanceBy(1.minutes)
            }
        }
    }

    @Test
    fun `an interactive call bypasses a tracker backlog up to its reserve and is then refused with the exact wait`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(10)) { laneShares = mapOf(Lane.Interactive to 0.2) }.use { budget ->
            val backlog = List(30) { callAcquire(budget, clock) }
            clock.advanceBy(Duration.ZERO)
            assertEquals(8, backlog.count { it.isCompleted })
            assertEquals(22, budget.utilisation().waiting)
            assertTrue(budget.acquire(1, Lane.Interactive, Duration.ZERO).isSuccess)
            assertTrue(budget.acquire(1, Lane.Interactive, Duration.ZERO).isSuccess)
            val third = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Interactive, Duration.ZERO).exceptionOrNull())
            assertEquals(1.minutes, third.wait)
            assertEquals(ExhaustedReason.Window, third.reason)
            assertEquals("minute", third.window)
            backlog.forEach { it.cancel() }
        }
    }

    @Test
    fun `a lane with a share is not starved by a saturated lane`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(10)) { laneShares = mapOf(Lane.Interactive to 0.2, Lane.Tracker to 0.3); maxQueued = 1_000 }.use { budget ->
            val tracker = List(1_000) { callAcquire(budget, clock) }
            val interactive = List(1_000) { callAcquire(budget, clock, lane = Lane.Interactive) }
            clock.advanceBy(Duration.ZERO)
            assertEquals(2_000 - 10, budget.utilisation().waiting)
            repeat(5) { minute ->
                val trackerGranted = tracker.count { it.completedAt == minute.minutes }
                val interactiveGranted = interactive.count { it.completedAt == minute.minutes }
                assertTrue(trackerGranted >= 3, "minute $minute: tracker got $trackerGranted")
                assertTrue(interactiveGranted >= 2, "minute $minute: interactive got $interactiveGranted")
                assertEquals(10, trackerGranted + interactiveGranted, "minute $minute")
                clock.advanceBy(1.minutes)
            }
            (tracker + interactive).forEach { it.cancel() }
        }
    }

    @Test
    fun `the rounding of a share is visible through the budget`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(3)) { laneShares = mapOf(Lane.Interactive to 0.2) }.use { budget ->
            repeat(2) { assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess) }
            assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull())
            assertTrue(budget.acquire(1, Lane.Interactive, Duration.ZERO).isSuccess)
        }
        budgetOn(clock, minuteWindow(7)) { laneShares = mapOf(Lane.Interactive to 0.2) }.use { budget ->
            repeat(5) { assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess) }
            assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull())
            repeat(2) { assertTrue(budget.acquire(1, Lane.Interactive, Duration.ZERO).isSuccess) }
        }
    }

    @Test
    fun `an unknown lane has no floor and the capacity limit less the reserves`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(10)) { laneShares = mapOf(Lane.Interactive to 0.2) }.use { budget ->
            val batch = Lane("batch")
            val tooBig = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(9, batch, Duration.INFINITE).exceptionOrNull())
            assertEquals(ExhaustedReason.CostExceedsLimit, tooBig.reason)
            repeat(8) { assertTrue(budget.acquire(1, batch, Duration.ZERO).isSuccess) }
            assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, batch, Duration.ZERO).exceptionOrNull())
            // No floor for the unknown lane: Interactive, with its own reserve, still gets its 2, and nothing is held back for batch.
            repeat(2) { assertTrue(budget.acquire(1, Lane.Interactive, Duration.ZERO).isSuccess) }
            assertEquals(10, budget.utilisation().windows.single().used)
        }
    }
}
