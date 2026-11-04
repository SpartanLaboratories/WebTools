package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.BudgetCall
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.callAcquire
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.WindowReading
import com.spartanlabs.webtools.http.testing.FakeBudgetStore
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - refunds and re-plans of `WindowedCallBudget`: cancelling a waiter moves the waiters behind it earlier, to exactly their new instants and
 * never a millisecond early, leaves the ones ahead unchanged and lets a newcomer land after the last slot; a cancelled waiter's reservation is refunded;
 * a pause longer than a waiter's cap fails it at once with `Paused` at unchanged virtual time; `release()` refunds a grant and moves a waiter earlier;
 * `complete()` with no signals re-plans nothing; a second `complete` or `release`, `complete` after `release` and `release` after `complete` change
 * nothing; a cancelled caller never receives a permit (an already-cancelled call, and one cancelled while parked on the lock as its slot arrives, end
 * with `CancellationException` and an unchanged ledger); a re-plan that moves a waiter's slot one millisecond past its deadline keeps it (the wall
 * clock's granularity) and one that moves it two milliseconds past fails it at once with `BudgetExhausted`.
 */
class WindowedCallBudgetRefundComponentTest {

    private fun assertGrantedAt(call: BudgetCall, at: Duration, name: String) {
        assertTrue(call.isCompleted, "$name did not complete")
        assertNotNull(call.permit, "$name was not granted: ${call.failure} ${call.thrown}")
        assertEquals(at, call.completedAt, "$name was granted at the wrong instant")
    }

