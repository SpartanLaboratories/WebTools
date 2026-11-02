package com.spartanlabs.webtools.http

import okhttp3.Call
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.TimeSource

/** The name of the transport's logger. */
internal const val TRANSPORT_LOGGER_NAME: String = "com.spartanlabs.webtools.http.OkHttpTransport"

/** The transport's logger, shared by every transport file (the dispatcher thread factory included). */
internal val transportLog: Log = logger(TRANSPORT_LOGGER_NAME)

/** The largest body a `ByteArray` can hold. */
internal const val MAX_BODY_BYTES: Long = Int.MAX_VALUE - 8L

/** The constant message for a refused `userAgent`; it never echoes the value. */
internal const val USER_AGENT_MESSAGE: String =
    "userAgent must be non-blank, visible ASCII (U+0020 to U+007E) and without a leading or trailing space."

/** The constant message for a `maxResponseBytes` above [MAX_BODY_BYTES]. */
internal const val MAX_RESPONSE_BYTES_MESSAGE: String =
    "maxResponseBytes must not exceed 2147483639 bytes, the largest body a byte array can hold."

/** The `InvalidRequest` rule for the build backstop: a hop-1 request OkHttp's builder refused. */
internal const val BUILD_RULE: String = "the request could not be built"

/**
 * Whether [userAgent] is accepted by the factory: non-blank, every character in U+0020..U+007E, no leading or trailing space.
 *
 * @param userAgent the offered value.
 * @return `true` when it is accepted.
 */
internal fun isValidUserAgent(userAgent: String): Boolean =
    userAgent.isNotBlank() && userAgent.all { it in ' '..'~' } && !userAgent.startsWith(' ') && !userAgent.endsWith(' ')

/**
 * The supplied [HttpTransport]: OkHttp 5 under the outbound-URL policy's rules, with manual redirects, a capped body, and never a retry.
 * Built with the factory `OkHttpTransport(userAgent, policy)`; usable without any opt-in (it is a supplied default, not a seam), and `final`:
 * every number comes from `policy.settings`, there is no builder and nothing to override.
 *
 * **Stable Core.**
 *
 * - **Main-safe.** [execute] suspends; every blocking step (DNS, connect, TLS, the write, the read of the body) runs on the transport's own
 *   daemon dispatcher threads, named `webtools-http-dispatcher-<n>`, never on the caller's thread or dispatcher.
 * - **Never retries.** One `Call` per hop, one request per `Call`. A dropped pooled connection, a `408`, a `421`, a `503` with `Retry-After: 0`
 *   or a `429` is returned (as the response it is, every header untouched) or failed once; OkHttp's own re-send paths are turned off and a
 *   network interceptor refuses any second exchange of one hop with [TransportFailure.RetryRefused].
 * - **The caps**, from `policy.settings`: `maxResponseBytes` bounds the decoded body ([TransportFailure.BodyTooLarge], with the declared length
 *   when the server declared one above the cap, decided before any byte is read); `maxRedirects` bounds the hops ([TransportFailure.TooManyRedirects];
 *   `0` follows none); `callTimeout` is one deadline across every hop ([TransportFailure.Timeout]); `connectTimeout`, `readTimeout` and
 *   `writeTimeout` apply per socket operation.
 * - **Controlled headers.** A request carrying `User-Agent`, `Accept-Encoding`, `Host`, `Connection`, `Content-Length`, `Transfer-Encoding`,
 *   `Keep-Alive`, `Proxy-Connection`, `TE`, `Trailer`, `Upgrade` or `Expect` (any case) fails with [TransportFailure.InvalidRequest] naming the
 *   first such header, before anything is sent. `Range` is allowed: on a hop that carries it the transport sends `Accept-Encoding: identity`, so
 *   nothing is decoded; a server that sends a content coding anyway is answered with the bytes as received.
 * - **Redirects.** `301`, `302`, `303`, `307` and `308` are followed for `GET` and `HEAD` only, the method kept (`HEAD` stays `HEAD` after a
 *   `303`), with no body and no `Referer`; every target is resolved and judged by the policy (a foreign scheme, an unresolvable target or a
 *   refused hop is [TransportFailure.RefusedByPolicy]); every sensitive header (one added as sensitive, `Authorization`, `Proxy-Authorization`,
 *   `Cookie`, `Set-Cookie`) is dropped for good once the scheme, host or port changes. Any other `3xx` (`300` included), a `3xx` to any other
 *   method, or one with no single usable `Location`, is the response. A followed redirect's body is never read, so its connection is not reused.
 *   [HttpResponse.url] is the last URL requested; [HttpResponse.redirectChain] lists each followed hop.
 * - **Received entries.** A header whose name is not a token is dropped; CR, LF and NUL in a value become SP; a status outside 100..599 is
 *   [TransportFailure.Protocol]; any status in 100..599 is a response (a `304` included).
 * - **Failures** are returned inside the `Result`, never thrown: the [TransportFailure] leaves, [HttpFailure.Closed] after [close].
 *   Cancellation of the calling coroutine cancels the call, stops a body read at once, and is thrown as `CancellationException`.
 * - **`close()`** is idempotent, non-suspending and never throws: it cancels every call in flight (each ends with `Closed("transport")`), shuts the
 *   dispatcher down and evicts the idle connections. The dispatcher's threads are daemon threads, so a transport that is never closed does not
 *   keep the JVM alive; `close()` is still the supported stop.
 * - **Thread safety.** [execute] may be called concurrently from many coroutines on any dispatcher; the only mutable state is the closed flag.
 * - **Logging**, under `com.spartanlabs.webtools.http.OkHttpTransport`: `INFO` once on creation (the policy kind and its settings, numbers only)
 *   and once on `close()`; `DEBUG` per call, per followed hop and per failure; `WARN` once for a policy refusal and for a refused second
 *   exchange. Never a header, a body, a query string, a credential or a resolved address.
 * - **Proxies.** An `Untrusted` policy pins the direct route and ignores every JVM-wide proxy setting; a `Trusted` policy keeps OkHttp's default
 *   proxy selection, so the JVM's `http.proxyHost` and `socksProxyHost` apply.
 *
 * The constructor that takes a guard, a base resolver and a donor client is not public: it exists for the test fixtures (`LoopbackTransports`)
 * and this module's own tests. Its last two parameters, the clock of the shared deadline and the factory of each hop's `Call`, are for this
 * module's tests only (a manual clock and fake calls); every other caller leaves them at their defaults, `TimeSource.Monotonic` and the
 * transport's own client.
 */
