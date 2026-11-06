package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.callAcquire
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.dayWindow
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.BUDGET_LOGGER_NAME
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.WindowReading
import com.spartanlabs.webtools.http.testing.FakeBudgetStore
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
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `close()` of `WindowedCallBudget`: with several waiters, `close()` twice and `advanceBy(ZERO)` every call completes, none cancelled, each with
 * `HttpFailure.Closed("budget")`, and nothing waits; a later `acquire` is `Closed`; a call cancelled before it started still throws `CancellationException`
 * after `close()`; a call parked on the lock when `close()` runs returns `Closed` once the lock frees; a call whose coroutine has not run yet when
 * `close()` happens returns `Closed`; a waiter whose own write-ahead save spans a `close()` returns `Closed` once the save ends, unlinked, the save kept;
 * after `close()`, `flush()` writes the exact counts (including permits completed or released after the close), `utilisation()` reads, `complete(signals)`
 * applies its signals and `release()` refunds; `close()` logs once at `INFO`; `close()` from another thread while calls wait ends them all.
 *
 * One case pins the code as it is written rather than a symmetry: a **call** (not a waiter) whose write-ahead save spans a `close()` is still granted, because the
 * admission re-checks the latch only at the top of its section and decides again after the save with a fresh clock, not a fresh latch (the budget's
 * `admitLocked`); the waiter's step does re-check (`stepLocked`). A grant that precedes the close is an admitted outcome.
 */
class WindowedCallBudgetCloseComponentTest {

    private fun assertClosed(failure: Throwable?) {
        val closed = assertIs<HttpFailure.Closed>(failure)
        assertEquals("budget", closed.component)
    }

    @Test
    fun `close ends every waiter with Closed as a value and never a cancellation`() = runTest {
        val clock = virtualClock()
        val budget = budgetOn(clock, minuteWindow(1))
        budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
        val waiters = List(5) { callAcquire(budget, clock, lane = if (it % 2 == 0) Lane.Tracker else Lane.Interactive) }
        clock.advanceBy(Duration.ZERO)
        assertEquals(5, budget.utilisation().waiting)

        budget.close()
        budget.close()
        clock.advanceBy(Duration.ZERO)

        waiters.forEach { waiter ->
            assertTrue(waiter.isCompleted, "a waiter did not complete")
            assertFalse(waiter.isCancelled, "a waiter ended cancelled")
            assertNull(waiter.thrown)
            assertClosed(waiter.failure)
            assertEquals(Duration.ZERO, waiter.completedAt)
        }
        assertEquals(0, budget.utilisation().waiting)
        assertEquals(0, budget.utilisation().windows.single().queued)
        assertClosed(budget.acquire(1, Lane.Tracker, Duration.INFINITE).exceptionOrNull())
    }

    @Test
    fun `a call cancelled before it started still throws CancellationException after close`() = runTest {
        val clock = virtualClock()
        val budget = budgetOn(clock, minuteWindow(10))
        budget.close()
        val call = callAcquire(budget, clock, cancelledFirst = true)
        clock.advanceBy(Duration.ZERO)
        assertIs<CancellationException>(call.thrown)
        assertNull(call.result)
    }

    @Test
    fun `a call parked on the lock when close runs returns Closed once the lock frees`() = runTest {
        val clock = virtualClock()
        val store = FakeBudgetStore()
        val budget = budgetOn(clock, minuteWindow(10)) { this.store = store; storeTimeout = 20.seconds }
        budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
        store.saveDelay = 10.seconds
        launch { budget.flush() }                                   // holds the lock for 10 s
        val parked = callAcquire(budget, clock)
        clock.advanceBy(Duration.ZERO)
        assertFalse(parked.isCompleted)

        budget.close()
        clock.advanceBy(5.seconds)
        assertFalse(parked.isCompleted, "the parked call completed while the lock was held")
        clock.advanceBy(5.seconds)
        assertTrue(parked.isCompleted)
        assertFalse(parked.isCancelled)
        assertClosed(parked.failure)
        assertEquals(10.seconds, parked.completedAt)
    }

    @Test
    fun `a call whose coroutine has not run when close happens returns Closed`() = runTest {
        val clock = virtualClock()
        val budget = budgetOn(clock, minuteWindow(1))
        budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
        val pending = callAcquire(budget, clock)
        budget.close()
        clock.advanceBy(Duration.ZERO)
        assertTrue(pending.isCompleted)
        assertClosed(pending.failure)
        assertEquals(0, budget.utilisation().waiting)
    }

