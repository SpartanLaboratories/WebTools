package com.spartanlabs.webtools.feeds

import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.FetchSpec
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlFetcher
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Fetches feeds with a stateless conditional GET over a [UrlFetcher]. **Stable Core.**
 *
 * **What a fetch does.** It sends `GET url` with `Accept` and, when the validators you pass hold them, `If-None-Match` and
 * `If-Modified-Since` exactly as held, through `UrlFetcher.fetch` with your per-fetch block. A `200` or `203` is parsed and
 * returned as [FeedFetch.Fetched] with the response's validators (`FeedValidators.None` when it carries neither; a header that
 * appears more than once counts as absent). A `304` to a conditional request is [FeedFetch.NotModified] with the sent
 * validators, each one the `304` carries replacing its counterpart. Every other status, a `304` to an unconditional request
 * and every other `2xx` included, is `UpstreamError(status, headers, body)`. Both outcomes carry `finalUrl` (the URL the
 * response came from) and `permanentlyMovedTo` (the target of the leading run of `301`/`308` hops, or `null`).
 *
 * **What the caller owns.** Storing the validators after every response; dropping them when `finalUrl` differs from the one
 * stored with them (a validator from one server must not reach another); deciding whether to adopt `permanentlyMovedTo` (a
 * fact about the redirect chain, not an instruction: adopt it only when the chain's first request was `https`, or after the
 * same value was reported on several polls, because one injected `301` over cleartext would re-point the subscription);
 * polling and back-off; the meaning of `410`; what to show on the first poll; and which entries are new (by `id`, never by
 * date).
 *
 * **Failures** are values in the `Result`. Every failure of the `UrlFetcher` passes through unchanged (`RefusedByPolicy`,
 * `RateLimited`, `BudgetExhausted`, transport failures, `Closed("fetcher")` of a `UrlFetcher` the caller closed);
 * `UpstreamError` for a status that is not a feed; a [FeedFailure] for a document that is not; `DecodeFailure` for a parser
 * that threw or returned a failure that is not an `HttpFailure`; `Closed("feed fetcher")` after [close].
 *
 * **Limits.** A feed over the transport's decoded-body cap (2 MiB under `Untrusted`) fails with `BodyTooLarge`, and one with
 * more than 1,000 entries (podcasts) with `LimitExceeded(Entries)`; with an `Untrusted` policy a feed that redirects from
 * `https` to `http` is refused (`allowHttpsToHttp` is off). Parses are bounded by `maxConcurrentParses` permits and run on
 * `parseDispatcher`; a parse cannot be cancelled, so a cancelled caller returns when its parse finishes.
 *
 * **Lifecycle.** [close] is non-suspending, idempotent, never throws and closes only a `UrlFetcher` this fetcher built (the
 * owning factories); every member may be called concurrently; a fetch after [close] returns `Closed("feed fetcher")`.
 *
 * **Logging**, under `com.spartanlabs.webtools.feeds.FeedFetcher`: `INFO` on creation and on close, one `DEBUG` line per
 * fetch, a `WARN` for a refused document. Every URL this module prints is scheme, host and port only. **The [UrlFetcher] it
 * composes, and the transport under it, print a URL with `Url.redacted()`, which hides the query and shows the path, so a
 * secret carried in a feed URL's path appears in their lines.**
 *
 * **Heap.** About 128 MB of headroom covers the default two concurrent parses of one fetcher; the figure grows with the sum of
 * every fetcher's `maxConcurrentParses` in the process.
 */
public class FeedFetcher internal constructor(parts: FeedFetcherParts) : AutoCloseable {
    private val urlFetcher: UrlFetcher = parts.urlFetcher
    private val parser: FeedParser = parts.resolved.parser
    private val parseDispatcher: CoroutineDispatcher = parts.resolved.parseDispatcher
    private val accept: String = parts.resolved.accept
    private val maxConcurrentParses: Int = parts.resolved.maxConcurrentParses
    private val ownsUrlFetcher: Boolean = parts.ownsUrlFetcher
    private val parses = Semaphore(maxConcurrentParses)
    private val clock: TimeSource = TimeSource.Monotonic
    private val log: Log = logger(FEED_FETCHER_LOGGER_NAME)

