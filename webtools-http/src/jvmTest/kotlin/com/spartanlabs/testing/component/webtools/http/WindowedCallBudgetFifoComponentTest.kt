package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.BudgetCall
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.callAcquire
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.BudgetEvent
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lane
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val TEN_SECONDS_IN: Long = VirtualClock.DEFAULT_START_EPOCH_MILLIS + 10_000L

/**
 * Level 2 - first come first served and the fail-fast cap of `WindowedCallBudget` on the virtual clock: the exact grant instants of a queue (window 2
 * per minute at 10 s into a minute: A and B at once, C and D at exactly 50 s, E at exactly 110 s, none a millisecond earlier); 25 callers on a window
 * of 10 per minute granted in arrival order, 10/10/5 per minute; `maxWait` (a wait above it fails at once with the exact wait, `Window` and the window's
 * name, charging nothing; `ZERO` fails unless the call fits now; `INFINITE` waits; a wait equal to `maxWait` is granted); `CostExceedsLimit` with
 * `INFINITE` and the window's name, for a cost above the limit and for one above the limit less the other lanes' reserve; strict FIFO within a lane
 * while another lane is granted now; `QueueFull` applied per lane and only to calls that would wait, `maxQueued = 0` refusing every call that would wait;
 * `Granted.waited` as the measured virtual wait.
 */
class WindowedCallBudgetFifoComponentTest {

    private fun assertGrantedAt(call: BudgetCall, at: Duration, name: String) {
        assertTrue(call.isCompleted, "$name did not complete")
        assertNotNull(call.permit, "$name was not granted: ${call.failure} ${call.thrown}")
        assertEquals(at, call.completedAt, "$name was granted at the wrong instant")
    }

    @Test
    fun `a queue is granted at exact instants and never a millisecond early`() = runTest {
        val clock = virtualClock(TEN_SECONDS_IN)
        budgetOn(clock, minuteWindow(2)).use { budget ->
            val calls = ('A'..'E').map { it to callAcquire(budget, clock) }.toMap()
            clock.advanceBy(Duration.ZERO)
            assertGrantedAt(calls.getValue('A'), Duration.ZERO, "A")
            assertGrantedAt(calls.getValue('B'), Duration.ZERO, "B")
            assertFalse(calls.getValue('C').isCompleted)

            clock.advanceBy(50.seconds - 1.milliseconds)
            assertFalse(calls.getValue('C').isCompleted, "C was granted a millisecond early")
            assertFalse(calls.getValue('D').isCompleted)
            clock.advanceBy(1.milliseconds)
            assertGrantedAt(calls.getValue('C'), 50.seconds, "C")
            assertGrantedAt(calls.getValue('D'), 50.seconds, "D")
            assertFalse(calls.getValue('E').isCompleted)

            clock.advanceBy(60.seconds - 1.milliseconds)
            assertFalse(calls.getValue('E').isCompleted, "E was granted a millisecond early")
            clock.advanceBy(1.milliseconds)
            assertGrantedAt(calls.getValue('E'), 110.seconds, "E")
        }
    }

