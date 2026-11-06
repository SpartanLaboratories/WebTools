package com.spartanlabs.webtools.http

/**
 * Builds a [UrlFetcher] for URLs an administrator supplies: an `Untrusted` policy (`OutboundUrlPolicy.untrusted()`), an owned `OkHttpTransport(userAgent, policy)`, `CallBudget.Unlimited` unless
 * `budget` is set. **Stable Core.** `close()` closes the transport. See the overload with a policy to add the deployment's own denied ranges or ports.
 *
 * @param userAgent the `User-Agent` sent on every request: non-blank, visible ASCII, no leading or trailing space.
 * @param configure sets the budget, reader, clocks and `defaultMaxWait`; the defaults when omitted.
 * @return the fetcher; the caller closes it.
 * @throws IllegalArgumentException if `defaultMaxWait` is negative or `userAgent` is refused by the transport; nothing stays open.
 */
public fun UrlFetcher(userAgent: String, configure: UrlFetcherConfig.() -> Unit = {}): UrlFetcher =
    UrlFetcher(userAgent, OutboundUrlPolicy.untrusted(), configure)

/**
 * As the overload without a policy, for a policy of the caller's own: `OutboundUrlPolicy.untrusted { extraDeniedRanges += … }` adds the deployment's own NAT64 prefix or internal ranges. **Stable Core.**
 * The transport is built with the policy's settings as they are (an `Untrusted` policy's concurrency caps are part of what makes it untrusted) and the pre-check applies the same policy.
 *
 * @param userAgent the `User-Agent` sent on every request.
 * @param policy the policy the transport enforces and the pre-check applies.
 * @param configure sets the budget, reader, clocks and `defaultMaxWait`.
 * @return the fetcher; the caller closes it.
 * @throws IllegalArgumentException as the overload without a policy, and for a `policy.settings.maxResponseBytes` above `Int.MAX_VALUE - 8`.
 */
public fun UrlFetcher(userAgent: String, policy: OutboundUrlPolicy, configure: UrlFetcherConfig.() -> Unit = {}): UrlFetcher {
    val resolved = UrlFetcherConfig().apply(configure).resolve()      // the pure check first: a refusal builds no transport
    return UrlFetcher(FetcherParts(OkHttpTransport(userAgent, policy), UrlGuard(policy), resolved, ownsTransport = true))
}
