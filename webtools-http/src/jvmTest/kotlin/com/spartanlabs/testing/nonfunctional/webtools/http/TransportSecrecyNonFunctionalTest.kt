package com.spartanlabs.testing.nonfunctional.webtools.http

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.inet
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Level 4c - nothing secret leaks: a refused redirect (a pinned `trusted` transport, a request with `Authorization: Bearer SECRET-HEADER`, a
 * `302` to `http://other.test:<port>/p?token=SECRET-QUERY`) and a rebinding refusal (a stub resolver answering `10.0.0.5`) each fail with
 * `RefusedByPolicy`, and neither the failure's message, `toString()` or cause chain, nor any captured log line at any level (messages and
 * rendered throwables), holds `SECRET-QUERY`, `SECRET-HEADER`, `10.0.0.5` or `token=`; the `WARN` carries the redacted URL (`?...`); a
 * `Protocol` failure's text holds no server-sent byte.
 */
class TransportSecrecyNonFunctionalTest {

    private val forbidden = listOf("SECRET-QUERY", "SECRET-HEADER", "10.0.0.5", "token=", "GARBAGE-SECRET")
    private lateinit var fleet: LoopbackFleet

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
    }

    @AfterTest
    fun stop() = fleet.close()

    private fun Throwable.texts(): List<String> =
        generateSequence(this) { it.cause }.flatMap { sequenceOf(it.toString(), it.message.orEmpty()) }.toList()

    private fun assertClean(failure: Throwable, events: List<ILoggingEvent>) {
        val texts = failure.texts() + events.flatMap { event ->
            listOf(event.formattedMessage) + (event.throwableProxy?.let { listOf(it.className + ": " + it.message) } ?: emptyList())
        }
        texts.forEach { text -> forbidden.forEach { secret -> assertTrue(secret !in text, "'$secret' leaked in: $text") } }
    }

    @Test
    fun `a refused redirect leaks neither the query nor the header`() {
        val server = fleet.server()
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://other.test:${server.port}/p?token=SECRET-QUERY").build())
        val transport = fleet.track(LoopbackTransports.trusted("agent", OutboundUrlPolicy.trusted { pinnedHosts = setOf("127.0.0.1") }))
        val request = HttpRequest(HttpMethod.Get, server.webtoolsUrl("/start?token=SECRET-QUERY")) { headers = HttpHeaders { add("Authorization", "Bearer SECRET-HEADER") } }
        val (result, events) = captureTransportLogs(Level.DEBUG) { fleet.fetch(transport, request) }
        val failure = assertIs<TransportFailure.RefusedByPolicy>(result.exceptionOrNull())
        assertEquals(RefusalReason.HostNotPinned, failure.reason)
        assertClean(failure, events)
        val warning = events.single { it.level == Level.WARN }
        assertTrue("/start?..." in warning.formattedMessage, warning.formattedMessage)
    }

    @Test
    fun `a rebinding refusal leaks no address`() {
        val server = fleet.server()
        val transport = fleet.track(LoopbackTransports.untrusted("agent", OutboundUrlPolicy.untrusted { allowedPorts = null }) { listOf(inet("10.0.0.5")) })
        val (result, events) = captureTransportLogs(Level.DEBUG) { fleet.fetch(transport, fleet.get(url("http://rebind.test:${server.port}/p?token=SECRET-QUERY"))) }
        val failure = assertIs<TransportFailure.RefusedByPolicy>(result.exceptionOrNull())
        assertEquals(RefusalReason.DeniedAddress, failure.reason)
        assertClean(failure, events)
        assertTrue("/p?..." in events.single { it.level == Level.WARN }.formattedMessage)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a Protocol failure holds no server-sent byte`() {
        val server = fleet.server()
        server.enqueue(MockResponse.Builder().status("GARBAGE-SECRET").body("x").build())
        val transport = fleet.track(LoopbackTransports.untrusted("agent"))
        val (result, events) = captureTransportLogs(Level.DEBUG) { fleet.fetch(transport, fleet.get(server.webtoolsUrl("/"))) }
        val failure: Throwable = assertIs<TransportFailure.Protocol>((result as Result<HttpResponse>).exceptionOrNull())
        assertClean(failure, events)
    }
}
