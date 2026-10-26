package com.spartanlabs.webtools.http

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The caps, timeouts and concurrency limits of a transport, one set per [OutboundUrlPolicy]: they belong to the policy
 * because `Untrusted`'s caps and timeouts are part of what makes it untrusted.
 *
 * **Stable Core.** Two default sets are supplied, [TrustedDefaults] and [UntrustedDefaults]; derive a modified copy with
 * the factory `TransportSettings(from) { ... }`. Every default number is a judgement, documented here, and **changing one
 * is a behaviour change** for every consumer that did not set it:
 *
 * | Setting | [TrustedDefaults] | [UntrustedDefaults] |
 * |---|---|---|
 * | [callTimeout] (all hops together) | 30 s | 15 s |
 * | [connectTimeout], [readTimeout], [writeTimeout] | 10 s each | 5 s each |
 * | [maxResponseBytes] (decoded bytes) | 5 MiB (5,242,880) | 2 MiB (2,097,152) |
 * | [maxRedirects] | 5 | 3 |
 * | [allowHttpsToHttp] | false | false |
 * | [maxConcurrentRequests], [maxConcurrentRequestsPerHost] | 16, 5 | 8, 2 |
 * | [idleConnectionTtl] | 4 s | 4 s |
 *
 * [callTimeout] spans every hop of a redirected fetch, not each hop. [allowHttpsToHttp] is read by the URL policy when it
 * judges a redirect. Equality is on every setting; `toString()` prints every setting (numbers only).
 */
public class TransportSettings internal constructor(
    /** The whole fetch, redirects included: at least 1 millisecond and at most `Int.MAX_VALUE` milliseconds. */
    public val callTimeout: Duration,
    /** The TCP connect timeout of one attempt; the same bounds as [callTimeout]. */
    public val connectTimeout: Duration,
    /** The socket read timeout; the same bounds as [callTimeout]. */
    public val readTimeout: Duration,
    /** The socket write timeout; the same bounds as [callTimeout]. */
    public val writeTimeout: Duration,
    /** The most decoded body bytes a transport reads; at least 1. */
    public val maxResponseBytes: Long,
    /** The most redirects followed; 0 follows none (a followable redirect beyond the cap is `TooManyRedirects`). */
    public val maxRedirects: Int,
    /** Whether a redirect from `https` to `http` is followed; read by the URL policy. */
    public val allowHttpsToHttp: Boolean,
    /** The most requests in flight; at least 1. */
    public val maxConcurrentRequests: Int,
    /** The most requests in flight to one host; at least 1 and at most [maxConcurrentRequests]. */
    public val maxConcurrentRequestsPerHost: Int,
    /** How long an idle pooled connection is kept; the same bounds as [callTimeout]. */
    public val idleConnectionTtl: Duration,
) {
    override fun equals(other: Any?): Boolean =
        other is TransportSettings &&
            other.callTimeout == callTimeout && other.connectTimeout == connectTimeout &&
            other.readTimeout == readTimeout && other.writeTimeout == writeTimeout &&
            other.maxResponseBytes == maxResponseBytes && other.maxRedirects == maxRedirects &&
            other.allowHttpsToHttp == allowHttpsToHttp && other.maxConcurrentRequests == maxConcurrentRequests &&
            other.maxConcurrentRequestsPerHost == maxConcurrentRequestsPerHost && other.idleConnectionTtl == idleConnectionTtl

    override fun hashCode(): Int {
        var hash = callTimeout.hashCode()
        hash = 31 * hash + connectTimeout.hashCode()
        hash = 31 * hash + readTimeout.hashCode()
        hash = 31 * hash + writeTimeout.hashCode()
        hash = 31 * hash + maxResponseBytes.hashCode()
        hash = 31 * hash + maxRedirects
        hash = 31 * hash + allowHttpsToHttp.hashCode()
        hash = 31 * hash + maxConcurrentRequests
        hash = 31 * hash + maxConcurrentRequestsPerHost
        hash = 31 * hash + idleConnectionTtl.hashCode()
        return hash
    }

    override fun toString(): String =
        "TransportSettings(callTimeout=$callTimeout, connectTimeout=$connectTimeout, readTimeout=$readTimeout, " +
            "writeTimeout=$writeTimeout, maxResponseBytes=$maxResponseBytes, maxRedirects=$maxRedirects, " +
            "allowHttpsToHttp=$allowHttpsToHttp, maxConcurrentRequests=$maxConcurrentRequests, " +
            "maxConcurrentRequestsPerHost=$maxConcurrentRequestsPerHost, idleConnectionTtl=$idleConnectionTtl)"

    /**
     * Collects the settings of a [TransportSettings]: one `var` per setting, seeded from the value the factory was given.
     * Not thread-safe; use it only inside the factory's lambda. A lambda that reads a property before it assigns sees the
     * seed (`maxConcurrentRequests = maxOf(maxConcurrentRequests, 32)`).
     *
     * **Stable Core.**
     */
    public class Builder internal constructor(from: TransportSettings) {
        /** See [TransportSettings.callTimeout]. */
        public var callTimeout: Duration = from.callTimeout
        /** See [TransportSettings.connectTimeout]. */
        public var connectTimeout: Duration = from.connectTimeout
        /** See [TransportSettings.readTimeout]. */
        public var readTimeout: Duration = from.readTimeout
        /** See [TransportSettings.writeTimeout]. */
        public var writeTimeout: Duration = from.writeTimeout
        /** See [TransportSettings.maxResponseBytes]. */
        public var maxResponseBytes: Long = from.maxResponseBytes
        /** See [TransportSettings.maxRedirects]. */
        public var maxRedirects: Int = from.maxRedirects
        /** See [TransportSettings.allowHttpsToHttp]. */
        public var allowHttpsToHttp: Boolean = from.allowHttpsToHttp
        /** See [TransportSettings.maxConcurrentRequests]. */
        public var maxConcurrentRequests: Int = from.maxConcurrentRequests
        /** See [TransportSettings.maxConcurrentRequestsPerHost]. */
        public var maxConcurrentRequestsPerHost: Int = from.maxConcurrentRequestsPerHost
        /** See [TransportSettings.idleConnectionTtl]. */
        public var idleConnectionTtl: Duration = from.idleConnectionTtl

        // Checked in the order of the properties; a message names the property and never a value.
        internal fun build(): TransportSettings {
            requireTimeout("callTimeout", callTimeout)
            requireTimeout("connectTimeout", connectTimeout)
            requireTimeout("readTimeout", readTimeout)
            requireTimeout("writeTimeout", writeTimeout)
            require(maxResponseBytes >= 1) { "maxResponseBytes must be at least 1" }
            require(maxRedirects >= 0) { "maxRedirects must not be negative" }
            require(maxConcurrentRequests >= 1) { "maxConcurrentRequests must be at least 1" }
            require(maxConcurrentRequestsPerHost in 1..maxConcurrentRequests) {
                "maxConcurrentRequestsPerHost must be at least 1 and at most maxConcurrentRequests"
            }
            requireTimeout("idleConnectionTtl", idleConnectionTtl)
            return TransportSettings(
                callTimeout, connectTimeout, readTimeout, writeTimeout, maxResponseBytes, maxRedirects,
                allowHttpsToHttp, maxConcurrentRequests, maxConcurrentRequestsPerHost, idleConnectionTtl,
            )
        }
    }

    /** The two supplied default sets. */
    public companion object {
        /** The defaults for a [OutboundUrlPolicy.Trusted] policy: 30 s, 10 s, 10 s, 10 s, 5 MiB, 5, false, 16, 5, 4 s. */
        public val TrustedDefaults: TransportSettings =
            TransportSettings(30.seconds, 10.seconds, 10.seconds, 10.seconds, 5_242_880L, 5, false, 16, 5, 4.seconds)

        /** The defaults for a [OutboundUrlPolicy.Untrusted] policy: 15 s, 5 s, 5 s, 5 s, 2 MiB, 3, false, 8, 2, 4 s. */
        public val UntrustedDefaults: TransportSettings =
            TransportSettings(15.seconds, 5.seconds, 5.seconds, 5.seconds, 2_097_152L, 3, false, 8, 2, 4.seconds)
    }
}

