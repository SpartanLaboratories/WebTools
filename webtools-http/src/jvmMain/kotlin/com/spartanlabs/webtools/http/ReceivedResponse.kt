package com.spartanlabs.webtools.http

import okhttp3.Response

/**
 * The headers of a received response, converted.
 *
 * @property headers the entries the types can hold, in order.
 * @property dropped the number of entries whose name was not a token.
 */
internal class ReceivedHeaders(val headers: HttpHeaders, val dropped: Int)

/**
 * Converts received (name, value) pairs: an entry whose name is not a token is dropped and counted; the private header is removed; each value goes
 * through [HttpSyntax.sanitizeReceivedValue]; order, spelling and duplicates are kept. Total: a sanitised value always satisfies the container
 * rule, so the builder cannot throw.
 *
 * @param pairs the received entries in wire order.
 * @return the converted headers and the dropped count.
 */
internal fun receiveHeaders(pairs: List<Pair<String, String>>): ReceivedHeaders {
    val (named, unnamed) = pairs.partition { (name, _) -> HttpSyntax.isToken(name) }
    val surfaced = named
        .filterNot { (name, _) -> HttpSyntax.equalsName(name, HIDDEN_STATUS_HEADER) }
        .map { (name, value) -> name to HttpSyntax.sanitizeReceivedValue(value) }
    return ReceivedHeaders(HttpHeaders { surfaced.forEach { (name, value) -> add(name, value) } }, unnamed.size)
}

/** The response's headers as (name, value) pairs in wire order, duplicates kept. */
internal fun Response.headerPairs(): List<Pair<String, String>> = (0 until headers.size).map { headers.name(it) to headers.value(it) }

/**
 * The transport's no-body rule, shared with the published fake: no body is read for `HEAD`, a `1xx`, `204` or `304`.
 *
 * @param method the request method.
 * @param status the response status.
 * @return `true` when a body is read.
 */
internal fun bodyExpected(method: HttpMethod, status: Int): Boolean =
    !(method == HttpMethod.Head || status in 100..199 || status == 204 || status == 304)
