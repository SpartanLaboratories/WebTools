package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RedirectHop
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.RequestBody
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Level 3 - manual redirects on the wire: `301 302 303 307 308` are followed for `GET` and `HEAD` (a `HEAD` stays `HEAD` after a `303`);
 * the chain and the final URL; a followed hop sends no body and no `Referer`; a `POST` keeps its `302`; a `300` and a `304` are returned; no,
 * an empty, a whitespace and two differing `Location`s return the `3xx` with both headers readable; two equal `Location`s are followed; a
 * followed `302` with `Retry-After: 3600` does not delay the next hop and a returned `3xx` keeps its `Retry-After`; `ftp:`, `file:` and
 * `javascript:` targets are `RefusedByPolicy(Scheme)` with the second server never contacted; `http://h:0/` is `HostForm`; `maxRedirects = 0`
 * makes a `302` `TooManyRedirects(0)` after one request; a user-info target is `UserInfo`.
 */
class RedirectFollowingIntegrationTest {

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

    private fun redirect(status: Int, vararg locations: String, configure: MockResponse.Builder.() -> Unit = {}): MockResponse =
        MockResponse.Builder().code(status).apply { locations.forEach { addHeaderLenient("Location", it) } }.apply(configure).build()

    private fun fetch(method: HttpMethod = HttpMethod.Get, path: String = "/a", body: RequestBody? = null): Result<HttpResponse> =
        fleet.fetch(transport, HttpRequest(method, server.webtoolsUrl(path)) { this.body = body })

    private fun nextRequest() = assertNotNull(server.takeRequest(1, TimeUnit.SECONDS), "no request arrived")

    @Test
    fun `the five statuses are followed for GET and HEAD and the method is kept`() {
        listOf(HttpMethod.Get, HttpMethod.Head).forEach { method ->
            listOf(301, 302, 303, 307, 308).forEach { status ->
                server.enqueue(redirect(status, "/b"))
                server.enqueue(MockResponse())
                val response = fetch(method).getOrThrow()
                assertEquals(200, response.status, "$method $status")
                assertEquals(listOf(RedirectHop(server.webtoolsUrl("/a"), status)), response.redirectChain, "$method $status")
                assertEquals(server.webtoolsUrl("/b"), response.url)
                assertEquals(method.name, nextRequest().method)
                assertEquals(method.name, nextRequest().method, "$method $status: the second request's method")
            }
        }
    }

    @Test
    fun `the chain lists each followed hop and the url is the last one requested`() {
        server.enqueue(redirect(302, "/b"))
        server.enqueue(redirect(301, "/c"))
        server.enqueue(MockResponse(body = "ok"))
        val response = fetch().getOrThrow()
        assertEquals(listOf(RedirectHop(server.webtoolsUrl("/a"), 302), RedirectHop(server.webtoolsUrl("/b"), 301)), response.redirectChain)
        assertEquals(server.webtoolsUrl("/c"), response.url)
        assertEquals("ok", response.body().decodeToString())
    }

    @Test
    fun `a followed hop sends no body and no Referer`() {
        server.enqueue(redirect(302, "/b"))
        server.enqueue(MockResponse())
        fetch().getOrThrow()
        nextRequest()
        val second = nextRequest()
        assertEquals(0L, second.bodySize)
        assertNull(second.headers["Referer"])
    }

    @Test
    fun `a POST keeps its 302 and a 300 and a 304 are returned`() {
        server.enqueue(redirect(302, "/b") { addHeader("Retry-After", "3600") })
        val post = fetch(HttpMethod.Post, body = RequestBody("x".encodeToByteArray(), null)).getOrThrow()
        assertEquals(302, post.status)
        assertEquals("/b", post.headers["Location"])
        assertEquals("3600", post.headers["Retry-After"])
        assertEquals(1, server.requestCount)
        server.enqueue(redirect(300, "/b"))
        assertEquals(300, fetch().getOrThrow().status)
        server.enqueue(MockResponse.Builder().code(304).build())
        assertEquals(304, fetch().getOrThrow().status)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a 302 without a single usable Location is returned with its headers readable`() {
        server.enqueue(redirect(302))
        assertEquals(302, fetch().getOrThrow().status)
        server.enqueue(redirect(302, ""))
        assertEquals(302, fetch().getOrThrow().status)
        server.enqueue(redirect(302, "   "))
        assertEquals(302, fetch().getOrThrow().status)
        server.enqueue(redirect(302, "/a", "/b"))
        val two = fetch().getOrThrow()
        assertEquals(302, two.status)
        assertEquals(listOf("/a", "/b"), two.headers.getAll("Location"))
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `two equal Location lines are followed`() {
        server.enqueue(redirect(302, "/b", "/b"))
        server.enqueue(MockResponse())
        assertEquals(server.webtoolsUrl("/b"), fetch().getOrThrow().url)
    }

    @Test
    fun `a followed 302 with Retry-After does not delay the next hop`() {
        server.enqueue(redirect(302, "/b") { addHeader("Retry-After", "3600") })
        server.enqueue(MockResponse())
        val mark = TimeSource.Monotonic.markNow()
        val response = fetch().getOrThrow()
        assertEquals(200, response.status)
        assertNull(response.headers["Retry-After"])
        assertTrue(mark.elapsedNow() < 5.seconds, "took ${mark.elapsedNow()}")
    }

    @Test
    fun `a foreign scheme is refused and the second server is never contacted`() {
        val second = fleet.server()
        listOf("ftp://h/x", "file:///etc/passwd", "javascript:alert(1)").forEach { location ->
            server.enqueue(redirect(302, location))
            val failure = assertIs<TransportFailure.RefusedByPolicy>(fetch().exceptionOrNull(), location)
            assertEquals(RefusalReason.Scheme, failure.reason, location)
        }
        server.enqueue(redirect(302, "http://h:0/"))
        assertEquals(RefusalReason.HostForm, assertIs<TransportFailure.RefusedByPolicy>(fetch().exceptionOrNull()).reason)
        server.enqueue(redirect(302, "http://u:p@127.0.0.1:${second.port}/"))
        assertEquals(RefusalReason.UserInfo, assertIs<TransportFailure.RefusedByPolicy>(fetch().exceptionOrNull()).reason)
        assertEquals(0, second.requestCount)
        assertEquals(5, server.requestCount)
    }

    @Test
    fun `maxRedirects 0 makes a 302 TooManyRedirects after one request`() {
        val none = fleet.track(LoopbackTransports.untrusted("agent", OutboundUrlPolicy.untrusted { settings { maxRedirects = 0 } }))
        server.enqueue(redirect(302, "/b"))
        server.enqueue(MockResponse())
        val failure = assertIs<TransportFailure.TooManyRedirects>(fleet.fetch(none, fleet.get(server.webtoolsUrl("/a"))).exceptionOrNull())
        assertEquals(0, failure.limit)
        assertEquals(1, server.requestCount)
    }
}
