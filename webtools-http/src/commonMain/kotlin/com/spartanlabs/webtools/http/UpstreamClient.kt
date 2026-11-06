package com.spartanlabs.webtools.http

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Calls the endpoints of one upstream API from a base URL, composing a response cache, a call budget and a transport in one fixed order: the policy's pre-check, then the cache, then the
 * budget **inside the cache's loader**, then the transport. A hit costs nothing and N coalesced callers cost one permit and one request. **Stable Core.**
 *
 * - **Per call** (`call`): path segments, query pairs, headers, method, body, lane, `maxWait` and `maxAge` (a `CallSpec`); only a `GET` is cached. The key is the endpoint id, the full request path
 *   and the query pairs; never a header, the body, the lane, `maxAge` or a credential. A shared load runs the first caller's request: per-call headers must not change the answer for a cached
 *   endpoint, and a caller with a tighter deadline wraps its own `call` in `withTimeoutOrNull`.
 * - **Outcome**: `Found(value)` (stored for `ttl`), `Missing` (a declared miss status, stored for `missTtl`), or a failure, never stored: a throttle is `RateLimited` (no retry), a
 *   `2xx` the decoder rejects is `DecodeFailure`, any other status is `UpstreamError`, and the transport's, budget's and cache's failures pass through. Match the groups you care about and keep an `else`.
 * - **A refusal costs nothing**: an `InvalidRequest` or a policy refusal reaches neither the budget nor the transport and is never cached.
 * - **Ownership**: the common factory owns only a budget built from `budget(windows)`; the JVM owning factory owns the transport, the cache (unless `cache(...)` was called) and the budget it built.
 *   `close()` closes what the client built, in the order cache, budget, transport, never what it was handed; it is non-suspending, idempotent, never throws and safe from any thread.
 *   Call [flush] before `close()` for the exact final write of a budget with a persistent store; [utilisation], [cacheStats] and [flush] keep working after `close()`.
 *
 * Every member may be called concurrently from any thread and coroutine.
 */
public class UpstreamClient internal constructor(parts: ClientParts) : AutoCloseable {
    /** The base URL every call is made against; it has no query. */
    public val baseUrl: Url = parts.resolved.baseUrl

    private val policy: OutboundUrlPolicy = parts.resolved.policy
    private val guard = UrlGuard(policy)
    private val cache: ResponseCache = parts.cache
    private val budget: CallBudget = parts.budget
    private val transport: HttpTransport = parts.transport
    private val owned: Owned = parts.owned
    private val timeSource: TimeSource = parts.resolved.timeSource
    private val defaultMaxWait: Duration = parts.resolved.defaultMaxWait
    private val defaultHeaders: HttpHeaders = parts.resolved.headers
    private val log: Log = logger(UPSTREAM_CLIENT_LOGGER_NAME)
    private val dispatch = CallDispatch(transport, budget, parts.resolved.rateSignals, parts.resolved.wallClock, timeSource, log)

    // The closed flag: `complete()` returns true for exactly one caller (`commonMain` has no atomics).
    private val closing: CompletableJob = Job()

    init {
        log.info("created: baseUrl={}, policy={}, cache={}, budget={}, owned={}, defaultMaxWait={}", baseUrl.redacted(), policyKind(policy), kindOf(cache), kindOf(budget), owned.names(), defaultMaxWait)
    }

    /**
     * Calls [endpoint].
     *
     * A failure is never stored. Concurrent callers of one key share one load and receive the first caller's result, so the request sent is the first caller's. A negative `maxAge` never
     * reaches the cache: it is an `InvalidRequest`.
     *
     * @param T the endpoint's value type.
     * @param endpoint the endpoint to call.
     * @param configure sets this call's path, query, headers, method, body, lane, `maxWait` and `maxAge`; the defaults when omitted.
     * @return `Found` or `Missing`; otherwise a failure: [HttpFailure.Closed] (`"client"`, or a component's), [TransportFailure.InvalidRequest], [TransportFailure.RefusedByPolicy],
     *   [HttpFailure.BudgetExhausted], any [TransportFailure], [UpstreamFailure.RateLimited], [UpstreamFailure.UpstreamError], [HttpFailure.DecodeFailure] or the decoder's own [HttpFailure].
     * @throws kotlinx.coroutines.CancellationException if the caller is cancelled (only that caller; a shared load continues for the others).
     * @throws Error if the decoder, the reader, the transport or the budget's store throws one.
     */
    public suspend fun <T : Any> call(endpoint: Endpoint<T>, configure: CallSpec.() -> Unit = {}): Result<Lookup<T>> {
        if (closing.isCompleted) return Result.failure(HttpFailure.Closed(CLIENT_COMPONENT))
        val started = timeSource.markNow()
        return prepare(endpoint, configure)
            .onFailure { failure -> if (failure is TransportFailure.InvalidRequest) log.debug("invalid request: endpoint={}, rule={}", endpoint.id, failure.rule) }
            .flatMap { prepared -> prepared.unless(refusalOf(endpoint, prepared)) }
            .fold(onSuccess = { prepared -> load(endpoint, prepared, started) }, onFailure = { Result.failure(it) })
    }

