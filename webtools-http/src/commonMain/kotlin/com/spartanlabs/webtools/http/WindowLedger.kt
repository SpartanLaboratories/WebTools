package com.spartanlabs.webtools.http

internal const val MAX_CELLS_PER_WINDOW: Int = 16

/** One window's cells and its arithmetic. [reserves] is each lane's reserved units in this window. */
internal class WindowLedger(val spec: RateWindow, val guardMillis: Long, private val reserves: Map<Lane, Int>) {
    val name: String get() = spec.name
    val limit: Int = spec.limit
    val published: Int = spec.published
    val hard: Boolean = spec.hard
    val periodMillis: Long = spec.period.inWholeMilliseconds
    var stopRaisedFor: Long? = null                              // the index a HardStop was last raised for

    private val cells = HashMap<Long, LedgerCell>()
    private var last: Long? = null                               // the highest wall index the ledger has been advanced to

    fun indexAt(wall: Long): Long = wall.floorDiv(periodMillis)
    fun startOf(index: Long): Long = index * periodMillis
    fun cell(index: Long): LedgerCell? = cells[index]
    fun cellOrCreate(index: Long): LedgerCell = cells.getOrPut(index) { LedgerCell() }
    fun cellIndices(): List<Long> = cells.keys.sorted()

    /** The cells a grant at [wall] charges: its own, the previous when within the guard after a boundary, the next when within the guard before one. */
    fun chargeIndices(wall: Long): List<Long> {
        val k = indexAt(wall)
        val start = startOf(k)
        val indices = ArrayList<Long>(3)
        indices += k
        if (wall - start < guardMillis) indices += k - 1
        if (start + periodMillis - wall <= guardMillis) indices += k + 1
        return indices
    }

    /** The first instant after [wall] at which a cell this window charges could have changed for the better. */
    fun nextCritical(wall: Long): Long {
        val start = startOf(indexAt(wall))
        return if (guardMillis > 0 && wall - start < guardMillis) start + guardMillis else start + periodMillis
    }

    /** The most [lane] can ever have in one period: the limit less every other lane's reserve. */
    fun capacityFor(lane: Lane): Int = limit - reserves.entries.sumOf { (m, r) -> if (m == lane) 0 else r }

    fun fits(lane: Lane, cost: Int, wall: Long): Boolean = chargeIndices(wall).all { index ->
        val cell = cells[index]
        (cell?.used ?: 0L) + cost + otherUnusedReserve(lane, cell) <= limit
    }

    // The floor held back for every other lane: its reserve less what it has used in this cell, never lent to [lane].
    private fun otherUnusedReserve(lane: Lane, cell: LedgerCell?): Long =
        reserves.entries.sumOf { (m, r) -> if (m == lane) 0L else maxOf(0, r - (cell?.laneUsed(m) ?: 0)).toLong() }

    /**
     * Moves the ledger to [wall]: copies on a backward step across a boundary, prunes cells below the previous index (never one holding planned units) and caps the cell count.
     *
     * @return whether the cells were copied (the shell then re-plans every waiter).
     */
    fun advance(wall: Long): Boolean {
        val k = indexAt(wall)
        val before = last
        var folded = false
        if (before != null && k < before) {
            // The wall went backwards across a boundary: copy, do not move. A window that guards a bill must never under-count,
            // whichever reading was wrong, so the higher cells stay for when the wall reaches them again.
            val above = cells.entries.filter { it.key > k }.sumOf { it.value.effective.toLong() }
            if (above > 0) {
                val cell = cellOrCreate(k)
                val room = (limit.toLong() - cell.effective).coerceAtLeast(0L)
                val target = cell.effective.toLong() + minOf(above, room)
                cell.base = (target - cell.granted).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                folded = above > 0 && room > 0
            }
            last = k                                                                                  // once per decrease
        } else if (before == null || k > before) {
            last = k
        }
        cells.entries.removeAll { it.key < k - 1 && it.value.planned == 0 }
        // The cap spares planned cells: a waiter's reservation must survive until it is granted or taken back.
        while (cells.size > MAX_CELLS_PER_WINDOW) {
            val farthest = cells.entries.filter { it.key > k && it.value.planned == 0 }.maxByOrNull { it.key } ?: break
            cells.remove(farthest.key)
        }
        return folded
    }
}
