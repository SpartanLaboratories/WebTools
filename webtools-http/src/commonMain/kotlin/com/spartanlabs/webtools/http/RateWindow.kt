package com.spartanlabs.webtools.http

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * One provider rate window: [published] calls per [period], counted in fixed windows that start at multiples of [period] since
 * the Unix epoch (UTC), so they meet the provider's own wall-clock boundaries. The budget enforces [limit] = [published] −
 * [margin]. A [hard] window refuses until its boundary with no overdraft and is persisted write-ahead when the budget has a
 * store (it guards a provider that bills per period). Build one with the factory
 * `RateWindow(name, published, period) { margin = …; hard = … }`.
 *
 * **Stable Core.** Equality is on every property; `toString()` prints them.
 */
public class RateWindow internal constructor(
    /** The window's name; readings are matched to it. Not blank; distinct within a budget. */
    public val name: String,
    /** The calls per period the provider documents. At least 1. */
    public val published: Int,
    /** The window's length: positive, finite, a whole number of milliseconds. */
    public val period: Duration,
    /** Calls held back below [published]. In `0 until published`. */
    public val margin: Int,
    /** Whether the window refuses until its boundary and is persisted write-ahead. */
    public val hard: Boolean,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(published >= 1) { "published must be at least 1" }
        require(margin in 0 until published) { "margin must be at least 0 and below published" }
        require(period.isFinite() && period.isPositive() && period.inWholeMilliseconds.milliseconds == period) {
            "period must be a positive whole number of milliseconds"
        }
    }

    /** The enforced limit: [published] minus [margin]. */
    public val limit: Int get() = published - margin

    /** Two [RateWindow]s are equal when their [name], [published], [period], [margin] and [hard] are equal. */
    override fun equals(other: Any?): Boolean =
        other is RateWindow && other.name == name && other.published == published && other.period == period && other.margin == margin && other.hard == hard

    /** Returns a hash code consistent with [equals]: it is computed from the same properties. */
    override fun hashCode(): Int = listOf(name, published, period, margin, hard).fold(0) { acc, v -> 31 * acc + v.hashCode() }
    /**
     * A diagnostic string for logs: [name], [published], [period], [margin] and [hard]. Its format is not part of the
     * API and may change.
     */
    override fun toString(): String = "RateWindow(name=$name, published=$published, period=$period, margin=$margin, hard=$hard)"

    /**
     * Collects the optional properties of a [RateWindow]. Not thread-safe; use it only inside the factory's lambda.
     *
     * **Stable Core.**
     */
    @WebToolsDsl
    public class Builder internal constructor() {
        /** Calls held back below the published limit; 0 by default. In `0 until published`. */
        public var margin: Int = 0

        /** Whether the window is hard; `false` by default. */
        public var hard: Boolean = false
    }
}

/**
 * Builds a [RateWindow]. `RateWindow("day", 3_000, 1.days) { margin = 100; hard = true }`.
 *
 * **Stable Core.**
 *
 * @param name the window's name; not blank.
 * @param published the provider's documented calls per [period]; at least 1.
 * @param period the window's length; positive, finite, a whole number of milliseconds.
 * @param configure assigns `margin` and `hard`.
 * @return the window.
 * @throws IllegalArgumentException if a value is outside its range; the message names the property and never a value.
 */
public fun RateWindow(name: String, published: Int, period: Duration, configure: RateWindow.Builder.() -> Unit = {}): RateWindow {
    val builder = RateWindow.Builder().apply(configure)
    return RateWindow(name, published, period, builder.margin, builder.hard)
}
