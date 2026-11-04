package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.DAY_MS
import com.spartanlabs.testing.support.webtools.http.MINUTE_MS
import com.spartanlabs.testing.support.webtools.http.day
import com.spartanlabs.testing.support.webtools.http.ledgerOf
import com.spartanlabs.testing.support.webtools.http.minute
import com.spartanlabs.testing.support.webtools.http.nowAt
import com.spartanlabs.webtools.http.BudgetEvent
import com.spartanlabs.webtools.http.Charge
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.LedgerNow
import com.spartanlabs.webtools.http.Pause
import com.spartanlabs.webtools.http.PauseHint
import com.spartanlabs.webtools.http.PauseSource
import com.spartanlabs.webtools.http.Plan
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.WindowReading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 4a - tables over `BudgetLedger`: the slot search (an empty ledger grants now; a full cell plans at the next boundary, or at
 * boundary plus guard; lane tails; the pause as a lower bound; reason and window attribution; the maximum rule over two blocking
 * windows; lane floors shrinking as the other lane uses its reserve; `capacityProblem`; `isStale`), the reservation and grant
 * bookkeeping, `reconcile`, `pauseHint` and `extendPause`, `raiseHardStops` once per window and period, `advance` expiring the
 * pause, and the `utilisation` fields.
 */
class BudgetLedgerPlanDeterministicTest {

    private val tracker = Lane.Tracker
    private val interactive = Lane.Interactive

    private fun ownCharge(plan: Plan): Charge = plan.charges.single { it.primary }

    //region the slot search
    @Test
    fun `an empty ledger grants now`() {
        val ledger = ledgerOf(minute(2))
        val now = nowAt(10_000L)
        val plan = ledger.plan(tracker, 1, now, null)
        assertEquals(10_000L, plan.targetWall)
        assertEquals(ExhaustedReason.Window, plan.reason)
        assertNull(plan.window)
        assertEquals(1, plan.charges.size)
        assertEquals(0L, ownCharge(plan).index)
        assertEquals(0, ownCharge(plan).window)
        assertEquals(0L, ledger.waitMillis(plan, now))
    }

    @Test
    fun `a full cell plans at the next boundary, or at the boundary plus the guard`() {
        val plain = ledgerOf(minute(2))
        val now = nowAt(10_000L)
        plain.grantDirect(plain.plan(tracker, 2, now, null).charges, tracker, 2)
        val next = plain.plan(tracker, 1, now, null)
        assertEquals(MINUTE_MS, next.targetWall)
        assertEquals(ExhaustedReason.Window, next.reason)
        assertEquals("minute", next.window)
        assertEquals(1L, ownCharge(next).index)
        assertEquals(50_000L, plain.waitMillis(next, now))

        val guarded = ledgerOf(minute(2), guardMillis = 1_000L)
        guarded.grantDirect(guarded.plan(tracker, 2, now, null).charges, tracker, 2)
        val afterGuard = guarded.plan(tracker, 1, now, null)
        assertEquals(MINUTE_MS + 1_000L, afterGuard.targetWall)
        assertEquals("minute", afterGuard.window)
        assertEquals(listOf(1L), afterGuard.charges.map { it.index })
    }

    @Test
    fun `a follower's slot is at least its lane's tail and inherits the tail's attribution`() {
        val ledger = ledgerOf(minute(2))
        val now = nowAt(10_000L)
        val tail = Plan(2 * MINUTE_MS, ExhaustedReason.Paused, null, emptyList())
        val follower = ledger.plan(tracker, 1, now, tail)
        assertEquals(2 * MINUTE_MS, follower.targetWall)
        assertEquals(ExhaustedReason.Paused, follower.reason)
        assertNull(follower.window)
        assertEquals(2L, ownCharge(follower).index)
        val named = ledger.plan(tracker, 1, now, Plan(MINUTE_MS, ExhaustedReason.Window, "minute", emptyList()))
        assertEquals(MINUTE_MS, named.targetWall)
        assertEquals("minute", named.window)
        val pastTail = ledger.plan(tracker, 1, now, Plan(5_000L, ExhaustedReason.Window, "minute", emptyList()))
        assertEquals(10_000L, pastTail.targetWall)
        assertNull(pastTail.window)
    }

