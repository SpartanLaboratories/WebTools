package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.dayWindow
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.BudgetEvent
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.WindowReading
import com.spartanlabs.webtools.http.testing.RecordingBudgetObserver
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - reconciliation of `WindowedCallBudget` with a server's count: on a window published 60 with a margin of 10, a permit completed with
 * `remaining = 40` raises usage to 20 and emits `Reconciled(window, 20, 19)`; a later `remaining = 59` leaves 20; a reading for a window that rolled since
 * the grant is ignored; a reading never lowers the count after a `release`; a negative `remaining` clamps to `published`; the raised count makes the
 * next call wait for the boundary; a hard window raised to its limit by a reading raises `HardStop`.
 */
class WindowedCallBudgetReconcileComponentTest {

    private fun reading(window: String, remaining: Int): RateSignals = RateSignals { reading(window, WindowReading(remaining)) }

    @Test
    fun `a reading raises usage to what the server counted and emits Reconciled`() = runTest {
        val clock = virtualClock()
        val recording = RecordingBudgetObserver()
        budgetOn(clock, minuteWindow(50, margin = 10)) { observer = recording }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow().complete(reading("minute", 40))
            assertEquals(20, budget.utilisation().windows.single().used)
            val reconciled = recording.eventsOf<BudgetEvent.Reconciled>().single()
            assertEquals("minute", reconciled.window)
            assertEquals(20, reconciled.serverUsed)
            assertEquals(19, reconciled.raisedBy)

            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow().complete(reading("minute", 59))
            assertEquals(20, budget.utilisation().windows.single().used, "a larger remaining changed the count")       // the floor of 20 still covers the two grants
            assertEquals(1, recording.eventsOf<BudgetEvent.Reconciled>().size)
        }
    }

    @Test
    fun `a reading for a window that rolled since the grant is ignored`() = runTest {
        val clock = virtualClock()
        val recording = RecordingBudgetObserver()
        budgetOn(clock, minuteWindow(50, margin = 10)) { observer = recording }.use { budget ->
            val permit = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            clock.advanceBy(1.minutes)
            permit.complete(reading("minute", 10))
            assertEquals(0, budget.utilisation().windows.single().used)
            assertTrue(recording.eventsOf<BudgetEvent.Reconciled>().isEmpty())
        }
    }

    @Test
    fun `a reading never lowers the count after a release`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(50, margin = 10)).use { budget ->
            val a = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val b = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            b.complete(reading("minute", 40))
            assertEquals(20, budget.utilisation().windows.single().used)
            a.release()
            assertEquals(20, budget.utilisation().windows.single().used, "a release lowered the count below the server's")
        }
    }

    @Test
    fun `a negative remaining clamps to published and makes the next call wait for the boundary`() = runTest {
        val clock = virtualClock(VirtualClock.DEFAULT_START_EPOCH_MILLIS + 10_000L)
        budgetOn(clock, minuteWindow(50, margin = 10)).use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow().complete(reading("minute", -5))
            assertEquals(60, budget.utilisation().windows.single().used)
            assertEquals(1.2, budget.utilisation().windows.single().fraction)
            val failure = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull())
            assertEquals(ExhaustedReason.Window, failure.reason)
            assertEquals(50.seconds, failure.wait)
        }
    }

    @Test
    fun `a hard window raised to its limit by a reading raises HardStop`() = runTest {
        val clock = virtualClock()
        val recording = RecordingBudgetObserver()
        budgetOn(clock, dayWindow(5)) { observer = recording }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow().complete(reading("day", 0))
            assertEquals(5, budget.utilisation().windows.single().used)
            val stop = recording.eventsOf<BudgetEvent.HardStop>().single()
            assertEquals("day", stop.window)
            assertEquals(VirtualClock.DEFAULT_START_EPOCH_MILLIS + 86_400_000L, stop.resetsAtEpochMillis)
            assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull())
        }
    }
}
