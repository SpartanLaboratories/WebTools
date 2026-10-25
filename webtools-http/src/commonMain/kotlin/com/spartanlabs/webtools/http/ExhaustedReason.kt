package com.spartanlabs.webtools.http

/**
 * Why the call budget refused a call; the `reason` of [HttpFailure.BudgetExhausted].
 *
 * **Stable Core.** An open constants class: the constants in the companion are the only instances, two
 * reasons are equal when their [name]s are equal, and `toString()` is the name. This set may gain
 * constants in a feature release; a `when` over it keeps an `else`.
 *
 * @param name the reason's name, as its constant is spelled.
 */
public class ExhaustedReason private constructor(
    /** The reason's name, as its constant is spelled. */
    public val name: String,
) {
    override fun equals(other: Any?): Boolean = other is ExhaustedReason && other.name == name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = name

    /** The reasons. */
    public companion object {
        /** The current window has no permit left. */
        public val Window: ExhaustedReason = ExhaustedReason("Window")

        /** The budget is paused, for example after an upstream throttle. */
        public val Paused: ExhaustedReason = ExhaustedReason("Paused")

        /** The call's cost is larger than the whole limit, so no wait would admit it. */
        public val CostExceedsLimit: ExhaustedReason = ExhaustedReason("CostExceedsLimit")

        /** The queue of waiting calls is full. */
        public val QueueFull: ExhaustedReason = ExhaustedReason("QueueFull")
    }
}
