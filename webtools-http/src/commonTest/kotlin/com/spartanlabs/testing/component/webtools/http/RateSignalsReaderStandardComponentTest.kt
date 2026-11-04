package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.RateSignalsReader
import com.spartanlabs.webtools.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `RateSignalsReader.Standard`: a `429` is throttled with or without `Retry-After`; a `503` only with one that parses;
 * every other status is `None`; several `Retry-After` lines give the longest wait among those that parse; a folded list is
 * ignored; a date-valued `Retry-After` is measured against the response's `Date` when it parses and against `now` otherwise
 * (the last `Date` line wins); no readings ever; `toString()`; the reader never throws for hostile values.
 */
class RateSignalsReaderStandardComponentTest {

    private val url = Url.parse("http://h/").getOrThrow()
    private val reader = RateSignalsReader.Standard
    private val midnight2000 = 946_684_800_000L                        // 2000-01-01T00:00:00Z

    private fun response(status: Int, vararg headers: Pair<String, String>): HttpResponse =
        HttpResponse(status, url) { this.headers = HttpHeaders { headers.forEach { (name, value) -> add(name, value) } } }

    private fun read(status: Int, vararg headers: Pair<String, String>, now: Long = 0L): RateSignals = reader.read(response(status, *headers), now)

    @Test
    fun `a 429 is throttled with or without Retry-After`() {
        val with = read(429, "Retry-After" to "30")
        assertTrue(with.throttled)
        assertEquals(30.seconds, with.retryAfter)
        val without = read(429)
        assertTrue(without.throttled)
        assertNull(without.retryAfter)
    }

    @Test
    fun `a 503 is throttled only when a Retry-After parses`() {
        val with = read(503, "Retry-After" to "30")
        assertTrue(with.throttled)
        assertEquals(30.seconds, with.retryAfter)
        assertEquals(RateSignals.None, read(503))
        assertEquals(RateSignals.None, read(503, "Retry-After" to "soon"))
    }

    @Test
    fun `every other status is None even with Retry-After`() {
        listOf(200, 302, 500, 403).forEach { status ->
            assertEquals(RateSignals.None, read(status, "Retry-After" to "30"), "status $status")
        }
    }

    @Test
    fun `several lines give the longest wait among those that parse`() {
        assertEquals(120.seconds, read(503, "Retry-After" to "60", "Retry-After" to "120").retryAfter)
        assertEquals(120.seconds, read(429, "Retry-After" to "120", "Retry-After" to "60").retryAfter)
        assertEquals(60.seconds, read(503, "Retry-After" to "60", "Retry-After" to "garbage").retryAfter)
        val garbageOnly = read(429, "Retry-After" to "garbage", "Retry-After" to "more garbage")
        assertTrue(garbageOnly.throttled)
        assertNull(garbageOnly.retryAfter)
    }

    @Test
    fun `a folded list is ignored`() {
        assertEquals(RateSignals.None, read(503, "Retry-After" to "120, 60"))
        assertNull(read(429, "Retry-After" to "120, 60").retryAfter)
    }

    @Test
    fun `a date is measured against the response's Date when it parses, whatever now is`() {
        val signals = read(503, "Date" to "Sat, 01 Jan 2000 00:00:00 GMT", "Retry-After" to "Sat, 01 Jan 2000 01:00:00 GMT", now = 1L)
        assertEquals(3_600.seconds, signals.retryAfter)
        assertEquals(3_600.seconds, read(503, "Date" to "Sat, 01 Jan 2000 00:00:00 GMT", "Retry-After" to "Sat, 01 Jan 2000 01:00:00 GMT", now = 2_000_000_000_000L).retryAfter)
    }

    @Test
    fun `a date is measured against now when Date is missing, malformed or in another zone`() {
        val retryAfter = "Retry-After" to "Sat, 01 Jan 2000 01:00:00 GMT"
        assertEquals(3_600.seconds, read(503, retryAfter, now = midnight2000).retryAfter)
        assertEquals(3_600.seconds, read(503, "Date" to "yesterday", retryAfter, now = midnight2000).retryAfter)
        assertEquals(3_600.seconds, read(503, "Date" to "Sat, 01 Jan 2000 00:30:00 UTC", retryAfter, now = midnight2000).retryAfter)
        assertEquals(7_200.seconds, read(503, retryAfter, now = midnight2000 - 3_600_000L).retryAfter)
    }

    @Test
    fun `the last Date line is the one used`() {
        val signals = read(
            503,
            "Date" to "Sat, 01 Jan 2000 00:00:00 GMT",
            "Date" to "Sat, 01 Jan 2000 00:30:00 GMT",
            "Retry-After" to "Sat, 01 Jan 2000 01:00:00 GMT",
        )
        assertEquals(1_800.seconds, signals.retryAfter)
    }

    @Test
    fun `no readings are ever reported`() {
        assertTrue(read(429, "Retry-After" to "30", "X-RateLimit-Remaining" to "0").readings.isEmpty())
        assertTrue(read(503, "Retry-After" to "30").readings.isEmpty())
    }

    @Test
    fun `toString names the object`() {
        assertEquals("RateSignalsReader.Standard", reader.toString())
    }

    @Test
    fun `the reader never throws for hostile values`() {
        assertNull(read(429, "Retry-After" to "").retryAfter)
        assertNull(read(429, "Retry-After" to "9".repeat(10_000) + "x").retryAfter)
        assertEquals(RateSignals.None, read(503, "Retry-After" to "1".repeat(10_000) + "x"))
        assertNull(read(429, "Retry-After" to "\u0001\u007F", "Date" to "\u0001").retryAfter)
        assertEquals(RateSignals.None, read(503, "Retry-After" to " " + "30"))
    }
}