    // The closed flag: `complete()` returns true for exactly one caller (`commonMain` has no atomics).
    private val closing: CompletableJob = Job()

    init {
        log.info("created: parser={}, parseDispatcher={}, maxConcurrentParses={}, ownsUrlFetcher={}", kindOf(parser), kindOf(parseDispatcher), maxConcurrentParses, ownsUrlFetcher)
    }

    /**
     * As the overload with validators, with [FeedValidators.None]: an unconditional request, so a `304` is an `UpstreamError`.
     *
     * @param url the feed's URL.
     * @param configure sets the cost, lane and `maxWait` of this fetch, passed to `UrlFetcher.fetch`.
     * @return [FeedFetch.Fetched], or a failure (see the class description).
     */
    public suspend fun fetch(url: Url, configure: FetchSpec.() -> Unit = {}): Result<FeedFetch> = fetch(url, FeedValidators.None, configure)

    /**
     * Fetches [url] conditionally.
     *
     * @param url the feed's URL.
     * @param validators what the last response gave (or [FeedValidators.None]); sent verbatim.
     * @param configure sets the cost, lane and `maxWait` of this fetch, passed to `UrlFetcher.fetch`.
     * @return [FeedFetch.Fetched] or [FeedFetch.NotModified], or a failure (see the class description).
     * @throws kotlinx.coroutines.CancellationException if the caller is cancelled (a parse already running is not interrupted).
     * @throws Error if a component throws one.
     */
    public suspend fun fetch(url: Url, validators: FeedValidators, configure: FetchSpec.() -> Unit = {}): Result<FeedFetch> {
        if (closing.isCompleted) return Result.failure(HttpFailure.Closed(FEED_FETCHER_COMPONENT))
        val started = clock.markNow()
        val handled = urlFetcher.fetch(HttpRequest(HttpMethod.Get, url) { headers = requestHeaders(accept, validators) }, configure).fold(
            onSuccess = { response -> answer(url, validators, response) },
            onFailure = { failure -> Handled.failed(failure) },      // unchanged: the same instance
        )
        report(url, handled, started.elapsedNow())
        return handled.result
    }

    private suspend fun answer(url: Url, sent: FeedValidators, response: HttpResponse): Handled = when (statusVerdictOf(response.status, sent.isConditional)) {
        StatusVerdict.Parse -> parsed(url, response)
        StatusVerdict.NotModified -> Handled.of(
            Result.success(FeedFetch.NotModified(sent.refreshedBy(response.headers), response.url, permanentMoveOf(url, response))), response,
        )
        StatusVerdict.Upstream -> Handled.of(
            Result.failure(UpstreamFailure.UpstreamError(response.status, response.headers, response.body())), response,
        )
    }

    // The semaphore bounds the parses in flight; the parse runs on parseDispatcher and cannot be interrupted, so cancellation of
    // the caller is observed when withContext returns, and the permit is held until then.
    private suspend fun parsed(url: Url, response: HttpResponse): Handled {
        val input = FeedInput(response.body(), response.headers[HEADER_CONTENT_TYPE], response.url)
        val outcome = parses.withPermit { withContext(parseDispatcher) { timed(clock) { runParser(input) } } }
        val fetched = outcome.value.map { feed -> FeedFetch.Fetched(feed, validatorsOf(response.headers), response.url, permanentMoveOf(url, response)) }
        return Handled.of(fetched, response, entries = outcome.value.getOrNull()?.entries?.size, parseTime = outcome.elapsed)
    }

