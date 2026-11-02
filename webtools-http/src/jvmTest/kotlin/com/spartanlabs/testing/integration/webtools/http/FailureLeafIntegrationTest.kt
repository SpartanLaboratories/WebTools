package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okio.Buffer
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

/**
 * Level 3 - the failure leaves on the wire: an `UnknownHostException` from the stub resolver is `DnsFailed` with the hop's host and the
 * stub's exception as the cause; a closed port is `ConnectFailed` whose cause is not OkHttp's `Failed to connect to` wrapper and whose text
 * holds no address; a 300 ms read timeout against a 3 s headers delay is `Timeout` with a `SocketTimeoutException` cause; a truncated chunked
 * body is `Io` with no cause; a garbage status line, a `600` and a `099` are `Protocol` with no cause and the body never read; a `101` is a
 * response.
 */
class FailureLeafIntegrationTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var server: MockWebServer

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        server = fleet.server()
    }

    @AfterTest
    fun stop() = fleet.close()

    private fun transport(configure: OutboundUrlPolicy.Untrusted.Builder.() -> Unit = {}): OkHttpTransport =
        fleet.track(LoopbackTransports.untrusted("agent", OutboundUrlPolicy.untrusted(configure)))

    private fun fetch(transport: OkHttpTransport = transport()): Result<HttpResponse> = fleet.fetch(transport, fleet.get(server.webtoolsUrl("/")))

    @Test
    fun `an unknown host is DnsFailed with the hop's host and the resolver's exception`() {
        val thrown = UnknownHostException("nonexistent.test: no answer")
        val transport = fleet.track(LoopbackTransports.untrusted("agent") { throw thrown })
        val failure = assertIs<TransportFailure.DnsFailed>(fleet.fetch(transport, fleet.get(url("http://nonexistent.test/"))).exceptionOrNull())
        assertEquals("nonexistent.test", failure.host)
        assertSame(thrown, failure.cause)
    }

    @Test
    fun `a closed port is ConnectFailed without OkHttp's wrapper or an address`() {
        val port = ServerSocket(0, 1, fleet.loopback).use { it.localPort }
        val failure = assertIs<TransportFailure.ConnectFailed>(fleet.fetch(transport(), fleet.get(url("http://127.0.0.1:$port/"))).exceptionOrNull())
        val cause = failure.cause
        if (cause != null) {
            assertFalse(cause.message.orEmpty().contains("Failed to connect to"), cause.toString())
            assertFalse(cause.message.orEmpty().contains("127.0.0.1"), cause.toString())
        }
        assertFalse(failure.message!!.contains("127.0.0.1"))
    }

    @Test
    fun `a read timeout is Timeout with a SocketTimeoutException cause`() {
        server.enqueue(MockResponse.Builder().headersDelay(3, TimeUnit.SECONDS).body("late").build())
        val failure = assertIs<TransportFailure.Timeout>(fetch(transport { settings { readTimeout = 300.milliseconds } }).exceptionOrNull())
        assertIs<SocketTimeoutException>(failure.cause)
    }

    @Test
    fun `a truncated chunked body is Io with no cause`() {
        server.enqueue(
            MockResponse.Builder().clearHeaders().addHeader("Transfer-Encoding", "chunked")
                .body(Buffer().writeUtf8("5\r\nhel")).removeHeader("Content-Length").onResponseEnd(SocketEffect.CloseSocket()).build(),
        )
        val failure = assertIs<TransportFailure.Io>(fetch().exceptionOrNull())
        assertNull(failure.cause)
    }

    @Test
    fun `a garbage status line a 600 and a 099 are Protocol with no cause and no body read`() {
        server.enqueue(MockResponse.Builder().status("GARBAGE").body("x").build())
        val garbage = assertIs<TransportFailure.Protocol>(fetch().exceptionOrNull())
        assertNull(garbage.cause)
        listOf("HTTP/1.1 600 X", "HTTP/1.1 099 X").forEach { line ->
            server.enqueue(MockResponse.Builder().status(line).body("tiny").setHeader("Content-Length", "5000000").build())
            val (result, elapsed) = measureTimedValue { fetch() }
            assertNull(assertIs<TransportFailure.Protocol>(result.exceptionOrNull(), line).cause)
            assertTrue(elapsed < 3.seconds, "$line took $elapsed: the body was read")
        }
    }

    @Test
    fun `a 101 is a response`() {
        server.enqueue(MockResponse.Builder().status("HTTP/1.1 101 Switching Protocols").build())
        val response = fetch().getOrThrow()
        assertEquals(101, response.status)
        assertEquals(0, response.bodySize)
    }
}
