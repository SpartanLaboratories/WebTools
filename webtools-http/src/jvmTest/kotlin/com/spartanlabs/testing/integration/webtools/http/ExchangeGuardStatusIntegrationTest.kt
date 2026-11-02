package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.webtools.http.HIDDEN_STATUS_HEADER
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Level 3 - the four hidden statuses are delivered as responses: a `407` (with `Proxy-Authenticate`), a `408`, a `421` and a `503` with
 * `Retry-After: 0`, each from a direct origin, arrive with their real status, their headers (the `503`'s `Retry-After` in value and position)
 * and body, no private header, after one request each; a server that forges the private header on a `200` gets a plain `200`; a genuine
 * `599` is a `599`.
 */
class ExchangeGuardStatusIntegrationTest {

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

    private fun fetch() = fleet.fetch(transport, fleet.get(server.webtoolsUrl("/"))).getOrThrow()

    @Test
    fun `a 407 with Proxy-Authenticate is a response`() {
        server.enqueue(MockResponse.Builder().code(407).addHeader("Proxy-Authenticate", "Basic realm=\"p\"").body("auth").build())
        val response = fetch()
        assertEquals(407, response.status)
        assertEquals("Basic realm=\"p\"", response.headers["Proxy-Authenticate"])
        assertEquals("auth", response.body().decodeToString())
        assertFalse(HIDDEN_STATUS_HEADER in response.headers)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a 408 and a 421 are responses`() {
        server.enqueue(MockResponse.Builder().code(408).body("late").build())
        server.enqueue(MockResponse.Builder().code(421).body("misdirected").build())
        val first = fetch()
        assertEquals(408, first.status)
        assertEquals("late", first.body().decodeToString())
        val second = fetch()
        assertEquals(421, second.status)
        assertEquals("misdirected", second.body().decodeToString())
        assertFalse(HIDDEN_STATUS_HEADER in second.headers)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a 503 with Retry-After 0 keeps the header's value and position`() {
        server.enqueue(MockResponse.Builder().code(503).addHeader("X-First", "a").addHeader("Retry-After", "0").addHeader("X-Last", "z").body("busy").build())
        val response = fetch()
        assertEquals(503, response.status)
        val names = response.headers.entries().map { it.name }
        assertEquals(listOf("X-First", "Retry-After", "X-Last"), names.filter { it in setOf("X-First", "Retry-After", "X-Last") })
        assertEquals("0", response.headers["Retry-After"])
        assertEquals("busy", response.body().decodeToString())
        assertFalse(HIDDEN_STATUS_HEADER in response.headers)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a forged private header on a 200 is stripped`() {
        server.enqueue(MockResponse.Builder().code(200).addHeader(HIDDEN_STATUS_HEADER, "407").body("ok").build())
        val response = fetch()
        assertEquals(200, response.status)
        assertFalse(HIDDEN_STATUS_HEADER in response.headers)
        assertEquals("ok", response.body().decodeToString())
    }

    @Test
    fun `a genuine 599 is a 599`() {
        server.enqueue(MockResponse.Builder().code(599).body("odd").build())
        val response = fetch()
        assertEquals(599, response.status)
        assertEquals("odd", response.body().decodeToString())
        assertFalse(HIDDEN_STATUS_HEADER in response.headers)
    }
}
