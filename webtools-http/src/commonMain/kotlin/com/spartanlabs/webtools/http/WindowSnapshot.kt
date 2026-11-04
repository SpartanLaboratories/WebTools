package com.spartanlabs.webtools.http

/**
 * The count of one hard window in one period: [used] calls in the period that started at [windowStartEpochMillis] (a multiple
 * of the window's period since the epoch).
 *
 * **Stable Core.** Equality is on the three properties; `toString()` prints the name and the numbers only.
 *
 * @param window the window's name, as [RateWindow.name] spells it; not blank.
 * @param windowStartEpochMillis the start of the period, in epoch milliseconds.
 * @param used the calls counted in the period; not negative.
 * @throws IllegalArgumentException if [window] is blank or [used] is negative.
 */
public class WindowSnapshot(
    /** The window's name, as [RateWindow.name] spells it; not blank. */
    public val window: String,
    /** The start of the period, in epoch milliseconds. */
    public val windowStartEpochMillis: Long,
    /** The calls counted in the period; not negative. */
    public val used: Int,
) {
    init {
        require(window.isNotBlank()) { "window must not be blank" }
        require(used >= 0) { "used must not be negative" }
    }

    override fun equals(other: Any?): Boolean =
        other is WindowSnapshot && other.window == window && other.windowStartEpochMillis == windowStartEpochMillis && other.used == used

    override fun hashCode(): Int = (window.hashCode() * 31 + windowStartEpochMillis.hashCode()) * 31 + used
    override fun toString(): String = "WindowSnapshot(window=$window, windowStart=$windowStartEpochMillis, used=$used)"
}
