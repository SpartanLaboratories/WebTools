package com.spartanlabs.webtools.http

import kotlin.math.ceil
import kotlin.time.Duration

/** Absorbs the excess of a floating-point product (`0.07 * 100 = 7.000000000000001`) before the `ceil`, so an exact share is not rounded one unit up. */
internal const val SHARE_EPSILON: Double = 1e-9

/** A share as reserved units of a window: `ceil`, at least 1 for a positive share; the epsilon keeps `0.07 * 100` from rounding to 8. */
internal fun reservedUnits(share: Double, limit: Int): Int = maxOf(1, ceil(share * limit - SHARE_EPSILON).toInt())

/** Where and when a call is planned to be granted, and the cells it charges. [reason] and [window] say what set the slot. */
internal class Plan(val targetWall: Long, val reason: ExhaustedReason, val window: String?, val charges: List<Charge>)

/** What `reconcile` reports: the window, the count the server reported and how much the local count rose. */
internal class Raised(val window: String, val serverUsed: Int, val by: Int)

/** One offer for the pause and its source. */
internal class PauseHint(val lengthMillis: Long, val source: PauseSource)

/** What `restore` merged, dropped and ignored for skew, and the pause it set (`null` when it set none). */
internal class RestoreOutcome(val restored: Int, val dropped: Int, val ignoredForSkew: Boolean, val pause: Pause?)

/**
 * The budget's counts and arithmetic: cells per window, the slot search, reservations, reconciliation, the pause, the snapshots and the restore. Pure and synchronous (no coroutine, no
 * clock read of its own: every method takes the [LedgerNow] it works at); the shell serialises all access under its lock.
 */
internal class BudgetLedger(windows: List<RateWindow>, laneShares: Map<Lane, Double>, val guardMillis: Long, private val maxPauseMillis: Long) {
    val windows: List<WindowLedger> = windows.map { w -> WindowLedger(w, guardMillis, laneShares.mapValues { (_, share) -> reservedUnits(share, w.limit) }) }
    var pause: Pause? = null
    val maxBurst: Int get() = windows.minOf { it.limit }

    /** Moves every window to [now] and expires the pause; returns whether any window copied its cells. */
    fun advance(now: LedgerNow): Boolean {
        val folded = windows.fold(false) { acc, w -> w.advance(now.wallMillis) || acc }      // every window is advanced: no short-circuit
        pause = pause?.takeUnless { it.isOver(now) }
        return folded
    }

    /** The first window in which [cost] on [lane] can never fit, or `null`. */
    fun capacityProblem(lane: Lane, cost: Int): WindowLedger? = windows.firstOrNull { cost > it.capacityFor(lane) }

    /** The earliest slot for [cost] on [lane], never before [tail] (the lane's last waiter), the pause or [now]. Pure over the ledger's state. */
    fun plan(lane: Lane, cost: Int, now: LedgerNow, tail: Plan?): Plan {
        var t = now.wallMillis
        var reason = ExhaustedReason.Window
        var window: String? = null
        if (tail != null && tail.targetWall > t) { t = tail.targetWall; reason = tail.reason; window = tail.window }
        val pausedMillis = pause?.remaining(now)?.ceilToMillis() ?: 0L
        if (pausedMillis > 0 && now.wallMillis + pausedMillis > t) { t = now.wallMillis + pausedMillis; reason = ExhaustedReason.Paused; window = null }
        while (true) {
            // Step by the maximum over the blocking windows: each blocker is infeasible throughout [t, its next critical instant),
            // so no feasible instant is skipped, and the loop ends because a call within capacity fits an empty cell.
            var next = t
            var blocker: String? = null
            for (w in windows) {
                if (!w.fits(lane, cost, t)) {
                    val critical = w.nextCritical(t)
                    if (critical > next) { next = critical; blocker = w.name }
                }
            }
            if (next == t) break
            t = next; reason = ExhaustedReason.Window; window = blocker
        }
        return Plan(t, reason, window, chargesAt(t))
    }

