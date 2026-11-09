package com.spartanlabs.webtools.http

import kotlin.time.Duration

/**
 * What a server said about one window after it counted a call: [remaining] calls left (may be negative once the server is
 * over its limit) and, when it said, [resetIn] until the window resets.
 *
 * **Stable Core.** Equality is on both properties; `toString()` prints them.
 *
 * @param remaining the calls the server says are left after it counted the call; may be negative.
 * @param resetIn how long until the window resets, or `null` when the server did not say; not negative.
 * @throws IllegalArgumentException if [resetIn] is negative.
 */
public class WindowReading(
    /** The calls the server says are left after it counted the call; may be negative. */
    public val remaining: Int,
    /** How long until the window resets, or `null` when the server did not say. Not negative. */
    public val resetIn: Duration?,
) {
    /**
     * A reading without a reset time.
     *
     * @param remaining the calls the server says are left after it counted the call; may be negative.
     */
    public constructor(remaining: Int) : this(remaining, null)

    init {
        require(resetIn == null || !resetIn.isNegative()) { "resetIn must not be negative" }
    }

    /** Two [WindowReading]s are equal when their [remaining] and [resetIn] are equal. */
    override fun equals(other: Any?): Boolean = other is WindowReading && other.remaining == remaining && other.resetIn == resetIn
    /** Returns a hash code consistent with [equals]: it is computed from the same properties. */
    override fun hashCode(): Int = 31 * remaining + (resetIn?.hashCode() ?: 0)
    /** A diagnostic string for logs: [remaining] and [resetIn]. Its format is not part of the API and may change. */
    override fun toString(): String = "WindowReading(remaining=$remaining, resetIn=$resetIn)"
}