    @Test
    fun `the pause is a lower bound with the Paused reason, unless the tail is later`() {
        val ledger = ledgerOf(minute(2))
        val now = nowAt(10_000L)
        ledger.pause = Pause.of(now, 30_000L)
        val paused = ledger.plan(tracker, 1, now, null)
        assertEquals(40_000L, paused.targetWall)
        assertEquals(ExhaustedReason.Paused, paused.reason)
        assertNull(paused.window)
        val laterTail = ledger.plan(tracker, 1, now, Plan(50_000L, ExhaustedReason.Window, "minute", emptyList()))
        assertEquals(50_000L, laterTail.targetWall)
        assertEquals(ExhaustedReason.Window, laterTail.reason)
        assertEquals("minute", laterTail.window)
        val earlierTail = ledger.plan(tracker, 1, now, Plan(20_000L, ExhaustedReason.Window, "minute", emptyList()))
        assertEquals(40_000L, earlierTail.targetWall)
        assertEquals(ExhaustedReason.Paused, earlierTail.reason)
        assertNull(earlierTail.window)
        // A pause that is over at the reading plays no part once advance has expired it.
        ledger.advance(nowAt(40_000L, 30_000L))
        assertNull(ledger.pause)
        assertEquals(40_000L, ledger.plan(tracker, 1, nowAt(40_000L, 30_000L), null).targetWall)
    }

    @Test
    fun `a sub-millisecond pause remainder rounds up to the next millisecond`() {
        val ledger = ledgerOf(minute(2))
        ledger.pause = Pause.of(nowAt(10_000L), 1_000L)
        val plan = ledger.plan(tracker, 1, LedgerNow(10_000L, 999.milliseconds + 500.microseconds), null)
        assertEquals(10_001L, plan.targetWall)
        assertEquals(ExhaustedReason.Paused, plan.reason)
        assertEquals(10_001L, ledger.plan(tracker, 1, nowAt(10_000L, 999L), null).targetWall)
    }

    @Test
    fun `two blocking windows step by the maximum and name the later blocker`() {
        val ledger = ledgerOf(minute(1), RateWindow("five", 1, 5.minutes))
        val now = nowAt(10_000L)
        ledger.grantDirect(ledger.plan(tracker, 1, now, null).charges, tracker, 1)
        val plan = ledger.plan(tracker, 1, now, null)
        assertEquals(5 * MINUTE_MS, plan.targetWall)
        assertEquals(ExhaustedReason.Window, plan.reason)
        assertEquals("five", plan.window)
        assertEquals(listOf(5L, 1L), plan.charges.map { it.index })
    }

    @Test
    fun `lane floors - the other lane's unused reserve shrinks as it uses it`() {
        val ledger = ledgerOf(minute(10), shares = mapOf(interactive to 0.2))
        val now = nowAt(10_000L)
        val w = ledger.windows[0]
        assertTrue(w.fits(tracker, 8, now.wallMillis))
        assertFalse(w.fits(tracker, 9, now.wallMillis))
        assertTrue(w.fits(interactive, 10, now.wallMillis))
        assertEquals(10_000L, ledger.plan(tracker, 8, now, null).targetWall)
        ledger.grantDirect(ledger.plan(interactive, 1, now, null).charges, interactive, 1)
        assertTrue(w.fits(tracker, 8, now.wallMillis))
        assertFalse(w.fits(tracker, 9, now.wallMillis))
        ledger.grantDirect(ledger.plan(interactive, 1, now, null).charges, interactive, 1)
        assertTrue(w.fits(tracker, 8, now.wallMillis))
        assertFalse(w.fits(tracker, 9, now.wallMillis))
        ledger.grantDirect(ledger.plan(interactive, 1, now, null).charges, interactive, 1)
        assertTrue(w.fits(tracker, 7, now.wallMillis))
        assertFalse(w.fits(tracker, 8, now.wallMillis))
        ledger.grantDirect(ledger.plan(tracker, 7, now, null).charges, tracker, 7)
        assertFalse(w.fits(tracker, 1, now.wallMillis))
        assertFalse(w.fits(interactive, 1, now.wallMillis))
    }