    fun waitMillis(plan: Plan, now: LedgerNow): Long = (plan.targetWall - now.wallMillis).coerceAtLeast(0L)

    /**
     * Whether [plan] reserves a period the wall clock has left behind: its primary charge in some window is below that window's current index. Granting it would charge a period that
     * has ended, so the shell re-plans every waiter instead.
     */
    fun isStale(plan: Plan, now: LedgerNow): Boolean =
        plan.charges.any { c -> c.primary && c.index < windows[c.window].indexAt(now.wallMillis) }

    private fun chargesAt(wall: Long): List<Charge> = windows.flatMapIndexed { position, w ->
        val own = w.indexAt(wall)
        w.chargeIndices(wall).map { Charge(position, it, it == own) }
    }

    //region reservations and grants
    fun reserve(charges: List<Charge>, lane: Lane, cost: Int): Unit = charges.forEach { c ->
        windows[c.window].cellOrCreate(c.index).let { it.planned += cost; it.plannedByLane.addUnits(lane, cost) }
    }

    fun unreserve(charges: List<Charge>, lane: Lane, cost: Int): Unit = charges.forEach { c ->
        windows[c.window].cell(c.index)?.let { it.planned = (it.planned - cost).coerceAtLeast(0); it.plannedByLane.addUnits(lane, -cost) }
    }

    fun commit(charges: List<Charge>, lane: Lane, cost: Int): Unit = charges.forEach { c ->
        windows[c.window].cellOrCreate(c.index).let {
            it.planned = (it.planned - cost).coerceAtLeast(0); it.plannedByLane.addUnits(lane, -cost)
            it.granted += cost; it.grantedByLane.addUnits(lane, cost)
        }
    }

    fun grantDirect(charges: List<Charge>, lane: Lane, cost: Int): Unit = charges.forEach { c ->
        windows[c.window].cellOrCreate(c.index).let { it.granted += cost; it.grantedByLane.addUnits(lane, cost) }
    }

    fun refundGranted(charges: List<Charge>, lane: Lane, cost: Int): Unit = charges.forEach { c ->
        windows[c.window].cell(c.index)?.let { it.granted = (it.granted - cost).coerceAtLeast(0); it.grantedByLane.addUnits(lane, -cost) }
    }
    //endregion

    //region signals
    /** Raises the permit's own cell to what the server counted; `null` when the window is unknown, has rolled, or the reading raises nothing. */
    fun reconcile(charges: List<Charge>, windowName: String, remaining: Int, now: LedgerNow): Raised? {
        val position = windows.indexOfFirst { it.name == windowName }
        if (position < 0) return null
        val w = windows[position]
        val own = charges.firstOrNull { it.window == position && it.primary } ?: return null
        if (own.index != w.indexAt(now.wallMillis)) return null                                     // a reading for a rolled window is ignored
        val cell = w.cell(own.index) ?: return null                                                 // a cell the cap or pruning removed reads as rolled
        // Measured against published, not the enforced limit: the server counts against its own documented limit; the margin is local and the server knows nothing of it.
        val serverUsed = (w.published.toLong() - remaining).coerceIn(0L, w.published.toLong()).toInt()
        val before = cell.effective
        if (serverUsed <= before) return null                                                       // a reading only raises
        cell.floor = serverUsed
        return Raised(w.name, serverUsed, cell.effective - before)
    }

