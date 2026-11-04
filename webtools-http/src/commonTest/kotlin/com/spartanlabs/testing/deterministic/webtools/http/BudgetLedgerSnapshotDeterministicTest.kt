package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.DAY_MS
import com.spartanlabs.testing.support.webtools.http.MINUTE_MS
import com.spartanlabs.testing.support.webtools.http.day
import com.spartanlabs.testing.support.webtools.http.ledgerOf
import com.spartanlabs.testing.support.webtools.http.minute
import com.spartanlabs.testing.support.webtools.http.nowAt
import com.spartanlabs.webtools.http.BudgetSnapshot
import com.spartanlabs.webtools.http.Charge
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.Pause
import com.spartanlabs.webtools.http.WindowSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 4a - the ledger's persistence arithmetic: `walNeeded` (hard windows only, cells at or above the current index only,
 * `effective + cost > persisted`); `writeAheadSnapshot` (`min(limit, effective + cost + persistAhead - 1)` for the charged cells
 * that need it, `max(persisted, effective)` for the rest, the stamp and the pause); `exactSnapshot` (a row of zero for a cell whose
 * persisted mark is above its count); `markPersisted` (exactly what was written, a lower value after a flush included); and every
 * row of the restore table (dropped stale, unknown name, unaligned
 * start, unknown version; the current index additive with the grants since the start and a floor; a future index kept; skew
 * ignored keeps the counts; the pause restored, capped and merged; the skew edge at exactly `maxRestoreSkew`).
 */
class BudgetLedgerSnapshotDeterministicTest {

    private val tracker = Lane.Tracker
    private val skew = 5 * MINUTE_MS

    @Test
    fun `walNeeded - hard windows, current or future cells, effective plus cost above the persisted mark`() {
        val ledger = ledgerOf(minute(10), day(30))
        val now = nowAt(10_000L)
        val charges = ledger.plan(tracker, 1, now, null).charges
        assertTrue(ledger.walNeeded(charges, 1, now))
        ledger.markPersisted(BudgetSnapshot(now.wallMillis, listOf(WindowSnapshot("day", 0L, 5)), 0L))
        assertFalse(ledger.walNeeded(charges, 1, now))
        assertFalse(ledger.walNeeded(charges, 5, now))
        assertTrue(ledger.walNeeded(charges, 6, now))
        ledger.grantDirect(charges, tracker, 5)
        assertTrue(ledger.walNeeded(charges, 1, now))
        assertFalse(ledgerOf(minute(10)).walNeeded(ledgerOf(minute(10)).plan(tracker, 1, now, null).charges, 1, now))
        assertFalse(ledger.walNeeded(listOf(Charge(1, -1L, false)), 1, now))              // a cell below the current index is never persisted
        assertTrue(ledger.walNeeded(listOf(Charge(1, 1L, false)), 1, now))                // a future cell is
    }

    @Test
    fun `writeAheadSnapshot writes ahead for the charged cells that need it and keeps the rest`() {
        val ledger = ledgerOf(minute(10), day(30))
        val now = nowAt(10_000L, 1_000L)
        val charges = ledger.plan(tracker, 1, now, null).charges
        val first = ledger.writeAheadSnapshot(charges, 1, 3, now)
        assertEquals(listOf(WindowSnapshot("day", 0L, 3)), first.windows)
        assertEquals(now.wallMillis, first.takenAtEpochMillis)
        assertEquals(0L, first.pausedUntilEpochMillis)
        assertEquals(BudgetSnapshot.CURRENT_VERSION, first.version)

        ledger.windows[1].cellOrCreate(1).base = 4                                       // a restored future cell: kept as max(persisted, effective)
        ledger.pause = Pause.of(now, 30_000L)
        val second = ledger.writeAheadSnapshot(charges, 1, 3, now)
        assertEquals(setOf(WindowSnapshot("day", 0L, 3), WindowSnapshot("day", DAY_MS, 4)), second.windows.toSet())
        assertEquals(2, second.windows.size)
        assertEquals(40_000L, second.pausedUntilEpochMillis)

        ledger.markPersisted(BudgetSnapshot(now.wallMillis, listOf(WindowSnapshot("day", 0L, 10)), 0L))
        ledger.grantDirect(charges, tracker, 2)
        val notNeeded = ledger.writeAheadSnapshot(charges, 1, 3, now)
        assertEquals(10, notNeeded.windows.single { it.windowStartEpochMillis == 0L }.used)      // 2 + 1 is within the mark: max(persisted, effective)

        val nearly = ledgerOf(day(30))
        nearly.grantDirect(charges.filter { it.window == 1 }.map { Charge(0, it.index, it.primary) }, tracker, 29)
        val capped = nearly.writeAheadSnapshot(listOf(Charge(0, 0L, true)), 1, 10, now)
        assertEquals(listOf(WindowSnapshot("day", 0L, 30)), capped.windows)
    }

