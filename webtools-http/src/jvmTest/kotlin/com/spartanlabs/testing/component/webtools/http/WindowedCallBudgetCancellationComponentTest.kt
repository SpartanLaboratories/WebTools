package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.callAcquire
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.WindowReading
import com.spartanlabs.webtools.http.testing.FakeBudgetStore
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - cancellation around the lock of `WindowedCallBudget`: a waiter cancelled while parked on the lock as its slot arrives is not complete until
 * the lock frees, then ends cancelled, and the waiter behind it is granted at the re-planned instant; `complete(signals)` and `release()` from a cancelled
 * coroutine (`launch { try { delay(…) } finally { permit.complete(signals) } }` cancelled while the lock is held) take effect once the lock frees, with no
 * exception reaching the coroutine, idempotently and after `close()`; a cancellation that lands in the same `advanceBy` as the slot ends with
 * `CancellationException` and no charge.
 */
class WindowedCallBudgetCancellationComponentTest {

    @Test
    fun `a waiter cancelled while parked on the lock ends after the lock frees and the one behind is granted at the re-planned instant`() = runTest {
        val clock = virtualClock()
        val store = FakeBudgetStore()
        budgetOn(clock, minuteWindow(1)) { this.store = store; storeTimeout = 20.seconds }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val head = callAcquire(budget, clock)
            val behind = callAcquire(budget, clock)
            clock.advanceBy(Duration.ZERO)
            assertEquals(2, budget.utilisation().waiting)

            clock.advanceBy(55.seconds)
            store.saveDelay = 10.seconds
            launch { budget.flush() }                               // the lock is held from 55 s to 65 s: the head's slot at 60 s parks on it
            clock.advanceBy(7.seconds)
            assertFalse(head.isCompleted)
            head.cancel()
            clock.advanceBy(Duration.ZERO)
            assertFalse(head.isCompleted, "the head completed while the lock was still held")

            clock.advanceBy(3.seconds)
            assertTrue(head.isCompleted && head.isCancelled)
            assertIs<CancellationException>(head.thrown)
            assertEquals(65.seconds, behind.completedAt, "the waiter behind was not granted at the re-planned instant")
            assertNotNull(behind.permit)
            assertEquals(0, budget.utilisation().waiting)
        }
    }

    @Test
    fun `complete and release from a cancelled coroutine take effect once the lock frees`() = runTest {
        val clock = virtualClock()
        val store = FakeBudgetStore()
        budgetOn(clock, minuteWindow(50, margin = 10)) { this.store = store; storeTimeout = 20.seconds }.use { budget ->
            val completing = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val releasing = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            var completeThrew: Throwable? = null
            var releaseThrew: Throwable? = null
            val completer = launch {
                try { delay(1.hours) } finally {
                    try { completing.complete(RateSignals { throttled = true; retryAfter = 30.seconds; reading("minute", WindowReading(40)) }) } catch (t: Throwable) { completeThrew = t }
                }
            }
            val releaser = launch {
                try { delay(1.hours) } finally {
                    try { releasing.release() } catch (t: Throwable) { releaseThrew = t }
                }
            }
            clock.advanceBy(Duration.ZERO)
            store.saveDelay = 10.seconds
            launch { budget.flush() }                               // holds the lock for 10 s
            clock.advanceBy(Duration.ZERO)
            completer.cancel()
            releaser.cancel()
            clock.advanceBy(Duration.ZERO)
            assertFalse(completer.isCompleted, "complete finished while the lock was held")
            assertFalse(releaser.isCompleted, "release finished while the lock was held")

            clock.advanceBy(10.seconds)
            assertTrue(completer.isCompleted && releaser.isCompleted)
            assertNull(completeThrew)
            assertNull(releaseThrew)
            val utilisation = budget.utilisation()
            assertEquals(20, utilisation.windows.single().used, "the server's count was not applied")
            assertEquals(30.seconds, utilisation.pausedFor, "the pause was not applied")                  // applied when the lock freed, at 10 s

            budget.close()
            completing.complete()
            releasing.release()
            completing.release()
            assertEquals(20, budget.utilisation().windows.single().used)
        }
    }

    @Test
    fun `a cancellation that lands in the same advanceBy as the slot ends cancelled with no charge`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(1)).use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            lateinit var cancelLater: () -> Unit
            launch { delay(1.minutes); cancelLater() }               // registered first: it runs first at 60 s
            val waiter = callAcquire(budget, clock)
            cancelLater = { waiter.cancel() }
            clock.advanceBy(Duration.ZERO)
            assertFalse(waiter.isCompleted)

            clock.advanceBy(1.minutes)
            assertTrue(waiter.isCompleted)
            assertTrue(waiter.isCancelled)
            assertIs<CancellationException>(waiter.thrown)
            assertNull(waiter.result)
            val utilisation = budget.utilisation()
            assertEquals(0, utilisation.windows.single().used)
            assertEquals(0, utilisation.windows.single().queued)
            assertEquals(0, utilisation.waiting)
        }
    }
}
