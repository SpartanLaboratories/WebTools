package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.RequestBody
import com.spartanlabs.webtools.http.TransportControlledHeaders
import com.spartanlabs.webtools.http.TransportFailure
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Level 3 - the controlled headers on the wire: each of the twelve names (and a mixed-case spelling) is `InvalidRequest` with no request
 * made; `Range: bytes=0-9` sends `Accept-Encoding: identity` while a request without `Range` sends `gzip`; exactly one `User-Agent`, the
 * factory's; a `POST` with a typed body arrives with its `Content-Type` and `Content-Length`; a `POST` without a body arrives with
 * `Content-Length: 0`; a `DELETE` with a body arrives with it.
 */
class ControlledHeadersIntegrationTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var server: MockWebServer
    private lateinit var transport: OkHttpTransport

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        server = fleet.server()
        transport = fleet.track(LoopbackTransports.untrusted("agent/1.0"))
    }

    @AfterTest
    fun stop() = fleet.close()

    private fun send(method: HttpMethod = HttpMethod.Get, body: RequestBody? = null, configure: HttpHeaders.Builder.() -> Unit = {}): RecordedRequest {
        server.enqueue(MockResponse(body = "ok"))
        fleet.fetch(transport, HttpRequest(method, server.webtoolsUrl("/")) { headers = HttpHeaders(configure); this.body = body }).getOrThrow()
        return assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
    }

    @Test
    fun `each controlled name is InvalidRequest with no request made`() {
        (TransportControlledHeaders.names + listOf("hOsT", "ACCEPT-ENCODING")).forEach { name ->
            val request = HttpRequest(HttpMethod.Get, server.webtoolsUrl("/")) { headers = HttpHeaders { add(name, "v") } }
            val failure = assertIs<TransportFailure.InvalidRequest>(fleet.fetch(transport, request).exceptionOrNull(), name)
            assertEquals(true, failure.rule.startsWith("header "), failure.rule)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `Range sends identity and its absence sends gzip`() {
        assertEquals("identity", send { add("Range", "bytes=0-9") }.headers["Accept-Encoding"])
        assertEquals("gzip", send().headers["Accept-Encoding"])
    }

    @Test
    fun `exactly one User-Agent the factory's`() {
        assertEquals(listOf("agent/1.0"), send().headers.values("User-Agent"))
    }

    @Test
    fun `bodies and their framing arrive as sent`() {
        val typed = send(HttpMethod.Post, RequestBody("{\"a\":1}".encodeToByteArray(), "application/json"))
        assertEquals("application/json", typed.headers["Content-Type"])
        assertEquals("7", typed.headers["Content-Length"])
        assertEquals("{\"a\":1}", typed.body?.utf8())
        val empty = send(HttpMethod.Post)
        assertEquals("0", empty.headers["Content-Length"])
        val delete = send(HttpMethod.Delete, RequestBody("bye".encodeToByteArray(), null))
        assertEquals("bye", delete.body?.utf8())
        assertEquals("3", delete.headers["Content-Length"])
    }
}
