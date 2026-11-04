package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.Boom
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.callAcquire
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.dayWindow
import com.spartanlabs.webtools.http.BudgetEvent
import com.spartanlabs.webtools.http.BudgetStoreFailure
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.StoreOperation
import com.spartanlabs.webtools.http.testing.FakeBudgetStore
import com.spartanlabs.webtools.http.testing.RecordingBudgetObserver
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SENTINEL = "SENTINEL-STORE-MESSAGE-5e2a"

/**
 * Level 2 - a store that fails under `WindowedCallBudget`: timeouts are virtual (`saveDelay = 5 s`, `storeTimeout = 2 s`: the write-ahead grant proceeds
 * at exactly 2 s with `StoreFailed(Save)`, `saves` stays empty, a grant inside `storeRetryInterval` makes no save attempt and the next one after it does,
 * the in-process stop holds meanwhile); a store that throws a `CancellationException`, or returns one in its `Result`, while the caller is active yields
 * `StoreFailed` and a normal grant, never a `CancellationException` to the caller; the caller's own cancellation during a store delay propagates and leaves
 * the ledger unchanged with no waiter behind; a `loadDelay` beyond `storeTimeout` fails the restore the same way; a failing store's exception message
 * appears in no log line, only its class name; a save that outlasts a hard window's period is written again for the cell then current, at most three
 * times, and the grant then proceeds on the fourth cell; a store that throws an `Error` (from `save` or from `load`) propagates the same instance to the
 * caller with no `StoreFailed` event and an unchanged ledger, and the lock is released, so the next `acquire` is granted.
 */
class WindowedCallBudgetStoreFailureComponentTest {

    @Test
    fun `a save that times out fails the write-ahead at exactly storeTimeout and the grant proceeds`() = runTest {
        val clock = virtualClock()
        val store = FakeBudgetStore()
        store.saveDelay = 5.seconds
        val recording = RecordingBudgetObserver()
        budgetOn(clock, dayWindow(5)) { this.store = store; persistAhead = 1; storeTimeout = 2.seconds; storeRetryInterval = 30.seconds; observer = recording }.use { budget ->
            val first = callAcquire(budget, clock)
            clock.advanceBy(2.seconds - 1.milliseconds)
            assertFalse(first.isCompleted)
            clock.advanceBy(1.milliseconds)
            assertEquals(2.seconds, first.completedAt)
            assertNotNull(first.permit)
            val failed = recording.eventsOf<BudgetEvent.StoreFailed>().single()
            assertEquals(StoreOperation.Save, failed.operation)
            val cause = assertIs<BudgetStoreFailure>(failed.cause)
            assertEquals(BudgetStoreFailure.TIMEOUT_MESSAGE, cause.message)
            assertTrue(store.saves.isEmpty(), "a timed-out save was recorded")
            assertEquals(1, store.saveAttemptCount)

            clock.advanceBy(10.seconds)
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            assertEquals(1, store.saveAttemptCount, "a save was attempted inside storeRetryInterval")
            assertEquals(2, budget.utilisation().windows.single().used)

            clock.advanceBy(20.seconds)
            store.saveDelay = Duration.ZERO
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            assertEquals(2, store.saveAttemptCount, "no save was attempted after storeRetryInterval")
            assertEquals(3, store.saves.single().windows.single().used)
        }
    }

