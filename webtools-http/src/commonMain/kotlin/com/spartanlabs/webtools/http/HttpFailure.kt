package com.spartanlabs.webtools.http

import kotlin.time.Duration

/**
 * The root of the stack's failure model: every failure a port or a facade returns inside a `Result`.
 *
 * **Experimental (subclass).** Subclassing needs `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`;
 * catching, matching and reading a failure are Stable Core. Its direct leaves [Closed], [BudgetExhausted]
 * and [DecodeFailure] are final; [TransportFailure] and [UpstreamFailure] are gated groups of their own.
 * Match the groups you care about and keep an `else`: new leaves may be added in a feature release.
 *
 * Every message is the type's constant text plus its named non-sensitive fields (a component name, a host
 * name, a library-written rule); it never carries free text a caller passes, a header value or a body. A
 * `cause`, when present, is stored for diagnostics and is not part of the message. Equality is identity.
 * Failures are not designed for Java serialisation.
 */
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public abstract class HttpFailure : Exception {
    /**
     * Creates a failure without a cause.
     *
     * @param message the constant text of the type plus its non-sensitive fields.
     */
    protected constructor(message: String) : super(message)

    /**
     * Creates a failure with a lower-level cause.
     *
     * @param message the constant text of the type plus its non-sensitive fields.
     * @param cause the lower-level throwable, or `null`.
     */
    protected constructor(message: String, cause: Throwable?) : super(message, cause)

    /**
     * A component was used after its `close()`.
     *
     * **Stable Core.** Message: `The <component> is closed`.
     *
     * @param component the closed component's name, for example `transport`.
     * @throws IllegalArgumentException if [component] is blank.
     */
    public class Closed(
        /** The closed component's name, for example `transport`. */
        public val component: String,
    ) : HttpFailure("The $component is closed") {
        init {
            require(component.isNotBlank()) { "component must not be blank" }
        }
    }

    /**
     * The call budget refused the call.
     *
     * **Stable Core.** Message: `The call budget is exhausted: <reason>, wait <wait>`, plus
     * `, window <window>` when a window is named.
     *
     * @param wait how long to wait before the call may be retried; not negative.
     * @param reason why the budget refused.
     * @param window the name of the exhausted window, or `null`; not blank when present.
     * @throws IllegalArgumentException if [wait] is negative or [window] is blank.
     */
    public class BudgetExhausted(
        /** How long to wait before the call may be retried. */
        public val wait: Duration,
        /** Why the budget refused. */
        public val reason: ExhaustedReason,
        /** The name of the exhausted window, or `null`. */
        public val window: String?,
    ) : HttpFailure(
        "The call budget is exhausted: ${reason.name}, wait $wait" + (if (window != null) ", window $window" else ""),
    ) {
        init {
            require(!wait.isNegative()) { "wait must not be negative" }
            require(window == null || window.isNotBlank()) { "window must be null or not blank" }
        }
    }

    /**
     * A response body could not be decoded into the requested type.
     *
     * **Stable Core.** Message: `The response body could not be decoded`.
     *
     * @param cause the decoder's exception, or `null`.
     */
    public class DecodeFailure(cause: Throwable?) : HttpFailure("The response body could not be decoded", cause) {
        /** Creates the failure without a cause. */
        public constructor() : this(null)
    }
}