    @Test
    fun `capacityProblem names the first window the cost can never fit`() {
        val ledger = ledgerOf(minute(10), day(100), shares = mapOf(interactive to 0.2))
        assertNull(ledger.capacityProblem(tracker, 8))
        assertEquals("minute", ledger.capacityProblem(tracker, 9)?.name)
        assertNull(ledger.capacityProblem(interactive, 10))
        assertEquals("minute", ledger.capacityProblem(interactive, 11)?.name)
        assertEquals("minute", ledger.capacityProblem(tracker, 81)?.name)
        assertEquals("day", ledgerOf(minute(200), day(100), shares = mapOf(interactive to 0.2)).capacityProblem(tracker, 81)?.name)
        assertEquals(2, ledger.windows[0].capacityFor(interactive) - ledger.windows[0].capacityFor(tracker))
    }

    @Test
    fun `isStale when the primary charge is below its window's current index`() {
        val ledger = ledgerOf(minute(2), guardMillis = 1_000L)
        val plan = ledger.plan(tracker, 1, nowAt(10_000L), null)
        assertFalse(ledger.isStale(plan, nowAt(10_000L)))
        assertFalse(ledger.isStale(plan, nowAt(MINUTE_MS - 1)))
        assertTrue(ledger.isStale(plan, nowAt(MINUTE_MS)))
        assertTrue(ledger.isStale(plan, nowAt(MINUTE_MS + 10_000L)))
        val nearBoundary = ledger.plan(tracker, 1, nowAt(MINUTE_MS + 500L), null)
        assertEquals(listOf(1L, 0L), nearBoundary.charges.map { it.index })
        assertFalse(ledger.isStale(nearBoundary, nowAt(MINUTE_MS + 1_000L)))                 // the guard neighbour below the current index alone does not
        assertTrue(ledger.isStale(nearBoundary, nowAt(2 * MINUTE_MS)))
    }
    //endregion

    //region bookkeeping
    @Test
    fun `reserve, commit, unreserve, grantDirect and refundGranted keep the counts and the lane maps`() {
        val ledger = ledgerOf(minute(10))
        val now = nowAt(10_000L)
        val charges = ledger.plan(tracker, 2, now, null).charges
        val cell = ledger.windows[0].cellOrCreate(0)
        ledger.reserve(charges, tracker, 2)
        assertEquals(2, cell.planned)
        assertEquals(2, cell.plannedByLane[tracker])
        assertEquals(2L, cell.used)
        assertEquals(0, cell.effective)
        ledger.commit(charges, tracker, 2)
        assertEquals(0, cell.planned)
        assertNull(cell.plannedByLane[tracker])
        assertEquals(2, cell.granted)
        assertEquals(2, cell.grantedByLane[tracker])
        assertEquals(2, cell.laneUsed(tracker))
        ledger.reserve(charges, interactive, 3)
        ledger.unreserve(charges, interactive, 3)
        assertEquals(0, cell.planned)
        assertTrue(cell.plannedByLane.isEmpty())
        ledger.grantDirect(charges, interactive, 4)
        assertEquals(6, cell.granted)
        assertEquals(4, cell.grantedByLane[interactive])
        ledger.refundGranted(charges, interactive, 4)
        assertEquals(2, cell.granted)
        assertNull(cell.grantedByLane[interactive])
        ledger.refundGranted(charges, tracker, 5)
        assertEquals(0, cell.granted)
        assertTrue(cell.grantedByLane.isEmpty())
        ledger.unreserve(charges, tracker, 1)
        assertEquals(0, cell.planned)
    }

