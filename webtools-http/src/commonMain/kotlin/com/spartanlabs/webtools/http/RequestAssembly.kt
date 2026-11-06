package com.spartanlabs.webtools.http

import kotlin.time.Duration

// The pure part of a facade's call: applying the consumer's settings, checking them and assembling the request. Each function returns a `Result`
// and never throws; none logs (the facade logs an `InvalidRequest` once, where it surfaces).

/** A call's URL and the cache-key parameters that describe it. */
internal class AssembledUrl(val url: Url, val keyParams: List<Pair<String, String>>)

/** A call ready to run: the request, its settings and its key parameters. */
internal class Prepared(val request: HttpRequest, val lane: Lane, val maxWait: Duration, val maxAge: Duration, val keyParams: List<Pair<String, String>>)

/** The reserved key-parameter name that carries the request path. An encoded query name never contains `/`. */
internal const val PATH_KEY_NAME: String = "/"

private fun <R> invalid(rule: String, cause: Throwable? = null): Result<R> = Result.failure(TransportFailure.InvalidRequest(rule, cause))

/** Applies the consumer's [configure] to [spec]; a non-fatal exception becomes `InvalidRequest(RULE_SETTINGS)` with that exception as its cause. */
internal fun <S> applySettings(spec: S, configure: S.() -> Unit): Result<S> =
    catchNonFatal { spec.configure() }.fold(onSuccess = { Result.success(spec) }, onFailure = { invalid(RULE_SETTINGS, it) })

/** `maxWait` first, then `maxAge`; `null` when both are fine. */
internal fun callLimitFailure(maxWait: Duration, maxAge: Duration): TransportFailure.InvalidRequest? = when {
    maxWait.isNegative() -> TransportFailure.InvalidRequest(RULE_MAX_WAIT)
    maxAge.isNegative() -> TransportFailure.InvalidRequest(RULE_MAX_AGE)
    else -> null
}

/** `cost` first, then `maxWait`; `null` when both are fine. */
internal fun fetchLimitFailure(cost: Int, maxWait: Duration): TransportFailure.InvalidRequest? = when {
    cost < 1 -> TransportFailure.InvalidRequest(RULE_COST)
    maxWait.isNegative() -> TransportFailure.InvalidRequest(RULE_MAX_WAIT)
    else -> null
}

/** `InvalidRequest` naming the first header the transport controls, or `null`. */
internal fun controlledHeaderFailure(headers: HttpHeaders): TransportFailure.InvalidRequest? =
    TransportControlledHeaders.ruleFor(headers)?.let { TransportFailure.InvalidRequest(it) }

/** `Result.success(this)` when [failure] is `null`, else `Result.failure(failure)`. */
internal fun <S> S.unless(failure: TransportFailure?): Result<S> = failure?.let { Result.failure<S>(it) } ?: Result.success(this)

/** Builds the request; an `IllegalArgumentException` from `HttpRequest(...)` becomes `InvalidRequest(RULE_REQUEST)` with that exception as its cause. */
// The parameters are named `requestHeaders` and `requestBody` because inside the builder's lambda `headers` and `body` are the builder's own properties.
internal fun buildRequest(method: HttpMethod, url: Url, requestHeaders: HttpHeaders, requestBody: RequestBody?): Result<HttpRequest> =
    catchNonFatal { HttpRequest(method, url) { headers = requestHeaders; body = requestBody } }
        .fold(onSuccess = { Result.success(it) }, onFailure = { invalid(RULE_REQUEST, it) })

/**
 * Assembles the URL as text and parses it again, so one parser and one canonical form decide the result: the base text (it has no query), with one
 * trailing `/` dropped when segments follow, then `/` and the encoded segments joined by `/`, then `?` and the encoded pairs joined by `&`. An empty
 * segment fails with [RULE_EMPTY_SEGMENT], a `.` or `..` with [RULE_DOT_SEGMENT], an unpaired surrogate with [RULE_SEGMENT_UNICODE] (a segment) or
 * [RULE_QUERY_UNICODE] (a query name or value), and a text that does not parse with [RULE_URL]. The key parameters are the parsed URL's path under
 * [PATH_KEY_NAME], then the encoded pairs in call order.
 */
internal fun assembleUrl(base: Url, segments: List<String>, query: List<Pair<String, String>>): Result<AssembledUrl> =
    segments.traverse(::encodeSegment).flatMap { encodedSegments ->
        query.traverse(::encodePair).flatMap { encodedPairs ->
            val baseText = base.toString()
            val withPath = if (encodedSegments.isEmpty()) baseText else baseText.removeSuffix("/") + "/" + encodedSegments.joinToString("/")
            val text = if (encodedPairs.isEmpty()) withPath else withPath + "?" + encodedPairs.joinToString("&") { (name, value) -> "$name=$value" }
            Url.parse(text).fold(
                onSuccess = { url -> Result.success(AssembledUrl(url, listOf(PATH_KEY_NAME to url.encodedPath) + encodedPairs)) },
                onFailure = { invalid(RULE_URL, it) },
            )
        }
    }

private fun encodeSegment(segment: String): Result<String> =
    if (segment.isEmpty()) invalid(RULE_EMPTY_SEGMENT)
    else encodePathSegment(segment).fold(
        onSuccess = { Result.success(it) },
        onFailure = { invalid(if ((it as? UrlParseException)?.problem == UrlProblem.DotSegment) RULE_DOT_SEGMENT else RULE_SEGMENT_UNICODE, it) },
    )

private fun encodePair(pair: Pair<String, String>): Result<Pair<String, String>> =
    encodeQueryComponent(pair.first).flatMap { name -> encodeQueryComponent(pair.second).map { value -> name to value } }
        .fold(onSuccess = { Result.success(it) }, onFailure = { invalid(RULE_QUERY_UNICODE, it) })

// Maps every element through [transform], stopping at the first failure; small lists only.
private fun <A, B> List<A>.traverse(transform: (A) -> Result<B>): Result<List<B>> =
    fold(Result.success(emptyList<B>())) { done, item -> done.flatMap { list -> transform(item).map { list + it } } }
