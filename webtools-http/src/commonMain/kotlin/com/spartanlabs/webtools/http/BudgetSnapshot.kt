package com.spartanlabs.webtools.http

/**
 * What a [BudgetStore] keeps between runs: the counts of the budget's hard windows, the instant they were taken and the pause
 * in force. A plain value with a public constructor and no serializer: a store chooses its own encoding.
 *
 * **Stable Core.** Equality is on every property; `toString()` prints the version, the stamp, the number of windows and the
 * pause, never an encoding.
 *
 * @param version the layout; [CURRENT_VERSION] today. The budget ignores (and counts as dropped) the windows of a version it
 *   does not know. At least 1.
 * @param takenAtEpochMillis the wall clock when the snapshot was taken.
 * @param windows the counts, one [WindowSnapshot] per window start; several starts of one window are allowed.
 * @param pausedUntilEpochMillis the epoch millisecond the pause ends, or 0 for none. Not negative.
 * @throws IllegalArgumentException if [version] is below 1 or [pausedUntilEpochMillis] is negative.
 */
public class BudgetSnapshot(
    /** The layout; [CURRENT_VERSION] today. At least 1. */
    public val version: Int,
    /** The wall clock when the snapshot was taken, in epoch milliseconds. */
    public val takenAtEpochMillis: Long,
    windows: List<WindowSnapshot>,
    /** The epoch millisecond the pause ends, or 0 for none. Not negative. */
    public val pausedUntilEpochMillis: Long,
) {
    /**
     * The snapshot of [CURRENT_VERSION].
     *
     * @param takenAtEpochMillis the wall clock when the snapshot was taken.
     * @param windows the counts, one [WindowSnapshot] per window start.
     * @param pausedUntilEpochMillis the epoch millisecond the pause ends, or 0 for none. Not negative.
     * @throws IllegalArgumentException if [pausedUntilEpochMillis] is negative.
     */
    public constructor(takenAtEpochMillis: Long, windows: List<WindowSnapshot>, pausedUntilEpochMillis: Long) :
        this(CURRENT_VERSION, takenAtEpochMillis, windows, pausedUntilEpochMillis)

    /** The counts, a read-only copy. */
    public val windows: List<WindowSnapshot> = windows.toList()

    init {
        require(version >= 1) { "version must be at least 1" }
        require(pausedUntilEpochMillis >= 0) { "pausedUntilEpochMillis must not be negative" }
    }

    /**
     * Two [BudgetSnapshot]s are equal when their [version], [takenAtEpochMillis], [windows] and
     * [pausedUntilEpochMillis] are equal.
     */
    override fun equals(other: Any?): Boolean =
        other is BudgetSnapshot && other.version == version && other.takenAtEpochMillis == takenAtEpochMillis && other.windows == windows &&
            other.pausedUntilEpochMillis == pausedUntilEpochMillis

    /** Returns a hash code consistent with [equals]: it is computed from the same properties. */
    override fun hashCode(): Int = listOf(version, takenAtEpochMillis, windows, pausedUntilEpochMillis).fold(0) { acc, v -> 31 * acc + v.hashCode() }
    /**
     * A diagnostic string for logs: [version], [takenAtEpochMillis], the number of [windows] and
     * [pausedUntilEpochMillis]. Its format is not part of the API and may change.
     */
    override fun toString(): String = "BudgetSnapshot(version=$version, takenAt=$takenAtEpochMillis, windows=${windows.size}, pausedUntil=$pausedUntilEpochMillis)"

    /** The layout version and the empty snapshot. */
    public companion object {
        /** The layout this library writes and reads: 1. */
        public const val CURRENT_VERSION: Int = 1

        /** What a store returns when it holds nothing: no windows, no pause. */
        public val Empty: BudgetSnapshot = BudgetSnapshot(CURRENT_VERSION, 0L, emptyList(), 0L)
    }
}