    @Test
    fun `a refund skips a pruned cell and never lowers a floor`() {
        val ledger = ledgerOf(minute(10))
        val charges = ledger.plan(tracker, 1, nowAt(0L), null).charges
        ledger.refundGranted(charges, tracker, 1)                                   // no cell yet: nothing happens
        assertNull(ledger.windows[0].cell(0))
        ledger.unreserve(charges, tracker, 1)
        assertNull(ledger.windows[0].cell(0))
        ledger.grantDirect(charges, tracker, 3)
        val cell = ledger.windows[0].cell(0)!!
        cell.floor = 5
        assertEquals(5, cell.effective)
        ledger.refundGranted(charges, tracker, 3)
        assertEquals(0, cell.granted)
        assertEquals(5, cell.effective)
    }
    //endregion

    //region signals
    @Test
    fun `reconcile raises only, clamps, and ignores a rolled window and an unknown name`() {
        val ledger = ledgerOf(minute(50, margin = 10))
        val now = nowAt(1_000L)
        val charges = ledger.plan(tracker, 1, now, null).charges
        ledger.grantDirect(charges, tracker, 1)
        val raised = ledger.reconcile(charges, "minute", 40, now)
        assertNotNull(raised)
        assertEquals("minute", raised.window)
        assertEquals(20, raised.serverUsed)
        assertEquals(19, raised.by)
        assertEquals(20, ledger.windows[0].cell(0)!!.effective)
        assertNull(ledger.reconcile(charges, "minute", 59, now))
        assertEquals(20, ledger.windows[0].cell(0)!!.effective)
        assertNull(ledger.reconcile(charges, "minute", 0, nowAt(MINUTE_MS)))             // the window rolled since the grant
        assertNull(ledger.reconcile(charges, "unknown", 0, now))
        val clamped = ledger.reconcile(charges, "minute", -5, now)
        assertEquals(60, clamped!!.serverUsed)
        assertEquals(40, clamped.by)
        assertEquals(60, ledger.windows[0].cell(0)!!.effective)
        assertNull(ledger.reconcile(charges, "minute", 100, now))
        ledger.refundGranted(charges, tracker, 1)
        assertEquals(60, ledger.windows[0].cell(0)!!.effective)
    }

    @Test
    fun `reconcile reads only the permit's own cell`() {
        val ledger = ledgerOf(minute(10), guardMillis = 1_000L)
        val now = nowAt(MINUTE_MS + 500L)
        val charges = ledger.plan(tracker, 1, now, null).charges                      // cells 1 (own) and 0
        ledger.grantDirect(charges, tracker, 1)
        val raised = ledger.reconcile(charges, "minute", 4, now)
        assertEquals(6, raised!!.serverUsed)
        assertEquals(6, ledger.windows[0].cell(1)!!.effective)
        assertEquals(1, ledger.windows[0].cell(0)!!.effective)
        val pruned = ledgerOf(minute(10))
        val old = pruned.plan(tracker, 1, nowAt(0L), null).charges
        assertNull(pruned.reconcile(old, "minute", 0, nowAt(0L)))                       // no cell exists for the own index: reads as rolled
    }

    @Test
    fun `pauseHint - Retry-After, resetIn, the boundary fallback, the maximum and the cap`() {
        val ledger = ledgerOf(minute(2), day(100), maxPauseMillis = 20_000L)
        val now = nowAt(10_000L)
        assertNull(ledger.pauseHint(RateSignals { retryAfter = 30.seconds }, now))
        assertNull(ledger.pauseHint(RateSignals.None, now))
        val retry = ledger.pauseHint(RateSignals { throttled = true; retryAfter = 5.seconds }, now)!!
        assertEquals(5_000L, retry.lengthMillis)
        assertEquals(PauseSource.RetryAfter, retry.source)
        val reset = ledger.pauseHint(RateSignals { throttled = true; reading("minute", WindowReading(0, 7.seconds)) }, now)!!
        assertEquals(7_000L, reset.lengthMillis)
        assertEquals(PauseSource.ResetIn, reset.source)
        val max = ledger.pauseHint(RateSignals { throttled = true; retryAfter = 5.seconds; reading("minute", WindowReading(-1, 7.seconds)) }, now)!!
        assertEquals(7_000L, max.lengthMillis)
        assertEquals(PauseSource.ResetIn, max.source)
        val capped = ledger.pauseHint(RateSignals { throttled = true; retryAfter = 30.seconds }, now)!!
        assertEquals(20_000L, capped.lengthMillis)
        assertNull(ledger.pauseHint(RateSignals { throttled = true; retryAfter = Duration.ZERO }, now))
        assertNull(ledger.pauseHint(RateSignals { throttled = true; retryAfter = Duration.ZERO; reading("day", WindowReading(0)) }, now))
        val roundedUp = ledger.pauseHint(RateSignals { throttled = true; retryAfter = 1.seconds + 1.seconds / 3 }, now)!!
        assertEquals(1_334L, roundedUp.lengthMillis)
    }

