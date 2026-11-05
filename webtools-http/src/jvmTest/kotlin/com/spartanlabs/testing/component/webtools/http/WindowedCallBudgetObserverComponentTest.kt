package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.Boom
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.dayWindow
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.BudgetEvent
import com.spartanlabs.webtools.http.BudgetUtilisation
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.PauseSource
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.StoreOperation
import com.spartanlabs.webtools.http.WindowReading
import com.spartanlabs.webtools.http.WindowedCallBudget
import com.spartanlabs.webtools.http.testing.FakeBudgetStore
import com.spartanlabs.webtools.http.testing.RecordingBudgetObserver
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - the observer seam of `WindowedCallBudget` with `RecordingBudgetObserver`: the events of one call arrive in order (`Restored`, then
 * `Granted`; a refusal gives `Refused`) with the leaf fields filled; an observer that calls back into the budget from real threads does not deadlock
 * (which fails if events were emitted under the lock); an observer that throws a `RuntimeException`, or a `CancellationException`, does not fail the
 * grant and is logged once at `WARN` with the class name; an `Error` from the observer propagates; the observer is not called for a call that threw
 * `IllegalArgumentException`.
 */
class WindowedCallBudgetObserverComponentTest {

    @Test
    fun `the events of one call arrive in order with their fields`() = runTest {
        val clock = virtualClock()
        val recording = RecordingBudgetObserver()
        val store = FakeBudgetStore()
        store.failSaves(IllegalStateException("down"))
        budgetOn(clock, minuteWindow(2), dayWindow(3)) { this.store = store; persistAhead = 1; observer = recording }.use { budget ->
            val first = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            assertEquals(listOf("Restored", "StoreFailed", "Granted"), recording.events.map { it::class.simpleName })
            val restored = assertIs<BudgetEvent.Restored>(recording.events[0])
            assertEquals(0, restored.windowsRestored)
            val failed = assertIs<BudgetEvent.StoreFailed>(recording.events[1])
            assertEquals(StoreOperation.Save, failed.operation)
            val granted = assertIs<BudgetEvent.Granted>(recording.events[2])
            assertEquals(Lane.Tracker, granted.lane)
            assertEquals(1, granted.cost)
            assertEquals(Duration.ZERO, granted.waited)

            budget.acquire(1, Lane.Interactive, Duration.ZERO).getOrThrow()
            budget.acquire(1, Lane.Tracker, Duration.ZERO)
            val refused = recording.eventsOf<BudgetEvent.Refused>().single()
            assertEquals(Lane.Tracker, refused.lane)
            assertEquals(1, refused.cost)
            assertEquals(1.seconds * 60, refused.wait)
            assertEquals(ExhaustedReason.Window, refused.reason)
            assertEquals("minute", refused.window)

            first.complete(RateSignals { throttled = true; retryAfter = 30.seconds; reading("day", WindowReading(0)) })
            val reconciled = recording.eventsOf<BudgetEvent.Reconciled>().single()
            assertEquals("day", reconciled.window)
            assertEquals(3, reconciled.serverUsed)
            assertEquals(1, reconciled.raisedBy)
            val paused = recording.eventsOf<BudgetEvent.Paused>().single()
            assertEquals(PauseSource.RetryAfter, paused.source)
            assertEquals(30.seconds, paused.length)
            assertEquals(VirtualClock.DEFAULT_START_EPOCH_MILLIS + 30_000L, paused.untilEpochMillis)
            val stop = recording.eventsOf<BudgetEvent.HardStop>().single()
            assertEquals("day", stop.window)
            assertEquals(VirtualClock.DEFAULT_START_EPOCH_MILLIS + 86_400_000L, stop.resetsAtEpochMillis)
        }
    }

    @Test
    fun `an observer that calls back into the budget does not deadlock`(): Unit = runBlocking {
        val clock = VirtualClock(TestCoroutineScheduler())
        lateinit var budget: WindowedCallBudget
        var seen: BudgetUtilisation? = null
        val recording = RecordingBudgetObserver { seen = runBlocking { budget.utilisation() } }
        budget = budgetOn(clock, minuteWindow(10)) { observer = recording }
        budget.use {
            val outcome = withTimeoutOrNull(5.seconds) {
                async(Dispatchers.Default) { budget.acquire(1, Lane.Tracker, Duration.ZERO) }.await()
            }
            assertNotNull(outcome, "the observer's callback into the budget deadlocked")
            assertTrue(outcome.isSuccess)
            assertEquals(1, seen?.windows?.single()?.used)
            assertTrue(recording.eventsOf<BudgetEvent.Granted>().isNotEmpty())
        }
    }

    @Test
    fun `an observer that throws does not fail the grant and is logged once at WARN with the class name`() {
        for (thrown in listOf<Exception>(RuntimeException("observer"), CancellationException("observer"))) {
            val (_, events) = captureTransportLogs(Level.DEBUG) {
                runTest {
                    val clock = virtualClock()
                    val recording = RecordingBudgetObserver { if (it is BudgetEvent.Granted) throw thrown }
                    budgetOn(clock, minuteWindow(10)) { observer = recording }.use { budget ->
                        assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess)
                        assertEquals(1, budget.utilisation().windows.single().used)
                        assertEquals(1, recording.eventsOf<BudgetEvent.Granted>().size)
                    }
                }
            }
            val warnings = events.filter { it.level == Level.WARN }
            assertEquals(1, warnings.size, events.map { it.formattedMessage }.toString())
            assertEquals("observer failed: ${thrown::class.simpleName}", warnings.single().formattedMessage)
        }
    }

    @Test
    fun `an Error from the observer propagates`() = runTest {
        val clock = virtualClock()
        val boom = Boom()
        val recording = RecordingBudgetObserver { if (it is BudgetEvent.Granted) throw boom }
        budgetOn(clock, minuteWindow(10)) { observer = recording }.use { budget ->
            assertSame(boom, assertFailsWith<Boom> { budget.acquire(1, Lane.Tracker, Duration.ZERO) })
            assertEquals(1, budget.utilisation().windows.single().used)              // the grant was made and the lock released before the observer ran
        }
    }

    @Test
    fun `the observer is not called for a call that threw IllegalArgumentException`() = runTest {
        val clock = virtualClock()
        val recording = RecordingBudgetObserver()
        budgetOn(clock, minuteWindow(10)) { observer = recording }.use { budget ->
            assertFailsWith<IllegalArgumentException> { budget.acquire(0, Lane.Tracker, Duration.ZERO) }
            assertFailsWith<IllegalArgumentException> { budget.acquire(1, Lane.Tracker, (-1).seconds) }
            assertEquals(emptyList<BudgetEvent>(), recording.events)
        }
    }
}
