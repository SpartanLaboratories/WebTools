package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.HttpDateOracle
import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.RateSignalsReader
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 3 - `RateSignalsReader.Standard` over responses the real transport produces, on real time under `runBlocking` within 30 s (the
 * fleet's bound): a `503` with `Retry-After: 30` (the header the transport's `ExchangeGuard` hides from OkHttp and restores) is throttled
 * with 30 s; a `429` with `Retry-After: 7` is throttled with 7 s and a `429` without the header is throttled with `null`; a `503` without
 * it is `None`; a `300` (returned, not followed) with `Retry-After: 120` is `None`, since the status is not read; a `503` whose
 * `Retry-After` is an IMF-fixdate 90 s after the response's own `Date` is throttled with exactly 90 s whatever `now` is passed; two
 * `Retry-After` lines survive the transport and give the longer. A regression here would silently disable every pause on a `503`.
 */
class RateSignalsReaderLoopbackIntegrationTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var server: MockWebServer
    private lateinit var transport: OkHttpTransport

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        server = fleet.server()
        transport = fleet.track(LoopbackTransports.untrusted("agent"))
    }

    @AfterTest
    fun stop() = fleet.close()

    private fun fetch(): HttpResponse = fleet.fetch(transport, fleet.get(server.webtoolsUrl("/"))).getOrThrow()

    private fun read(response: HttpResponse, nowEpochMillis: Long = 0L): RateSignals = RateSignalsReader.Standard.read(response, nowEpochMillis)

    private fun response(status: Int, vararg headers: Pair<String, String>): MockResponse =
        MockResponse.Builder().code(status).apply { headers.forEach { (name, value) -> addHeader(name, value) } }.body("busy").build()

    @Test
    fun `a 503 with Retry-After 30 is throttled with 30 s`() {
        server.enqueue(response(503, "Retry-After" to "30"))
        val response = fetch()
        assertEquals(503, response.status)
        assertEquals("30", response.headers["Retry-After"])
        val signals = read(response)
        assertTrue(signals.throttled)
        assertEquals(30.seconds, signals.retryAfter)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a 429 is throttled with its Retry-After, or with null without one`() {
        server.enqueue(response(429, "Retry-After" to "7"))
        server.enqueue(response(429))
        val withHeader = read(fetch())
        assertTrue(withHeader.throttled)
        assertEquals(7.seconds, withHeader.retryAfter)
        val withoutHeader = read(fetch())
        assertTrue(withoutHeader.throttled)
        assertNull(withoutHeader.retryAfter)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a 503 without Retry-After is None`() {
        server.enqueue(response(503))
        val response = fetch()
        assertEquals(503, response.status)
        assertEquals(RateSignals.None, read(response))
    }

    @Test
    fun `a 300 with Retry-After 120 is returned, not followed, and is None`() {
        server.enqueue(response(300, "Location" to "/elsewhere", "Retry-After" to "120"))
        val response = fetch()
        assertEquals(300, response.status)
        assertEquals("120", response.headers["Retry-After"])
        assertEquals(RateSignals.None, read(response))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a date-valued Retry-After is measured against the response's Date whatever now is`() {
        // 2000-01-01T00:00:00Z as the server's Date; Retry-After 90 s later, both printed as IMF-fixdates by the differential's oracle.
        val dateSecond = 946_684_800L
        val date = HttpDateOracle.print(HttpDateOracle.imfFixdate, dateSecond)
        val retryAt = HttpDateOracle.print(HttpDateOracle.imfFixdate, dateSecond + 90)
        server.enqueue(response(503, "Date" to date, "Retry-After" to retryAt))
        val response = fetch()
        assertEquals(date, response.headers["Date"], "the server's Date must be the one read")
        for (now in listOf(0L, dateSecond * 1_000L, System.currentTimeMillis(), 4_102_444_800_000L)) {
            val signals = read(response, now)
            assertTrue(signals.throttled, "now=$now")
            assertEquals(90.seconds, signals.retryAfter, "now=$now")
        }
    }

    @Test
    fun `two Retry-After lines survive the transport and give the longer`() {
        server.enqueue(response(503, "Retry-After" to "60", "Retry-After" to "120"))
        val response = fetch()
        assertEquals(listOf("60", "120"), response.headers.getAll("Retry-After"))
        val signals = read(response)
        assertTrue(signals.throttled)
        assertEquals(120.seconds, signals.retryAfter)
    }
}
