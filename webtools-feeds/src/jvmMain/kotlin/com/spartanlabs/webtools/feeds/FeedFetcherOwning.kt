package com.spartanlabs.webtools.feeds

import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.UrlFetcher

/**
 * Builds a [FeedFetcher] for feed URLs an administrator supplies: an `Untrusted` policy (`OutboundUrlPolicy.untrusted()`), an
 * owned `UrlFetcher(userAgent, policy)` and so an owned `OkHttpTransport` with a 2 MiB decoded-body cap, 3 redirects and no
 * `https`-to-`http` hop. **Stable Core.** `close()` closes the `UrlFetcher`. See the overload with a policy to add the
 * deployment's own denied ranges or ports.
 *
 * @param userAgent the `User-Agent` sent on every request: non-blank, visible ASCII, no leading or trailing space.
 * @param configure sets the parser, the parse dispatcher, the parse concurrency and `Accept`.
 * @return the fetcher; the caller closes it.
 * @throws IllegalArgumentException if the configuration is out of range or `userAgent` is refused by the transport; nothing stays open.
 */
public fun FeedFetcher.Companion.untrusted(userAgent: String, configure: FeedFetcherConfig.() -> Unit = {}): FeedFetcher =
    untrusted(userAgent, OutboundUrlPolicy.untrusted(), configure)

/**
 * As the overload without a policy, for a policy of the caller's own: `OutboundUrlPolicy.untrusted { extraDeniedRanges += … }`.
 * **Stable Core.** The transport is built with the policy's settings as they are.
 *
 * @param userAgent the `User-Agent` sent on every request: non-blank, visible ASCII, no leading or trailing space.
 * @param policy the policy the transport enforces and the pre-check applies.
 * @param configure sets the parser, the parse dispatcher, the parse concurrency and `Accept`.
 * @return the fetcher; the caller closes it.
 * @throws IllegalArgumentException as the overload without a policy, and for a `policy.settings.maxResponseBytes` above `Int.MAX_VALUE - 8`.
 */
public fun FeedFetcher.Companion.untrusted(userAgent: String, policy: OutboundUrlPolicy.Untrusted, configure: FeedFetcherConfig.() -> Unit = {}): FeedFetcher {
    val resolved = FeedFetcherConfig().apply(configure).resolve()       // the pure check first: a refused configuration builds no transport
    return FeedFetcher(FeedFetcherParts(UrlFetcher(userAgent, policy), resolved, ownsUrlFetcher = true))
}
