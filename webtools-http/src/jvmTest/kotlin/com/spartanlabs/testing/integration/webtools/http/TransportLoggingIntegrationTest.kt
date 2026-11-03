package com.spartanlabs.testing.integration.webtools.http

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Level 3 - what the transport logs: one `INFO` on creation with the policy kind and the settings; one `DEBUG` line per successful call with
 * the method, the redacted URL, the status, the size, the hop count and the elapsed time; one `DEBUG` line per followed hop; one `WARN` and no
 * more for a policy refusal; one `INFO` for two `close()` calls; and no line at any level holding a query string, a header value, a resolved
 * address or an OkHttp object's text.
 */
class TransportLoggingIntegrationTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var server: MockWebServer

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        server = fleet.server()
    }

    @AfterTest
    fun stop() = fleet.close()

    private fun List<ILoggingEvent>.transport(): List<ILoggingEvent> = filter { it.loggerName == "com.spartanlabs.webtools.http.OkHttpTransport" }

    @Test
    fun `creation calls redirects refusals and close log as specified and nothing secret`() {
        server.enqueue(MockResponse(body = "ok"))
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/b").build())
        server.enqueue(MockResponse(body = "ok"))
        val (_, events) = captureTransportLogs(Level.DEBUG) {
            val transport: OkHttpTransport = fleet.track(LoopbackTransports.untrusted("agent"))
            val secret = HttpRequest(HttpMethod.Get, server.webtoolsUrl("/a?token=SECRET-QUERY")) { headers = HttpHeaders { add("X-Secret", "SECRET-HEADER") } }
            fleet.fetch(transport, secret).getOrThrow()
            fleet.fetch(transport, fleet.get(server.webtoolsUrl("/a"))).getOrThrow()
            fleet.fetch(transport, fleet.get(url("http://10.0.0.5/p?token=SECRET-QUERY")))
            transport.close()
            transport.close()
        }
        val lines = events.transport()
        val created = lines.filter { it.level == Level.INFO && it.formattedMessage.startsWith("created") }
        assertEquals(1, created.size)
        assertTrue("policy=Untrusted" in created.single().formattedMessage)
        assertTrue("maxRedirects=3" in created.single().formattedMessage)
        val calls = lines.filter { it.level == Level.DEBUG && it.formattedMessage.startsWith("GET ") }
        assertEquals(2, calls.size, lines.map { it.formattedMessage }.toString())
        assertTrue(calls[0].formattedMessage.contains("-> 200 (2 bytes, 1 hops,"), calls[0].formattedMessage)
        assertTrue(calls[0].formattedMessage.contains("/a?..."), calls[0].formattedMessage)
        assertTrue(calls[0].formattedMessage.endsWith(" ms)"), calls[0].formattedMessage)
        assertTrue(calls[1].formattedMessage.contains("-> 200 (2 bytes, 2 hops,"), calls[1].formattedMessage)
        val redirects = lines.filter { it.level == Level.DEBUG && it.formattedMessage.startsWith("redirect ") }
        assertEquals(1, redirects.size)
        assertTrue(redirects.single().formattedMessage.contains("/a -> ") && redirects.single().formattedMessage.contains("/b (302)"), redirects.single().formattedMessage)
        val warnings = lines.filter { it.level == Level.WARN }
        assertEquals(1, warnings.size, warnings.map { it.formattedMessage }.toString())
        assertTrue("reason=DeniedAddress" in warnings.single().formattedMessage)
        assertEquals(1, lines.count { it.level == Level.INFO && it.formattedMessage == "closed" })
        events.forEach { event ->
            val text = event.formattedMessage + (event.throwableProxy?.message ?: "")
            listOf("SECRET", "token=", "okhttp3", "X-Secret").forEach { forbidden -> assertTrue(forbidden !in text, "${event.loggerName}: $text") }
        }
    }
}
