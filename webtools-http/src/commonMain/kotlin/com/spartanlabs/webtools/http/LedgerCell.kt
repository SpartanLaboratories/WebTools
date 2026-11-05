package com.spartanlabs.webtools.http

/** The counts of one window in one period. Mutable state under the budget's lock; no method suspends. */
internal class LedgerCell {
    var base: Int = 0          // units restored from a store (or copied by the fold), attributed to no lane
    var granted: Int = 0       // units granted in this process
    var floor: Int = 0         // the highest count a server reading raised
    var planned: Int = 0       // units reserved for waiters
    var persisted: Int = 0     // the count believed to be in the store (hard windows)
    val grantedByLane: MutableMap<Lane, Int> = HashMap()
    val plannedByLane: MutableMap<Lane, Int> = HashMap()

    /** The count the provider has seen or will have seen: a server reading only ever raises it. */
    val effective: Int get() = maxOf((base.toLong() + granted).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), floor)

    /** What the slot search compares with the limit. */
    val used: Long get() = effective.toLong() + planned

    fun laneUsed(lane: Lane): Int = (grantedByLane[lane] ?: 0) + (plannedByLane[lane] ?: 0)
}

/** Adds [delta] to the lane's count, removing the entry at zero. */
internal fun MutableMap<Lane, Int>.addUnits(lane: Lane, delta: Int) {
    val next = (this[lane] ?: 0) + delta
    if (next <= 0) remove(lane) else this[lane] = next
}

/** One cell a grant charges: window position in the ledger, cell index, and whether it is the grant's own period (as opposed to a guard neighbour). */
internal class Charge(val window: Int, val index: Long, val primary: Boolean)
