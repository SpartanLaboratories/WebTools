package com.spartanlabs.webtools.http

import kotlin.time.Duration

/**
 * A reading of the budget: each rate window's current period ([windows], in declaration order), how long the budget is paused
 * ([pausedFor], zero for none) and how many calls wait ([waiting]).
 *
 * **Stable Core.** Public constructor so an implementer of the port can build one. Equality is on the three properties;
 * `toString()` prints them.
 *
 * @param windows each window's current period, in declaration order.
 * @param pausedFor how long the budget is paused; zero for none; not negative.
 * @param waiting how many calls wait for a slot; not negative.
 * @throws IllegalArgumentException if [pausedFor] is negative or [waiting] is below 0.
 */
public class BudgetUtilisation(
    windows: List<WindowUtilisation>,
    /** How long the budget is paused; zero for none. */
    public val pausedFor: Duration,
    /** How many calls wait for a slot. */
    public val waiting: Int,
) {
    /** The windows in declaration order, a read-only copy. */
    public val windows: List<WindowUtilisation> = windows.toList()

    init {
        require(!pausedFor.isNegative()) { "pausedFor must not be negative" }
        require(waiting >= 0) { "waiting must not be negative" }
    }

    override fun equals(other: Any?): Boolean =
        other is BudgetUtilisation && other.windows == windows && other.pausedFor == pausedFor && other.waiting == waiting

    override fun hashCode(): Int = (windows.hashCode() * 31 + pausedFor.hashCode()) * 31 + waiting
    override fun toString(): String = "BudgetUtilisation(windows=$windows, pausedFor=$pausedFor, waiting=$waiting)"
}