    @Test
    fun `the in-process stop holds while the store is down`() = runTest {
        val clock = virtualClock()
        val store = FakeBudgetStore()
        store.failSaves(IllegalStateException(SENTINEL))
        budgetOn(clock, dayWindow(3)) { this.store = store; persistAhead = 1 }.use { budget ->
            repeat(3) { assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess) }
            assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull())
            assertEquals(1, store.saveAttemptCount)
            assertTrue(store.saves.isEmpty())
        }
    }

    @Test
    fun `a store that leaks a CancellationException yields StoreFailed and a normal grant`() = runTest {
        val clock = virtualClock()
        suspend fun grantWith(arm: FakeBudgetStore.() -> Unit): BudgetEvent.StoreFailed {
            val store = FakeBudgetStore().apply(arm)
            val recording = RecordingBudgetObserver()
            return budgetOn(clock, dayWindow(5)) { this.store = store; persistAhead = 1; observer = recording }.use { budget ->
                val call = callAcquire(budget, clock)
                clock.advanceBy(Duration.ZERO)
                assertTrue(call.isCompleted)
                assertNull(call.thrown, "the caller received ${call.thrown}")
                assertNotNull(call.permit, "the call was not granted: ${call.failure}")
                assertFalse(call.isCancelled)
                recording.eventsOf<BudgetEvent.StoreFailed>().single()
            }
        }
        val thrownOnSave = grantWith { failSaves(CancellationException("leaked"), thrown = true) }
        assertEquals(StoreOperation.Save, thrownOnSave.operation)
        assertEquals(BudgetStoreFailure.CANCELLED_MESSAGE, assertIs<BudgetStoreFailure>(thrownOnSave.cause).message)
        val returnedOnSave = grantWith { failSaves(CancellationException("leaked")) }
        assertEquals(BudgetStoreFailure.CANCELLED_MESSAGE, assertIs<BudgetStoreFailure>(returnedOnSave.cause).message)
        val thrownOnLoad = grantWith { failLoads(CancellationException("leaked"), thrown = true) }
        assertEquals(StoreOperation.Load, thrownOnLoad.operation)
        assertIs<CancellationException>(assertIs<BudgetStoreFailure>(thrownOnLoad.cause).cause)
    }

    @Test
    fun `the caller's own cancellation during a store delay propagates and leaves the ledger unchanged`() = runTest {
        val clock = virtualClock()
        val store = FakeBudgetStore()
        store.saveDelay = 5.seconds
        budgetOn(clock, dayWindow(5)) { this.store = store; persistAhead = 1; storeTimeout = 10.seconds }.use { budget ->
            val call = callAcquire(budget, clock)
            clock.advanceBy(1.seconds)
            assertFalse(call.isCompleted)
            call.cancel()
            clock.advanceBy(Duration.ZERO)
            assertTrue(call.isCompleted)
            assertIs<CancellationException>(call.thrown)
            assertNull(call.result)
            val utilisation = budget.utilisation()
            assertEquals(0, utilisation.windows.single().used)
            assertEquals(0, utilisation.windows.single().queued)
            assertEquals(0, utilisation.waiting)
            clock.advanceBy(10.seconds)
            assertTrue(store.saves.isEmpty(), "the cancelled save was stored")
        }
    }

    @Test
    fun `a save that outlasts the period is written again for the cell then current at most three times and the grant proceeds`() = runTest {
        val clock = virtualClock()
        val store = FakeBudgetStore()
        store.saveDelay = 1.seconds                                                     // every save ends in the next period
        budgetOn(clock, RateWindow("sec", 5, 1.seconds) { hard = true }) { this.store = store; persistAhead = 1; storeTimeout = 5.seconds }.use { budget ->
            val call = callAcquire(budget, clock)
            clock.advanceBy(3.seconds - 1.milliseconds)
            assertFalse(call.isCompleted, "the call was granted before its third save ended")
            clock.advanceBy(1.milliseconds)
            assertEquals(3.seconds, call.completedAt)
            assertNotNull(call.permit, "the call was not granted: ${call.failure} ${call.thrown}")
            // Each save ended in the next period, so the grant was planned again onto the cell then current and written ahead for it, three times in all.
            assertEquals(3, store.saveAttemptCount)
            assertEquals(listOf(0L, 1_000L, 2_000L).map { clock.startEpochMillis + it }, store.saves.map { it.windows.single().windowStartEpochMillis })
            assertEquals(listOf(1, 1, 1), store.saves.map { it.windows.single().used })
            // The fourth cell's grant proceeds without a save of its own and is charged to the cell current when it was made.
            val window = budget.utilisation().windows.single()
            assertEquals(clock.startEpochMillis + 3_000L, window.windowStartEpochMillis)
            assertEquals(1, window.used)
            assertEquals(0, window.queued)
        }
    }

    @Test
    fun `a store that throws an Error propagates it and leaves the budget usable`() = runTest {
        val clock = virtualClock()
        suspend fun propagates(arm: FakeBudgetStore.(Boom) -> Unit) {
            val boom = Boom()
            val store = FakeBudgetStore().apply { arm(boom) }
            val recording = RecordingBudgetObserver()
            budgetOn(clock, dayWindow(5)) { this.store = store; persistAhead = 1; observer = recording }.use { budget ->
                val call = callAcquire(budget, clock)
                clock.advanceBy(Duration.ZERO)
                assertTrue(call.isCompleted)
                assertNull(call.result, "the call returned ${call.result}")
                val thrown = assertIs<Boom>(call.thrown, "the call threw ${call.thrown}")
                // With assertions enabled, kotlinx.coroutines' stack-trace recovery re-creates an Error that crosses withTimeoutOrNull's boundary, with the
                // original as its cause; the budget rethrows what the store threw, as the instance or as that copy, never wrapped in a BudgetStoreFailure.
                assertTrue(thrown === boom || thrown.cause === boom, "the call threw another Boom (cause=${thrown.cause})")
                assertTrue(recording.eventsOf<BudgetEvent.StoreFailed>().isEmpty(), "an Error was reported as a store failure")
                val utilisation = budget.utilisation()
                assertEquals(0, utilisation.windows.single().used)
                assertEquals(0, utilisation.windows.single().queued)
                assertEquals(0, utilisation.waiting)

                val next = callAcquire(budget, clock)                       // the store healed after one failure
                clock.advanceBy(Duration.ZERO)
                assertTrue(next.isCompleted, "the lock was not released after the Error")
                assertNotNull(next.permit, "the next call was not granted: ${next.failure} ${next.thrown}")
                assertEquals(1, budget.utilisation().windows.single().used)
                assertEquals(listOf(1), store.saves.map { it.windows.single().used })
            }
        }
        propagates { boom -> failSaves(boom, thrown = true, times = 1) }
        propagates { boom -> failLoads(boom, thrown = true, times = 1) }
    }

    @Test
    fun `a load beyond storeTimeout fails the restore the same way`() = runTest {
        val clock = virtualClock()
        val store = FakeBudgetStore()
        store.loadDelay = 5.seconds
        val recording = RecordingBudgetObserver()
        budgetOn(clock, dayWindow(5)) { this.store = store; storeTimeout = 2.seconds; observer = recording }.use { budget ->
            val call = callAcquire(budget, clock)
            clock.advanceBy(2.seconds)
            assertEquals(2.seconds, call.completedAt)
            assertNotNull(call.permit)
            val failed = recording.eventsOf<BudgetEvent.StoreFailed>().single()
            assertEquals(StoreOperation.Load, failed.operation)
            assertEquals(BudgetStoreFailure.TIMEOUT_MESSAGE, assertIs<BudgetStoreFailure>(failed.cause).message)
            assertTrue(recording.eventsOf<BudgetEvent.Restored>().isEmpty())
        }
    }

    @Test
    fun `a failing store's message appears in no log line only its class name`() {
        val cause = IllegalStateException(SENTINEL)
        val (_, events) = captureTransportLogs(Level.DEBUG) {
            runTest {
                val clock = virtualClock()
                val store = FakeBudgetStore()
                store.failLoads(cause)
                store.failSaves(cause)
                budgetOn(clock, dayWindow(5)) { this.store = store; persistAhead = 1 }.use { budget ->
                    budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
                    assertSame(cause, budget.flush().exceptionOrNull())
                }
            }
        }
        val storeLines = events.filter { it.level == Level.WARN && "store" in it.formattedMessage }
        assertEquals(2, storeLines.size, events.map { it.formattedMessage }.toString())
        storeLines.forEach { assertTrue("IllegalStateException" in it.formattedMessage, it.formattedMessage) }
        events.forEach { event ->
            assertFalse(SENTINEL in event.formattedMessage, event.formattedMessage)
            assertFalse(SENTINEL in (event.throwableProxy?.message ?: ""), "the exception was attached to a log line")
        }
    }
}
