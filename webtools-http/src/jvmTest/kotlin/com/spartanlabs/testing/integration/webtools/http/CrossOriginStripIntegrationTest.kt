package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Level 3 - the cross-origin strip under a `trusted` transport: server 1 redirects to server 2 (the origin differs by port); a request with
 * `Authorization`, `Cookie`, a header added as sensitive and an ordinary `X-Trace` reaches server 1 with all four and server 2 with `X-Trace`
 * only; the control redirect on the same server keeps all four; `A -> B -> A` leaves the sensitive headers off the third request; conditional
 * headers and `Range` go along, with `Accept-Encoding: identity` on every `Range` hop.
 */
class CrossOriginStripIntegrationTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var first: MockWebServer
    private lateinit var second: MockWebServer
    private lateinit var transport: OkHttpTransport

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        first = fleet.server()
        second = fleet.server()
        transport = fleet.track(LoopbackTransports.trusted("agent"))
    }

    @AfterTest
    fun stop() = fleet.close()

    private fun redirectTo(url: Url) = MockResponse.Builder().code(302).addHeader("Location", url.toString()).build()

    private fun request(url: Url, extra: HttpHeaders.Builder.() -> Unit = {}) = HttpRequest(HttpMethod.Get, url) {
        headers = HttpHeaders {
            add("Authorization", "Bearer t")
            add("Cookie", "a=1")
            addSensitive("X-Api-Key", "k")
            add("X-Trace", "trace")
            extra()
        }
    }

    private fun MockWebServer.recorded(): RecordedRequest = assertNotNull(takeRequest(1, TimeUnit.SECONDS), "no request arrived")

    private fun RecordedRequest.sensitive(): List<String?> = listOf(headers["Authorization"], headers["Cookie"], headers["X-Api-Key"])

    @Test
    fun `sensitive headers are stripped on a cross-origin hop and kept on a same-origin hop`() {
        first.enqueue(redirectTo(second.webtoolsUrl("/x")))
        second.enqueue(MockResponse(body = "ok"))
        fleet.fetch(transport, request(first.webtoolsUrl("/a"))).getOrThrow()
        val atFirst = first.recorded()
        assertEquals(listOf("Bearer t", "a=1", "k"), atFirst.sensitive())
        assertEquals("trace", atFirst.headers["X-Trace"])
        val atSecond = second.recorded()
        assertEquals(listOf(null, null, null), atSecond.sensitive())
        assertEquals("trace", atSecond.headers["X-Trace"])

        first.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/b").build())
        first.enqueue(MockResponse(body = "ok"))
        fleet.fetch(transport, request(first.webtoolsUrl("/a"))).getOrThrow()
        first.recorded()
        val control = first.recorded()
        assertEquals(listOf("Bearer t", "a=1", "k"), control.sensitive())
        assertEquals("trace", control.headers["X-Trace"])
    }

    @Test
    fun `A to B to A leaves the sensitive headers off the third request`() {
        first.enqueue(redirectTo(second.webtoolsUrl("/b")))
        second.enqueue(redirectTo(first.webtoolsUrl("/back")))
        first.enqueue(MockResponse(body = "ok"))
        val response = fleet.fetch(transport, request(first.webtoolsUrl("/a"))).getOrThrow()
        assertEquals(first.webtoolsUrl("/back"), response.url)
        first.recorded()
        second.recorded()
        val third = first.recorded()
        assertEquals(listOf(null, null, null), third.sensitive())
        assertEquals("trace", third.headers["X-Trace"])
    }

    @Test
    fun `conditional headers and Range go along with identity on every Range hop`() {
        first.enqueue(redirectTo(second.webtoolsUrl("/x")))
        second.enqueue(MockResponse(body = "ok"))
        fleet.fetch(transport, request(first.webtoolsUrl("/a")) { add("If-None-Match", "\"v1\""); add("Range", "bytes=0-9") }).getOrThrow()
        listOf(first.recorded(), second.recorded()).forEach { recorded ->
            assertEquals("\"v1\"", recorded.headers["If-None-Match"])
            assertEquals("bytes=0-9", recorded.headers["Range"])
            assertEquals("identity", recorded.headers["Accept-Encoding"])
            assertNull(recorded.headers["Referer"])
        }
    }
}