    @Test
    fun `pauseHint - the boundary of the exhausted windows or of the shortest window, plus the guard`() {
        val plain = ledgerOf(minute(2), day(100))
        val now = nowAt(10_000L)
        val shortest = plain.pauseHint(RateSignals { throttled = true }, now)!!
        assertEquals(50_000L, shortest.lengthMillis)
        assertEquals(PauseSource.Boundary, shortest.source)
        val dayExhausted = plain.pauseHint(RateSignals { throttled = true; reading("day", WindowReading(0)) }, now)!!
        assertEquals(DAY_MS - 10_000L, dayExhausted.lengthMillis)
        assertEquals(PauseSource.Boundary, dayExhausted.source)
        val resetWithRemaining = plain.pauseHint(RateSignals { throttled = true; reading("minute", WindowReading(1, 7.seconds)) }, now)!!
        assertEquals(50_000L, resetWithRemaining.lengthMillis)                          // a reading with something remaining is not a hint
        val unknown = plain.pauseHint(RateSignals { throttled = true; reading("other", WindowReading(0, 7.seconds)) }, now)!!
        assertEquals(50_000L, unknown.lengthMillis)
        assertEquals(PauseSource.Boundary, unknown.source)
        plain.grantDirect(plain.plan(tracker, 2, now, null).charges, tracker, 2)       // the minute's current cell is full
        assertEquals(50_000L, plain.pauseHint(RateSignals { throttled = true }, now)!!.lengthMillis)
        val guarded = ledgerOf(minute(2), guardMillis = 1_000L)
        assertEquals(51_000L, guarded.pauseHint(RateSignals { throttled = true }, now)!!.lengthMillis)
        val capped = ledgerOf(minute(2), day(100), maxPauseMillis = 30_000L)
        assertEquals(30_000L, capped.pauseHint(RateSignals { throttled = true; reading("day", WindowReading(0)) }, now)!!.lengthMillis)
    }

    @Test
    fun `extendPause returns the pause in force when extended and null for a shorter hint`() {
        val ledger = ledgerOf(minute(2))
        val now = nowAt(10_000L)
        val first = ledger.extendPause(PauseHint(30_000L, PauseSource.RetryAfter), now)
        assertNotNull(first)
        assertEquals(30.seconds, first.untilElapsed)
        assertEquals(40_000L, first.untilEpochMillis)
        assertSame(first, ledger.pause)
        assertNull(ledger.extendPause(PauseHint(10_000L, PauseSource.RetryAfter), now))
        assertNull(ledger.extendPause(PauseHint(30_000L, PauseSource.RetryAfter), now))
        assertSame(first, ledger.pause)
        val longer = ledger.extendPause(PauseHint(60_000L, PauseSource.ResetIn), now)
        assertEquals(60.seconds, longer!!.untilElapsed)
        assertEquals(70_000L, longer.untilEpochMillis)
        val later = ledger.extendPause(PauseHint(60_000L, PauseSource.Boundary), nowAt(20_000L, 10_000L))
        assertEquals(70.seconds, later!!.untilElapsed)
        assertEquals(80_000L, later.untilEpochMillis)
    }