    @Test
    fun `exactSnapshot writes the effective count of every hard cell at or above the current index`() {
        val ledger = ledgerOf(minute(10), day(30))
        val today = nowAt(DAY_MS + 10_000L, 1_000L)
        ledger.windows[1].cellOrCreate(0).granted = 7                                  // yesterday: not written
        ledger.windows[1].cellOrCreate(1).granted = 3
        ledger.windows[1].cellOrCreate(2).base = 4
        ledger.windows[1].cellOrCreate(3)                                              // empty: not written
        ledger.windows[0].cellOrCreate(1_440).granted = 9                              // not hard: not written
        ledger.pause = Pause.of(today, 5_000L)
        val snapshot = ledger.exactSnapshot(today)
        assertEquals(listOf(WindowSnapshot("day", DAY_MS, 3), WindowSnapshot("day", 2 * DAY_MS, 4)), snapshot.windows)
        assertEquals(DAY_MS + 10_000L, snapshot.takenAtEpochMillis)
        assertEquals(DAY_MS + 15_000L, snapshot.pausedUntilEpochMillis)
        assertTrue(ledgerOf(minute(10)).exactSnapshot(today).windows.isEmpty())
    }

    @Test
    fun `exactSnapshot writes a zero row for a cell whose persisted mark is above its count and markPersisted lowers the mark`() {
        val ledger = ledgerOf(day(30))
        val now = nowAt(10_000L)
        val charges = ledger.plan(tracker, 1, now, null).charges
        val cell = ledger.windows[0].cellOrCreate(0)
        cell.persisted = 10                                                              // a write-ahead save whose every grant was then released
        assertEquals(0, cell.effective)
        assertFalse(ledger.walNeeded(charges, 1, now))
        val snapshot = ledger.exactSnapshot(now)
        assertEquals(listOf(WindowSnapshot("day", 0L, 0)), snapshot.windows)
        ledger.markPersisted(snapshot)
        assertEquals(0, cell.persisted)
        assertTrue(ledger.walNeeded(charges, 1, now))                                    // the next grant is written ahead again
        assertTrue(ledger.exactSnapshot(now).windows.isEmpty())                          // nothing counted and nothing persisted: no row
    }

    @Test
    fun `markPersisted assigns exactly what was written, creating the cell if needed`() {
        val ledger = ledgerOf(day(30))
        ledger.markPersisted(BudgetSnapshot(0L, listOf(WindowSnapshot("day", 0L, 10), WindowSnapshot("other", 0L, 3)), 0L))
        assertEquals(10, ledger.windows[0].cell(0)!!.persisted)
        ledger.markPersisted(BudgetSnapshot(0L, listOf(WindowSnapshot("day", 0L, 4)), 0L))
        assertEquals(4, ledger.windows[0].cell(0)!!.persisted)                          // a flush writes the lower exact count
        ledger.markPersisted(BudgetSnapshot(0L, listOf(WindowSnapshot("day", DAY_MS, 2)), 0L))
        assertEquals(2, ledger.windows[0].cell(1)!!.persisted)
        assertEquals(0, ledger.windows[0].cell(1)!!.effective)
    }

    //region restore
    private fun snapshot(vararg windows: WindowSnapshot, takenAt: Long = 0L, pausedUntil: Long = 0L, version: Int = BudgetSnapshot.CURRENT_VERSION): BudgetSnapshot =
        BudgetSnapshot(version, takenAt, windows.toList(), pausedUntil)

    @Test
    fun `a stale, unknown, unaligned or unknown-version window is dropped`() {
        val now = nowAt(DAY_MS + 10_000L)
        val ledger = ledgerOf(day(30))
        ledger.advance(now)
        val stale = ledger.restore(snapshot(WindowSnapshot("day", 0L, 5), takenAt = now.wallMillis), now, skew)
        assertEquals(0, stale.restored)
        assertEquals(1, stale.dropped)
        assertFalse(stale.ignoredForSkew)
        assertNull(stale.pause)
        assertNull(ledger.windows[0].cell(0))
        val unknown = ledger.restore(snapshot(WindowSnapshot("other", DAY_MS, 5), takenAt = now.wallMillis), now, skew)
        assertEquals(0, unknown.restored)
        assertEquals(1, unknown.dropped)
        val unaligned = ledger.restore(snapshot(WindowSnapshot("day", DAY_MS + 1, 5), takenAt = now.wallMillis), now, skew)
        assertEquals(1, unaligned.dropped)
        assertNull(ledger.windows[0].cell(1))
        val version = ledger.restore(snapshot(WindowSnapshot("day", DAY_MS, 5), WindowSnapshot("day", 2 * DAY_MS, 1), takenAt = now.wallMillis, version = 2), now, skew)
        assertEquals(0, version.restored)
        assertEquals(2, version.dropped)
        assertNull(ledger.windows[0].cell(1))
        assertNull(ledger.pause)
    }

