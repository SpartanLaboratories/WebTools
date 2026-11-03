package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.StubDns
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UrlGuard
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - what `execute` refuses before anything is sent, each under a short timeout with a resolver that must never be asked: a
 * controlled header is `InvalidRequest` with the list's spelling in its rule, naming the first such entry, under `Untrusted` and `Trusted`
 * alike; on a closed transport it is `Closed`; a denied address is `RefusedByPolicy(DeniedAddress)` with exactly one `WARN` (reason name,
 * range class, no query); a sensitive header under `Untrusted` is `RefusedByPolicy(SensitiveHeader)`; the controlled-header check precedes
 * the policy check.
 */
class OkHttpTransportPreflightComponentTest {

    private val resolver = StubDns { error("the resolver must not be reached before the preflight") }
    private val transports = mutableListOf<OkHttpTransport>()

    private fun untrusted(): OkHttpTransport =
        OkHttpTransport("agent", UrlGuard(OutboundUrlPolicy.untrusted()), resolver, null).also { transports += it }

    private fun trusted(): OkHttpTransport =
        OkHttpTransport("agent", UrlGuard(OutboundUrlPolicy.trusted()), resolver, null).also { transports += it }

    private fun request(urlText: String = "https://example.com/p", configure: HttpHeaders.Builder.() -> Unit = {}): HttpRequest =
        HttpRequest(HttpMethod.Get, url(urlText)) { headers = HttpHeaders(configure) }

    private fun fetch(transport: OkHttpTransport, request: HttpRequest): Result<HttpResponse> =
        runBlocking { withTimeout(5.seconds) { transport.execute(request) } }

    @AfterTest
    fun close() {
        transports.forEach { it.close() }
        assertEquals(0, resolver.calls.get(), "the resolver was asked")
    }

    @Test
    fun `a controlled header is InvalidRequest with the list's spelling`() {
        val host = assertIs<TransportFailure.InvalidRequest>(fetch(untrusted(), request { add("Host", "h") }).exceptionOrNull())
        assertEquals("header Host is controlled by the transport", host.rule)
        val encoding = assertIs<TransportFailure.InvalidRequest>(fetch(untrusted(), request { add("accept-encoding", "br") }).exceptionOrNull())
        assertEquals("header Accept-Encoding is controlled by the transport", encoding.rule)
    }

    @Test
    fun `the first controlled entry in request order is named under Untrusted and Trusted`() {
        val headers: HttpHeaders.Builder.() -> Unit = { add("X-A", "1"); add("te", "trailers"); add("Host", "h") }
        listOf(untrusted(), trusted()).forEach { transport ->
            val failure = assertIs<TransportFailure.InvalidRequest>(fetch(transport, request(configure = headers)).exceptionOrNull())
            assertEquals("header TE is controlled by the transport", failure.rule)
        }
    }

    @Test
    fun `a controlled header on a closed transport is Closed`() {
        val transport = untrusted()
        transport.close()
        val failure = assertIs<HttpFailure.Closed>(fetch(transport, request { add("Host", "h") }).exceptionOrNull())
        assertEquals("transport", failure.component)
    }

    @Test
    fun `a denied address is RefusedByPolicy with exactly one WARN`() {
        val transport = untrusted()
        val (result, events) = captureTransportLogs(Level.DEBUG) { fetch(transport, request("http://10.0.0.5/p?token=SECRET-QUERY")) }
        val failure = assertIs<TransportFailure.RefusedByPolicy>(result.exceptionOrNull())
        assertEquals(RefusalReason.DeniedAddress, failure.reason)
        val warnings = events.filter { it.level == Level.WARN }
        assertEquals(1, warnings.size, events.map { it.formattedMessage }.toString())
        val text = warnings.single().formattedMessage
        assertTrue("reason=DeniedAddress" in text, text)
        assertTrue("rangeClass=private-use" in text, text)
        assertTrue("request http://10.0.0.5/p?..." in text, text)
        assertFalse("SECRET" in text, text)
        assertTrue(events.none { "SECRET" in it.formattedMessage })
    }

    @Test
    fun `a sensitive header under Untrusted is RefusedByPolicy SensitiveHeader`() {
        val failure = assertIs<TransportFailure.RefusedByPolicy>(fetch(untrusted(), request { addSensitive("X-Api-Key", "k") }).exceptionOrNull())
        assertEquals(RefusalReason.SensitiveHeader, failure.reason)
    }

    @Test
    fun `the controlled-header check precedes the policy check`() {
        val failure = fetch(untrusted(), request("http://10.0.0.5/") { addSensitive("X-Api-Key", "k"); add("Host", "h") }).exceptionOrNull()
        assertEquals("header Host is controlled by the transport", assertIs<TransportFailure.InvalidRequest>(failure).rule)
    }
}
