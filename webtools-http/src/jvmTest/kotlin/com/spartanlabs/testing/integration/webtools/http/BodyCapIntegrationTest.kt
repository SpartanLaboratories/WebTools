package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.testing.support.webtools.http.gzipOfZeros
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.measureTimedValue

/**
 * Level 3 - the body cap on the wire with a 1 MB cap and a 10 s read timeout: a declared length above the cap fails promptly without a
 * read (`BodyTooLarge(cap, declared)`); a chunked 5 MB body is `BodyTooLarge(cap, null)`; exactly the cap succeeds and cap + 1 fails; a gzip
 * bomb (50 MiB decoded) is `BodyTooLarge` after one request made with `Accept-Encoding: gzip`; a `304` declaring 5 MB returns promptly with
 * its `ETag`; a `HEAD` answered with a declared length returns promptly with an empty body; a `204`; a followed `302` whose body is huge is
 * never read.
 */
class BodyCapIntegrationTest {

    private val cap = 1_000_000L
    private lateinit var fleet: LoopbackFleet
    private lateinit var server: MockWebServer
    private lateinit var transport: OkHttpTransport

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        server = fleet.server()
        transport = fleet.track(LoopbackTransports.untrusted("agent", OutboundUrlPolicy.untrusted { settings { maxResponseBytes = cap; readTimeout = 10.seconds; callTimeout = 30.seconds } }))
    }

    @AfterTest
    fun stop() = fleet.close()

    private fun fetch(method: HttpMethod = HttpMethod.Get): Result<HttpResponse> = fleet.fetch(transport, HttpRequest(method, server.webtoolsUrl("/")))

    private fun prompt(block: () -> Result<HttpResponse>): Result<HttpResponse> {
        val (result, elapsed) = measureTimedValue(block)
        assertTrue(elapsed < 3.seconds, "took $elapsed")
        return result
    }

    private fun bytes(n: Int): Buffer = Buffer().write(ByteArray(n) { 'x'.code.toByte() })

    @Test
    fun `a declared length above the cap fails promptly without a read`() {
        server.enqueue(MockResponse.Builder().body("tiny").setHeader("Content-Length", "5000000").build())
        val failure = assertIs<TransportFailure.BodyTooLarge>(prompt { fetch() }.exceptionOrNull())
        assertEquals(cap, failure.limit)
        assertEquals(5_000_000L, failure.declared)
    }

    @Test
    fun `a chunked body past the cap is BodyTooLarge with no declared length`() {
        server.enqueue(MockResponse.Builder().chunkedBody(bytes(5_000_000), 64 * 1024).build())
        val failure = assertIs<TransportFailure.BodyTooLarge>(fetch().exceptionOrNull())
        assertEquals(cap, failure.limit)
        assertNull(failure.declared)
    }

    @Test
    fun `exactly the cap succeeds and one more fails`() {
        server.enqueue(MockResponse.Builder().body(bytes(cap.toInt())).build())
        assertEquals(cap.toInt(), fetch().getOrThrow().bodySize)
        server.enqueue(MockResponse.Builder().body(bytes(cap.toInt() + 1)).build())
        val failure = assertIs<TransportFailure.BodyTooLarge>(fetch().exceptionOrNull())
        assertEquals(cap + 1, failure.declared)
    }

    @Test
    fun `a gzip bomb is BodyTooLarge after one request that asked for gzip`() {
        server.enqueue(MockResponse.Builder().addHeader("Content-Encoding", "gzip").body(Buffer().write(gzipOfZeros(50))).build())
        assertIs<TransportFailure.BodyTooLarge>(fetch().exceptionOrNull())
        assertEquals("gzip", assertNotNull(server.takeRequest(1, TimeUnit.SECONDS)).headers["Accept-Encoding"])
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a 304 a HEAD and a 204 read no body whatever is declared`() {
        server.enqueue(MockResponse.Builder().code(304).clearHeaders().addHeader("ETag", "\"v1\"").addHeader("Content-Length", "5000000").build())
        val notModified = prompt { fetch() }.getOrThrow()
        assertEquals(304, notModified.status)
        assertEquals("\"v1\"", notModified.headers["ETag"])
        assertEquals(0, notModified.bodySize)
        server.enqueue(MockResponse.Builder().clearHeaders().addHeader("Content-Length", "5000000").build())
        val head = prompt { fetch(HttpMethod.Head) }.getOrThrow()
        assertEquals(200, head.status)
        assertEquals(0, head.bodySize)
        assertEquals("5000000", head.headers["Content-Length"])
        server.enqueue(MockResponse.Builder().code(204).clearHeaders().build())
        assertEquals(204, prompt { fetch() }.getOrThrow().status)
    }

    @Test
    fun `a followed redirect's huge body is never read`() {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/ok").body(bytes(5_000_000)).build())
        server.enqueue(MockResponse(body = "ok"))
        val mark = TimeSource.Monotonic.markNow()
        val response = fetch().getOrThrow()
        val elapsed: Duration = mark.elapsedNow()
        assertEquals("ok", response.body().decodeToString())
        assertEquals(1, response.redirectChain.size)
        assertTrue(elapsed < 5.seconds, "took $elapsed")
    }
}