    @Test
    fun `a waiter whose write-ahead save spans a close returns Closed once the save ends`() = runTest {
        val clock = virtualClock()
        val store = FakeBudgetStore()
        val budget = budgetOn(clock, minuteWindow(1), dayWindow(5)) { this.store = store; persistAhead = 1; storeTimeout = 10.seconds }
        budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()       // fills the minute; its own write-ahead save is instant
        assertEquals(1, store.saves.size)
        store.saveDelay = 5.seconds
        val waiter = callAcquire(budget, clock)
        clock.advanceBy(Duration.ZERO)
        assertEquals(1, budget.utilisation().waiting)

        clock.advanceBy(60.seconds)                                         // the slot: the waiter's write-ahead save starts and holds the lock
        assertFalse(waiter.isCompleted)
        assertEquals(2, store.saveAttemptCount, "the waiter's write-ahead save did not start at its slot")
        clock.advanceBy(2.seconds)
        budget.close()
        clock.advanceBy(Duration.ZERO)
        assertFalse(waiter.isCompleted, "the waiter completed while its own save held the lock")

        clock.advanceBy(3.seconds)                                          // the save ends: the step finds the latch
        assertTrue(waiter.isCompleted)
        assertFalse(waiter.isCancelled)
        assertNull(waiter.thrown)
        assertClosed(waiter.failure)
        assertEquals(65.seconds, waiter.completedAt)
        val utilisation = budget.utilisation()
        assertEquals(0, utilisation.waiting)
        assertTrue(utilisation.windows.all { it.queued == 0 }, "the closed waiter's reservation was not unlinked: $utilisation")
        assertEquals(1, utilisation.windows.single { it.name == "day" }.used, "the closed waiter was charged")
        assertEquals(2, store.saves.size, "the waiter's write-ahead save was not kept")
        assertEquals(2, store.saves.last().windows.single().used)
    }

    @Test
    fun `a call whose write-ahead save spans a close is still granted`() = runTest {
        // `admitLocked` as it is written: after its save the admission decides again with a fresh clock, not a fresh latch, so the grant is
        // made. The waiter's step (the case above) re-checks; a grant made before the close is an admitted outcome.
        val clock = virtualClock()
        val store = FakeBudgetStore()
        store.saveDelay = 5.seconds
        val budget = budgetOn(clock, dayWindow(5)) { this.store = store; persistAhead = 1; storeTimeout = 10.seconds }
        val call = callAcquire(budget, clock)
        clock.advanceBy(2.seconds)
        assertFalse(call.isCompleted)
        assertEquals(1, store.saveAttemptCount, "the call's write-ahead save did not start")

        budget.close()
        clock.advanceBy(Duration.ZERO)
        assertFalse(call.isCompleted, "the call completed while its own save held the lock")
        clock.advanceBy(3.seconds)
        assertTrue(call.isCompleted)
        assertNull(call.thrown)
        assertNotNull(call.permit, "the call was not granted: ${call.failure}")
        assertEquals(5.seconds, call.completedAt)
        assertEquals(1, budget.utilisation().windows.single().used)
        assertEquals(listOf(1), store.saves.map { it.windows.single().used })
        assertClosed(budget.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull())
    }

    @Test
    fun `after close flush utilisation complete and release keep working`() = runTest {
        val clock = virtualClock()
        val store = FakeBudgetStore()
        val budget = budgetOn(clock, dayWindow(10)) { this.store = store; persistAhead = 10 }
        val permits = List(4) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
        budget.close()

        permits[0].release()
        assertEquals(3, budget.utilisation().windows.single().used)
        permits[1].complete(RateSignals { throttled = true; retryAfter = 30.seconds; reading("day", WindowReading(4)) })
        val utilisation = budget.utilisation()
        assertEquals(6, utilisation.windows.single().used, "the reading was not applied after close")
        assertEquals(30.seconds, utilisation.pausedFor)
        assertEquals(Result.success(Unit), budget.flush())
        assertEquals(6, store.saves.last().windows.single().used)
        assertEquals(clock.wallClock.nowEpochMillis() + 30_000L, store.saves.last().pausedUntilEpochMillis)
        permits[2].complete()
        permits[3].release()
        assertEquals(6, budget.utilisation().windows.single().used)
    }

    @Test
    fun `close logs once at INFO even when called twice`() {
        val (_, events) = captureTransportLogs(Level.DEBUG) {
            runTest {
                val clock = virtualClock()
                val budget = budgetOn(clock, minuteWindow(10))
                budget.close()
                budget.close()
                budget.close()
            }
        }
        val closed = events.filter { it.level == Level.INFO && it.formattedMessage == "closed" }
        assertEquals(1, closed.size, events.map { it.formattedMessage }.toString())
        assertEquals(BUDGET_LOGGER_NAME, closed.single().loggerName)
    }

    @Test
    fun `close from another thread while calls wait ends them all`() = runTest {
        val clock = virtualClock()
        val budget = budgetOn(clock, minuteWindow(1))
        budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
        val waiters = List(3) { callAcquire(budget, clock) }
        clock.advanceBy(Duration.ZERO)
        assertTrue(waiters.none { it.isCompleted })

        Thread { budget.close() }.apply { start(); join() }
        clock.advanceBy(Duration.ZERO)

        waiters.forEach { assertClosed(it.failure) }
        assertTrue(budget.toString().endsWith("closed=true)"))
    }
}
