package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.testing.support.webtools.http.stringEndpoint
import com.spartanlabs.webtools.http.CaffeineResponseCache
import com.spartanlabs.webtools.http.CallSpec
import com.spartanlabs.webtools.http.Endpoint
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.RequestBody
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UpstreamClient
import com.spartanlabs.webtools.http.UpstreamClientConfig
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.WindowedCallBudget
import com.spartanlabs.webtools.http.testing.LoopbackTransports
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
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 3 - `UpstreamClient` over a real exchange with a loopback server, on real time under `runBlocking` within 30 s: the common factory over
 * `LoopbackTransports.trusted` (cleartext, pinned to the server's host), a real `CaffeineResponseCache` and a real `WindowedCallBudget` on the
 * production clocks and dispatcher. A `GET` is decoded and the second call is served from the cache; the default headers and a per-call header reach
 * the server; a `POST` carries its body and bypasses the cache; a `429` pauses the budget and the next call fails fast; a declared miss is stored;
 * a response over the transport's cap is `BodyTooLarge`. The server's request count is the proof of what was sent.
 */
class UpstreamClientLoopbackIntegrationTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var server: MockWebServer
    private lateinit var baseUrl: Url
    private val closeables = ArrayList<AutoCloseable>()

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        server = fleet.server()
        baseUrl = server.webtoolsUrl("/v1/")
    }

    @AfterTest
    fun stop() {
        closeables.asReversed().forEach { runCatching { it.close() } }
        fleet.close()
    }

    private fun <T : AutoCloseable> closing(closeable: T): T = closeable.also { closeables += it }

    private fun pinned(configure: OutboundUrlPolicy.Trusted.Builder.() -> Unit = {}): OutboundUrlPolicy.Trusted =
        OutboundUrlPolicy.trusted { allowCleartext = true; pinnedHosts = setOf(baseUrl.host); configure() }

    // The common factory over the loopback transport, with a real cache and a real budget the test owns.
    private fun client(policy: OutboundUrlPolicy.Trusted = pinned(), configure: UpstreamClientConfig.() -> Unit = {}): UpstreamClient {
        val transport = fleet.track(LoopbackTransports.trusted("agent", policy))
        val responseCache = closing(CaffeineResponseCache())
        val callBudget = closing(WindowedCallBudget(listOf(RateWindow("minute", 60, 1.minutes))))
        val chosen = policy
        return closing(UpstreamClient(transport, baseUrl) { this.policy = chosen; cache(responseCache); budget(callBudget); configure() })
    }

    private fun <T : Any> call(client: UpstreamClient, endpoint: Endpoint<T>, configure: CallSpec.() -> Unit = {}): Result<Lookup<T>> =
        runBlocking { withTimeout(30.seconds) { client.call(endpoint, configure) } }

    private fun response(status: Int, body: String = "", vararg headers: Pair<String, String>): MockResponse =
        MockResponse.Builder().code(status).apply { headers.forEach { (name, value) -> addHeader(name, value) } }.body(body).build()

    @Test
    fun `a GET is decoded and a second call is served from the cache with the request count unchanged`() {
        server.enqueue(response(200, "Ada"))
        val client = client()
        val endpoint = stringEndpoint { ttl = 10.minutes }

        assertEquals(Lookup.Found("Ada"), call(client, endpoint) { path("players", "7") }.getOrThrow())
        assertEquals(Lookup.Found("Ada"), call(client, endpoint) { path("players", "7") }.getOrThrow())

        assertEquals(1, server.requestCount)
        assertEquals("/v1/players/7", assertNotNull(server.takeRequest(1, TimeUnit.SECONDS)).url.encodedPath)
        assertEquals(1L, client.cacheStats().hits)
    }

    @Test
    fun `default headers and a per-call header reach the server`() {
        server.enqueue(response(200, "ok"))
        val client = client { headers = HttpHeaders { add("Accept", "application/json"); addSensitive("Authorization", "Bearer t") } }

        call(client, stringEndpoint()) { headers { add("X-Trace", "1") } }.getOrThrow()

        val received = assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("application/json", received.headers["Accept"])
        assertEquals("Bearer t", received.headers["Authorization"])
        assertEquals("1", received.headers["X-Trace"])
        assertEquals("agent", received.headers["User-Agent"])
    }

    @Test
    fun `a POST with a body reaches the server and bypasses the cache`() {
        server.enqueue(response(200, "made"))
        server.enqueue(response(200, "made"))
        val client = client()
        val endpoint = stringEndpoint { ttl = 10.minutes }

        repeat(2) {
            val lookup = call(client, endpoint) {
                path("items")
                method = HttpMethod.Post
                body = RequestBody("abc".encodeToByteArray(), "text/plain")
            }.getOrThrow()
            assertEquals(Lookup.Found("made"), lookup)
        }

        assertEquals(2, server.requestCount)
        repeat(2) {
            val received = assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("POST", received.method)
            assertEquals("abc", received.body?.utf8())
        }
        assertEquals(0L, client.cacheStats().misses)
    }

    @Test
    fun `a 429 pauses the real budget and the next call fails fast with the request count unchanged`() {
        server.enqueue(response(429, "slow down", "Retry-After" to "30"))
        val client = client()

        val limited = assertIs<UpstreamFailure.RateLimited>(call(client, stringEndpoint()).exceptionOrNull())
        assertEquals(429, limited.status)
        assertEquals(30.seconds, limited.retryAfter)

        val exhausted = assertIs<HttpFailure.BudgetExhausted>(call(client, stringEndpoint()) { query("n", "2") }.exceptionOrNull())
        assertEquals(ExhaustedReason.Paused, exhausted.reason)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a 404 declared a miss is stored as Missing and not requested again`() {
        server.enqueue(response(404, "nope"))
        val client = client()
        val endpoint = stringEndpoint { misses(404); ttl = 10.minutes }

        repeat(2) { assertSame(Lookup.Missing, call(client, endpoint) { path("players", "0") }.getOrThrow()) }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a response larger than the transport's cap is BodyTooLarge`() {
        server.enqueue(response(200, "x".repeat(100)))
        val client = client(pinned { settings { maxResponseBytes = 16 } })

        val failure = assertIs<TransportFailure.BodyTooLarge>(call(client, stringEndpoint()).exceptionOrNull())

        assertEquals(16L, failure.limit)
        assertEquals(1, server.requestCount)
    }
}
