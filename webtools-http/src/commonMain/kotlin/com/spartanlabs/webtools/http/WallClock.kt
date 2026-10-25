package com.spartanlabs.webtools.http

/**
 * A source of wall-clock time, in milliseconds since the Unix epoch (UTC).
 *
 * **Experimental (implement).** Implementing or delegating to this interface needs
 * `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`; calling it and passing it are Stable Core, and
 * the supplied [WallClock.System] needs no opt-in. An implementation must be safe for concurrent calls and
 * cheap. Its reading may step backwards when the system clock is adjusted; a caller that needs a monotonic
 * reading uses `kotlin.time.TimeSource`.
 */
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public interface WallClock {
    /**
     * Reads the clock.
     *
     * @return milliseconds since 1970-01-01T00:00:00Z; may step backwards when the system clock is adjusted.
     */
    public fun nowEpochMillis(): Long

    /**
     * The system clock, read from the platform.
     *
     * **Stable Core.** Final, stateless and usable without the opt-in; its `toString()` is
     * `WallClock.System`.
     */
    public object System : WallClock {
        override fun nowEpochMillis(): Long = epochMillisNow()
        override fun toString(): String = "WallClock.System"
    }
}