// 1 ms is the smallest value OkHttp 5.5.0 accepts as a non-zero timeout; Int.MAX_VALUE ms is the largest. A Duration
// compares by value, so Duration.INFINITE (Long.MAX_VALUE ms) and every negative value fail the same test.
private fun requireTimeout(name: String, value: Duration) {
    require(value >= 1.milliseconds && value.inWholeMilliseconds <= Int.MAX_VALUE) {
        "$name must be at least 1 millisecond and at most ${Int.MAX_VALUE} milliseconds"
    }
}

/**
 * Derives a [TransportSettings]: starts from [from], applies [configure], validates.
 *
 * **Stable Core.** `TransportSettings(TransportSettings.UntrustedDefaults) { callTimeout = 20.seconds }` is the
 * `Untrusted` defaults with a longer call timeout. [from] is never modified.
 *
 * @param from the value to start from, usually [TransportSettings.TrustedDefaults] or [TransportSettings.UntrustedDefaults].
 * @param configure assigns the settings to change.
 * @return the new, validated settings.
 * @throws IllegalArgumentException if a setting breaks its rule: a timeout under 1 millisecond or over `Int.MAX_VALUE`
 *   milliseconds; [TransportSettings.maxResponseBytes] under 1; a negative [TransportSettings.maxRedirects];
 *   [TransportSettings.maxConcurrentRequests] under 1; [TransportSettings.maxConcurrentRequestsPerHost] under 1 or over
 *   [TransportSettings.maxConcurrentRequests]. The message names the property and never a value.
 */
public fun TransportSettings(from: TransportSettings, configure: TransportSettings.Builder.() -> Unit): TransportSettings =
    TransportSettings.Builder(from).apply(configure).build()
