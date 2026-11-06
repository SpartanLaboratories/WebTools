package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.AssembledUrl
import com.spartanlabs.webtools.http.RULE_COST
import com.spartanlabs.webtools.http.RULE_DOT_SEGMENT
import com.spartanlabs.webtools.http.RULE_EMPTY_SEGMENT
import com.spartanlabs.webtools.http.RULE_MAX_AGE
import com.spartanlabs.webtools.http.RULE_MAX_WAIT
import com.spartanlabs.webtools.http.RULE_QUERY_UNICODE
import com.spartanlabs.webtools.http.RULE_REQUEST
import com.spartanlabs.webtools.http.RULE_SEGMENT_UNICODE
import com.spartanlabs.webtools.http.RULE_SETTINGS
import com.spartanlabs.webtools.http.RULE_URL
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.assembleUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * Level 4a - the request-assembly table of `assembleUrl`: segments and query pairs encode as `HttpUrl.Builder` encodes them; a base path joins with
 * exactly one slash and is kept as it is without segments; an empty segment, a dot segment and an unpaired surrogate each fail with their constant
 * rule; the key parameters are the parsed path under `/` then the encoded pairs in call order; an absolute URL or a host form passed as a segment
 * stays one encoded segment on the base host (the absolute-URL-in-a-path-argument bypass); each of the ten rules of an invalid request has its
 * constant text.
 */
class RequestAssemblyDeterministicTest {

    private val base: Url = url("https://h.example.test/v1/")

    private fun url(text: String): Url = Url.parse(text).getOrThrow()

    private fun assembled(segments: List<String>, pairs: List<Pair<String, String>> = emptyList(), from: Url = base): AssembledUrl =
        assembleUrl(from, segments, pairs).getOrThrow()

    private fun ruleOf(segments: List<String>, pairs: List<Pair<String, String>> = emptyList()): String =
        assertIs<TransportFailure.InvalidRequest>(assembleUrl(base, segments, pairs).exceptionOrNull(), "expected a failure for $segments $pairs").rule

    @Test
    fun `every rule of an invalid request has its constant text`() {
        // The texts, written out: a reworded or swapped constant fails here.
        val table = listOf(
            RULE_SETTINGS to "the call settings could not be applied",
            RULE_MAX_WAIT to "maxWait must not be negative",
            RULE_MAX_AGE to "maxAge must not be negative",
            RULE_COST to "cost must be at least 1",
            RULE_EMPTY_SEGMENT to "a path segment must not be empty",
            RULE_DOT_SEGMENT to "a path segment must not be . or ..",
            RULE_SEGMENT_UNICODE to "a path segment must be valid Unicode",
            RULE_QUERY_UNICODE to "a query name and value must be valid Unicode",
            RULE_URL to "the request URL could not be built",
            RULE_REQUEST to "the request could not be built",
        )
        table.forEach { (constant, text) -> assertEquals(text, constant) }
        assertEquals(10, table.map { it.second }.toSet().size)                  // ten rules, ten different texts
    }

    @Test
    fun `segments and pairs encode like HttpUrl Builder`() {
        val table = listOf(
            Triple(listOf("players", "7"), listOf("region" to "eu"), "https://h.example.test/v1/players/7?region=eu"),
            Triple(listOf("a b"), listOf("q" to "c d"), "https://h.example.test/v1/a%20b?q=c%20d"),
            Triple(listOf("é"), listOf("n" to "ü"), "https://h.example.test/v1/%C3%A9?n=%C3%BC"),
            Triple(listOf("a+b"), listOf("q" to "a+b"), "https://h.example.test/v1/a+b?q=a%2Bb"),
            Triple(listOf("a~b"), listOf("q" to "a~b"), "https://h.example.test/v1/a~b?q=a%7Eb"),
            Triple(listOf("a*b"), listOf("q" to "a*b"), "https://h.example.test/v1/a*b?q=a*b"),
            Triple(listOf("x"), listOf("a b" to "1", "k" to "v=w&z"), "https://h.example.test/v1/x?a%20b=1&k=v%3Dw%26z"),
        )
        table.forEach { (segments, pairs, expected) ->
            assertEquals(expected, assembled(segments, pairs).url.toString(), "segments $segments, pairs $pairs")
        }
    }

