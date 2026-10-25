package com.spartanlabs.webtools.http

import kotlin.time.Duration

/**
 * A complete answer from the server that the caller must treat as a failure: the exchange succeeded,
 * the status did not. Produced by the call facades.
 *
 * **Experimental (subclass).** Subclassing needs `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`;
 * catching, matching and reading a failure are Stable Core. The leaves [RateLimited] and [UpstreamError]
 * are final. Match the leaves you care about and keep an `else`: new leaves may be added in a feature
 * release.
 *
 * @param status the response status, in 100..599.
 * @param message the constant text of the type plus its non-sensitive fields.
 * @throws IllegalArgumentException if [status] is outside 100..599.
 */
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public abstract class UpstreamFailure protected constructor(
    /** The response status, in 100..599. */
    public val status: Int,
    message: String,
) : HttpFailure(message) {
    init {
        require(status in 100..599) { "Status code must be in 100..599" }
    }

    /**
     * The server throttled the call and said when to retry.
     *
     * **Stable Core.** Created by the call facades only. Message:
     * `The server rate-limited the call (status <status>); retry in <retryAfter>`.
     *
     * @param status the response status, in 100..599.
     * @param retryAfter how long the server asked the caller to wait; not negative.
     * @throws IllegalArgumentException if [status] is outside 100..599 or [retryAfter] is negative.
     */
    public class RateLimited internal constructor(
        status: Int,
        /** How long the server asked the caller to wait. */
        public val retryAfter: Duration,
    ) : UpstreamFailure(status, "The server rate-limited the call (status $status); retry in $retryAfter") {
        init {
            require(!retryAfter.isNegative()) { "retryAfter must not be negative" }
        }
    }

    /**
     * The server answered with a status the caller must handle. It keeps the response headers and body,
     * copied in and read back as copies, and prints neither: `message` and `toString()` carry the status
     * only.
     *
     * **Stable Core.** Message: `The server answered with status <status>`.
     *
     * @param status the response status, in 100..599.
     * @param headers the response headers.
     * @param body the response body; copied.
     * @throws IllegalArgumentException if [status] is outside 100..599.
     */
    public class UpstreamError(
        status: Int,
        /** The response headers. */
        public val headers: HttpHeaders,
        body: ByteArray,
    ) : UpstreamFailure(status, "The server answered with status $status") {
        private val bodyBytes: ByteArray = body.copyOf()

        /** The body's size in bytes. */
        public val bodySize: Int get() = bodyBytes.size

        /**
         * Returns the body.
         *
         * @return a fresh copy of the body bytes on every call.
         */
        public fun body(): ByteArray = bodyBytes.copyOf()
    }
}
