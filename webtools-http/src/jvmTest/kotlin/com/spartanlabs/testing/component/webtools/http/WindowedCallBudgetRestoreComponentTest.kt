package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.DAY_MS
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.callAcquire
import com.spartanlabs.testing.support.webtools.http.dayWindow
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.BudgetEvent
import com.spartanlabs.webtools.http.BudgetSnapshot
import com.spartanlabs.webtools.http.BudgetUtilisation
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.PauseSource
import com.spartanlabs.webtools.http.StoreOperation
import com.spartanlabs.webtools.http.WindowSnapshot
import com.spartanlabs.webtools.http.WindowedCallBudget
import com.spartanlabs.webtools.http.testing.FakeBudgetStore
import com.spartanlabs.webtools.http.testing.RecordingBudgetObserver
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - the restore of `WindowedCallBudget` from a `FakeBudgetStore`: the merge rules (a stale window dropped; the current window merged additively
 * with the grants made while the first load had failed; a future window kept as a cell at its own index and read once the clock reaches it; an unknown
 * name, an unaligned start and an unknown snapshot version dropped); skew (a stamp 4 minutes ahead keeps counts and pause, 6 minutes ahead keeps the
 * counts, ignores the stamp and pause and reports `ignoredForSkew`; a restored pause is converted once, capped by `maxPause` and emits
 * `Paused(…, Snapshot)`); when it runs (the first of `acquire`, `utilisation()` and `flush()`; concurrent first calls make one load); the gate (a failing
 * load raises `StoreFailed(Load)`, grants continue, no save is made, no retry inside `storeRetryInterval`, a retry after it that merges additively and
 * resumes the write-ahead; `utilisation()` never retries; `flush()` retries unconditionally, returns the failure and saves nothing while the store is
 * down, then writes the exact counts once healed; `flush()` writes `persisted == effective` and works after `close()`); a retried restore that merges
 * counts re-plans every waiter. That case gives the waiter a cap, so the re-plan rule
 * fails it at the instant of the retry with the wait it would now have, `BudgetExhausted(…, Window, "day")`, which makes the re-plan observable there; a
 * waiter with `maxWait = INFINITE` would be moved to the next midnight instead, observable only a day later.
 */
class WindowedCallBudgetRestoreComponentTest {

    private val midnight = VirtualClock.DEFAULT_START_EPOCH_MILLIS
    private val tenPastMidnight = midnight + 10.minutes.inWholeMilliseconds

    private fun snapshot(vararg windows: WindowSnapshot, takenAt: Long = tenPastMidnight, pausedUntil: Long = 0L): BudgetSnapshot =
        BudgetSnapshot(takenAt, windows.toList(), pausedUntil)