    @Test
    fun `the current index merges additively with the grants since the start and a floor stays a floor`() {
        val now = nowAt(10_000L)
        val ledger = ledgerOf(day(30))
        ledger.advance(now)
        val charges = ledger.plan(tracker, 1, now, null).charges
        ledger.grantDirect(charges, tracker, 2)
        val outcome = ledger.restore(snapshot(WindowSnapshot("day", 0L, 3), takenAt = now.wallMillis), now, skew)
        assertEquals(1, outcome.restored)
        assertEquals(0, outcome.dropped)
        val cell = ledger.windows[0].cell(0)!!
        assertEquals(3, cell.base)
        assertEquals(5, cell.effective)
        assertEquals(3, cell.persisted)
        cell.floor = 7
        assertEquals(7, cell.effective)
        ledger.restore(snapshot(WindowSnapshot("day", 0L, 4), takenAt = now.wallMillis), now, skew)
        assertEquals(4, cell.base)
        assertEquals(7, cell.effective)
        ledger.restore(snapshot(WindowSnapshot("day", 0L, 40), takenAt = now.wallMillis), now, skew)
        assertEquals(30, cell.base)                                                      // clamped to the limit
        assertEquals(30, cell.persisted)
    }

    @Test
    fun `a future index is kept as a cell at its own index`() {
        val now = nowAt(DAY_MS + 10_000L)
        val ledger = ledgerOf(day(30))
        ledger.advance(now)
        val outcome = ledger.restore(snapshot(WindowSnapshot("day", 2 * DAY_MS, 4), takenAt = now.wallMillis), now, skew)
        assertEquals(1, outcome.restored)
        assertEquals(4, ledger.windows[0].cell(2)!!.base)
        assertNull(ledger.windows[0].cell(1))
        assertEquals(0, ledger.utilisation(now, 0).windows[0].used)
        assertEquals(4, ledger.utilisation(nowAt(2 * DAY_MS + 1), 0).windows[0].used)
    }

    @Test
    fun `a stamp ahead by more than maxRestoreSkew ignores the stamp and the pause but keeps the counts`() {
        val now = nowAt(10_000L, 1_000L)
        val ledger = ledgerOf(day(30))
        ledger.advance(now)
        val edge = ledger.restore(snapshot(WindowSnapshot("day", 0L, 3), takenAt = now.wallMillis + skew, pausedUntil = now.wallMillis + 20_000L), now, skew)
        assertFalse(edge.ignoredForSkew)
        assertNotNull(edge.pause)
        assertEquals(1, edge.restored)
        ledger.pause = null
        val skewed = ledger.restore(snapshot(WindowSnapshot("day", 0L, 5), takenAt = now.wallMillis + skew + 1, pausedUntil = now.wallMillis + 20_000L), now, skew)
        assertTrue(skewed.ignoredForSkew)
        assertNull(skewed.pause)
        assertNull(ledger.pause)
        assertEquals(1, skewed.restored)
        assertEquals(5, ledger.windows[0].cell(0)!!.base)
        val behind = ledger.restore(snapshot(takenAt = now.wallMillis - DAY_MS, pausedUntil = now.wallMillis + 1_000L), now, skew)
        assertFalse(behind.ignoredForSkew)                                               // a stamp behind the wall is never skew
        assertNotNull(behind.pause)
    }

    @Test
    fun `a saved pause is restored as its remaining length, capped by maxPause and merged with the pause in force`() {
        val now = nowAt(10_000L, 1_000L)
        val ledger = ledgerOf(day(30), maxPauseMillis = 30_000L)
        ledger.advance(now)
        val restored = ledger.restore(snapshot(takenAt = now.wallMillis, pausedUntil = now.wallMillis + 40_000L), now, skew)
        assertEquals(0, restored.restored)
        val pause = assertNotNull(restored.pause)
        assertEquals(31.seconds, pause.untilElapsed)
        assertEquals(40_000L, pause.untilEpochMillis)
        assertEquals(pause, ledger.pause)
        val past = ledger.restore(snapshot(takenAt = now.wallMillis, pausedUntil = now.wallMillis), now, skew)
        assertNull(past.pause)
        assertEquals(pause, ledger.pause)
        ledger.pause = Pause.of(now, 50_000L)
        val merged = ledger.restore(snapshot(takenAt = now.wallMillis, pausedUntil = now.wallMillis + 20_000L), now, skew)
        assertEquals(21.seconds, merged.pause!!.untilElapsed)
        assertEquals(51.seconds, ledger.pause!!.untilElapsed)                            // the pause in force is never shortened
        assertEquals(60_000L, ledger.pause!!.untilEpochMillis)
        assertNull(ledger.restore(snapshot(takenAt = now.wallMillis, pausedUntil = 0L), now, skew).pause)
    }
    //endregion
}