    /** The longest pause the signals ask for, capped by `maxPause`; `null` when not throttled or the hint is zero. */
    fun pauseHint(signals: RateSignals, now: LedgerNow): PauseHint? {
        if (!signals.throttled) return null
        val offers = ArrayList<PauseHint>()
        fun offer(lengthMillis: Long, source: PauseSource) {
            val capped = minOf(lengthMillis, maxPauseMillis)
            if (capped > 0) offers += PauseHint(capped, source)
        }
        val retryAfter = signals.retryAfter
        if (retryAfter != null) offer(retryAfter.ceilToMillis(), PauseSource.RetryAfter)
        val resets = signals.readings.entries.mapNotNull { (name, r) -> r.resetIn?.takeIf { r.remaining <= 0 && windows.any { it.name == name } } }
        resets.forEach { offer(it.ceilToMillis(), PauseSource.ResetIn) }
        if (retryAfter == null && resets.isEmpty()) {                                                // no timing hint: the next boundary of the exhausted windows
            val exhausted = windows.filter { w ->
                (signals.readings[w.name]?.remaining ?: 1) <= 0 || (w.cell(w.indexAt(now.wallMillis))?.used ?: 0L) >= w.limit
            }
            val targets = exhausted.ifEmpty { listOf(windows.minBy { it.periodMillis }) }
            offer(targets.maxOf { w -> w.startOf(w.indexAt(now.wallMillis) + 1) - now.wallMillis + guardMillis }, PauseSource.Boundary)
        }
        return offers.maxByOrNull { it.lengthMillis }                                                // the first of equal lengths wins
    }

    /** Merges [hint] into the pause in force; returns the pause now in force if it was extended, else `null`. */
    fun extendPause(hint: PauseHint, now: LedgerNow): Pause? {
        val candidate = Pause.of(now, hint.lengthMillis)
        val current = pause
        val merged = current?.extendedTo(candidate) ?: candidate
        if (current != null && merged.untilElapsed <= current.untilElapsed && merged.untilEpochMillis <= current.untilEpochMillis) return null
        pause = merged
        return merged
    }

    /** A [BudgetEvent.HardStop] for each hard window whose current cell is full and has not raised one for this period yet. */
    fun raiseHardStops(now: LedgerNow): List<BudgetEvent> = windows.filter { it.hard }.mapNotNull { w ->
        val k = w.indexAt(now.wallMillis)
        val cell = w.cell(k)
        if (cell != null && cell.used >= w.limit && w.stopRaisedFor != k) {
            w.stopRaisedFor = k
            BudgetEvent.HardStop(w.name, w.startOf(k + 1))
        } else null
    }
    //endregion

    //region persistence
    /** Whether a grant of [cost] over [charges] would pass a hard window's persisted mark (cells below the current index are never persisted). */
    fun walNeeded(charges: List<Charge>, cost: Int, now: LedgerNow): Boolean = charges.any { c ->
        val w = windows[c.window]
        w.hard && c.index >= w.indexAt(now.wallMillis) && (w.cell(c.index)?.effective ?: 0).toLong() + cost > (w.cell(c.index)?.persisted ?: 0)
    }

    /** The snapshot a write-ahead save writes: `min(limit, effective + cost + persistAhead - 1)` for the charged cells that need it, `max(persisted, effective)` for the rest. */
    fun writeAheadSnapshot(charges: List<Charge>, cost: Int, persistAhead: Int, now: LedgerNow): BudgetSnapshot {
        val entries = windows.filter { it.hard }.flatMap { w ->
            val current = w.indexAt(now.wallMillis)
            val charged = charges.filter { windows[it.window] === w }.map { it.index }.toSet()
            (w.cellIndices() + charged).filter { it >= current }.distinct().mapNotNull { index ->
                val effective = w.cell(index)?.effective ?: 0
                val persisted = w.cell(index)?.persisted ?: 0
                val target =
                    if (index in charged && effective.toLong() + cost > persisted) {
                        maxOf(effective.toLong(), minOf(w.limit.toLong(), effective.toLong() + cost + persistAhead - 1)).toInt()
                    } else maxOf(persisted, effective)
                if (target > 0) WindowSnapshot(w.name, w.startOf(index), target) else null
            }
        }
        return BudgetSnapshot(now.wallMillis, entries, pause?.untilEpochMillis ?: 0L)
    }