    @Test
    fun `25 callers on a window of 10 are granted in arrival order 10 10 5`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(10)).use { budget ->
            val calls = List(25) { callAcquire(budget, clock) }
            clock.advanceBy(Duration.ZERO)
            assertEquals(List(25) { it < 10 }, calls.map { it.isCompleted })
            clock.advanceBy(1.minutes)
            assertEquals(List(25) { it < 20 }, calls.map { it.isCompleted })
            calls.subList(10, 20).forEach { assertEquals(1.minutes, it.completedAt) }
            clock.advanceBy(1.minutes)
            assertTrue(calls.all { it.isCompleted && it.permit != null })
            calls.subList(20, 25).forEach { assertEquals(2.minutes, it.completedAt) }
            assertEquals(5, budget.utilisation().windows.single().used)
        }
    }

    @Test
    fun `a wait above maxWait fails at once with the exact wait and charges nothing`() = runTest {
        val clock = virtualClock(TEN_SECONDS_IN)
        budgetOn(clock, minuteWindow(2)).use { budget ->
            repeat(2) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            val before = budget.utilisation()
            val failure = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, 10.seconds).exceptionOrNull())
            assertEquals(50.seconds, failure.wait)
            assertEquals(ExhaustedReason.Window, failure.reason)
            assertEquals("minute", failure.window)
            assertEquals(Duration.ZERO, clock.elapsed)
            assertEquals(before, budget.utilisation())
            assertEquals(0, budget.utilisation().waiting)
        }
    }

    @Test
    fun `maxWait ZERO fails unless the call fits now and INFINITE waits`() = runTest {
        val clock = virtualClock(TEN_SECONDS_IN)
        budgetOn(clock, minuteWindow(2)).use { budget ->
            assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess)
            assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess)
            assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull())
            val waiting = callAcquire(budget, clock, maxWait = Duration.INFINITE)
            clock.advanceBy(Duration.ZERO)
            assertFalse(waiting.isCompleted)
            assertEquals(1, budget.utilisation().waiting)
            clock.advanceBy(50.seconds)
            assertGrantedAt(waiting, 50.seconds, "the INFINITE call")
        }
    }

    @Test
    fun `a wait exactly equal to maxWait is granted`() = runTest {
        val clock = virtualClock(TEN_SECONDS_IN)
        budgetOn(clock, minuteWindow(2)).use { budget ->
            repeat(2) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            val exact = callAcquire(budget, clock, maxWait = 50.seconds)
            val over = callAcquire(budget, clock, maxWait = 50.seconds - 1.milliseconds)
            clock.advanceBy(Duration.ZERO)
            assertFalse(exact.isCompleted)
            assertIs<HttpFailure.BudgetExhausted>(over.failure)
            clock.advanceBy(50.seconds)
            assertGrantedAt(exact, 50.seconds, "the exact call")
        }
    }

    @Test
    fun `a cost above the limit or above the lane's capacity is CostExceedsLimit at once`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(50)).use { budget ->
            val failure = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(51, Lane.Tracker, Duration.INFINITE).exceptionOrNull())
            assertEquals(ExhaustedReason.CostExceedsLimit, failure.reason)
            assertEquals(Duration.INFINITE, failure.wait)
            assertEquals("minute", failure.window)
            assertEquals(Duration.ZERO, clock.elapsed)
            assertTrue(budget.acquire(50, Lane.Tracker, Duration.ZERO).isSuccess)
        }
        budgetOn(clock, minuteWindow(10)) { laneShares = mapOf(Lane.Interactive to 0.2) }.use { budget ->
            val failure = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(9, Lane.Tracker, Duration.INFINITE).exceptionOrNull())
            assertEquals(ExhaustedReason.CostExceedsLimit, failure.reason)
            assertEquals(Duration.INFINITE, failure.wait)
            assertTrue(budget.acquire(8, Lane.Tracker, Duration.ZERO).isSuccess)
            // The lane itself may use the whole window: cost 10 on Interactive is within capacity and only waits for the boundary.
            val interactive = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(10, Lane.Interactive, 30.seconds).exceptionOrNull())
            assertEquals(ExhaustedReason.Window, interactive.reason)
            assertEquals(1.minutes, interactive.wait)
        }
    }

    @Test
    fun `strict FIFO within a lane while another lane is granted now`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(10)).use { budget ->
            repeat(8) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            val head = callAcquire(budget, clock, cost = 5)
            val follower = callAcquire(budget, clock, cost = 1)
            val other = callAcquire(budget, clock, cost = 1, lane = Lane.Interactive)
            clock.advanceBy(Duration.ZERO)
            assertFalse(head.isCompleted)
            assertFalse(follower.isCompleted, "the follower overtook the head of its lane")
            assertGrantedAt(other, Duration.ZERO, "the other lane's call")
            clock.advanceBy(1.minutes)
            assertGrantedAt(head, 1.minutes, "the head")
            assertGrantedAt(follower, 1.minutes, "the follower")
        }
    }

    @Test
    fun `QueueFull is applied per lane and only to calls that would wait`() = runTest {
        val clock = virtualClock(TEN_SECONDS_IN)
        budgetOn(clock, minuteWindow(5)) { maxQueued = 3 }.use { budget ->
            repeat(5) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }     // granted now: not counted
            val queued = List(3) { callAcquire(budget, clock) }
            val fourth = callAcquire(budget, clock)
            val otherLane = callAcquire(budget, clock, lane = Lane.Interactive)
            clock.advanceBy(Duration.ZERO)
            assertTrue(queued.none { it.isCompleted })
            val failure = assertIs<HttpFailure.BudgetExhausted>(fourth.failure, "the fourth waiter was not refused: ${fourth.result} ${fourth.thrown}")
            assertEquals(ExhaustedReason.QueueFull, failure.reason)
            assertEquals(50.seconds, failure.wait, "QueueFull does not carry the wait the call would have had")
            assertEquals("minute", failure.window)
            assertFalse(otherLane.isCompleted, "the other lane could not queue")
            assertEquals(4, budget.utilisation().waiting)
            queued.forEach { it.cancel() }
            otherLane.cancel()
        }
    }

    @Test
    fun `maxQueued 0 refuses every call that would wait`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(1)) { maxQueued = 0 }.use { budget ->
            assertTrue(budget.acquire(1, Lane.Tracker, Duration.INFINITE).isSuccess)
            val failure = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.INFINITE).exceptionOrNull())
            assertEquals(ExhaustedReason.QueueFull, failure.reason)
            assertEquals(1.minutes, failure.wait)
            assertEquals(0, budget.utilisation().waiting)
        }
    }

    @Test
    fun `Granted waited is the measured virtual wait`() = runTest {
        val clock = virtualClock(TEN_SECONDS_IN)
        val recording = RecordingBudgetObserver()
        budgetOn(clock, minuteWindow(2)) { observer = recording }.use { budget ->
            repeat(2) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            val waiting = callAcquire(budget, clock)
            clock.advanceBy(50.seconds)
            assertGrantedAt(waiting, 50.seconds, "the waiter")
            assertEquals(listOf(Duration.ZERO, Duration.ZERO, 50.seconds), recording.eventsOf<BudgetEvent.Granted>().map { it.waited })
        }
    }
}