    @Test
    fun `a base path joins with exactly one slash`() {
        val table = listOf(
            "https://h.example.test/v1/" to "/v1/s",
            "https://h.example.test/v1" to "/v1/s",
            "https://h.example.test/" to "/s",
            "https://h.example.test/a//" to "/a//s",
        )
        table.forEach { (baseText, expectedPath) ->
            assertEquals(expectedPath, assembled(listOf("s"), from = url(baseText)).url.encodedPath, "base $baseText")
        }
    }

    @Test
    fun `no segments keeps the base as it is`() {
        assertEquals("https://h.example.test/v1/", assembled(emptyList()).url.toString())
        assertEquals("https://h.example.test/v1/?a=1", assembled(emptyList(), listOf("a" to "1")).url.toString())
        val bare = url("https://h.example.test/v1")
        assertEquals("https://h.example.test/v1", assembled(emptyList(), from = bare).url.toString())
        assertEquals("https://h.example.test/v1?a=1", assembled(emptyList(), listOf("a" to "1"), from = bare).url.toString())
    }

    @Test
    fun `an empty segment fails with the empty rule`() {
        assertEquals(RULE_EMPTY_SEGMENT, ruleOf(listOf("")))
        assertEquals(RULE_EMPTY_SEGMENT, ruleOf(listOf("a", "")))
        assertEquals(RULE_EMPTY_SEGMENT, ruleOf(listOf("a", "", "b")))
    }

    @Test
    fun `dot and double-dot segments fail with the dot rule`() {
        assertEquals(RULE_DOT_SEGMENT, ruleOf(listOf(".")))
        assertEquals(RULE_DOT_SEGMENT, ruleOf(listOf("..")))
        assertEquals(RULE_DOT_SEGMENT, ruleOf(listOf("a", "..")))
        // An encoded dot is text: its percent sign is encoded. A dot inside a longer segment is no dot segment.
        assertEquals("/v1/%252e", assembled(listOf("%2e")).url.encodedPath)
        assertEquals("/v1/a.", assembled(listOf("a.")).url.encodedPath)
    }

    @Test
    fun `an unpaired surrogate fails in a segment and in a query part`() {
        assertEquals(RULE_SEGMENT_UNICODE, ruleOf(listOf("\uD800")))
        assertEquals(RULE_SEGMENT_UNICODE, ruleOf(listOf("a", "x\uDC00")))
        assertEquals(RULE_QUERY_UNICODE, ruleOf(emptyList(), listOf("\uD800" to "v")))
        assertEquals(RULE_QUERY_UNICODE, ruleOf(emptyList(), listOf("n" to "\uDC00")))
        // A paired surrogate (U+1F600) is valid Unicode.
        val emoji = assembled(listOf("😀"), listOf("😀" to "😀"))
        assertEquals("https://h.example.test/v1/%F0%9F%98%80?%F0%9F%98%80=%F0%9F%98%80", emoji.url.toString())
    }

    @Test
    fun `the key parameters are the path under slash then the encoded pairs in call order`() {
        val result = assembled(listOf("players", "7"), listOf("region" to "eu", "a" to "1", "a" to "2"))
        assertEquals(listOf("/" to "/v1/players/7", "region" to "eu", "a" to "1", "a" to "2"), result.keyParams)
        assertEquals(result.url.encodedPath, result.keyParams.first().second)

        // The pairs are the encoded ones, and the path entry is the parsed URL's path, base path included.
        val encoded = assembled(listOf("a b"), listOf("q" to "c d"))
        assertEquals(listOf("/" to "/v1/a%20b", "q" to "c%20d"), encoded.keyParams)
        assertEquals(listOf("/" to "/v1/"), assembled(emptyList()).keyParams)
    }

    @Test
    fun `an absolute URL or a double-slash host as a segment stays one segment on the base host`() {
        val table = listOf(
            "https://evil.test/x" to "https:%2F%2Fevil.test%2Fx",
            "//evil.test" to "%2F%2Fevil.test",
            "\\\\evil.test" to "%5C%5Cevil.test",
            "http:evil.test" to "http:evil.test",
            "@evil.test" to "@evil.test",
            "evil.test:80" to "evil.test:80",
        )
        table.forEach { (segment, encoded) ->
            val result = assembled(listOf(segment)).url
            assertEquals(base.host, result.host, "segment $segment")
            assertEquals(base.scheme, result.scheme, "segment $segment")
            assertEquals(base.port, result.port, "segment $segment")
            assertEquals("/v1/$encoded", result.encodedPath, "segment $segment")
            assertFalse("/" in encoded, "segment $segment")
            assertEquals(2, result.encodedPath.removePrefix("/").split("/").size, "segment $segment")
        }
    }
}