    // Exceptions thrown out of parse are DecodeFailure(cause); a failure value that is not an HttpFailure is too; cancellation is
    // rethrown by catchNonFatal; an Error propagates.
    private fun runParser(input: FeedInput): Result<Feed> =
        catchNonFatal { parser.parse(input) }
            .fold(onSuccess = { it }, onFailure = { Result.failure(HttpFailure.DecodeFailure(it)) })
            .fold(onSuccess = { Result.success(it) }, onFailure = { if (it is HttpFailure) Result.failure(it) else Result.failure(HttpFailure.DecodeFailure(it)) })

    // One DEBUG line per fetch with the outcome; a WARN for a refused document. Only origins, numbers and type names are printed.
    private fun report(url: Url, handled: Handled, elapsed: Duration) {
        val fetch = handled.result.getOrNull()
        val failure = handled.result.exceptionOrNull()
        val outcome = when (fetch) {
            is FeedFetch.Fetched -> "Fetched"
            is FeedFetch.NotModified -> "NotModified"
            null -> failure?.let { it::class.simpleName } ?: "unknown"
        }
        if (failure is FeedFailure.Refused) log.warn("refused document: reason={}, target={}", failure.reason.name, url.origin())
        log.debug(
            "fetch: target={}, outcome={}, status={}, bytes={}, entries={}, parseMs={}, elapsedMs={}",
            url.origin(), outcome, handled.status ?: "-", handled.bytes ?: "-", handled.entries ?: "-", handled.parseTime.millisOrDash(), elapsed.inWholeMilliseconds,
        )
    }

    /** Closes the `UrlFetcher` when this fetcher built it; idempotent, non-suspending, never throws. A parse already running is not interrupted. */
    override fun close() {
        if (!closing.complete()) return
        if (ownsUrlFetcher) catchNonFatal { urlFetcher.close() }.onFailure { log.debug("close step failed: {}", it::class.simpleName) }
        log.info("closed")
    }

    override fun toString(): String =
        "FeedFetcher(parser=${kindOf(parser)}, maxConcurrentParses=$maxConcurrentParses, ownsUrlFetcher=$ownsUrlFetcher, closed=${closing.isCompleted})"

    /** The companion exists so that the JVM owning factories can be written as extensions: `FeedFetcher.untrusted(…)`. */
    public companion object
}

/**
 * Builds a [FeedFetcher] over a [UrlFetcher] the caller owns. **Stable Core.** The fetcher never closes it. Share one budget by
 * building the `UrlFetcher` with it.
 *
 * @param urlFetcher the fetcher to compose; the caller closes it.
 * @param configure sets the parser, the parse dispatcher, the parse concurrency and `Accept`; the defaults when omitted.
 * @return the fetcher.
 * @throws IllegalArgumentException if `maxConcurrentParses` is below 1 or `accept` is blank or not a sendable header value.
 */
public fun FeedFetcher(urlFetcher: UrlFetcher, configure: FeedFetcherConfig.() -> Unit = {}): FeedFetcher =
    FeedFetcher(FeedFetcherParts(urlFetcher, FeedFetcherConfig().apply(configure).resolve(), ownsUrlFetcher = false))

// The result of one fetch with the figures its log line prints.
private class Handled(val result: Result<FeedFetch>, val status: Int?, val bytes: Int?, val entries: Int?, val parseTime: Duration?) {
    companion object {
        fun of(result: Result<FeedFetch>, response: HttpResponse, entries: Int? = null, parseTime: Duration? = null): Handled =
            Handled(result, response.status, response.bodySize, entries, parseTime)

        fun failed(failure: Throwable): Handled = Handled(Result.failure(failure), (failure as? UpstreamFailure)?.status, null, null, null)
    }
}

private class Timed<T>(val value: T, val elapsed: Duration)

private inline fun <T> timed(clock: TimeSource, block: () -> T): Timed<T> {
    val mark = clock.markNow()
    val value = block()
    return Timed(value, mark.elapsedNow())
}

/** A duration in whole milliseconds for a log line, or `-` when unknown. */
private fun Duration?.millisOrDash(): String = this?.inWholeMilliseconds?.toString() ?: "-"
