package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.FakeCall
import com.spartanlabs.testing.support.webtools.http.ManualTimeSource
import com.spartanlabs.testing.support.webtools.http.StubDns
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.fakeResponse
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RedirectHop
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UrlGuard
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Headers.Companion.headersOf
import okhttp3.Request
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - the deadline check before a later hop, with no socket and no wall clock: the transport takes a manual clock and a factory of
 * fake `Call`s whose hop 1 moves the clock and answers a followable `302`. (1) The clock at the whole `callTimeout`: `Timeout` with no
 * `cause`, one `Call` created, hop 1 enqueued and never cancelled, one `DEBUG` redirect line and one `DEBUG` failure line, no `WARN`.
 * (2) The clock 999 us short of the timeout (no whole millisecond left): `Timeout`, one `Call`. (3) The clock 1 ms short: hop 2's `Call`
 * is created with a 1 ms limit and the result is its `200` with the redirect chain. The resolver is never asked.
 */
class OkHttpTransportDeadlineComponentTest {

    private val policy = OutboundUrlPolicy.untrusted()
    private val total: Duration = policy.settings.callTimeout
    private val clock = ManualTimeSource()
    private val resolver = StubDns { error("every hop is a fake Call; the resolver must not be asked") }
    private val requests = mutableListOf<Request>()          // one per Call the transport created, in order
    private val calls = mutableListOf<FakeCall>()
    private val transports = mutableListOf<OkHttpTransport>()

    // Hop 1 sets the clock to clockDuringHop1 (when given), then answers 302 Location: /next; every later hop answers an empty 200.
    private fun transport(clockDuringHop1: Duration?): OkHttpTransport {
        val factory = Call.Factory { request ->
            requests += request
            val hop = requests.size
            FakeCall(request) { call ->
                if (hop == 1) {
                    clockDuringHop1?.let { clock.now = it }
                    call.deliverResponse(fakeResponse(302, headersOf("Location", "/next"), request = call.request()))
                } else {
                    call.deliverResponse(fakeResponse(200, request = call.request()))
                }
            }.also { calls += it }
        }
        return OkHttpTransport("agent", UrlGuard(policy), resolver, null, clock, factory).also { transports += it }
    }

    private fun fetch(transport: OkHttpTransport): Result<HttpResponse> =
        runBlocking { withTimeout(5.seconds) { transport.execute(HttpRequest(HttpMethod.Get, url("https://example.com/start"))) } }

    @AfterTest
    fun close() {
        transports.forEach { it.close() }
        assertEquals(0, resolver.calls.get(), "the resolver was asked")
    }

    @Test
    fun `a hop that spends the whole deadline makes the next hop a Timeout with no Call`() {
        val transport = transport(clockDuringHop1 = total)
        val (result, events) = captureTransportLogs(Level.DEBUG) { fetch(transport) }
        val failure = assertIs<TransportFailure.Timeout>(result.exceptionOrNull())
        assertNull(failure.cause)
        assertEquals(listOf("https://example.com/start"), requests.map { it.url.toString() })
        assertEquals(listOf("enqueue"), calls.single().events.toList())
        val lines = events.map { it.formattedMessage }
        assertEquals(2, events.size, lines.toString())
        assertTrue(events.all { it.level == Level.DEBUG }, lines.toString())
        assertEquals("redirect https://example.com/start -> https://example.com/next (302)", lines[0])
        assertTrue(lines[1].startsWith("Timeout failed for https://example.com/next"), lines[1])
        assertTrue(events.none { it.level == Level.WARN }, lines.toString())
    }

    @Test
    fun `no whole millisecond left is a Timeout with no Call`() {
        val result = fetch(transport(clockDuringHop1 = total - 999.microseconds))
        val failure = assertIs<TransportFailure.Timeout>(result.exceptionOrNull())
        assertNull(failure.cause)
        assertEquals(1, requests.size, requests.toString())
    }

    @Test
    fun `one millisecond left reaches the next hop with a one millisecond limit`() {
        val result = fetch(transport(clockDuringHop1 = total - 1.milliseconds))
        val response = result.getOrThrow()
        assertEquals(200, response.status)
        assertEquals(url("https://example.com/next"), response.url)
        assertEquals(listOf(RedirectHop(url("https://example.com/start"), 302)), response.redirectChain)
        assertEquals(listOf("https://example.com/start", "https://example.com/next"), requests.map { it.url.toString() })
        assertEquals(2, calls.size)
        assertEquals(total.inWholeMilliseconds * 1_000_000, calls[0].timeout().timeoutNanos())
        assertEquals(1_000_000L, calls[1].timeout().timeoutNanos())
    }
}