    @Test
    fun `raiseHardStops once per hard window and period`() {
        val soft = ledgerOf(minute(2))
        soft.grantDirect(soft.plan(tracker, 2, nowAt(10_000L), null).charges, tracker, 2)
        assertTrue(soft.raiseHardStops(nowAt(10_000L)).isEmpty())                       // the minute is full, but it is not hard
        val ledger = ledgerOf(minute(10), day(5))
        val now = nowAt(10_000L)
        ledger.grantDirect(ledger.plan(tracker, 2, now, null).charges, tracker, 2)
        assertTrue(ledger.raiseHardStops(now).isEmpty())                               // 2 of 5
        ledger.grantDirect(ledger.plan(tracker, 3, nowAt(MINUTE_MS), null).charges, tracker, 3)
        val stops = ledger.raiseHardStops(nowAt(MINUTE_MS))
        assertEquals(1, stops.size)
        val stop = assertIs<BudgetEvent.HardStop>(stops.single())
        assertEquals("day", stop.window)
        assertEquals(DAY_MS, stop.resetsAtEpochMillis)
        assertTrue(ledger.raiseHardStops(nowAt(MINUTE_MS)).isEmpty())
        assertTrue(ledger.raiseHardStops(nowAt(2 * MINUTE_MS)).isEmpty())
        val tomorrow = nowAt(DAY_MS + 10_000L)
        ledger.advance(tomorrow)
        assertTrue(ledger.raiseHardStops(tomorrow).isEmpty())
        ledger.grantDirect(ledger.plan(tracker, 5, tomorrow, null).charges, tracker, 5)
        val again = assertIs<BudgetEvent.HardStop>(ledger.raiseHardStops(tomorrow).single())
        assertEquals(2 * DAY_MS, again.resetsAtEpochMillis)
    }

    @Test
    fun `advance expires the pause and reports a fold in any window`() {
        val ledger = ledgerOf(minute(2), day(5))
        ledger.pause = Pause.of(nowAt(0L), 1_000L)
        assertFalse(ledger.advance(nowAt(500L, 500L)))
        assertNotNull(ledger.pause)
        assertFalse(ledger.advance(nowAt(1_000L, 1_000L)))
        assertNull(ledger.pause)
        ledger.grantDirect(ledger.plan(tracker, 1, nowAt(1_000L), null).charges, tracker, 1)
        ledger.advance(nowAt(MINUTE_MS))
        ledger.grantDirect(ledger.plan(tracker, 1, nowAt(MINUTE_MS), null).charges, tracker, 1)
        assertTrue(ledger.advance(nowAt(0L)))                                           // the minute window folds, the day window does not
        assertEquals(2, ledger.windows[0].cell(0)!!.effective)
        assertEquals(2, ledger.windows[1].cell(0)!!.effective)
    }
    //endregion

    @Test
    fun `utilisation reports each window's current period, the pause and the waiting count`() {
        val ledger = ledgerOf(minute(50, margin = 10), day(100))
        val now = nowAt(10_000L)
        val empty = ledger.utilisation(now, 0)
        assertEquals(2, empty.windows.size)
        val minute = empty.windows[0]
        assertEquals("minute", minute.name)
        assertEquals(0, minute.used)
        assertEquals(0, minute.queued)
        assertEquals(50, minute.limit)
        assertEquals(60, minute.published)
        assertEquals(0L, minute.windowStartEpochMillis)
        assertEquals(MINUTE_MS, minute.resetsAtEpochMillis)
        assertFalse(minute.hard)
        assertEquals(10_000.0 / 60_000.0, minute.elapsedFraction)
        assertTrue(empty.windows[1].hard)
        assertEquals(DAY_MS, empty.windows[1].resetsAtEpochMillis)
        assertEquals(Duration.ZERO, empty.pausedFor)
        assertEquals(0, empty.waiting)

        val charges = ledger.plan(tracker, 3, now, null).charges
        ledger.grantDirect(charges, tracker, 3)
        ledger.reserve(charges, interactive, 2)
        ledger.pause = Pause.of(now, 30_000L)
        val busy = ledger.utilisation(nowAt(20_000L, 10_000L), 4)
        assertEquals(3, busy.windows[0].used)
        assertEquals(2, busy.windows[0].queued)
        assertEquals(45, busy.windows[0].remaining)
        assertEquals(3, busy.windows[1].used)
        assertEquals(20.seconds, busy.pausedFor)
        assertEquals(4, busy.waiting)
    }
}
