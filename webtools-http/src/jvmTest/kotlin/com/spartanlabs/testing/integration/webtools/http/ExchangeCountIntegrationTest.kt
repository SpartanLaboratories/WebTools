package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.RedirectHop
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Level 3 - one request per hop, never a retry, counted at the server: a `503` with `Retry-After: 0` is returned with its body and its
 * `Retry-After` after one request (control, measured with the status hiding removed: the same script gives two requests and a `200`); a `408`
 * and a `429` likewise; a pooled connection the server drops mid-exchange fails the second call with `Io` after exactly two requests on one
 * connection (`exchangeIndex` 0 and 1); each followed redirect is one request; a hop that answers a `503` with `Retry-After: 0` after a
 * redirect stays at one request for that hop.
 */
class ExchangeCountIntegrationTest {

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

    private fun status(code: Int, vararg headers: Pair<String, String>, body: String = "busy"): MockResponse =
        MockResponse.Builder().code(code).apply { headers.forEach { (name, value) -> addHeader(name, value) } }.body(body).build()

    @Test
    fun `a 503 with Retry-After 0 is returned once with its header`() {
        server.enqueue(status(503, "Retry-After" to "0"))
        server.enqueue(MockResponse(body = "ok"))
        val response = fleet.fetch(transport, fleet.get(server.webtoolsUrl("/"))).getOrThrow()
        assertEquals(503, response.status)
        assertEquals("busy", response.body().decodeToString())
        assertEquals("0", response.headers["Retry-After"])
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a 408 with Connection close is returned once`() {
        server.enqueue(status(408, "Connection" to "close"))
        server.enqueue(MockResponse(body = "ok"))
        assertEquals(408, fleet.fetch(transport, fleet.get(server.webtoolsUrl("/"))).getOrThrow().status)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a 429 with Retry-After 0 is returned once`() {
        server.enqueue(status(429, "Retry-After" to "0"))
        server.enqueue(MockResponse(body = "ok"))
        val response = fleet.fetch(transport, fleet.get(server.webtoolsUrl("/"))).getOrThrow()
        assertEquals(429, response.status)
        assertEquals("0", response.headers["Retry-After"])
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a dropped pooled connection fails the second call with Io after two requests on one connection`() {
        server.enqueue(MockResponse(body = "one"))
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        server.enqueue(MockResponse(body = "unreachable"))
        val first = fleet.fetch(transport, fleet.get(server.webtoolsUrl("/"))).getOrThrow()
        assertEquals("one", first.body().decodeToString())
        val second = fleet.fetch(transport, fleet.get(server.webtoolsUrl("/")))
        assertIs<TransportFailure.Io>(second.exceptionOrNull())
        assertEquals(2, server.requestCount)
        val requestOne = assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val requestTwo = assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals(requestOne.connectionIndex, requestTwo.connectionIndex)
        assertEquals(listOf(0, 1), listOf(requestOne.exchangeIndex, requestTwo.exchangeIndex))
    }

    @Test
    fun `each followed redirect is one request`() {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/b").build())
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/c").build())
        server.enqueue(MockResponse(body = "ok"))
        val response = fleet.fetch(transport, fleet.get(server.webtoolsUrl("/a"))).getOrThrow()
        assertEquals(200, response.status)
        assertEquals(server.webtoolsUrl("/c"), response.url)
        assertEquals(listOf(RedirectHop(server.webtoolsUrl("/a"), 302), RedirectHop(server.webtoolsUrl("/b"), 302)), response.redirectChain)
        assertEquals(3, server.requestCount)
        assertEquals(listOf("/a", "/b", "/c"), (1..3).map { assertNotNull(server.takeRequest(1, TimeUnit.SECONDS)).url.encodedPath })
    }

    @Test
    fun `a hop that answers 503 with Retry-After 0 after a redirect stays at one request`() {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/b").build())
        server.enqueue(status(503, "Retry-After" to "0"))
        server.enqueue(MockResponse(body = "ok"))
        val response = fleet.fetch(transport, fleet.get(server.webtoolsUrl("/a"))).getOrThrow()
        assertEquals(503, response.status)
        assertEquals(1, response.redirectChain.size)
        assertEquals(2, server.requestCount)
    }
}
