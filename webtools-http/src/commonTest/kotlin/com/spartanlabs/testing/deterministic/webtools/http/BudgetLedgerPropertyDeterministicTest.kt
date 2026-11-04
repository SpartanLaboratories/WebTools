package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.DAY_MS
import com.spartanlabs.testing.support.webtools.http.day
import com.spartanlabs.testing.support.webtools.http.ledgerOf
import com.spartanlabs.testing.support.webtools.http.minute
import com.spartanlabs.testing.support.webtools.http.nowAt
import com.spartanlabs.webtools.http.BudgetLedger
import com.spartanlabs.webtools.http.BudgetSnapshot
import com.spartanlabs.webtools.http.Charge
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.LedgerNow
import com.spartanlabs.webtools.http.Plan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Level 4a - a seeded property test over `BudgetLedger` (`Random(7)`; a fixed 3,000 steps per property, since common code reads no environment variable to raise
 * the count). Property 1: a 12-per-minute window (published 15, margin 3) with a 2 s guard and a hard day window of 100, lanes
 * `Interactive` (share 0.25), `Tracker` and `Batch`; time advances to the next arrival or the earliest pending target; arrivals of
 * random lane and cost 1 to 3 are granted now or reserved, due reservations are committed at their target, 10 % of pending ones are
 * cancelled and 20 % of permits are released (each followed by the re-plan the budget makes). After every step: no cell's `used`
 * exceeds its limit; within a lane, planned targets never decrease in arrival order; provider-side overspend never happens (each
 * grant counted in the cell of `grantInstant + skew` for a random skew in `[-guard, +guard]`); and at the end, after cancelling
 * every pending reservation and releasing every permit, no cell holds granted or planned units. Property 2: write-ahead never
 * under-counts - a hard window written through `writeAheadSnapshot`/`markPersisted`, crashed at a random step and restored into
 * a fresh ledger, restores a current-cell count at least the true count, at most `true + persistAhead - 1` and at most the limit.
 */
class BudgetLedgerPropertyDeterministicTest {

    private val seed = 7
    private val steps: Int = 3_000
    private val guardMillis = 2_000L
    private val lanes = listOf(Lane.Interactive, Lane.Tracker, Lane("Batch"))
    private val start = 1_767_225_600_000L + 37_000L                                   // 2026-01-01T00:00:37Z: not on a boundary

    private class Pending(val seq: Long, val lane: Lane, val cost: Int, var plan: Plan)
    private class Granted(val lane: Lane, val cost: Int, val charges: List<Charge>, val instant: Long, val skew: Long)

    private class Simulation(val ledger: BudgetLedger, val random: Random, val guardMillis: Long) {
        var wall: Long = 0L
        var nextSeq = 0L
        val pending = ArrayList<Pending>()                                              // arrival order
        val granted = ArrayList<Granted>()
        val now: LedgerNow get() = nowAt(wall)

        private fun tailOf(lane: Lane, before: Long): Plan? = pending.lastOrNull { it.lane == lane && it.seq < before }?.plan

        fun arrive(lane: Lane, cost: Int) {
            if (ledger.capacityProblem(lane, cost) != null) return
            val plan = ledger.plan(lane, cost, now, tailOf(lane, Long.MAX_VALUE))
            if (ledger.waitMillis(plan, now) == 0L && pending.none { it.lane == lane }) {
                grant(lane, cost, plan.charges)
            } else {
                ledger.reserve(plan.charges, lane, cost)
                pending += Pending(nextSeq++, lane, cost, plan)
            }
        }

        private fun grant(lane: Lane, cost: Int, charges: List<Charge>) {
            ledger.grantDirect(charges, lane, cost)
            granted += Granted(lane, cost, charges, wall, random.nextLong(-guardMillis, guardMillis + 1))
        }

        /** Commits every reservation whose target has come, in arrival order (the waiter grants itself at its slot). */
        fun commitDue() {
            val due = pending.filter { it.plan.targetWall <= wall }
            due.forEach { p ->
                ledger.commit(p.plan.charges, p.lane, p.cost)
                granted += Granted(p.lane, p.cost, p.plan.charges, wall, random.nextLong(-guardMillis, guardMillis + 1))
                pending.remove(p)
            }
        }

        /** The budget's re-plan: takes back every reservation from [fromSeq] on and plans them again in arrival order. */
        fun replan(fromSeq: Long) {
            val again = pending.filter { it.seq >= fromSeq }
            again.forEach { ledger.unreserve(it.plan.charges, it.lane, it.cost) }
            again.forEach { p ->
                p.plan = ledger.plan(p.lane, p.cost, now, tailOf(p.lane, p.seq))
                ledger.reserve(p.plan.charges, p.lane, p.cost)
            }
        }

        fun cancel(p: Pending) {
            ledger.unreserve(p.plan.charges, p.lane, p.cost)
            pending.remove(p)
            replan(p.seq)
        }

        fun release(g: Granted) {
            ledger.refundGranted(g.charges, g.lane, g.cost)
            granted.remove(g)
            replan(0L)
        }
    }