    /**
     * The snapshot `flush()` writes: the exact effective count of every hard window cell at or above the current index. A cell whose count is zero is
     * still written when its persisted mark is above zero (a write-ahead save whose grants were all released): the row of zero replaces the stored count,
     * and [markPersisted] then lowers the mark, so the next grant is written ahead again instead of running unsaved under a mark the store no longer holds.
     */
    fun exactSnapshot(now: LedgerNow): BudgetSnapshot {
        val entries = windows.filter { it.hard }.flatMap { w ->
            val current = w.indexAt(now.wallMillis)
            w.cellIndices().filter { it >= current }.mapNotNull { index ->
                val cell = w.cell(index)
                val effective = cell?.effective ?: 0
                if (effective > 0 || (cell?.persisted ?: 0) > 0) WindowSnapshot(w.name, w.startOf(index), effective) else null
            }
        }
        return BudgetSnapshot(now.wallMillis, entries, pause?.untilEpochMillis ?: 0L)
    }

    /** After a successful save: each written cell's persisted mark is exactly what was written (creating the cell if the save came before the grant). */
    fun markPersisted(snapshot: BudgetSnapshot): Unit = snapshot.windows.forEach { ws ->
        val w = windows.firstOrNull { it.name == ws.window } ?: return@forEach
        w.cellOrCreate(w.indexAt(ws.windowStartEpochMillis)).persisted = ws.used      // a charged cell may not exist yet (a first grant saves before it is made)
    }

    /** Merges [snapshot] into the ledger. Call after [advance]. */
    fun restore(snapshot: BudgetSnapshot, now: LedgerNow, maxRestoreSkewMillis: Long): RestoreOutcome {
        val ignored = snapshot.takenAtEpochMillis > now.wallMillis && snapshot.takenAtEpochMillis - now.wallMillis > maxRestoreSkewMillis
        if (snapshot.version != BudgetSnapshot.CURRENT_VERSION) return RestoreOutcome(0, snapshot.windows.size, ignored, null)
        var restored = 0
        var dropped = 0
        for (ws in snapshot.windows) {
            val w = windows.firstOrNull { it.name == ws.window }
            if (w == null || ws.windowStartEpochMillis.mod(w.periodMillis) != 0L || ws.windowStartEpochMillis / w.periodMillis < w.indexAt(now.wallMillis)) {
                dropped++
                continue
            }
            // The division is exact: the mod check above passed. The stored count is added to the grants made since the start
            // (effective = max(saved + grants since start, floor)): a late restore after a store outage must not under-count.
            val cell = w.cellOrCreate(ws.windowStartEpochMillis / w.periodMillis)
            val saved = ws.used.coerceIn(0, w.limit)
            cell.base = maxOf(cell.base, saved)
            cell.persisted = maxOf(cell.persisted, saved)
            restored++
        }
        val restoredPause = if (!ignored && snapshot.pausedUntilEpochMillis > now.wallMillis) Pause.restored(now, snapshot.pausedUntilEpochMillis, maxPauseMillis) else null
        if (restoredPause != null) pause = pause?.extendedTo(restoredPause) ?: restoredPause
        return RestoreOutcome(restored, dropped, ignored, restoredPause)
    }
    //endregion

    fun utilisation(now: LedgerNow, waiting: Int): BudgetUtilisation = BudgetUtilisation(
        windows.map { w ->
            val k = w.indexAt(now.wallMillis)
            val cell = w.cell(k)
            val start = w.startOf(k)
            WindowUtilisation(
                w.name, cell?.effective ?: 0, cell?.planned ?: 0, w.limit, w.published, start, start + w.periodMillis, w.hard,
                (now.wallMillis - start).toDouble() / w.periodMillis,
            )
        },
        pause?.remaining(now) ?: Duration.ZERO,
        waiting,
    )
}
