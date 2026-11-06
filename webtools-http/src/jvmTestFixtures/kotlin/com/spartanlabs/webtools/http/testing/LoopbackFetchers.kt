package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.FetcherParts
import com.spartanlabs.webtools.http.GuardRelaxation
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.UrlFetcherConfig
import com.spartanlabs.webtools.http.UrlGuard
import java.net.InetAddress

/**
 * A [UrlFetcher] over a loopback test server (a `mockwebserver3.MockWebServer` started on `127.0.0.1`), for a consumer's Level 3 tests. Each factory builds the matching
 * [LoopbackTransports] transport and pre-checks with the same policy; the untrusted one relaxes the pre-check for loopback exactly as the transport is relaxed, so every `Untrusted` rule except the
 * loopback ones is live in both places. An unrelaxed pre-check would refuse every loopback URL, which is why this fixture exists.
 *
 * **Stable Core, source compatibility only.** The fetcher owns its transport; the caller closes the fetcher. A budget set in `configure` stays the caller's: the fetcher never closes it. Not a
 * production switch: nothing here is reachable from the main artifact. Stateless: both factories are safe to call from any thread, each call builds a new fetcher, and the fetcher returned is
 * safe to share (every member of [UrlFetcher] may be called concurrently).
 *
 * **Boundary.** The fetcher opens real sockets through the real `OkHttpTransport`, with the timeouts and limits of `policy.settings`; closing the fetcher releases them. An exception thrown by
 * `resolve` reaches the transport unchanged (an `UnknownHostException` is a DNS failure). The fetcher and its transport log on the module's loggers as in production (`INFO` on creation and on
 * close, the per-fetch line at `DEBUG`), never a header value, a query string or a body.
 */
public object LoopbackFetchers {
    /**
     * A fetcher under an `Untrusted` policy with every rule live except the loopback ones (the host `localhost`, `127.0.0.0/8`, `::1` and the port rule for exactly those hosts).
     *
     * @param userAgent the `User-Agent` sent on every request, under the rule of the public `UrlFetcher(userAgent)` factory: non-blank, visible ASCII, no leading or trailing space.
     * @param policy the policy; the defaults when omitted.
     * @param resolve answers a host name with addresses; the system resolver by default. Called on the transport's own threads, never the caller's, and possibly from several at once, so it
     *   must be thread-safe; it may block. Its exceptions reach the transport unchanged.
     * @param configure sets the budget (caller-owned, never closed by the fetcher), reader, clocks and `defaultMaxWait`; empty by default.
     * @return the fetcher; the caller closes it.
     * @throws IllegalArgumentException for a `userAgent` the public factory refuses, a `policy.settings.maxResponseBytes` above `Int.MAX_VALUE - 8` or a negative `defaultMaxWait`;
     *   nothing stays open, and the message is constant and never echoes a value.
     */
    public fun untrusted(
        userAgent: String,
        policy: OutboundUrlPolicy.Untrusted = OutboundUrlPolicy.untrusted(),
        resolve: (host: String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
        configure: UrlFetcherConfig.() -> Unit = {},
    ): UrlFetcher {
        val resolved = UrlFetcherConfig().apply(configure).resolve()
        return UrlFetcher(FetcherParts(LoopbackTransports.untrusted(userAgent, policy, resolve), UrlGuard(policy, GuardRelaxation.LoopbackForTests), resolved, ownsTransport = true))
    }

    /**
     * A fetcher under a `Trusted` policy with cleartext forced on (loopback servers speak `http`) in both the transport and the pre-check; no relaxation (`Trusted` has no loopback rule).
     *
     * @param userAgent the `User-Agent` sent on every request, under the rule of the public `UrlFetcher(userAgent)` factory: non-blank, visible ASCII, no leading or trailing space.
     * @param policy the policy; the defaults when omitted. Every property but `allowCleartext` is kept.
     * @param resolve answers a host name with addresses; the system resolver by default. Called on the transport's own threads, never the caller's, and possibly from several at once, so it
     *   must be thread-safe; it may block. Its exceptions reach the transport unchanged.
     * @param configure sets the budget (caller-owned, never closed by the fetcher), reader, clocks and `defaultMaxWait`; empty by default.
     * @return the fetcher; the caller closes it.
     * @throws IllegalArgumentException for a `userAgent` the public factory refuses, a `policy.settings.maxResponseBytes` above `Int.MAX_VALUE - 8` or a negative `defaultMaxWait`;
     *   nothing stays open, and the message is constant and never echoes a value.
     */
    public fun trusted(
        userAgent: String,
        policy: OutboundUrlPolicy.Trusted = OutboundUrlPolicy.trusted(),
        resolve: (host: String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
        configure: UrlFetcherConfig.() -> Unit = {},
    ): UrlFetcher {
        val effective = if (policy.allowCleartext) policy else OutboundUrlPolicy.trusted(policy) { allowCleartext = true }
        val resolved = UrlFetcherConfig().apply(configure).resolve()
        return UrlFetcher(FetcherParts(LoopbackTransports.trusted(userAgent, effective, resolve), UrlGuard(effective), resolved, ownsTransport = true))
    }
}
