package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest

/**
 * A predicate over an [HttpRequest] that selects the requests a [FakeHttpTransport] stub answers. It is a
 * `fun interface`, so a consumer writes `RequestMatch { it.url.host == "api.example.com" }`; the companion
 * holds the common shapes and [and] combines them.
 *
 * **Stable Core, source compatibility only.** A frozen single-method predicate, not a seam: no opt-in is
 * involved. Path and query text is compared, never validated. [path] compares `Url.encodedPath` exactly with
 * the given (already percent-encoded) path, and matches query pairs after decoding them as OkHttp's
 * `HttpUrl` does (`+` is a space, `%XX` is a byte, a name without `=` has no value): an expected pair
 * matches when some decoded pair equals it, order and extra pairs are ignored, and a `null`-valued pair never
 * matches an expected pair. A matcher runs under the fake's lock and must not block.
 */
public fun interface RequestMatch {
    /**
     * Decides whether a request is accepted.
     *
     * @param request the request the fake received.
     * @return whether [request] is accepted.
     */
    public fun matches(request: HttpRequest): Boolean

    /**
     * Combines two matches.
     *
     * @param other the second condition.
     * @return a match that accepts a request both this and [other] accept.
     */
    public infix fun and(other: RequestMatch): RequestMatch = RequestMatch { matches(it) && other.matches(it) }

    /** The common matches. */
    public companion object {
        /** Accepts every request. A value, not a type: `RequestMatch.Any`. */
        public val Any: RequestMatch = RequestMatch { true }

        /**
         * A `GET` of [path] whose query holds every pair of [query].
         *
         * @param path the percent-encoded path, as `Url.encodedPath` prints it, for example `/api/players/1`.
         * @param query the decoded `name to value` pairs the query must hold; none by default.
         * @return the match.
         */
        public fun get(path: String, vararg query: Pair<String, String>): RequestMatch = request(HttpMethod.Get, path, *query)

        /**
         * A request of [method] for [path] whose query holds every pair of [query].
         *
         * @param method the method, compared by name.
         * @param path the percent-encoded path, as `Url.encodedPath` prints it.
         * @param query the decoded `name to value` pairs the query must hold; none by default.
         * @return the match.
         */
        public fun request(method: HttpMethod, path: String, vararg query: Pair<String, String>): RequestMatch =
            path(path, *query) and RequestMatch { it.method == method }

        /**
         * Any method for [path] whose query holds every pair of [query].
         *
         * @param path the percent-encoded path, as `Url.encodedPath` prints it.
         * @param query the decoded `name to value` pairs the query must hold; none by default.
         * @return the match.
         */
        public fun path(path: String, vararg query: Pair<String, String>): RequestMatch {
            val expected = query.toList()
            return RequestMatch { request ->
                request.url.encodedPath == path && decodeQueryPairs(request.url.encodedQuery).let { actual -> expected.all { it in actual } }
            }
        }

        /**
         * A request carrying a header [name] (ASCII case-insensitive) with exactly [value] among its values.
         *
         * @param name the header name.
         * @param value the value one of the entries must equal.
         * @return the match.
         */
        public fun header(name: String, value: String): RequestMatch =
            RequestMatch { request -> request.headers.getAll(name).any { it == value } }
    }
}
