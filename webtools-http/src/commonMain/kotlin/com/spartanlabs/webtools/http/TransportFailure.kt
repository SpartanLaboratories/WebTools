package com.spartanlabs.webtools.http

/**
 * A failure of the exchange itself: the request never completed, or completed with something no
 * [HttpResponse] can hold. Produced by an [HttpTransport] and by the URL policy.
 *
 * **Experimental (subclass).** Subclassing needs `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`;
 * catching, matching and reading a failure are Stable Core. Every leaf nested here is final and has a
 * public constructor, so a transport or a fake can produce any of them. Match the leaves you care about
 * and keep an `else`: new leaves may be added in a feature release. Messages are constant text plus
 * non-sensitive fields; the `cause`, when present, is the producer's decision and may carry what the
 * message never does.
 */
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public abstract class TransportFailure : HttpFailure {
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
     * The request did not complete within the transport's timeout.
     *
     * **Stable Core.** Message: `The request timed out`.
     *
     * @param cause the lower-level throwable, or `null`.
     */
    public class Timeout(cause: Throwable?) : TransportFailure("The request timed out", cause) {
        /** Creates the failure without a cause. */
        public constructor() : this(null)
    }

    /**
     * No connection to the server could be established.
     *
     * **Stable Core.** Message: `The connection could not be established`.
     *
     * @param cause the lower-level throwable, or `null`.
     */
    public class ConnectFailed(cause: Throwable?) : TransportFailure("The connection could not be established", cause) {
        /** Creates the failure without a cause. */
        public constructor() : this(null)
    }

    /**
     * The host name could not be resolved.
     *
     * **Stable Core.** Message: `The host name could not be resolved: <host>`.
     *
     * @param host the host name that did not resolve.
     * @param cause the lower-level throwable, or `null`.
     * @throws IllegalArgumentException if [host] is blank.
     */
    public class DnsFailed(
        /** The host name that did not resolve. */
        public val host: String,
        cause: Throwable?,
    ) : TransportFailure("The host name could not be resolved: $host", cause) {
        /**
         * Creates the failure without a cause.
         *
         * @param host the host name that did not resolve.
         * @throws IllegalArgumentException if [host] is blank.
         */
        public constructor(host: String) : this(host, null)

        init {
            require(host.isNotBlank()) { "host must not be blank" }
        }
    }

    /**
     * The TLS handshake failed.
     *
     * **Stable Core.** Message: `The TLS handshake failed`.
     *
     * @param cause the lower-level throwable, or `null`.
     */
    public class TlsFailed(cause: Throwable?) : TransportFailure("The TLS handshake failed", cause) {
        /** Creates the failure without a cause. */
        public constructor() : this(null)
    }

    /**
     * The server's response broke the HTTP protocol, a status outside 100..599 included.
     *
     * **Stable Core.** Message: `The server's response broke the HTTP protocol`.
     *
     * @param cause the lower-level throwable, or `null`.
     */
    public class Protocol(cause: Throwable?) : TransportFailure("The server's response broke the HTTP protocol", cause) {
        /** Creates the failure without a cause. */
        public constructor() : this(null)
    }

    /**
     * An I/O error interrupted the exchange after the connection was made.
     *
     * **Stable Core.** Message: `An I/O error interrupted the exchange`.
     *
     * @param cause the lower-level throwable, or `null`.
     */
    public class Io(cause: Throwable?) : TransportFailure("An I/O error interrupted the exchange", cause) {
        /** Creates the failure without a cause. */
        public constructor() : this(null)
    }

    /**
     * The request broke a rule before it could be sent.
     *
     * **Stable Core.** Message: `The request is invalid: <rule>`.
     *
     * @param rule a short description of the broken rule, written by the library; it never holds request
     *   data.
     * @param cause the lower-level throwable, or `null`.
     * @throws IllegalArgumentException if [rule] is blank.
     */
    public class InvalidRequest(
        /** A short description of the broken rule, written by the library; it never holds request data. */
        public val rule: String,
        cause: Throwable?,
    ) : TransportFailure("The request is invalid: $rule", cause) {
        /**
         * Creates the failure without a cause.
         *
         * @param rule a short description of the broken rule, written by the library.
         * @throws IllegalArgumentException if [rule] is blank.
         */
        public constructor(rule: String) : this(rule, null)

        init {
            require(rule.isNotBlank()) { "rule must not be blank" }
        }
    }

    /**
     * The outbound-URL policy refused the request or a redirect hop.
     *
     * **Stable Core.** Message: `The request was refused by the URL policy: <reason>`.
     *
     * @param reason the policy rule that refused.
     * @param cause the lower-level throwable, or `null`.
     */
    public class RefusedByPolicy(
        /** The policy rule that refused. */
        public val reason: RefusalReason,
        cause: Throwable?,
    ) : TransportFailure("The request was refused by the URL policy: ${reason.name}", cause) {
        /**
         * Creates the failure without a cause.
         *
         * @param reason the policy rule that refused.
         */
        public constructor(reason: RefusalReason) : this(reason, null)
    }

    /**
     * The response body is larger than the transport's limit.
     *
     * **Stable Core.** Message: `The response body is larger than the limit of <limit> bytes`, plus
     * ` (declared <declared> bytes)` when the server declared a length.
     *
     * @param limit the transport's limit in bytes; not negative.
     * @param declared the length the server declared, or `null`; not negative when present.
     * @param cause the lower-level throwable, or `null`.
     * @throws IllegalArgumentException if [limit] or [declared] is negative.
     */
    public class BodyTooLarge(
        /** The transport's limit in bytes. */
        public val limit: Long,
        /** The length the server declared, or `null`. */
        public val declared: Long?,
        cause: Throwable?,
    ) : TransportFailure(
        "The response body is larger than the limit of $limit bytes" +
            (if (declared != null) " (declared $declared bytes)" else ""),
        cause,
    ) {
        /**
         * Creates the failure without a cause.
         *
         * @param limit the transport's limit in bytes; not negative.
         * @param declared the length the server declared, or `null`.
         * @throws IllegalArgumentException if [limit] or [declared] is negative.
         */
        public constructor(limit: Long, declared: Long?) : this(limit, declared, null)

        init {
            require(limit >= 0) { "limit must not be negative" }
            require(declared == null || declared >= 0) { "declared must be null or not negative" }
        }
    }

    /**
     * The request was redirected more often than the transport allows.
     *
     * **Stable Core.** Message: `The request was redirected more than <limit> times`.
     *
     * @param limit the transport's redirect limit; not negative.
     * @param cause the lower-level throwable, or `null`.
     * @throws IllegalArgumentException if [limit] is negative.
     */
    public class TooManyRedirects(
        /** The transport's redirect limit. */
        public val limit: Int,
        cause: Throwable?,
    ) : TransportFailure("The request was redirected more than $limit times", cause) {
        /**
         * Creates the failure without a cause.
         *
         * @param limit the transport's redirect limit; not negative.
         * @throws IllegalArgumentException if [limit] is negative.
         */
        public constructor(limit: Int) : this(limit, null)

        init {
            require(limit >= 0) { "limit must not be negative" }
        }
    }

    /**
     * The transport refused to send the request a second time, because it is not safe to repeat.
     *
     * **Stable Core.** Message: `The transport refused to send the request a second time`.
     *
     * @param cause the lower-level throwable, or `null`.
     */
    public class RetryRefused(cause: Throwable?) : TransportFailure("The transport refused to send the request a second time", cause) {
        /** Creates the failure without a cause. */
        public constructor() : this(null)
    }
}
