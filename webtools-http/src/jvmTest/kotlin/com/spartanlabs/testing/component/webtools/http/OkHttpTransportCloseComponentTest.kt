package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.StubDns
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.UrlGuard
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Level 2 - `close()`: twice gives one `INFO` "closed" line and no exception; `execute` after it is `Closed("transport")`; it never blocks
 * (a fresh transport closes well under a second); a transport whose user agent was refused left nothing to close.
 */
class OkHttpTransportCloseComponentTest {

    private fun transport(): OkHttpTransport =
        OkHttpTransport("agent", UrlGuard(OutboundUrlPolicy.untrusted()), StubDns.of("127.0.0.1"), null)

    @Test
    fun `close twice logs one INFO closed line and never throws`() {
        val (_, events) = captureTransportLogs(Level.INFO) {
            val transport = transport()
            transport.close()
            transport.close()
        }
        assertEquals(1, events.count { it.formattedMessage == "closed" }, events.map { it.formattedMessage }.toString())
        assertTrue(events.filter { it.formattedMessage == "closed" }.all { it.level == Level.INFO })
    }

    @Test
    fun `execute after close is Closed transport`() = runBlocking {
        val transport = transport()
        transport.close()
        val failure = assertIs<HttpFailure.Closed>(withTimeout(5.seconds) { transport.execute(HttpRequest(HttpMethod.Get, url("https://example.com/"))) }.exceptionOrNull())
        assertEquals("transport", failure.component)
    }

    @Test
    fun `close does not block`() {
        val transport = transport()
        val mark = TimeSource.Monotonic.markNow()
        transport.close()
        assertTrue(mark.elapsedNow() < 1.seconds, "close took ${mark.elapsedNow()}")
    }
}
