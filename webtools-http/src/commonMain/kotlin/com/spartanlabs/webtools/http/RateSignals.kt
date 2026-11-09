package com.spartanlabs.webtools.http

import kotlin.time.Duration

/**
 * What a response told the budget, passed to `Permit.complete`: whether the server [throttled] the call, how long it asked the
 * caller to wait ([retryAfter]), and its [readings] by [RateWindow.name]. Build one with
 * `RateSignals { throttled = true; retryAfter = 30.seconds; reading("minute", WindowReading(0)) }`.
 *
 * **Stable Core.** A signal that is not [throttled] never pauses the budget (its readings still raise local counts); a reading
 * for a window the budget does not have is ignored. [RateSignals.None] changes nothing. Equality is on the three properties;
 * `toString()` prints the window names only.
 */
public class RateSignals internal constructor(
    /** Whether the server throttled the call (a `429`, or a `503` that asks for a pause). */
    public val throttled: Boolean,
    /** The wait the server asked for, or `null`. Not negative. */
    public val retryAfter: Duration?,
    readings: Map<String, WindowReading>,
) {
    /** The readings by window name, a read-only copy. */
    public val readings: Map<String, WindowReading> = readings.toMap()

    init {
        require(retryAfter == null || !retryAfter.isNegative()) { "retryAfter must not be negative" }
        require(readings.keys.none { it.isBlank() }) { "a reading's window name must not be blank" }
    }

    /** Two [RateSignals] are equal when their [throttled], [retryAfter] and [readings] are equal. */
    override fun equals(other: Any?): Boolean =
        other is RateSignals && other.throttled == throttled && other.retryAfter == retryAfter && other.readings == readings

    /** Returns a hash code consistent with [equals]: it is computed from the same properties. */
    override fun hashCode(): Int = (throttled.hashCode() * 31 + (retryAfter?.hashCode() ?: 0)) * 31 + readings.hashCode()
    /**
     * A diagnostic string for logs: [throttled], [retryAfter] and the window names only, never a reading. Its format is
     * not part of the API and may change.
     */
    override fun toString(): String = "RateSignals(throttled=$throttled, retryAfter=$retryAfter, windows=${readings.keys})"

    /**
     * Collects the properties of a [RateSignals]. Not thread-safe; use it only inside the factory's lambda.
     *
     * **Stable Core.**
     */
    @WebToolsDsl
    public class Builder internal constructor() {
        /** Whether the server throttled the call; `false` by default. */
        public var throttled: Boolean = false

        /** The wait the server asked for; `null` by default. Not negative. */
        public var retryAfter: Duration? = null
        internal val readings: MutableMap<String, WindowReading> = LinkedHashMap()

        /**
         * Records what the server said about [window]; a second call for the same name replaces the first.
         *
         * @param window the window's name, as `RateWindow.name` spells it; not blank.
         * @param reading what the server said.
         */
        public fun reading(window: String, reading: WindowReading) {
            readings[window] = reading
        }
    }

    /** The empty signal. */
    public companion object {
        /** No throttle, no wait, no readings; completing a permit with it changes nothing. */
        public val None: RateSignals = RateSignals(false, null, emptyMap())
    }
}

/**
 * Builds a [RateSignals].
 *
 * **Stable Core.**
 *
 * @param configure assigns the properties.
 * @return the signals.
 * @throws IllegalArgumentException if `retryAfter` is negative or a window name is blank.
 */
public fun RateSignals(configure: RateSignals.Builder.() -> Unit = {}): RateSignals {
    val builder = RateSignals.Builder().apply(configure)
    return RateSignals(builder.throttled, builder.retryAfter, builder.readings)
}
