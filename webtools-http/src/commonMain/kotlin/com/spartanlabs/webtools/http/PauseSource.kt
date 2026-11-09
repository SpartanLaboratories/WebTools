package com.spartanlabs.webtools.http

/**
 * Which hint set a pause of the call budget; the `source` of [BudgetEvent.Paused].
 *
 * **Stable Core.** An open constants class: the constants in the companion are the only instances, two sources are equal when
 * their [name]s are equal, and `toString()` is the name. This set may gain constants in a feature release; a `when` over it
 * keeps an `else`.
 *
 * @param name the source's name, as its constant is spelled.
 */
public class PauseSource private constructor(
    /** The source's name, as its constant is spelled. */
    public val name: String,
) {
    /** Two [PauseSource]s are equal when their [name]s are equal. */
    override fun equals(other: Any?): Boolean = other is PauseSource && other.name == name
    /** Returns a hash code consistent with [equals]: it is computed from the same properties. */
    override fun hashCode(): Int = name.hashCode()
    /** Returns the source's [name]. */
    override fun toString(): String = name

    /** The sources. */
    public companion object {
        /** The server's `Retry-After`, carried in `RateSignals.retryAfter`. */
        public val RetryAfter: PauseSource = PauseSource("RetryAfter")

        /** A window reading with nothing remaining and a `resetIn`. */
        public val ResetIn: PauseSource = PauseSource("ResetIn")

        /** No timing hint: the next boundary of the exhausted windows (or of the shortest window), plus the guard. */
        public val Boundary: PauseSource = PauseSource("Boundary")

        /** A pause restored from the store's snapshot. */
        public val Snapshot: PauseSource = PauseSource("Snapshot")
    }
}
