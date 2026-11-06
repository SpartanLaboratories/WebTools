package com.spartanlabs.webtools.http

/**
 * Builds an [UpstreamClient] that owns what it uses: an `OkHttpTransport(userAgent, policy)`, a `CaffeineResponseCache` on the configuration's clock and dispatcher (unless `cache(...)` was
 * called) and, when `budget(windows)` was called, the `WindowedCallBudget` it built. **Stable Core.** When the budget is a `WindowedCallBudget` (built or supplied) both OkHttp concurrency limits
 * are raised to at least its `maxBurst`, because a client talks to one host (the per-host limit binds) and a permit queued in OkHttp would wait outside the call timeout and the wait cap; they are never
 * lowered and there is no ceiling (worst-case memory is concurrent requests × `policy.settings.maxResponseBytes`). `close()` closes the cache, the budget and the transport, in that order, and
 * nothing it was handed. The default policy is `Trusted`, pinned to the base URL's host, `https` only.
 *
 * @param baseUrl the upstream's base URL; no query.
 * @param userAgent the `User-Agent` sent on every request: non-blank, visible ASCII, no leading or trailing space.
 * @param configure sets the policy, clocks, dispatcher, reader, default wait, default headers, cache and budget; the defaults when omitted.
 * @return the client; the caller closes it.
 * @throws IllegalArgumentException for a refusal of the common factory, a `userAgent` the transport refuses, or a `policy.settings.maxResponseBytes` above `Int.MAX_VALUE - 8`;
 *   what had already been built is closed first, and the message is constant.
 */
public fun UpstreamClient(baseUrl: Url, userAgent: String, configure: UpstreamClientConfig.() -> Unit = {}): UpstreamClient {
    val resolved = UpstreamClientConfig(baseUrl).apply(configure).resolve()
    val built = BuiltParts()
    return catchNonFatal {
        val budget = resolved.buildBudget()                                      // first: the transport's sizing needs its maxBurst
        if (budget.owned) built.budget = budget.budget
        val transport = OkHttpTransport(userAgent, resolved.policy.sizedFor(budget.budget))   // validates userAgent and maxResponseBytes before it builds anything
        built.transport = transport
        val cache = resolved.cache ?: CaffeineResponseCache { timeSource = resolved.timeSource; loadDispatcher = resolved.dispatcher }.also { built.cache = it }
        UpstreamClient(ClientParts(resolved, transport, cache, budget.budget, Owned(cache = resolved.cache == null, budget = budget.owned, transport = true)))
    }.getOrElse { failure ->
        // The one place a non-fatal exception is rethrown after cleanup: a refused construction is a programmer error and reaches the caller as it
        // was thrown, with nothing left open. An `Error` is not caught, and nothing is cleaned up for it.
        built.closeAll()
        throw failure
    }
}

/** The parts a factory has built so far, closed together if a later step throws. */
internal class BuiltParts {
    var cache: AutoCloseable? = null
    var budget: AutoCloseable? = null
    var transport: AutoCloseable? = null
    fun closeAll() = closeInOrder(listOf("cache" to cache, "budget" to budget, "transport" to transport), log = null)
}
