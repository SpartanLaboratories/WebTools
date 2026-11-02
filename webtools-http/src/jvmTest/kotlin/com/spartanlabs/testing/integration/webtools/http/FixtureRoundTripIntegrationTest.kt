package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.testing.support.webtools.http.gzipOfZeros
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.testing.Fixture
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.enqueue
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Level 3 - fixtures through the real transport: `enqueue(fixture)` of a `200`, a `204`, a `304` with a declared length, a response to
 * `HEAD`, a gzip-encoded fixture and a `Transfer-Encoding: chunked` fixture each arrive with their status, headers and body and without a
 * hang; a `1xx` fixture is refused before it is queued.
 */
class FixtureRoundTripIntegrationTest {

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

    private fun fixture(status: Int, body: ByteArray = ByteArray(0), configure: HttpHeaders.Builder.() -> Unit = {}) = Fixture(status, HttpHeaders(configure), body)

    private fun fetch(method: HttpMethod = HttpMethod.Get): HttpResponse = fleet.fetch(transport, HttpRequest(method, server.webtoolsUrl("/"))).getOrThrow()

    @Test
    fun `a 200 a 204 and a 304 round-trip`() {
        server.enqueue(fixture(200, "hello".encodeToByteArray()) { add("Content-Type", "text/plain"); add("X-A", "1") })
        val ok = fetch()
        assertEquals(200, ok.status)
        assertEquals("text/plain", ok.headers["Content-Type"])
        assertEquals("1", ok.headers["X-A"])
        assertEquals("hello", ok.body().decodeToString())
        server.enqueue(fixture(204) { add("X-A", "2") })
        val none = fetch()
        assertEquals(204, none.status)
        assertEquals(0, none.bodySize)
        server.enqueue(fixture(304) { add("ETag", "\"v1\""); add("Content-Length", "5000") })
        val notModified = fetch()
        assertEquals(304, notModified.status)
        assertEquals("\"v1\"", notModified.headers["ETag"])
        assertEquals("5000", notModified.headers["Content-Length"])
        assertEquals(0, notModified.bodySize)
    }

    @Test
    fun `a HEAD response round-trips without a body`() {
        server.enqueue(fixture(200, "body".encodeToByteArray()) { add("X-A", "1") }, HttpMethod.Head)
        val head = fetch(HttpMethod.Head)
        assertEquals(200, head.status)
        assertEquals("4", head.headers["Content-Length"])
        assertEquals(0, head.bodySize)
    }

    @Test
    fun `a gzip-encoded and a chunked fixture round-trip`() {
        val plain = ByteArray(2 * 1024 * 1024)
        server.enqueue(fixture(200, gzipOfZeros(2)) { add("Content-Encoding", "gzip") })
        val decoded = fetch()
        assertEquals(200, decoded.status)
        assertContentEquals(plain, decoded.body())
        assertNull(decoded.headers["Content-Encoding"])
        server.enqueue(fixture(200, "chunked body".encodeToByteArray()) { add("Transfer-Encoding", "chunked"); add("X-A", "1") })
        val chunked = fetch()
        assertEquals("chunked body", chunked.body().decodeToString())
        assertEquals("1", chunked.headers["X-A"])
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a 1xx fixture is refused before it is queued`() {
        assertFailsWith<IllegalArgumentException> { server.enqueue(fixture(100)) }
        assertEquals(0, server.requestCount)
    }
}
