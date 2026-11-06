package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.CloseLog
import com.spartanlabs.testing.support.webtools.http.FETCH_URL
import com.spartanlabs.testing.support.webtools.http.LoggedTransport
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.facadeEvents
import com.spartanlabs.testing.support.webtools.http.fetcherOn
import com.spartanlabs.testing.support.webtools.http.respondWith
import com.spartanlabs.webtools.http.FetcherParts
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpTransport
import com.spartanlabs.webtools.http.MSG_DEFAULT_MAX_WAIT
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.UrlFetcherConfig
import com.spartanlabs.webtools.http.UrlGuard
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - the lifecycle of `UrlFetcher`: `close()` closes the transport only when the fetcher built it, is idempotent and never throws (a transport
 * whose `close()` throws is logged at `DEBUG` and swallowed); a fetch after it returns `Closed("fetcher")` and reaches nothing; a negative
 * `defaultMaxWait` is refused when the fetcher is built, by the JVM owning factories before any transport exists; creation and close each log one
 * `INFO` line; `toString()` names the policy and budget kinds and never a URL; the per-fetch `DEBUG` line carries the redacted URL and never the
 * query, and dashes for the wait, the status and the size when the transport failed.
 */
class UrlFetcherLifecycleComponentTest {

    private val policy = OutboundUrlPolicy.untrusted()

    // A fetcher built the way an owning factory builds one: through the `internal` parts, with the ownership flag stated.
    private fun fetcherOver(transport: HttpTransport, ownsTransport: Boolean): UrlFetcher =
        UrlFetcher(FetcherParts(transport, UrlGuard(policy), UrlFetcherConfig().resolve(), ownsTransport))

    @Test
    fun `close closes the transport only when the fetcher built it`() {
        val ownedLog = CloseLog()
        val owned = FakeHttpTransport()
        fetcherOver(LoggedTransport(owned, ownedLog), ownsTransport = true).close()
        assertEquals(listOf("transport"), ownedLog.names)
        assertTrue(owned.isClosed)

        val handedLog = CloseLog()
        val handed = FakeHttpTransport()
        fetcherOver(LoggedTransport(handed, handedLog), ownsTransport = false).close()
        assertTrue(handedLog.names.isEmpty())
        assertFalse(handed.isClosed)

        // The common factory never owns the transport it is given.
        val commonLog = CloseLog()
        val common = FakeHttpTransport()
        UrlFetcher(LoggedTransport(common, commonLog), policy).close()
        assertTrue(commonLog.names.isEmpty())
        assertFalse(common.isClosed)
    }

    @Test
    fun `close is idempotent and never throws`() {
        val log = CloseLog()
        val events = captureTransportLogs {
            val fetcher = fetcherOver(LoggedTransport(FakeHttpTransport(), log, IllegalStateException("secret")), ownsTransport = true)
            repeat(3) { fetcher.close() }
        }.second

        val lines = facadeEvents(events, "UrlFetcher")
        assertEquals(listOf("transport"), log.names)
        assertEquals(1, lines.count { it.level == Level.INFO && it.formattedMessage == "closed" })
        assertEquals(listOf("close step transport failed: IllegalStateException"), lines.filter { it.level == Level.DEBUG }.map { it.formattedMessage })
        assertFalse(lines.any { "secret" in it.formattedMessage }, lines.map { it.formattedMessage }.toString())
    }

