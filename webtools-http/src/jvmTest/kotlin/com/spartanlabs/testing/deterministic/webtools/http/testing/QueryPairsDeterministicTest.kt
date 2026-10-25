package com.spartanlabs.testing.deterministic.webtools.http.testing

import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.decodeQueryPairs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Level 4a - `decodeQueryPairs` against OkHttp 5.5.0's `HttpUrl.queryParameterName` /
 * `queryParameterValue`: a table of hand-picked queries, then a seeded differential (seed 7, 100,000
 * queries built from the plan's pieces; the escapes it draws as pieces are valid UTF-8, and the lone `%` piece
 * also forms ill-formed escapes such as `%ab` with its neighbours) and a fixed list of ill-formed escapes compared
 * exactly, which pins the agreed cases. Every input goes through `Url.parse("http://h/?" + q)` and is re-read as an
 * `HttpUrl` from the canonical text, so both sides see the same encoded query.
 */
class QueryPairsDeterministicTest {

    private val seed = 7
    private val inputs = 100_000
    private val pieces = listOf("a", "b", "=", "&", "+", "%", "%zz", "%41", "%C3%A9", "é", "日", "😀", "%2B", "%26", "%3D")   // U+00E9, U+65E5, U+1F600

    private fun oracle(url: HttpUrl): List<Pair<String, String?>> = (0 until url.querySize).map { url.queryParameterName(it) to url.queryParameterValue(it) }

    private fun compare(query: String, label: String) {
        val url = Url.parse("http://h/?$query").getOrThrow()
        val theirs = url.toString().toHttpUrl()
        assertEquals(theirs.encodedQuery, url.encodedQuery, "$label: encoded query")
        assertEquals(oracle(theirs), decodeQueryPairs(url.encodedQuery), "$label: pairs of '$query'")
    }

    @Test
    fun `a table of queries decodes to the expected pairs`() {
        val table = mapOf(
            "a=1&b=2" to listOf("a" to "1", "b" to "2"),
            "a" to listOf("a" to null),
            "a=" to listOf("a" to ""),
            "=v" to listOf("" to "v"),
            "a=b=c" to listOf("a" to "b=c"),
            "&&" to listOf("" to null, "" to null, "" to null),
            "a&" to listOf("a" to null, "" to null),
            "" to listOf("" to null),
            "a+b=c%20d" to listOf("a b" to "c d"),
            "%zz" to listOf("%zz" to null),
            "%4" to listOf("%4" to null),
            "%C3%A9=%C3%A9" to listOf("é" to "é"),
            "日=😀" to listOf("日" to "😀"),
            "a%3Db=c%26d" to listOf("a=b" to "c&d"),
        )
        table.forEach { (query, expected) ->
            assertEquals(expected, decodeQueryPairs(Url.parse("http://h/?$query").getOrThrow().encodedQuery), query)
            compare(query, "table")
        }
        assertEquals(emptyList(), decodeQueryPairs(null))
        assertEquals(listOf("" to null), decodeQueryPairs(Url.parse("http://h/?").getOrThrow().encodedQuery))
    }

    @Test
    fun `a seeded differential agrees with HttpUrl on every pair`() {
        val random = Random(seed)
        var withPlus = 0
        var withEscape = 0
        var withNonAscii = 0
        repeat(inputs) { index ->
            val query = buildString { repeat(random.nextInt(0, 9)) { append(pieces[random.nextInt(pieces.size)]) } }
            if ('+' in query) withPlus++
            if ('%' in query) withEscape++
            if (query.any { it.code >= 0x80 }) withNonAscii++
            compare(query, "input $index")
        }
        assertTrue(withPlus > inputs / 20 && withEscape > inputs / 10 && withNonAscii > inputs / 10, "the generator went vacuous: + $withPlus, % $withEscape, non-ASCII $withNonAscii")
    }

    @Test
    fun `a fixed list of ill-formed escapes decodes as HttpUrl does`() {
        listOf("%FF", "a=%FF", "%C3", "%C3=1", "%80%80", "%E2%82", "%C3%A9%FF", "%ED%A0%80", "a=%FF%FE&b=%C0%AF", "%F0%9F%98", "%ff=%Ff").forEach { query ->
            compare(query, "ill-formed")
        }
    }
}