    @Test
    fun `a stale window is dropped`() = runTest {
        val clock = virtualClock(tenPastMidnight)
        val recording = RecordingBudgetObserver()
        val store = FakeBudgetStore(snapshot(WindowSnapshot("day", midnight - DAY_MS, 3)))
        budgetOn(clock, dayWindow(5)) { this.store = store; observer = recording }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val restored = recording.eventsOf<BudgetEvent.Restored>().single()
            assertEquals(0, restored.windowsRestored)
            assertEquals(1, restored.windowsDropped)
            assertFalse(restored.ignoredForSkew)
            assertEquals(1, budget.utilisation().windows.single().used)
        }
    }

    @Test
    fun `the current window is merged additively with the grants made while the load had failed`() = runTest {
        val clock = virtualClock(tenPastMidnight)
        val store = FakeBudgetStore(snapshot(WindowSnapshot("day", midnight, 3)))
        store.failLoads(IllegalStateException("down"), times = 1)
        budgetOn(clock, dayWindow(5)) { this.store = store; storeRetryInterval = 30.seconds }.use { budget ->
            repeat(2) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            assertEquals(2, budget.utilisation().windows.single().used)
            assertEquals(1, store.loadCount)
            clock.advanceBy(30.seconds)
            val refused = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull())
            assertEquals("day", refused.window)
            assertEquals(2, store.loadCount)
            assertEquals(5, budget.utilisation().windows.single().used)
        }
    }

    @Test
    fun `a future window is kept as a cell at its own index and read once the clock reaches it`() = runTest {
        val clock = virtualClock(tenPastMidnight)
        val recording = RecordingBudgetObserver()
        val store = FakeBudgetStore(snapshot(WindowSnapshot("day", midnight + DAY_MS, 4)))
        budgetOn(clock, dayWindow(5)) { this.store = store; observer = recording }.use { budget ->
            assertEquals(0, budget.utilisation().windows.single().used)
            assertEquals(1, recording.eventsOf<BudgetEvent.Restored>().single().windowsRestored)
            clock.advanceBy(1.days)
            assertEquals(4, budget.utilisation().windows.single().used)
            assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess)
            assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull())
        }
    }

    @Test
    fun `an unknown name an unaligned start and an unknown version are dropped`() = runTest {
        val clock = virtualClock(tenPastMidnight)
        val recording = RecordingBudgetObserver()
        FakeBudgetStore(snapshot(WindowSnapshot("week", midnight, 3), WindowSnapshot("day", midnight + 1L, 3))).let { store ->
            budgetOn(clock, dayWindow(5)) { this.store = store; observer = recording }.use { budget ->
                budget.utilisation()
                val restored = recording.eventsOf<BudgetEvent.Restored>().single()
                assertEquals(0, restored.windowsRestored)
                assertEquals(2, restored.windowsDropped)
                assertEquals(0, budget.utilisation().windows.single().used)
            }
        }
        recording.clear()
        FakeBudgetStore(BudgetSnapshot(2, tenPastMidnight, listOf(WindowSnapshot("day", midnight, 3)), 0L)).let { store ->
            budgetOn(clock, dayWindow(5)) { this.store = store; observer = recording }.use { budget ->
                budget.utilisation()
                val restored = recording.eventsOf<BudgetEvent.Restored>().single()
                assertEquals(0, restored.windowsRestored)
                assertEquals(1, restored.windowsDropped)
                assertEquals(0, budget.utilisation().windows.single().used)
            }
        }
    }

    @Test
    fun `a stamp within maxRestoreSkew keeps the counts and the pause and one beyond it keeps only the counts`() = runTest {
        val clock = virtualClock(tenPastMidnight)
        val pausedUntil = tenPastMidnight + 20.minutes.inWholeMilliseconds
        suspend fun restoreWithStampAhead(ahead: Duration): Pair<BudgetEvent.Restored, Duration> {
            val recording = RecordingBudgetObserver()
            val store = FakeBudgetStore(snapshot(WindowSnapshot("day", midnight, 3), takenAt = tenPastMidnight + ahead.inWholeMilliseconds, pausedUntil = pausedUntil))
            return budgetOn(clock, dayWindow(5)) { this.store = store; observer = recording }.use { budget ->
                val utilisation = budget.utilisation()
                assertEquals(3, utilisation.windows.single().used, "the counts were not kept at a skew of $ahead")
                recording.eventsOf<BudgetEvent.Restored>().single() to utilisation.pausedFor
            }
        }
        val (kept, keptPause) = restoreWithStampAhead(4.minutes)
        assertFalse(kept.ignoredForSkew)
        assertEquals(20.minutes, keptPause)
        val (ignored, ignoredPause) = restoreWithStampAhead(6.minutes)
        assertTrue(ignored.ignoredForSkew)
        assertEquals(1, ignored.windowsRestored)
        assertEquals(Duration.ZERO, ignoredPause)
    }

    @Test
    fun `a restored pause is converted once capped by maxPause and emits Paused from the snapshot`() = runTest {
        val clock = virtualClock(tenPastMidnight)
        val recording = RecordingBudgetObserver()
        val store = FakeBudgetStore(snapshot(pausedUntil = tenPastMidnight + 48.hours.inWholeMilliseconds))
        budgetOn(clock, dayWindow(5)) { this.store = store; observer = recording }.use { budget ->
            assertEquals(24.hours, budget.utilisation().pausedFor)
            val paused = recording.eventsOf<BudgetEvent.Paused>().single()
            assertEquals(PauseSource.Snapshot, paused.source)
            assertEquals(24.hours, paused.length)
            assertEquals(tenPastMidnight + 24.hours.inWholeMilliseconds, paused.untilEpochMillis)
            clock.advanceBy(10.hours)
            assertEquals(14.hours, budget.utilisation().pausedFor)
        }
    }

    @Test
    fun `the restore runs at the first of acquire utilisation and flush`() = runTest {
        val clock = virtualClock(tenPastMidnight)
        suspend fun loadsOf(first: suspend (budget: WindowedCallBudget) -> Unit): Int {
            val store = FakeBudgetStore()
            budgetOn(clock, dayWindow(5)) { this.store = store }.use { budget ->
                first(budget)
                assertEquals(1, store.loadCount)
                budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
                budget.utilisation()
                budget.flush()
            }
            return store.loadCount
        }
        assertEquals(1, loadsOf { it.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() })
        assertEquals(1, loadsOf { it.utilisation() })
        assertEquals(1, loadsOf { it.flush().getOrThrow() })
    }

    @Test
    fun `concurrent first calls make one load and all complete`() = runTest {
        val clock = virtualClock(tenPastMidnight)
        val store = FakeBudgetStore(snapshot(WindowSnapshot("day", midnight, 2)))
        store.loadDelay = 1.seconds
        budgetOn(clock, dayWindow(5)) { this.store = store }.use { budget ->
            val first = callAcquire(budget, clock)
            val second = callAcquire(budget, clock)
            var utilisation: BudgetUtilisation? = null
            launch { utilisation = budget.utilisation() }
            clock.advanceBy(Duration.ZERO)
            assertFalse(first.isCompleted)
            clock.advanceBy(1.seconds)
            assertEquals(1.seconds, first.completedAt)
            assertEquals(1.seconds, second.completedAt)
            assertEquals(4, utilisation?.windows?.single()?.used)
            assertEquals(1, store.loadCount)
        }
    }

    @Test
    fun `a failing load fails open and is retried only after storeRetryInterval`() = runTest {
        val clock = virtualClock(tenPastMidnight)
        val recording = RecordingBudgetObserver()
        val cause = IllegalStateException("down")
        val store = FakeBudgetStore(snapshot(WindowSnapshot("day", midnight, 2)))
        store.failLoads(cause)
        budgetOn(clock, dayWindow(5)) { this.store = store; persistAhead = 1; storeRetryInterval = 30.seconds; observer = recording }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val failed = recording.eventsOf<BudgetEvent.StoreFailed>().single()
            assertEquals(StoreOperation.Load, failed.operation)
            assertSame(cause, failed.cause)
            assertEquals(1, budget.utilisation().windows.single().used)
            assertEquals(0, store.saveAttemptCount, "a save was made before a restore succeeded")

            clock.advanceBy(29.seconds)
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            assertEquals(1, store.loadCount, "a retry inside storeRetryInterval")
            budget.utilisation()
            assertEquals(1, store.loadCount, "utilisation retried the restore")

            clock.advanceBy(1.seconds)
            store.heal()
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            assertEquals(2, store.loadCount)
            assertEquals(5, budget.utilisation().windows.single().used, "the stored 2 and the 3 grants were not added")
            assertEquals(1, recording.eventsOf<BudgetEvent.Restored>().size)
            assertEquals(listOf(5), store.saves.map { it.windows.single().used }, "the write-ahead did not resume with the restore")
        }
    }

    @Test
    fun `a retried restore that merges counts re-plans the waiters`() = runTest {
        val clock = virtualClock(tenPastMidnight)
        val store = FakeBudgetStore(snapshot(WindowSnapshot("day", midnight, 4)))
        store.failLoads(IllegalStateException("down"), times = 1)
        budgetOn(clock, minuteWindow(1), dayWindow(5)) { this.store = store; storeRetryInterval = 30.seconds }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()   // fails open: the day holds 1 locally, the minute is full
            assertEquals(1, store.loadCount)
            val waiter = callAcquire(budget, clock, maxWait = 5.minutes)   // planned at the minute boundary, 60 s away, inside its cap
            clock.advanceBy(Duration.ZERO)
            assertFalse(waiter.isCompleted)
            val before = budget.utilisation()
            assertEquals(1, before.waiting)
            assertEquals(1, before.windows.single { it.name == "day" }.queued)
            assertEquals(1, before.windows.single { it.name == "day" }.used)

            clock.advanceBy(30.seconds)
            val retrying = budget.acquire(1, Lane.Tracker, Duration.ZERO)  // retries the load: the stored 4 and the local 1 merge to 5
            assertEquals(2, store.loadCount)
            assertIs<HttpFailure.BudgetExhausted>(retrying.exceptionOrNull())
            clock.advanceBy(Duration.ZERO)
            assertTrue(waiter.isCompleted, "the waiter was not re-planned at the retry")
            assertFalse(waiter.isCancelled)
            assertEquals(30.seconds, waiter.completedAt)
            val refused = assertIs<HttpFailure.BudgetExhausted>(waiter.failure)
            assertEquals(ExhaustedReason.Window, refused.reason)
            assertEquals("day", refused.window)
            assertEquals(1.days - 10.minutes - 30.seconds, refused.wait, "the wait is not the one to the next midnight")
            val after = budget.utilisation()
            assertEquals(0, after.waiting)
            assertEquals(0, after.windows.single { it.name == "day" }.queued, "the failed waiter's reservation was kept")
            assertEquals(0, after.windows.single { it.name == "minute" }.queued)
            assertEquals(5, after.windows.single { it.name == "day" }.used)
        }
    }

    @Test
    fun `flush retries unconditionally and saves nothing while the store is down`() = runTest {
        val clock = virtualClock(tenPastMidnight)
        val cause = IllegalStateException("down")
        val store = FakeBudgetStore(snapshot(WindowSnapshot("day", midnight, 2)))
        store.failLoads(cause)
        budgetOn(clock, dayWindow(5)) { this.store = store; storeRetryInterval = 30.seconds }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            assertSame(cause, budget.flush().exceptionOrNull())
            assertSame(cause, budget.flush().exceptionOrNull())
            assertEquals(3, store.loadCount)
            assertEquals(0, store.saveAttemptCount)
            store.heal()
            assertEquals(Result.success(Unit), budget.flush())
            assertEquals(3, store.saves.single().windows.single().used)
            assertEquals(3, budget.utilisation().windows.single().used)
        }
    }

    @Test
    fun `flush writes the exact effective counts and works after close`() = runTest {
        val clock = virtualClock(tenPastMidnight)
        val store = FakeBudgetStore()
        budgetOn(clock, dayWindow(10)) { this.store = store; persistAhead = 4 }.use { budget ->
            val permits = List(3) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            assertEquals(listOf(4), store.saves.map { it.windows.single().used })
            assertEquals(Result.success(Unit), budget.flush())
            assertEquals(listOf(4, 3), store.saves.map { it.windows.single().used })

            budget.close()
            permits[0].release()
            permits[1].complete()
            assertEquals(Result.success(Unit), budget.flush())
            assertEquals(2, store.saves.last().windows.single().used)
            assertEquals(2, budget.utilisation().windows.single().used)
            // The persisted mark follows the exact write: the next grant from a fresh budget restores 2, not 4.
            budgetOn(clock, dayWindow(10)) { this.store = store }.use { second -> assertEquals(2, second.utilisation().windows.single().used) }
        }
    }
}
