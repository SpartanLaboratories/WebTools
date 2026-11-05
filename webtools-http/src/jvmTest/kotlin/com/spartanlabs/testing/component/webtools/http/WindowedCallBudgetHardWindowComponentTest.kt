package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.DAY_MS
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.dayWindow
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.BudgetEvent
import com.spartanlabs.webtools.http.BudgetSnapshot
import com.spartanlabs.webtools.http.ExhaustedReason
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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Level 2 - a hard window of `WindowedCallBudget` with a `FakeBudgetStore`: the first acquire loads; with a day of 5 and `persistAhead = 3` the
 * write-ahead saves are exactly `[3, 5]` (at grants 1 and 4), none at grants 2, 3 and 5; the sixth call is refused with the exact wait to the next
 * midnight; `HardStop` is raised once, at the fifth grant, with `resetsAtEpochMillis` the next midnight, and once more after the next day's fifth grant;
 * `persistAhead = 1` saves every grant; a non-hard window is never saved; each save carries the wall stamp and the pause in force.
 */
class WindowedCallBudgetHardWindowComponentTest {

    private val midnight = VirtualClock.DEFAULT_START_EPOCH_MILLIS
    private val nextMidnight = midnight + DAY_MS

    private fun List<BudgetSnapshot>.dayCounts(): List<Int> = map { it.windows.single { w -> w.window == "day" }.used }

    @Test
    fun `the first acquire loads and the write-ahead saves are exactly 3 and 5`() = runTest {
        val clock = virtualClock(midnight + 3_600_000L)
        val store = FakeBudgetStore()
        val recording = RecordingBudgetObserver()
        budgetOn(clock, dayWindow(5)) { this.store = store; persistAhead = 3; observer = recording }.use { budget ->
            assertEquals(0, store.loadCount)
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            assertEquals(1, store.loadCount)
            assertEquals(listOf(3), store.saves.dayCounts())
            repeat(2) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            assertEquals(listOf(3), store.saves.dayCounts(), "grants 2 and 3 saved")
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            assertEquals(listOf(3, 5), store.saves.dayCounts())
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            assertEquals(listOf(3, 5), store.saves.dayCounts(), "grant 5 saved")
            assertEquals(2, store.saveAttemptCount)
            assertEquals(1, store.loadCount)

            val sixth = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, 1.hours).exceptionOrNull())
            assertEquals(23.hours, sixth.wait)
            assertEquals(ExhaustedReason.Window, sixth.reason)
            assertEquals("day", sixth.window)

            val stop = recording.eventsOf<BudgetEvent.HardStop>().single()
            assertEquals("day", stop.window)
            assertEquals(nextMidnight, stop.resetsAtEpochMillis)
            // The stop is raised at the fifth grant, right after it, and the refusal raises no second one.
            assertEquals(
                listOf("Restored", "Granted", "Granted", "Granted", "Granted", "Granted", "HardStop", "Refused"),
                recording.events.map { it::class.simpleName },
            )
        }
    }

    @Test
    fun `HardStop is raised once per period and the window opens after midnight`() = runTest {
        val clock = virtualClock(midnight + 23.hours.inWholeMilliseconds)
        val store = FakeBudgetStore()
        val recording = RecordingBudgetObserver()
        budgetOn(clock, dayWindow(5)) { this.store = store; persistAhead = 3; observer = recording }.use { budget ->
            repeat(5) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            repeat(3) { assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull()) }
            assertEquals(1, recording.eventsOf<BudgetEvent.HardStop>().size, "a refused call raised another HardStop")
            assertEquals(nextMidnight, recording.eventsOf<BudgetEvent.HardStop>().single().resetsAtEpochMillis)

            clock.advanceBy(1.hours)
            assertEquals(0, budget.utilisation().windows.single().used)
            repeat(4) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            assertEquals(1, recording.eventsOf<BudgetEvent.HardStop>().size)
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            assertEquals(2, recording.eventsOf<BudgetEvent.HardStop>().size)
            assertEquals(nextMidnight + DAY_MS, recording.eventsOf<BudgetEvent.HardStop>()[1].resetsAtEpochMillis)
            assertEquals(listOf(3, 5, 3, 5), store.saves.dayCounts())
        }
    }

    @Test
    fun `persistAhead 1 saves every grant`() = runTest {
        val clock = virtualClock()
        val store = FakeBudgetStore()
        budgetOn(clock, dayWindow(5)) { this.store = store; persistAhead = 1 }.use { budget ->
            repeat(5) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            assertEquals(listOf(1, 2, 3, 4, 5), store.saves.dayCounts())
        }
    }

    @Test
    fun `a non-hard window is never saved`() = runTest {
        val clock = virtualClock()
        val store = FakeBudgetStore()
        budgetOn(clock, minuteWindow(5), dayWindow(5, hard = false)) { this.store = store; persistAhead = 1 }.use { budget ->
            repeat(5) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            assertEquals(0, store.saveAttemptCount)
            assertEquals(1, store.loadCount)
            assertEquals(Result.success(Unit), budget.flush())
            assertTrue(store.saves.single().windows.isEmpty(), "a non-hard window was written by flush")
        }
    }

    @Test
    fun `each save carries the wall stamp and the pause in force`() = runTest {
        val clock = virtualClock(midnight + 10_000L)
        val store = FakeBudgetStore()
        budgetOn(clock, dayWindow(5)) { this.store = store; persistAhead = 1 }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            clock.advanceBy(7.minutes + 3.milliseconds)
            val permit = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            assertEquals(listOf(midnight + 10_000L, midnight + 10_000L + 7.minutes.inWholeMilliseconds + 3L), store.saves.map { it.takenAtEpochMillis })
            assertTrue(store.saves.all { it.pausedUntilEpochMillis == 0L })

            permit.complete(RateSignals { throttled = true; retryAfter = 30.minutes })
            assertEquals(Result.success(Unit), budget.flush())
            val flushed = store.saves.last()
            assertEquals(clock.wallClock.nowEpochMillis(), flushed.takenAtEpochMillis)
            assertEquals(clock.wallClock.nowEpochMillis() + 30.minutes.inWholeMilliseconds, flushed.pausedUntilEpochMillis)
            assertEquals(2, flushed.windows.single().used)
            assertEquals(midnight, flushed.windows.single().windowStartEpochMillis)
        }
    }
}