    /**
     * Reads the budget.
     *
     * @return the budget's reading: each window's current period, the pause remaining and the number of waiting calls.
     */
    public suspend fun utilisation(): BudgetUtilisation = budget.utilisation()

    /**
     * Reads the cache's counts.
     *
     * @return the cache's four counts (`CacheStats.Empty` for `ResponseCache.Disabled`).
     */
    public fun cacheStats(): CacheStats = cache.stats()

    /**
     * Writes the budget's exact counts to its store. Call it before [close] for the exact final write of a budget with a persistent store; it also works after [close].
     *
     * @return success, or why the write failed; `CallBudget.Unlimited` succeeds.
     * @throws kotlinx.coroutines.CancellationException if the caller is cancelled.
     */
    public suspend fun flush(): Result<Unit> = budget.flush()

    /** Closes what the client built, in the order cache, budget, transport; idempotent, non-suspending, never throws. */
    override fun close() {
        if (!closing.complete()) return
        closeInOrder(
            listOf("cache" to cache.takeIf { owned.cache }, "budget" to budget.takeIf { owned.budget }, "transport" to transport.takeIf { owned.transport }),
            log,
        )
        log.info("closed")
    }

    override fun toString(): String =
        "UpstreamClient(baseUrl=${baseUrl.redacted()}, policy=${policyKind(policy)}, cache=${kindOf(cache)}, budget=${kindOf(budget)}, owned=${owned.names()}, closed=${closing.isCompleted})"

    // The spec, its limits, the URL, the headers and the request: nothing is spent. Every failure is an InvalidRequest.
    private fun <T : Any> prepare(endpoint: Endpoint<T>, configure: CallSpec.() -> Unit): Result<Prepared> =
        applySettings(CallSpec(endpoint.lane, defaultMaxWait, defaultHeaders), configure)
            .flatMap { spec -> spec.unless(callLimitFailure(spec.maxWait, spec.maxAge)) }
            .flatMap { spec ->
                assembleUrl(baseUrl, spec.pathSegments(), spec.queryPairs()).flatMap { assembled ->
                    val headers = spec.requestHeaders()
                    headers.unless(controlledHeaderFailure(headers))
                        .flatMap { buildRequest(spec.method, assembled.url, it, spec.body) }
                        .map { request -> Prepared(request, spec.lane, spec.maxWait, spec.maxAge, assembled.keyParams) }
                }
            }

    // The pre-check runs before the cache: a refusal costs nothing and caches nothing. It is created, and logged once, here.
    private fun refusalOf(endpoint: Endpoint<*>, prepared: Prepared): TransportFailure.RefusedByPolicy? = guard.checkRequest(prepared.request)?.let { refusal ->
        log.warn("refused by policy: endpoint={}, reason={}, rangeClass={}, url={}", endpoint.id, refusal.reason.name, refusal.rangeClass ?: "-", prepared.request.url.redacted())
        TransportFailure.RefusedByPolicy(refusal.reason)
    }

