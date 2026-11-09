package com.spartanlabs.webtools.http

/**
 * One window's current period. [used] is the count charged against the window this period, in cost units (a call of cost 3 adds 3), not calls: what was granted in this process, what
 * was restored from the store, any rise a server reading forced (see [BudgetEvent.Reconciled]) and any count a backward wall-clock step copied into the period; [queued] is the count reserved
 * for waiters; [remaining] = `max(0, limit - used - queued)`; [fraction] = `used / limit`. A consumer that wants to stretch polling
 * intervals needs the burn ratio, not the raw fraction: **worked example** — 40 of 50 at second 55 of a minute (`fraction` 0.8,
 * `elapsedFraction` 0.92) is slack, 40 of 50 at second 10 (0.8 over 0.17) is not; `fraction / elapsedFraction` is the projected
 * end-of-period usage, and the threshold is the consumer's policy.
 *
 * **Stable Core.** Public constructor so an implementer of the port can build one. Equality is on every property; `toString()`
 * prints them.
 *
 * @param name the window's name; not blank.
 * @param used the count charged against the window this period, in cost units; not negative.
 * @param queued the count reserved for waiters; not negative.
 * @param limit the enforced limit; at least 1.
 * @param published the provider's documented limit; at least [limit].
 * @param windowStartEpochMillis the start of the current period, in epoch milliseconds.
 * @param resetsAtEpochMillis the end of the current period, in epoch milliseconds.
 * @param hard whether the window is hard.
 * @param elapsedFraction how far the period has run, in `[0, 1]`.
 * @throws IllegalArgumentException if [name] is blank, [used] or [queued] is negative, [limit] is below 1, [published] is below
 *   [limit], or [elapsedFraction] is outside `[0, 1]` (`NaN` included).
 */
public class WindowUtilisation(
    /** The window's name. */
    public val name: String,
    /** The count charged against the window this period, in cost units. */
    public val used: Int,
    /** The count reserved for waiters. */
    public val queued: Int,
    /** The enforced limit. */
    public val limit: Int,
    /** The provider's documented limit. */
    public val published: Int,
    /** The start of the current period, in epoch milliseconds. */
    public val windowStartEpochMillis: Long,
    /** The end of the current period, in epoch milliseconds. */
    public val resetsAtEpochMillis: Long,
    /** Whether the window is hard. */
    public val hard: Boolean,
    /** How far the period has run, in `[0, 1]`. */
    public val elapsedFraction: Double,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(used >= 0) { "used must not be negative" }
        require(queued >= 0) { "queued must not be negative" }
        require(limit >= 1) { "limit must be at least 1" }
        require(published >= limit) { "published must be at least limit" }
        require(elapsedFraction in 0.0..1.0) { "elapsedFraction must be between 0 and 1" }   // a NaN is in no range
    }

    /** What is left for a call now: `max(0, limit - used - queued)`. */
    public val remaining: Int get() = (limit - used - queued).coerceAtLeast(0)

    /** The raw usage, `used / limit`; may exceed 1.0 when a server reading raised the count above the limit. */
    public val fraction: Double get() = used.toDouble() / limit

    /**
     * Two [WindowUtilisation]s are equal when their [name], [used], [queued], [limit], [published],
     * [windowStartEpochMillis], [resetsAtEpochMillis], [hard] and [elapsedFraction] are equal.
     */
    override fun equals(other: Any?): Boolean =
        other is WindowUtilisation && other.name == name && other.used == used && other.queued == queued && other.limit == limit &&
            other.published == published && other.windowStartEpochMillis == windowStartEpochMillis && other.resetsAtEpochMillis == resetsAtEpochMillis &&
            other.hard == hard && other.elapsedFraction == elapsedFraction

    /** Returns a hash code consistent with [equals]: it is computed from the same properties. */
    override fun hashCode(): Int =
        listOf(name, used, queued, limit, published, windowStartEpochMillis, resetsAtEpochMillis, hard, elapsedFraction).fold(0) { acc, v -> 31 * acc + v.hashCode() }

    /**
     * A diagnostic string for logs: [name], [used], [queued], [limit], [published], [windowStartEpochMillis],
     * [resetsAtEpochMillis], [hard] and [elapsedFraction]. Its format is not part of the API and may change.
     */
    override fun toString(): String =
        "WindowUtilisation(name=$name, used=$used, queued=$queued, limit=$limit, published=$published, windowStart=$windowStartEpochMillis, " +
            "resetsAt=$resetsAtEpochMillis, hard=$hard, elapsedFraction=$elapsedFraction)"
}