    private fun assertInvariants(sim: Simulation, step: Int) {
        val label = "seed $seed step $step wall ${sim.wall}"
        sim.ledger.windows.forEach { w ->
            w.cellIndices().forEach { index ->
                val cell = w.cell(index)!!
                assertTrue(cell.used <= w.limit, "$label: ${w.name} cell $index used ${cell.used} above the limit ${w.limit}")
                assertTrue(cell.planned >= 0 && cell.granted >= 0, "$label: ${w.name} cell $index has a negative count")
            }
        }
        lanes.forEach { lane ->
            val targets = sim.pending.filter { it.lane == lane }.map { it.plan.targetWall }
            assertEquals(targets, targets.sorted(), "$label: lane ${lane.name} targets are not in arrival order")
        }
        sim.ledger.windows.forEachIndexed { position, w ->
            val providerCounts = HashMap<Long, Int>()
            sim.granted.forEach { g -> providerCounts.merge(w.indexAt(g.instant + g.skew), g.cost, Int::plus) }
            providerCounts.forEach { (index, count) ->
                assertTrue(count <= w.limit, "$label: the provider counts $count in ${w.name} cell $index (limit ${w.limit}); position $position")
            }
        }
    }

    @Test
    fun `property 1 - no cell overspends, lanes stay in arrival order, the provider never sees an overspend, and everything refunds`() {
        val random = Random(seed)
        val ledger = ledgerOf(minute(12, margin = 3), day(100), shares = mapOf(Lane.Interactive to 0.25), guardMillis = guardMillis)
        val sim = Simulation(ledger, random, guardMillis)
        sim.wall = start
        ledger.advance(sim.now)
        var nextArrival = start
        repeat(steps) { step ->
            val earliestTarget = sim.pending.minOfOrNull { it.plan.targetWall }
            if (earliestTarget != null && earliestTarget <= nextArrival) {
                sim.wall = maxOf(sim.wall, earliestTarget)
                ledger.advance(sim.now)
                sim.commitDue()
            } else {
                sim.wall = maxOf(sim.wall, nextArrival)
                ledger.advance(sim.now)
                sim.commitDue()
                sim.arrive(lanes[random.nextInt(lanes.size)], random.nextInt(1, 4))
                nextArrival = sim.wall + random.nextLong(0L, 3_001L)
            }
            sim.pending.toList().forEach { if (random.nextInt(10) == 0) sim.cancel(it) }
            sim.granted.toList().forEach { if (random.nextInt(5) == 0) sim.release(it) }
            sim.commitDue()
            assertInvariants(sim, step)
        }
        assertTrue(sim.granted.size + sim.pending.size > 0, "the simulation did nothing")
        sim.pending.toList().forEach { sim.cancel(it) }
        sim.granted.toList().forEach { sim.release(it) }
        ledger.windows.forEach { w ->
            w.cellIndices().forEach { index ->
                val cell = w.cell(index)!!
                assertEquals(0, cell.granted, "seed $seed: ${w.name} cell $index still holds granted units")
                assertEquals(0, cell.planned, "seed $seed: ${w.name} cell $index still holds planned units")
                assertTrue(cell.grantedByLane.isEmpty() && cell.plannedByLane.isEmpty(), "seed $seed: ${w.name} cell $index keeps lane entries")
            }
        }
    }

    @Test
    fun `property 2 - a write-ahead store restores at least the true count and at most persistAhead minus one above it`() {
        val random = Random(seed)
        val limit = 30
        repeat(steps) { step ->
            val persistAhead = listOf(1, 3, 10)[random.nextInt(3)]
            val ledger = ledgerOf(day(limit))
            var stored: BudgetSnapshot = BudgetSnapshot.Empty
            val wall = start + random.nextLong(0L, DAY_MS / 2)
            val now = nowAt(wall)
            ledger.advance(now)
            val grants = random.nextInt(1, limit + 1)
            val crashAfter = random.nextInt(0, grants + 1)
            var trueCount = 0
            for (g in 1..grants) {
                val plan = ledger.plan(Lane.Tracker, 1, now, null)
                if (ledger.waitMillis(plan, now) != 0L) break
                if (ledger.walNeeded(plan.charges, 1, now)) {
                    val snapshot = ledger.writeAheadSnapshot(plan.charges, 1, persistAhead, now)
                    stored = snapshot
                    ledger.markPersisted(snapshot)
                }
                ledger.grantDirect(plan.charges, Lane.Tracker, 1)
                trueCount++
                if (g == crashAfter) break
            }
            val fresh = ledgerOf(day(limit))
            fresh.advance(now)
            fresh.restore(stored, now, 5 * 60_000L)
            val k = fresh.windows[0].indexAt(wall)
            val restored = fresh.windows[0].cell(k)?.effective ?: 0
            val label = "seed $seed step $step persistAhead $persistAhead true $trueCount restored $restored"
            assertTrue(restored >= trueCount, "$label: the restore under-counts")
            assertTrue(restored <= trueCount + persistAhead - 1, "$label: the restore over-counts beyond persistAhead - 1")
            assertTrue(restored <= limit, "$label: the restore exceeds the limit")
            assertEquals(ledger.windows[0].cell(k)?.effective ?: 0, trueCount, "$label: the true count is the ledger's")
        }
    }
}
