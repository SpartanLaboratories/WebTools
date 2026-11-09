package com.spartanlabs.webtools.http

import kotlin.time.Duration

/**
 * Something the budget did, delivered to a [BudgetObserver] and logged. Every event describes itself, so an observer need not
 * rely on order.
 *
 * **Experimental (subclass).** Subclassing needs `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`; reading and matching an
 * event are Stable Core, and the seven leaves are final. The set grows in feature releases: match the leaves you care about and
 * keep an `else`. No leaf prints a header value, a body, a credential or a store exception's message. Equality is identity.
 */
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public abstract class BudgetEvent protected constructor() {

    /**
     * A call was granted after waiting [waited] (zero for an immediate grant).
     *
     * **Stable Core.**
     *
     * @param lane the lane the call was charged to.
     * @param cost the units charged; at least 1.
     * @param waited how long the call waited for its slot; not negative.
     * @throws IllegalArgumentException if [cost] is below 1 or [waited] is negative.
     */
    public class Granted(
        /** The lane the call was charged to. */
        public val lane: Lane,
        /** The units charged; at least 1. */
        public val cost: Int,
        /** How long the call waited for its slot; zero for an immediate grant. */
        public val waited: Duration,
    ) : BudgetEvent() {
        init {
            require(cost >= 1) { "cost must be at least 1" }
            require(!waited.isNegative()) { "waited must not be negative" }
        }

        /**
         * A diagnostic string for logs: the [lane]'s name, [cost] and [waited]. Its format is not part of the API and
         * may change.
         */
        override fun toString(): String = "Granted(lane=${lane.name}, cost=$cost, waited=$waited)"
    }

    /**
     * A call was refused (or a waiter failed after a re-plan): [wait] is what it would have waited, `Duration.INFINITE` for
     * `CostExceedsLimit`.
     *
     * **Stable Core.**
     *
     * @param lane the lane the call asked for.
     * @param cost the units it asked for; at least 1.
     * @param wait the wait it would have had; not negative.
     * @param reason why it was refused.
     * @param window the window that set the wait, or `null`; not blank when present.
     * @throws IllegalArgumentException if [cost] is below 1, [wait] is negative or [window] is blank.
     */
    public class Refused(
        /** The lane the call asked for. */
        public val lane: Lane,
        /** The units it asked for; at least 1. */
        public val cost: Int,
        /** The wait it would have had; `Duration.INFINITE` for `CostExceedsLimit`. */
        public val wait: Duration,
        /** Why it was refused. */
        public val reason: ExhaustedReason,
        /** The window that set the wait, or `null`. */
        public val window: String?,
    ) : BudgetEvent() {
        init {
            require(cost >= 1) { "cost must be at least 1" }
            require(!wait.isNegative()) { "wait must not be negative" }
            require(window == null || window.isNotBlank()) { "window must be null or not blank" }
        }

        /**
         * A diagnostic string for logs: the [lane]'s name, [cost], [wait], the [reason]'s name and [window]. Its format
         * is not part of the API and may change.
         */
        override fun toString(): String = "Refused(lane=${lane.name}, cost=$cost, wait=$wait, reason=${reason.name}, window=$window)"
    }

    /**
     * The budget is paused until [untilEpochMillis], [length] from now, because of the hint [source] ([PauseSource.RetryAfter],
     * [PauseSource.ResetIn], [PauseSource.Boundary], or [PauseSource.Snapshot] for a restored pause). Emitted when a pause begins
     * or is extended, never when a hint is shorter than the pause in force.
     *
     * **Stable Core.**
     *
     * @param untilEpochMillis the epoch millisecond the pause ends.
     * @param length how long the pause lasts from now; not negative.
     * @param source which hint set the pause.
     * @throws IllegalArgumentException if [length] is negative.
     */
    public class Paused(
        /** The epoch millisecond the pause ends. */
        public val untilEpochMillis: Long,
        /** How long the pause lasts from now. */
        public val length: Duration,
        /** Which hint set the pause. */
        public val source: PauseSource,
    ) : BudgetEvent() {
        init {
            require(!length.isNegative()) { "length must not be negative" }
        }

        /**
         * A diagnostic string for logs: [untilEpochMillis], [length] and the [source]'s name. Its format is not part of
         * the API and may change.
         */
        override fun toString(): String = "Paused(until=$untilEpochMillis, length=$length, source=${source.name})"
    }

    /**
     * A hard window is full for its current period; it refuses until [resetsAtEpochMillis]. Raised once per window and period.
     *
     * **Stable Core.**
     *
     * @param window the window's name; not blank.
     * @param resetsAtEpochMillis the epoch millisecond the period ends.
     * @throws IllegalArgumentException if [window] is blank.
     */
    public class HardStop(
        /** The window's name. */
        public val window: String,
        /** The epoch millisecond the period ends. */
        public val resetsAtEpochMillis: Long,
    ) : BudgetEvent() {
        init {
            require(window.isNotBlank()) { "window must not be blank" }
        }

        /** A diagnostic string for logs: [window] and [resetsAtEpochMillis]. Its format is not part of the API and may change. */
        override fun toString(): String = "HardStop(window=$window, resetsAt=$resetsAtEpochMillis)"
    }

    /**
     * A server reading raised the local count of [window] to [serverUsed], by [raisedBy] (at least 1).
     *
     * **Stable Core.**
     *
     * @param window the window's name; not blank.
     * @param serverUsed the count the server reported; not negative.
     * @param raisedBy how much the local count rose; at least 1.
     * @throws IllegalArgumentException if [window] is blank, [serverUsed] is negative or [raisedBy] is below 1.
     */
    public class Reconciled(
        /** The window's name. */
        public val window: String,
        /** The count the server reported. */
        public val serverUsed: Int,
        /** How much the local count rose; at least 1. */
        public val raisedBy: Int,
    ) : BudgetEvent() {
        init {
            require(window.isNotBlank()) { "window must not be blank" }
            require(serverUsed >= 0) { "serverUsed must not be negative" }
            require(raisedBy >= 1) { "raisedBy must be at least 1" }
        }

        /**
         * A diagnostic string for logs: [window], [serverUsed] and [raisedBy]. Its format is not part of the API and
         * may change.
         */
        override fun toString(): String = "Reconciled(window=$window, serverUsed=$serverUsed, raisedBy=$raisedBy)"
    }

    /**
     * A store call failed: [operation] is [StoreOperation.Load] or [StoreOperation.Save]; [cause] is for diagnostics (the log
     * prints its class name only).
     *
     * **Stable Core.** `toString()` prints the operation and the cause's simple class name, never its message.
     *
     * @param operation which store call failed.
     * @param cause what the store reported.
     */
    public class StoreFailed(
        /** Which store call failed. */
        public val operation: StoreOperation,
        /** What the store reported; for diagnostics. */
        public val cause: Throwable,
    ) : BudgetEvent() {
        /**
         * A diagnostic string for logs: the [operation]'s name and the [cause]'s simple class name, never its message.
         * Its format is not part of the API and may change.
         */
        override fun toString(): String = "StoreFailed(operation=${operation.name}, cause=${cause::class.simpleName})"
    }

    /**
     * The restore ran: [windowsRestored] snapshot windows were merged, [windowsDropped] were stale or unknown, and
     * [ignoredForSkew] says the snapshot was stamped further ahead than `maxRestoreSkew` (its stamp and pause ignored, its counts
     * kept).
     *
     * **Stable Core.**
     *
     * @param windowsRestored the snapshot windows merged; not negative.
     * @param windowsDropped the snapshot windows dropped as stale or unknown; not negative.
     * @param ignoredForSkew whether the stamp and the pause were ignored for skew.
     * @throws IllegalArgumentException if [windowsRestored] or [windowsDropped] is negative.
     */
    public class Restored(
        /** The snapshot windows merged. */
        public val windowsRestored: Int,
        /** The snapshot windows dropped as stale or unknown. */
        public val windowsDropped: Int,
        /** Whether the stamp and the pause were ignored for skew. */
        public val ignoredForSkew: Boolean,
    ) : BudgetEvent() {
        init {
            require(windowsRestored >= 0) { "windowsRestored must not be negative" }
            require(windowsDropped >= 0) { "windowsDropped must not be negative" }
        }

        /**
         * A diagnostic string for logs: [windowsRestored], [windowsDropped] and [ignoredForSkew]. Its format is not
         * part of the API and may change.
         */
        override fun toString(): String = "Restored(windowsRestored=$windowsRestored, windowsDropped=$windowsDropped, ignoredForSkew=$ignoredForSkew)"
    }
}
