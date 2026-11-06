package com.spartanlabs.webtools.http

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Fetches administrator-supplied URLs: checks the URL against the outbound-URL policy first, then charges the call budget, sends the request through the
 * transport and returns the response. **Stable Core.** No endpoint, no decode and no cache: an administrator-supplied URL population is an unbounded key space and a conditional
 * GET is its cache.
 *
 * **Every status is the response**, a `304` and a returned `3xx` included, except a throttle (a `429`, or what the configured reader reports as throttled), which is
 * [UpstreamFailure.RateLimited] because the budget has already paused. [RateLimited.retryAfter][UpstreamFailure.RateLimited.retryAfter] is the longer of the server's `Retry-After` and the
 * budget's pause. A refusal by the policy costs nothing: no permit and no call. Nothing is retried.
 *
 * The fetcher closes the transport only when a JVM owning factory (or `LoopbackFetchers`) built it, and never a budget. [close] is non-suspending, idempotent, never throws and safe from any thread;
 * a fetch after it returns `Closed("fetcher")`. Every member may be called concurrently.
 */
public class UrlFetcher internal constructor(parts: FetcherParts) : AutoCloseable {
    private val transport: HttpTransport = parts.transport
    private val guard: UrlGuard = parts.guard
    private val budget: CallBudget = parts.resolved.budget
    private val timeSource: TimeSource = parts.resolved.timeSource
    private val defaultMaxWait: Duration = parts.resolved.defaultMaxWait
    private val ownsTransport: Boolean = parts.ownsTransport
    private val log: Log = logger(URL_FETCHER_LOGGER_NAME)
    private val dispatch = CallDispatch(transport, budget, parts.resolved.rateSignals, parts.resolved.wallClock, timeSource, log)

    // The closed flag: `complete()` returns true for exactly one caller (`commonMain` has no atomics).
    private val closing: CompletableJob = Job()

    init {
        log.info("created: policy={}, budget={}, ownsTransport={}, defaultMaxWait={}", policyKind(guard.policy), kindOf(budget), ownsTransport, defaultMaxWait)
    }

    /**
     * Fetches [request].
     *
     * @param request the request; its headers must not hold a header the transport controls.
     * @param configure sets the cost, lane and `maxWait` of this fetch; the defaults when omitted.
     * @return the response for any status but a throttle; otherwise a failure: [HttpFailure.Closed] (`"fetcher"`), [TransportFailure.InvalidRequest] (a bad spec or a controlled header),
     *   [TransportFailure.RefusedByPolicy], [HttpFailure.BudgetExhausted], [HttpFailure.Closed] (`"budget"` or `"transport"`), any [TransportFailure], or [UpstreamFailure.RateLimited].
     * @throws kotlinx.coroutines.CancellationException if the caller is cancelled.
     * @throws Error if a component throws one.
     */
    public suspend fun fetch(request: HttpRequest, configure: FetchSpec.() -> Unit = {}): Result<HttpResponse> {
        if (closing.isCompleted) return Result.failure(HttpFailure.Closed(FETCHER_COMPONENT))
        val started = timeSource.markNow()
        return applySettings(FetchSpec(defaultMaxWait), configure)
            .flatMap { spec -> spec.unless(fetchLimitFailure(spec.cost, spec.maxWait)) }
            .flatMap { spec -> spec.unless(controlledHeaderFailure(request.headers)) }
            .onFailure { failure -> if (failure is TransportFailure.InvalidRequest) log.debug("invalid request: rule={}", failure.rule) }
            .flatMap { spec -> spec.unless(refusalOf(request)) }
            .fold(onSuccess = { spec -> load(request, spec, started) }, onFailure = { Result.failure(it) })
    }

    // The pre-check: a refusal costs nothing and is logged once, here, where the failure is created.
    private fun refusalOf(request: HttpRequest): TransportFailure.RefusedByPolicy? = guard.checkRequest(request)?.let { refusal ->
        log.warn("refused by policy: reason={}, rangeClass={}, url={}", refusal.reason.name, refusal.rangeClass ?: "-", request.url.redacted())
        TransportFailure.RefusedByPolicy(refusal.reason)
    }

    private suspend fun load(request: HttpRequest, spec: FetchSpec, started: TimeMark): Result<HttpResponse> {
        val sent = dispatch.send(request, spec.cost, spec.lane, spec.maxWait)
        val result: Result<HttpResponse> = sent.flatMap { s ->
            when (fetchVerdictOf(s.response.status, s.signals.throttled)) {
                FetchVerdict.Throttled -> Result.failure(dispatch.throttleOf(s, request.url.redacted()))
                FetchVerdict.Delivered -> Result.success(s.response)
            }
        }
        val s = sent.getOrNull()
        log.debug(
            "fetch: method={}, url={}, lane={}, cost={}, waitedMs={}, status={}, bytes={}, elapsedMs={}",
            request.method, request.url.redacted(), spec.lane.name, spec.cost, s?.waited.millisOrDash(), s?.response?.status ?: "-", s?.response?.bodySize ?: "-", started.elapsedNow().inWholeMilliseconds,
        )
        return result
    }

    /** Closes the transport when this fetcher built it; idempotent, non-suspending, never throws. */
    override fun close() {
        if (!closing.complete()) return
        closeInOrder(listOf("transport" to transport.takeIf { ownsTransport }), log)
        log.info("closed")
    }

    override fun toString(): String =
        "UrlFetcher(policy=${policyKind(guard.policy)}, budget=${kindOf(budget)}, ownsTransport=$ownsTransport, closed=${closing.isCompleted})"
}

/**
 * Builds a [UrlFetcher] over a transport the caller owns. **Stable Core.** The fetcher never closes the transport. [policy] is the pre-check's; the
 * transport enforces its own, so pass the same policy to both.
 *
 * @param transport the transport; the caller closes it.
 * @param policy the outbound-URL policy the pre-check applies.
 * @param configure sets the budget, reader, clocks and `defaultMaxWait`; the defaults when omitted.
 * @return the fetcher.
 * @throws IllegalArgumentException if `defaultMaxWait` is negative.
 */
public fun UrlFetcher(transport: HttpTransport, policy: OutboundUrlPolicy, configure: UrlFetcherConfig.() -> Unit = {}): UrlFetcher =
    UrlFetcher(FetcherParts(transport, UrlGuard(policy), UrlFetcherConfig().apply(configure).resolve(), ownsTransport = false))