    @Test
    fun `cancelling a waiter moves the waiters behind it earlier to exact instants`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(2)).use { budget ->
            val calls = ('A'..'G').associateWith { callAcquire(budget, clock) }
            clock.advanceBy(Duration.ZERO)
            assertGrantedAt(calls.getValue('A'), Duration.ZERO, "A")
            assertGrantedAt(calls.getValue('B'), Duration.ZERO, "B")
            assertEquals(5, budget.utilisation().waiting)

            calls.getValue('C').cancel()
            clock.advanceBy(Duration.ZERO)
            assertTrue(calls.getValue('C').isCancelled)
            assertEquals(4, budget.utilisation().waiting)
            val h = callAcquire(budget, clock)

            clock.advanceBy(1.minutes - 1.milliseconds)
            assertFalse(calls.getValue('D').isCompleted, "D was granted early")
            assertFalse(calls.getValue('E').isCompleted, "E was granted a millisecond early")
            clock.advanceBy(1.milliseconds)
            assertGrantedAt(calls.getValue('D'), 1.minutes, "D")
            assertGrantedAt(calls.getValue('E'), 1.minutes, "E")
            assertFalse(calls.getValue('F').isCompleted)

            clock.advanceBy(1.minutes - 1.milliseconds)
            assertFalse(calls.getValue('F').isCompleted, "F was granted a millisecond early")
            clock.advanceBy(1.milliseconds)
            assertGrantedAt(calls.getValue('F'), 2.minutes, "F")
            assertGrantedAt(calls.getValue('G'), 2.minutes, "G")
            assertFalse(h.isCompleted)

            clock.advanceBy(1.minutes)
            assertGrantedAt(h, 3.minutes, "H")
        }
    }

    @Test
    fun `a cancelled waiter's reservation is refunded`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, RateWindow("second", 1, 1.seconds), minuteWindow(10)).use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val waiter = callAcquire(budget, clock)
            clock.advanceBy(Duration.ZERO)
            assertFalse(waiter.isCompleted)
            val minute = budget.utilisation().windows.first { it.name == "minute" }
            assertEquals(1, minute.queued)
            assertEquals(1, budget.utilisation().waiting)

            waiter.cancel()
            clock.advanceBy(Duration.ZERO)
            assertTrue(waiter.isCancelled)
            val after = budget.utilisation()
            assertEquals(0, after.windows.first { it.name == "minute" }.queued)
            assertEquals(1, after.windows.first { it.name == "minute" }.used)
            assertEquals(0, after.waiting)
        }
    }

    @Test
    fun `a pause longer than a waiter's cap fails it at once with Paused`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(2)).use { budget ->
            val a = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val waiter = callAcquire(budget, clock, maxWait = 2.minutes)
            clock.advanceBy(Duration.ZERO)
            assertFalse(waiter.isCompleted)

            a.complete(RateSignals { throttled = true; retryAfter = 5.minutes })
            clock.advanceBy(Duration.ZERO)

            val failure = assertIs<HttpFailure.BudgetExhausted>(waiter.failure, "the waiter did not fail: ${waiter.result} ${waiter.thrown}")
            assertEquals(ExhaustedReason.Paused, failure.reason)
            assertEquals(5.minutes, failure.wait)
            assertNull(failure.window)
            assertEquals(Duration.ZERO, waiter.completedAt)
            assertEquals(0, budget.utilisation().waiting)
        }
    }

    @Test
    fun `release refunds the units and moves a waiter earlier`() = runTest {
        val clock = virtualClock(VirtualClock.DEFAULT_START_EPOCH_MILLIS + 10_000L)
        budgetOn(clock, minuteWindow(2)).use { budget ->
            val a = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val waiter = callAcquire(budget, clock)
            clock.advanceBy(Duration.ZERO)
            assertFalse(waiter.isCompleted)

            clock.advanceBy(5.seconds)
            a.release()
            clock.advanceBy(Duration.ZERO)
            assertGrantedAt(waiter, 5.seconds, "the waiter")
            assertEquals(2, budget.utilisation().windows.single().used)
        }
    }

    @Test
    fun `a re-plan one millisecond past a waiter's deadline keeps it and two milliseconds past fails it at once`() = runTest {
        val clock = virtualClock()
        // A 2 ms guard: a slot within 2 ms after the minute boundary charges the previous minute too, so a re-plan can move two waiters 1 ms apart.
        budgetOn(clock, minuteWindow(4)) { boundaryGuard = 2.milliseconds }.use { budget ->
            val a = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val b = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            a.complete(RateSignals { throttled = true; retryAfter = 1.minutes })
            // Both are planned at the pause's end, 60 s, where the minute still has room for two: each cap equals its planned wait, so each deadline is 60 s.
            val c = callAcquire(budget, clock, maxWait = 1.minutes)
            val d = callAcquire(budget, clock, maxWait = 1.minutes)
            clock.advanceBy(Duration.ZERO)
            assertFalse(c.isCompleted)
            assertFalse(d.isCompleted)
            assertEquals(2, budget.utilisation().waiting)

            // The reading raises the minute to 3 used (room for one more in it) and the pause moves to 60.001 s: C fits there, within the guard, at
            // 1 ms past its deadline; D no longer fits beside it and is moved to the guard's end, 60.002 s, 2 ms past its deadline.
            b.complete(RateSignals { throttled = true; retryAfter = 1.minutes + 1.milliseconds; reading("minute", WindowReading(1)) })
            clock.advanceBy(Duration.ZERO)
            assertFalse(c.isCompleted, "C was ended by the re-plan")
            assertTrue(d.isCompleted, "D survived a slot 2 ms past its deadline")
            val failure = assertIs<HttpFailure.BudgetExhausted>(d.failure, "D did not fail: ${d.result} ${d.thrown}")
            assertEquals(1.minutes + 2.milliseconds, failure.wait)
            assertEquals(ExhaustedReason.Window, failure.reason)
            assertEquals("minute", failure.window)
            assertEquals(Duration.ZERO, d.completedAt)
            assertEquals(1, budget.utilisation().waiting)

            clock.advanceBy(1.minutes)
            assertFalse(c.isCompleted, "C was granted at its old slot")
            clock.advanceBy(1.milliseconds)
            assertGrantedAt(c, 1.minutes + 1.milliseconds, "C")
        }
    }

    @Test
    fun `complete with no signals re-plans nothing`() = runTest {
        val clock = virtualClock(VirtualClock.DEFAULT_START_EPOCH_MILLIS + 10_000L)
        budgetOn(clock, minuteWindow(2)).use { budget ->
            val a = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val waiter = callAcquire(budget, clock)
            clock.advanceBy(Duration.ZERO)
            a.complete()
            clock.advanceBy(50.seconds - 1.milliseconds)
            assertFalse(waiter.isCompleted)
            clock.advanceBy(1.milliseconds)
            assertGrantedAt(waiter, 50.seconds, "the waiter")
        }
    }

    @Test
    fun `complete and release are idempotent and ignore each other afterwards`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(10)).use { budget ->
            val a = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val b = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            assertEquals(2, budget.utilisation().windows.single().used)
            a.release()
            assertEquals(1, budget.utilisation().windows.single().used)
            a.release()
            a.complete()
            a.complete(RateSignals { throttled = true; retryAfter = 30.seconds })
            assertEquals(1, budget.utilisation().windows.single().used)
            assertEquals(Duration.ZERO, budget.utilisation().pausedFor, "a complete after a release took effect")
            b.complete()
            b.complete()
            b.release()
            assertEquals(1, budget.utilisation().windows.single().used, "a release after a complete took effect")
        }
    }

    @Test
    fun `an already-cancelled caller gets its CancellationException and no permit`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(10)).use { budget ->
            val before = budget.utilisation()
            val call = callAcquire(budget, clock, cancelledFirst = true)
            clock.advanceBy(Duration.ZERO)
            assertTrue(call.isCompleted)
            assertIs<CancellationException>(call.thrown)
            assertNull(call.result)
            assertEquals(before, budget.utilisation())
        }
    }

    @Test
    fun `a waiter cancelled while parked on the lock as its slot arrives ends cancelled with an unchanged ledger`() = runTest {
        val clock = virtualClock()
        val store = FakeBudgetStore()
        budgetOn(clock, minuteWindow(1)) { this.store = store; storeTimeout = 20.seconds }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val waiter = callAcquire(budget, clock)
            clock.advanceBy(Duration.ZERO)
            assertFalse(waiter.isCompleted)

            // A slow flush holds the lock from 55 s to 65 s: the waiter's slot at 60 s finds the lock taken and parks.
            clock.advanceBy(55.seconds)
            store.saveDelay = 10.seconds
            launch { budget.flush() }
            clock.advanceBy(5.seconds)
            assertFalse(waiter.isCompleted)
            waiter.cancel()
            clock.advanceBy(Duration.ZERO)
            assertFalse(waiter.isCompleted, "the cancelled waiter completed before the lock freed")

            clock.advanceBy(5.seconds)
            assertTrue(waiter.isCompleted)
            assertTrue(waiter.isCancelled)
            assertIs<CancellationException>(waiter.thrown)
            assertNull(waiter.result)
            val utilisation = budget.utilisation()
            assertEquals(0, utilisation.windows.single().used)         // the minute that holds the first grant has ended
            assertEquals(0, utilisation.windows.single().queued)
            assertEquals(0, utilisation.waiting)
        }
    }
}
