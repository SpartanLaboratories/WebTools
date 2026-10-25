package com.spartanlabs.webtools.http.testing

import java.io.ByteArrayOutputStream

/**
 * OkHttp 5.5.0 `HttpUrl.toQueryNamesAndValues` + `percentDecode(plusIsSpace = true)`, for a canonical
 * `Url.encodedQuery`: the query is split on `&` (an empty query is one pair with an empty name; a trailing
 * `&` yields a last empty-name pair), each piece on its first `=`, and a piece without `=` has a `null`
 * value. Pure; never throws. A `null` query is no pairs.
 */
internal fun decodeQueryPairs(encodedQuery: String?): List<Pair<String, String?>> {
    if (encodedQuery == null) return emptyList()
    val pairs = ArrayList<Pair<String, String?>>()
    var position = 0
    while (position <= encodedQuery.length) {
        val ampersand = encodedQuery.indexOf('&', position).let { if (it == -1) encodedQuery.length else it }
        val equals = encodedQuery.indexOf('=', position)
        if (equals == -1 || equals > ampersand) {
            pairs.add(percentDecodeQueryComponent(encodedQuery.substring(position, ampersand)) to null)
        } else {
            pairs.add(
                percentDecodeQueryComponent(encodedQuery.substring(position, equals)) to
                    percentDecodeQueryComponent(encodedQuery.substring(equals + 1, ampersand)),
            )
        }
        position = ampersand + 1
    }
    return pairs
}

/**
 * `+` is a space; `%XX` is one byte; a `%` not followed by two hex digits is kept as typed; every other
 * character contributes its UTF-8 bytes; the bytes decode as UTF-8 with a malformed sequence replaced.
 */
internal fun percentDecodeQueryComponent(text: String): String {
    if (text.none { it == '%' || it == '+' }) return text
    val out = ByteArrayOutputStream(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '+' -> { out.write(' '.code); i++ }
            c == '%' && i + 2 < text.length && hexValue(text[i + 1]) >= 0 && hexValue(text[i + 2]) >= 0 -> {
                out.write(hexValue(text[i + 1]) * 16 + hexValue(text[i + 2]))
                i += 3
            }
            else -> {
                val width = if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
                val bytes = text.substring(i, i + width).toByteArray(Charsets.UTF_8)
                out.write(bytes, 0, bytes.size)
                i += width
            }
        }
    }
    return String(out.toByteArray(), Charsets.UTF_8)
}

private fun hexValue(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> -1
}
