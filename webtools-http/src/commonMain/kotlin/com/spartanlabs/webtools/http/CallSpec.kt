package com.spartanlabs.webtools.http

import kotlin.time.Duration

/**
 * The per-call settings of [UpstreamClient.call]: the receiver of its lambda. **Stable Core.** Every property is seeded, so reading it inside the lambda gives the
 * effective value (`maxWait = maxWait * 2` doubles the client's default). Not thread-safe; use it only inside the lambda.
 */
@WebToolsDsl
public class CallSpec internal constructor(lane: Lane, maxWait: Duration, defaultHeaders: HttpHeaders) {
    /** The request method; `GET` by default. Only `GET` goes through the cache. */
    public var method: HttpMethod = HttpMethod.Get

    /** The request body; `null` (none) by default. A body on `GET` is an `InvalidRequest`. */
    public var body: RequestBody? = null

    /** The lane this call is charged to; the endpoint's lane by default. */
    public var lane: Lane = lane

    /** The longest the call waits for a budget permit before failing with `BudgetExhausted`; the client's `defaultMaxWait` by default. Not negative. */
    public var maxWait: Duration = maxWait

    /** The oldest cached answer this call accepts; [Duration.INFINITE] (any live entry) by default. Not negative. */
    public var maxAge: Duration = Duration.INFINITE

    private val segments: MutableList<String> = ArrayList()
    private val pairs: MutableList<Pair<String, String>> = ArrayList()
    private val headerBuilder: HttpHeaders.Builder = HttpHeaders.Builder().also { it.addAll(defaultHeaders) }

    /**
     * Appends raw path segments, each encoded when the request is built (a `/`, `?` or `%` inside one is encoded, never interpreted).
     * A `.`, `..` or empty segment, or one with an unpaired surrogate, makes the call an `InvalidRequest`.
     *
     * @param segments the raw segments, in order.
     */
    public fun path(vararg segments: String) { this.segments.addAll(segments) }

    /**
     * Appends one raw query pair, name and value each encoded when the request is built. A repeated name is allowed; the order is kept.
     *
     * @param name the raw name.
     * @param value the raw value.
     */
    public fun query(name: String, value: String) { pairs.add(name to value) }

    /**
     * Edits this call's request headers, which start as the client's default headers: `add` appends, `set` and `setSensitive` replace, `remove` drops.
     * Several blocks edit the same builder. Per-call headers are **not** part of the cache key: do not use one that changes the answer on a cached endpoint.
     *
     * @param configure the edits; a bad name or value throws `IllegalArgumentException`, which the call turns into an `InvalidRequest`.
     */
    public fun headers(configure: HttpHeaders.Builder.() -> Unit) { headerBuilder.configure() }

    internal fun pathSegments(): List<String> = segments.toList()
    internal fun queryPairs(): List<Pair<String, String>> = pairs.toList()
    internal fun requestHeaders(): HttpHeaders = headerBuilder.build()
}