    @Test
    fun `a fetch after close returns Closed fetcher`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200))
        val fetcher = fetcherOn(clock, fake)
        fetcher.close()

        val closed = assertIs<HttpFailure.Closed>(fetcher.fetch(HttpRequest(HttpMethod.Get, FETCH_URL)).exceptionOrNull())

        assertEquals("fetcher", closed.component)
        assertTrue(fake.calls.isEmpty())
        assertFalse(fake.isClosed)
    }

    @Test
    fun `a negative defaultMaxWait is refused when built`() {
        val failure = assertFailsWith<IllegalArgumentException> { UrlFetcher(FakeHttpTransport(), policy) { defaultMaxWait = (-1).seconds } }
        assertEquals(MSG_DEFAULT_MAX_WAIT, failure.message)
    }

    @Test
    fun `a negative defaultMaxWait through the owning factory builds no transport`() {
        val events = captureTransportLogs(Level.INFO) {
            // Should a factory ever return, what it built is closed before the assertion fails.
            val plain = assertFailsWith<IllegalArgumentException> { UrlFetcher("agent") { defaultMaxWait = (-1).seconds }.close() }
            assertEquals(MSG_DEFAULT_MAX_WAIT, plain.message)
            val withPolicy = assertFailsWith<IllegalArgumentException> { UrlFetcher("agent", OutboundUrlPolicy.trusted()) { defaultMaxWait = (-1).seconds }.close() }
            assertEquals(MSG_DEFAULT_MAX_WAIT, withPolicy.message)
        }.second

        // The pure check runs first: neither a transport nor a fetcher logged its creation.
        assertFalse(events.any { it.formattedMessage.startsWith("created") }, events.map { it.loggerName + " " + it.formattedMessage }.toString())
    }

    @Test
    fun `a failed fetch logs dashes for the wait, the status and the size`() {
        val timeout = TransportFailure.Timeout()
        val events = captureTransportLogs {
            runTest {
                val clock = virtualClock()
                val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.fail(timeout))
                fetcherOn(clock, fake).use { fetcher ->
                    assertSame(timeout, fetcher.fetch(HttpRequest(HttpMethod.Get, FETCH_URL)).exceptionOrNull())
                }
            }
        }.second

        val lines = facadeEvents(events, "UrlFetcher")
        assertEquals(
            listOf("fetch: method=GET, url=https://files.example.test/a, lane=Interactive, cost=1, waitedMs=-, status=-, bytes=-, elapsedMs=0"),
            lines.filter { it.level == Level.DEBUG }.map { it.formattedMessage },
        )
        assertTrue(lines.none { it.level == Level.WARN }, lines.map { it.formattedMessage }.toString())
    }

    @Test
    fun `creation and close log at INFO`() {
        val events = captureTransportLogs { UrlFetcher(FakeHttpTransport(), policy).close() }.second

        assertEquals(
            listOf("created: policy=Untrusted, budget=Unlimited, ownsTransport=false, defaultMaxWait=5s", "closed"),
            facadeEvents(events, "UrlFetcher").filter { it.level == Level.INFO }.map { it.formattedMessage },
        )
    }

    @Test
    fun `toString names the policy and budget kinds and never a URL`() {
        val fetcher = UrlFetcher(FakeHttpTransport(), policy)
        assertEquals("UrlFetcher(policy=Untrusted, budget=Unlimited, ownsTransport=false, closed=false)", fetcher.toString())
        fetcher.close()
        assertEquals("UrlFetcher(policy=Untrusted, budget=Unlimited, ownsTransport=false, closed=true)", fetcher.toString())

        val owning = UrlFetcher(FetcherParts(FakeHttpTransport(), UrlGuard(OutboundUrlPolicy.trusted()), UrlFetcherConfig().resolve(), ownsTransport = true))
        assertEquals("UrlFetcher(policy=Trusted, budget=Unlimited, ownsTransport=true, closed=false)", owning.toString())
        owning.close()
    }

    @Test
    fun `the per-fetch DEBUG line carries the redacted URL, lane, cost and status and no query`() {
        val events = captureTransportLogs {
            runTest {
                val clock = virtualClock()
                val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "abc"))
                fetcherOn(clock, fake).use { fetcher ->
                    val request = HttpRequest(HttpMethod.Get, Url.parse("https://files.example.test/a?token=SECRET").getOrThrow())
                    assertEquals(200, fetcher.fetch(request).getOrThrow().status)
                }
            }
        }.second

        val lines = facadeEvents(events, "UrlFetcher")
        assertEquals(
            listOf("fetch: method=GET, url=https://files.example.test/a?..., lane=Interactive, cost=1, waitedMs=0, status=200, bytes=3, elapsedMs=0"),
            lines.filter { it.level == Level.DEBUG }.map { it.formattedMessage },
        )
        assertFalse(lines.any { "SECRET" in it.formattedMessage }, lines.map { it.formattedMessage }.toString())
    }
}
