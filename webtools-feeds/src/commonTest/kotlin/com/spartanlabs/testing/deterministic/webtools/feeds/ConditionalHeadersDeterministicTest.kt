package com.spartanlabs.testing.deterministic.webtools.feeds

import com.spartanlabs.webtools.feeds.FeedValidators
import com.spartanlabs.webtools.feeds.isConditional
import com.spartanlabs.webtools.feeds.refreshedBy
import com.spartanlabs.webtools.feeds.requestHeaders
import com.spartanlabs.webtools.feeds.validatorsOf
import com.spartanlabs.webtools.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 4a - the conditional-GET header rules: `requestHeaders` carries `Accept` first, then the known validators verbatim;
 * `validatorsOf` reads exactly one line per header and applies the rules; `refreshedBy` replaces each validator the `304` carries
 * once, keeps the others and nulls an unusable one; a repeated header is absent in both readings.
 */
class ConditionalHeadersDeterministicTest {

    private val accept = "application/rss+xml"
    private val date = "Fri, 6 Nov 2026 04:25:27 GMT"

    @Test
    fun `requestHeaders carries Accept first, then the known validators verbatim`() {
        val both = requestHeaders(accept, FeedValidators("W/\"a\", \"b\"", date)).entries().map { it.name to it.value }
        assertEquals(listOf("Accept" to accept, "If-None-Match" to "W/\"a\", \"b\"", "If-Modified-Since" to date), both)
        assertEquals(listOf("Accept" to accept), requestHeaders(accept, FeedValidators.None).entries().map { it.name to it.value })
        assertEquals(listOf("Accept" to accept, "If-Modified-Since" to date), requestHeaders(accept, FeedValidators(null, date)).entries().map { it.name to it.value })
        assertEquals(listOf("Accept" to accept, "If-None-Match" to "\"a\""), requestHeaders(accept, FeedValidators("\"a\"", null)).entries().map { it.name to it.value })
        assertTrue(FeedValidators("\"a\"", null).isConditional)
        assertTrue(FeedValidators(null, date).isConditional)
        assertFalse(FeedValidators.None.isConditional)
        assertFalse(FeedValidators("*", "").isConditional, "values the rules reject leave the request unconditional")
    }

    @Test
    fun `validatorsOf reads exactly one line per header and applies the rules`() {
        val both = validatorsOf(HttpHeaders { add("Content-Type", "application/rss+xml"); add("etag", " \"a\" "); add("LAST-MODIFIED", date) })
        assertEquals(FeedValidators("\"a\"", date), both)
        assertEquals(FeedValidators.None, validatorsOf(HttpHeaders { add("Content-Type", "text/xml") }))
        assertEquals(FeedValidators("\"a\"", null), validatorsOf(HttpHeaders { add("ETag", "\"a\"") }))
        val unusable = validatorsOf(HttpHeaders { add("ETag", "*"); add("Last-Modified", "a".repeat(65)) })
        assertNull(unusable.etag)
        assertNull(unusable.lastModified)
    }

    @Test
    fun `refreshedBy replaces each validator the 304 carries once, keeps the others and nulls an unusable one`() {
        val sent = FeedValidators("\"a\"", date)
        assertEquals(sent, sent.refreshedBy(HttpHeaders()))
        assertEquals(FeedValidators("\"b\"", date), sent.refreshedBy(HttpHeaders { add("ETag", "\"b\"") }))
        assertEquals(FeedValidators("\"a\"", "Sat, 7 Nov 2026 00:00:00 GMT"), sent.refreshedBy(HttpHeaders { add("Last-Modified", "Sat, 7 Nov 2026 00:00:00 GMT") }))
        assertEquals(FeedValidators(null, date), sent.refreshedBy(HttpHeaders { add("ETag", "*") }))
        assertEquals(FeedValidators("\"a\"", null), sent.refreshedBy(HttpHeaders { add("Last-Modified", "café") }))
        assertEquals(FeedValidators("\"c\"", null), FeedValidators.None.refreshedBy(HttpHeaders { add("ETag", "\"c\"") }))
    }

    @Test
    fun `a repeated header is absent in both readings`() {
        val repeated = HttpHeaders { add("ETag", "\"a\""); add("ETag", "\"b\""); add("Last-Modified", date) }
        assertEquals(FeedValidators(null, date), validatorsOf(repeated))
        val sent = FeedValidators("\"sent\"", "Thu, 5 Nov 2026 00:00:00 GMT")
        assertEquals(FeedValidators("\"sent\"", date), sent.refreshedBy(repeated))
        val bothRepeated = HttpHeaders { add("ETag", "\"a\""); add("ETag", "\"a\""); add("Last-Modified", date); add("Last-Modified", date) }
        assertEquals(FeedValidators.None, validatorsOf(bothRepeated))
        assertEquals(sent, sent.refreshedBy(bothRepeated))
    }
}
