package com.spartanlabs.testing.gating.webtools.http

import com.spartanlabs.webtools.http.BudgetEvent
import com.spartanlabs.webtools.http.BudgetObserver
import com.spartanlabs.webtools.http.BudgetSnapshot
import com.spartanlabs.webtools.http.BudgetStore
import com.spartanlabs.webtools.http.BudgetUtilisation
import com.spartanlabs.webtools.http.CallBudget
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.InMemoryBudgetStore
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.PauseSource
import com.spartanlabs.webtools.http.Permit
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.RateSignalsReader
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.RetryAfter
import com.spartanlabs.webtools.http.StoreOperation
import com.spartanlabs.webtools.http.WindowReading
import com.spartanlabs.webtools.http.WindowSnapshot
import com.spartanlabs.webtools.http.WindowUtilisation
import com.spartanlabs.webtools.http.WindowedCallBudget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// A function and a class whose defaults name the gated budget types: a consumer writes exactly this without any opt-in.
private fun budgetOf(budget: CallBudget = CallBudget.Unlimited, reader: RateSignalsReader = RateSignalsReader.Standard): Pair<CallBudget, RateSignalsReader> = budget to reader

private class Seams(
    val budget: CallBudget = CallBudget.Unlimited,
    val reader: RateSignalsReader = RateSignalsReader.Standard,
    val store: BudgetStore = InMemoryBudgetStore(),
    val observer: BudgetObserver = BudgetObserver.None,
)

// Gated types in a constructor signature. A consumer holds and passes them; it cannot implement them here.
private class Holder(val permit: Permit?, val budget: CallBudget, val store: BudgetStore, val observer: BudgetObserver)

/**
 * Level 1 - the permanent consumer of the budget's gated types, compiled in `commonTest` **without any opt-in**. It names `CallBudget`, `Permit`,
 * `RateSignalsReader`, `BudgetStore` and `BudgetObserver` in a function, a class and a constructor signature; takes `CallBudget.Unlimited`,
 * `RateSignalsReader.Standard`, `InMemoryBudgetStore()` and `BudgetObserver.None` as defaults; builds every value type and every `BudgetEvent` leaf;
 * builds `WindowedCallBudget(windows) { }` and uses it as a `CallBudget`; matches a `BudgetEvent` with a `when` that keeps an `else`; and calls
 * `RetryAfter.parse`. It cannot implement or subclass any gated type, which is the point.
 */
class CallBudgetConsumerGatingTest {

    // Gated types in a function signature.
    private fun describe(budget: CallBudget, permit: Permit?, reader: RateSignalsReader, store: BudgetStore, observer: BudgetObserver): String =
        "$budget ${permit?.cost ?: 0} $reader $store $observer"

    @Test
    fun `the supplied objects are usable as defaults without the opt-in`() {
        val (budget, reader) = budgetOf()
        assertSame(CallBudget.Unlimited, budget)
        assertSame(RateSignalsReader.Standard, reader)
        val seams = Seams()
        assertSame(BudgetObserver.None, seams.observer)
        assertEquals("InMemoryBudgetStore", seams.store.toString())
        assertEquals("CallBudget.Unlimited 0 RateSignalsReader.Standard InMemoryBudgetStore BudgetObserver.None", describe(seams.budget, null, seams.reader, seams.store, seams.observer))
        assertSame(seams.budget, Holder(null, seams.budget, seams.store, seams.observer).budget)
    }

    @Test
    fun `every value type is constructible`() {
        val lane = Lane("own")
        val plain = RateWindow("minute", 60, 1.minutes)
        val configured = RateWindow("day", 1_000, 1.minutes * 60 * 24) { margin = 100; hard = true }
        assertEquals(60, plain.limit)
        assertEquals(900, configured.limit)
        assertTrue(configured.hard)
        val signals = RateSignals { throttled = true; retryAfter = 30.seconds; reading("minute", WindowReading(5, 20.seconds)) }
        assertTrue(signals.throttled)
        assertFalse(RateSignals.None.throttled)
        assertEquals(5, WindowReading(5).remaining)
        assertEquals(null, WindowReading(5).resetIn)
        val windows = listOf(WindowSnapshot("day", 0L, 3))
        assertEquals(1, BudgetSnapshot(1_000L, windows, 0L).version)
        assertEquals(1, BudgetSnapshot(1, 1_000L, windows, 0L).version)
        assertEquals(0, BudgetSnapshot.Empty.windows.size)
        val utilisation = BudgetUtilisation(listOf(WindowUtilisation("minute", 1, 0, 60, 60, 0L, 60_000L, false, 0.5)), Duration.ZERO, 0)
        assertEquals(59, utilisation.windows[0].remaining)
        assertEquals(Lane.Interactive, Lane("Interactive"))
        assertEquals("Lane(own)", lane.toString())
    }

    @Test
    fun `every event leaf and both vocabularies are constructible and a when keeps an else`() {
        val events: List<BudgetEvent> = listOf(
            BudgetEvent.Granted(Lane.Tracker, 1, Duration.ZERO),
            BudgetEvent.Refused(Lane.Tracker, 1, 30.seconds, ExhaustedReason.Window, "minute"),
            BudgetEvent.Paused(1_000L, 30.seconds, PauseSource.RetryAfter),
            BudgetEvent.HardStop("day", 2_000L),
            BudgetEvent.Reconciled("minute", 20, 19),
            BudgetEvent.StoreFailed(StoreOperation.Save, IllegalStateException("down")),
            BudgetEvent.Restored(1, 0, false),
        )
        assertEquals(7, events.size)
        assertEquals(4, listOf(PauseSource.RetryAfter, PauseSource.ResetIn, PauseSource.Boundary, PauseSource.Snapshot).size)
        assertEquals(2, listOf(StoreOperation.Load, StoreOperation.Save).size)
        val kinds = events.map { event ->
            when (event) {
                is BudgetEvent.Granted -> "granted"
                is BudgetEvent.Refused -> "refused"
                is BudgetEvent.Paused -> "paused"
                else -> "other"
            }
        }
        assertEquals(listOf("granted", "refused", "paused", "other", "other", "other", "other"), kinds)
    }

    @Test
    fun `the supplied budget is built with the factory and used as a CallBudget`() {
        val budget: CallBudget = WindowedCallBudget(listOf(RateWindow("minute", 60, 1.minutes) { margin = 10 }, RateWindow("day", 3_000, 1.minutes * 60 * 24))) { maxQueued = 200 }
        val windowed = budget as WindowedCallBudget
        assertEquals(50, windowed.maxBurst)
        assertEquals("WindowedCallBudget(windows=[minute, day], closed=false)", budget.toString())
        budget.close()                                              // non-suspending: called from a plain function
        budget.close()
        assertEquals("WindowedCallBudget(windows=[minute, day], closed=true)", budget.toString())
        val plain: CallBudget = WindowedCallBudget(listOf(RateWindow("minute", 60, 1.minutes)))     // without the lambda: every property at its default
        assertEquals("WindowedCallBudget(windows=[minute], closed=false)", plain.toString())
        plain.close()
    }

    @Test
    fun `RetryAfter parse is callable`() {
        assertEquals(120.seconds, RetryAfter.parse("120", 0L).getOrThrow())
        assertEquals(Duration.ZERO, RetryAfter.parse("Thu, 01 Jan 1970 00:00:00 GMT", 1_000L, 1_000L).getOrThrow())
        assertTrue(RetryAfter.parse("soon", 0L).isFailure)
    }
}