public class OkHttpTransport internal constructor(
    private val userAgent: String,
    private val guard: UrlGuard,
    baseDns: Dns,
    donor: OkHttpClient?,
    private val timeSource: TimeSource = TimeSource.Monotonic,     // the shared deadline's clock; a test passes a manual one
    callFactory: Call.Factory? = null,                             // each hop's Call; null is this transport's own client
) : HttpTransport {
    private val settings: TransportSettings = guard.policy.settings
    private val policyKind: String = when (guard.policy) {
        is OutboundUrlPolicy.Trusted -> "Trusted"
        is OutboundUrlPolicy.Untrusted -> "Untrusted"
    }

    // Validated before the client is built, so a refusal leaves no thread or pool behind.
    init {
        require(isValidUserAgent(userAgent)) { USER_AGENT_MESSAGE }
        require(settings.maxResponseBytes <= MAX_BODY_BYTES) { MAX_RESPONSE_BYTES_MESSAGE }
    }

    private val closed = AtomicBoolean(false)
    private val http: TransportClient = newTransportClient(settings, guard.enforcement(baseDns), donor)

    // A substitute's calls are not this client's: no resolver or socket guard, no ExchangeGuard, and close() cannot reach them. Tests only.
    private val hopCalls: Call.Factory = callFactory ?: http.client

    init {
        transportLog.info("created: policy={}, settings={}", policyKind, settings)
    }

    /**
     * Sends [request] and returns the response, following redirects as the class description says.
     *
     * @param request the request to send.
     * @return the response for any status in 100..599, or a failure holding a [TransportFailure] or, after [close], [HttpFailure.Closed].
     *   Cancellation is thrown, never returned.
     */
    override suspend fun execute(request: HttpRequest): Result<HttpResponse> =
        catchNonFatal { fetch(request) }.fold(onSuccess = { it }, onFailure = { unexpected(it) })

    /**
     * Closes the transport: every call in flight ends with `Closed("transport")`, the dispatcher is shut down and the idle connections are
     * evicted. Idempotent, non-suspending, never throws.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        listOf<Pair<String, () -> Unit>>(
            "cancelAll" to { http.dispatcher.cancelAll() },
            "shutdown" to { http.dispatcher.executorService.shutdown() },          // an orderly shutdown, never the interrupting one: an interrupt would leave HTTP/2 streams half-closed
            "evictAll" to { http.pool.evictAll() },
        ).forEach { (step, action) ->
            catchNonFatal(action).onFailure { transportLog.debug("close step {} failed: {}", step, it.javaClass.name) }
        }
        transportLog.info("closed")
    }

    override fun toString(): String = "OkHttpTransport(policy=$policyKind, closed=${closed.get()})"

    //region the call
    private class Hop(val url: Url, val headers: HttpHeaders, val body: RequestBody?, val chain: List<RedirectHop>)

    private suspend fun fetch(request: HttpRequest): Result<HttpResponse> {
        if (closed.get()) return closedResult()
        // The controlled-header check precedes the policy check, and both precede any socket.
        TransportControlledHeaders.ruleFor(request.headers)?.let { rule ->
            return failed(request.url, plainFailure(TransportFailure.InvalidRequest(rule)))
        }
        guard.checkRequest(request)?.let { refusal ->
            return failed(request.url, refusalFailure(refusal, RefusalStage.Request))
        }
        return follow(request.method, Hop(request.url, request.headers, request.body, emptyList()), CallDeadline(settings.callTimeout, timeSource))
    }

    //region the hop loop
    private tailrec suspend fun follow(method: HttpMethod, hop: Hop, deadline: CallDeadline): Result<HttpResponse> {
        if (closed.get()) return closedResult()
        // The deadline is checked at the top of each hop, so a Follow decided past it is a Timeout while every other outcome keeps its own.
        val remaining = deadline.remainingMillis()
        if (remaining < 1) return failed(hop.url, plainFailure(TransportFailure.Timeout()))
        val built = hopRequest(method, hop.url, hop.headers, hop.body, userAgent).getOrElse {
            // Hop 1 cannot have been sent; a later hop's build cannot fail by construction, and would be Io if it did.
            val leaf = if (hop.chain.isEmpty()) TransportFailure.InvalidRequest(BUILD_RULE) else TransportFailure.Io()
            return failed(hop.url, plainFailure(leaf))
        }
        val call = hopCalls.newCall(built)
        call.timeout().timeout(remaining, TimeUnit.MILLISECONDS)          // before enqueue; whole milliseconds, at least 1
        val context = HopContext(method, hop.url, hop.chain, settings, guard)
        val outcome = awaitHop(
            call,
            handleResponse = { response ->
                catchNonFatal { settleResponse(context, response) { e -> failureOf(context, call, Phase.Body, e) } }
                    .getOrElse { e -> HopOutcome.Failed(failureOf(context, call, Phase.Body, e)) }
            },
            handleFailure = { e -> HopOutcome.Failed(failureOf(context, call, Phase.Exchange, e)) },
        )
        if (outcome is HopOutcome.Redirect) {
            transportLog.debug("redirect {} -> {} ({})", hop.url.redacted(), outcome.next.redacted(), outcome.status)
            // The next request is built from the admitted Url's text, so the judged URL and the connected URL are the same.
            val next = Hop(outcome.next, headersForHop(hop.headers, hop.url, outcome.next), null, hop.chain + RedirectHop(hop.url, outcome.status))
            return follow(method, next, deadline)
        }
        return finish(method, hop, deadline, outcome)
    }
    //endregion

    private fun finish(method: HttpMethod, hop: Hop, deadline: CallDeadline, outcome: HopOutcome): Result<HttpResponse> = when (outcome) {
        is HopOutcome.Done -> {
            transportLog.debug(
                "{} {} -> {} ({} bytes, {} hops, {} ms)",
                method, outcome.response.url.redacted(), outcome.response.status, outcome.response.bodySize,
                hop.chain.size + 1, deadline.elapsed().inWholeMilliseconds,
            )
            Result.success(outcome.response)
        }
        is HopOutcome.Failed -> failed(hop.url, outcome.mapped)
        // Redirect is consumed by `follow` and Abandoned follows a caller cancellation, which is thrown, not returned: neither arrives here.
        is HopOutcome.Redirect, HopOutcome.Abandoned -> failed(hop.url, plainFailure(TransportFailure.Io()))
    }

    private fun failureOf(context: HopContext, call: Call, phase: Phase, e: Throwable): MappedFailure =
        mapFailure(e, FailureContext(context.url.host, context.url.scheme == "https", closed.get(), call.isCanceled(), phase))

    private fun failed(url: Url, mapped: MappedFailure): Result<HttpResponse> {
        transportLog.report(url, mapped)
        return Result.failure(mapped.failure)
    }

    private fun closedResult(): Result<HttpResponse> = Result.failure(HttpFailure.Closed("transport"))

    private fun unexpected(e: Throwable): Result<HttpResponse> {
        transportLog.debug("execute failed unexpectedly: {}", e.javaClass.name)
        return Result.failure(TransportFailure.Io())
    }
    //endregion
}

/**
 * Builds an [OkHttpTransport] for [policy]: the system resolver, a fresh client, dispatcher and connection pool. The caller closes it.
 *
 * **Stable Core.** Both parameters are required and every number lives in `policy.settings`; derive a policy with other settings before
 * calling (`OutboundUrlPolicy.untrusted { settings { callTimeout = 20.seconds } }`).
 *
 * @param userAgent the `User-Agent` sent on every hop: non-blank, visible ASCII (U+0020..U+007E), no leading or trailing space.
 * @param policy the outbound-URL policy whose rules and settings the transport enforces.
 * @return a new transport.
 * @throws IllegalArgumentException if [userAgent] breaks its rule, or `policy.settings.maxResponseBytes` exceeds `Int.MAX_VALUE - 8`; the
 *   message is constant and never echoes a value.
 */
public fun OkHttpTransport(userAgent: String, policy: OutboundUrlPolicy): OkHttpTransport =
    OkHttpTransport(userAgent, UrlGuard(policy), Dns.SYSTEM, null)
