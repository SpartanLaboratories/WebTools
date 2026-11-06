package com.spartanlabs.testing.integration.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.CidrBlock
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.WindowedCallBudget
import com.spartanlabs.webtools.http.testing.LoopbackFetchers
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 3 - `UrlFetcher` over a real exchange with a loopback server, on real time under `runBlocking` within 30 s: the loopback untrusted fetcher
 * returns a `200`, a `304` and a `404` as responses; a `429` is `RateLimited` with the server's `Retry-After` and pauses a supplied budget; a
 * private address that is not loopback is refused with no request at the server; the same loopback URL through the public owning factory is
 * refused (the relaxation is the fixture's only); the loopback trusted fetcher forces cleartext on; a fetcher over a policy with an added denied
 * range refuses it without a socket and without a permit; the public owning factory under a `Trusted` cleartext policy fetches from the server
 * with its `User-Agent` and closes the transport it built.
 */
class UrlFetcherLoopbackIntegrationTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var server: MockWebServer
    private val closeables = ArrayList<AutoCloseable>()

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        server = fleet.server()
    }

    @AfterTest
    fun stop() {
        closeables.asReversed().forEach { runCatching { it.close() } }
        fleet.close()
    }

    private fun <T : AutoCloseable> closing(closeable: T): T = closeable.also { closeables += it }

    private fun fetch(fetcher: UrlFetcher, url: Url): Result<HttpResponse> =
        runBlocking { withTimeout(30.seconds) { fetcher.fetch(HttpRequest(HttpMethod.Get, url)) } }

    private fun reasonOf(result: Result<*>): RefusalReason = assertIs<TransportFailure.RefusedByPolicy>(result.exceptionOrNull()).reason

    private fun response(status: Int, body: String = "", vararg headers: Pair<String, String>): MockResponse =
        MockResponse.Builder().code(status).apply { headers.forEach { (name, value) -> addHeader(name, value) } }.body(body).build()

    @Test
    fun `the loopback untrusted fetcher returns a 200 body from the server`() {
        server.enqueue(response(200, "hello", "Content-Type" to "text/plain"))
        val fetcher = closing(LoopbackFetchers.untrusted("agent"))

        val fetched = fetch(fetcher, server.webtoolsUrl("/a")).getOrThrow()

        assertEquals(200, fetched.status)
        assertEquals("hello", fetched.body().decodeToString())
        assertEquals("text/plain", fetched.headers["Content-Type"])
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a 304 and a 404 are returned as responses`() {
        server.enqueue(response(304, "", "ETag" to "\"v\""))
        server.enqueue(response(404, "nope", "X-Reason" to "gone"))
        val fetcher = closing(LoopbackFetchers.untrusted("agent"))

        val notModified = fetch(fetcher, server.webtoolsUrl("/a")).getOrThrow()
        assertEquals(304, notModified.status)
        assertEquals("\"v\"", notModified.headers["ETag"])
        assertEquals(0, notModified.bodySize)

        val notFound = fetch(fetcher, server.webtoolsUrl("/b")).getOrThrow()
        assertEquals(404, notFound.status)
        assertEquals("gone", notFound.headers["X-Reason"])
        assertEquals("nope", notFound.body().decodeToString())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a 429 is RateLimited with the server's Retry-After and pauses a supplied budget`() {
        server.enqueue(response(429, "slow down", "Retry-After" to "30"))
        val supplied = closing(WindowedCallBudget(listOf(RateWindow("minute", 60, 1.minutes))))
        val fetcher = closing(LoopbackFetchers.untrusted("agent") { budget = supplied })

        val limited = assertIs<UpstreamFailure.RateLimited>(fetch(fetcher, server.webtoolsUrl("/a")).exceptionOrNull())
        assertEquals(429, limited.status)
        assertEquals(30.seconds, limited.retryAfter)

        val exhausted = assertIs<HttpFailure.BudgetExhausted>(fetch(fetcher, server.webtoolsUrl("/a")).exceptionOrNull())
        assertEquals(ExhaustedReason.Paused, exhausted.reason)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a private non-loopback address is RefusedByPolicy with zero requests at the server`() {
        val fetcher = closing(LoopbackFetchers.untrusted("agent"))

        assertEquals(RefusalReason.DeniedAddress, reasonOf(fetch(fetcher, url("http://10.0.0.5/"))))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the same loopback URL through the public owning factory is refused`() {
        server.enqueue(response(200, "hello"))
        val fetcher = closing(UrlFetcher("agent"))

        // The loopback relaxation is the fixture's only: the public factory's pre-check refuses 127.0.0.1.
        assertEquals(RefusalReason.DeniedAddress, reasonOf(fetch(fetcher, server.webtoolsUrl("/a"))))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the loopback trusted fetcher forces cleartext on`() {
        server.enqueue(response(200, "hello"))
        val fetcher = closing(LoopbackFetchers.trusted("agent"))

        val fetched = fetch(fetcher, server.webtoolsUrl("/a")).getOrThrow()        // an `http` URL under a policy that is `https` only by default

        assertEquals(200, fetched.status)
        assertEquals("hello", fetched.body().decodeToString())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a UrlFetcher over a policy with an added denied range refuses it`() {
        val supplied = closing(WindowedCallBudget(listOf(RateWindow("minute", 60, 1.minutes))))
        val policy = OutboundUrlPolicy.untrusted { extraDeniedRanges += CidrBlock.parse("8.8.8.0/24").getOrThrow() }
        val fetcher = closing(UrlFetcher("agent", policy) { budget = supplied })

        assertEquals(RefusalReason.DeniedAddress, reasonOf(fetch(fetcher, url("http://8.8.8.8/"))))
        assertEquals(0, server.requestCount)
        // The refusal cost nothing: the supplied budget counted no unit.
        assertEquals(0, runBlocking { withTimeout(30.seconds) { supplied.utilisation() } }.windows.single().used)
    }

    @Test
    fun `the public owning factory under a Trusted cleartext policy fetches from the server and closes its transport`() {
        server.enqueue(response(200, "hello", "Content-Type" to "text/plain"))
        val events = captureTransportLogs(Level.INFO) {
            UrlFetcher("agent", OutboundUrlPolicy.trusted { allowCleartext = true }).use { fetcher ->
                val fetched = fetch(fetcher, server.webtoolsUrl("/a")).getOrThrow()

                assertEquals(200, fetched.status)
                assertEquals("hello", fetched.body().decodeToString())
            }
        }.second

        assertEquals(1, server.requestCount)
        assertEquals("agent", assertNotNull(server.takeRequest(1, TimeUnit.SECONDS)).headers["User-Agent"])
        // The fetcher closes the transport it built, then logs its own close.
        assertEquals(listOf("OkHttpTransport", "UrlFetcher"), events.filter { it.formattedMessage == "closed" }.map { it.loggerName.substringAfterLast('.') })
    }
}