    // A GET through the cache, any other method straight to the loader on the caller's coroutine.
    private suspend fun <T : Any> load(endpoint: Endpoint<T>, prepared: Prepared, started: TimeMark): Result<Lookup<T>> {
        val slot = TraceSlot()
        val loader: suspend () -> Result<Lookup<T>> = { loadOnce(endpoint, prepared, slot) }
        val result: Result<Lookup<T>> =
            if (prepared.request.method == HttpMethod.Get) {
                cache.getOrLoad(CacheKey<T>(endpoint.id, prepared.keyParams), CachePolicy(endpoint.ttl) { missTtl = endpoint.missTtl }, prepared.maxAge, loader)
            } else {
                runLoader(loader)
            }
        // The cache port cannot tell a hit from a join; only this call's own loader having run tells the facade that the call started the load.
        val trace = slot.take()
        log.debug(
            "call: endpoint={}, method={}, loaded={}, lane={}, cost={}, waitedMs={}, status={}, bytes={}, elapsedMs={}, outcome={}",
            endpoint.id, prepared.request.method, trace != null, prepared.lane.name, endpoint.cost, trace?.waited.millisOrDash(), trace?.status ?: "-", trace?.bytes ?: "-",
            started.elapsedNow().inWholeMilliseconds, outcomeOf(result),
        )
        return result
    }

    private suspend fun <T : Any> loadOnce(endpoint: Endpoint<T>, prepared: Prepared, slot: TraceSlot): Result<Lookup<T>> {
        val sent = dispatch.send(prepared.request, endpoint.cost, prepared.lane, prepared.maxWait)
        val s = sent.getOrNull()
        slot.put(LoadTrace(s?.waited, s?.response?.status, s?.response?.bodySize))
        return sent.flatMap { classify(endpoint, it) }
    }

    private suspend fun <T : Any> classify(endpoint: Endpoint<T>, sent: Sent): Result<Lookup<T>> =
        when (verdictOf(sent.response.status, sent.signals.throttled, endpoint.misses)) {
            Verdict.Throttled -> Result.failure(dispatch.throttleOf(sent, endpoint.id))
            Verdict.Missing -> Result.success(Lookup.Missing)
            Verdict.Decode -> decoded(endpoint, sent.response)
            Verdict.Rejected -> Result.failure(UpstreamFailure.UpstreamError(sent.response.status, sent.response.headers, sent.response.body()))
        }

    // The decoder's HttpFailure passes through; any other exception, returned or thrown, is a DecodeFailure (logged once, here); an Error propagates.
    private fun <T : Any> decoded(endpoint: Endpoint<T>, response: HttpResponse): Result<Lookup<T>> =
        catchNonFatal { endpoint.decode(response) }.flatMap { it }.fold(
            onSuccess = { value -> Result.success<Lookup<T>>(Lookup.Found(value)) },
            onFailure = { cause ->
                if (cause is HttpFailure) {
                    Result.failure(cause)
                } else {
                    log.debug("decode failed: endpoint={}, cause={}", endpoint.id, cause::class.simpleName)
                    Result.failure(HttpFailure.DecodeFailure(cause))
                }
            },
        )

    private fun outcomeOf(result: Result<Lookup<*>>): String = result.fold(
        onSuccess = { lookup -> if (lookup is Lookup.Found) "Found" else "Missing" },
        onFailure = { failure -> failure::class.simpleName ?: "failure" },
    )
}

/**
 * Builds an [UpstreamClient] over a transport the caller owns. **Stable Core.** The client never closes the transport. Without `cache(...)` it uses `ResponseCache.Disabled`, without a budget
 * `CallBudget.Unlimited`; a `budget(windows)` is built and owned. On the JVM, `UpstreamClient(baseUrl, userAgent)` builds and owns the transport, a Caffeine cache and the budget instead.
 *
 * @param transport the transport; the caller closes it.
 * @param baseUrl the base URL of the upstream; no query. Every call extends its path.
 * @param configure sets the policy, clocks, dispatcher, reader, default wait, default headers, cache and budget; the defaults when omitted.
 * @return the client.
 * @throws IllegalArgumentException if `defaultMaxWait` is negative, [baseUrl] has a query or is refused by the policy (the message names the `RefusalReason`), a default header is one the transport controls
 *   or, under an `Untrusted` policy, is sensitive, or a budget built from `budget(windows)` is refused; the message is constant and never echoes a URL, a header value or a value.
 */
public fun UpstreamClient(transport: HttpTransport, baseUrl: Url, configure: UpstreamClientConfig.() -> Unit = {}): UpstreamClient {
    val resolved = UpstreamClientConfig(baseUrl).apply(configure).resolve()
    val budget = resolved.buildBudget()
    return UpstreamClient(ClientParts(resolved, transport, resolved.cache ?: ResponseCache.Disabled, budget.budget, Owned(cache = false, budget = budget.owned, transport = false)))
}
