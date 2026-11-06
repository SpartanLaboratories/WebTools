package com.spartanlabs.testing.gating.webtools.http

import com.spartanlabs.webtools.http.BudgetUtilisation
import com.spartanlabs.webtools.http.CacheStats
import com.spartanlabs.webtools.http.CallBudget
import com.spartanlabs.webtools.http.Endpoint
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.ResponseCache
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UpstreamClient
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 1 - the call facades as a consumer uses them, compiled **without any opt-in**: the common factory with a configuration lambda builds a
 * client over a fake transport, a call with every per-call setting but a body (a `GET` carries none) returns a found value, and `baseUrl`,
 * `utilisation()`, `cacheStats()` and `flush()` answer; the JVM owning factory with a budget built from windows builds a client that closes twice and refuses a call afterwards;
 * `UrlFetcher("agent") { }` refuses a private address before any socket and closes twice, and the common fetcher factory fetches over a fake
 * transport. No server and no network are involved.
 */
class UpstreamClientConsumerGatingTest {

    private val baseUrl: Url = Url.parse("https://api.example.test/v1/").getOrThrow()
    private val player: Endpoint<String> = Endpoint("player", { response -> Result.success(response.body().decodeToString()) })

    @Test
    fun `the common factory builds a client over a fake transport and a call returns a found value`() {
        val fake = FakeHttpTransport().on(RequestMatch.get("/v1/players/7", "region" to "eu"), Step.respond(200, body = "Ada"))
        val client = UpstreamClient(fake, baseUrl) {
            defaultMaxWait = 2.seconds
            headers = HttpHeaders { addSensitive("Authorization", "Bearer token") }
            cache(ResponseCache.Disabled)
            budget(CallBudget.Unlimited)
        }
        try {
            assertEquals(baseUrl, client.baseUrl)

            val lookup = runBlocking {
                withTimeout(5.seconds) {
                    client.call(player) {
                        path("players", "7")
                        query("region", "eu")
                        headers { add("X-Trace", "1") }
                        method = HttpMethod.Get
                        lane = Lane.Tracker
                        // Reads the value the client seeded (its `defaultMaxWait`, 2 s) and sets it: the property is readable and writable in the lambda.
                        maxWait = maxWait * 2
                        maxAge = 1.minutes
                    }
                }
            }.getOrThrow()

            assertEquals(Lookup.Found("Ada"), lookup)
            val sent = fake.calls.single().request
            assertEquals("Bearer token", sent.headers["Authorization"])
            assertEquals("1", sent.headers["X-Trace"])
            runBlocking {
                withTimeout(5.seconds) {
                    assertEquals(BudgetUtilisation(emptyList(), Duration.ZERO, 0), client.utilisation())
                    assertTrue(client.flush().isSuccess)
                }
            }
            assertEquals(CacheStats.Empty, client.cacheStats())
        } finally {
            client.close()
        }
    }

    @Test
    fun `the owning factory builds a client that closes twice`() {
        val client = UpstreamClient(baseUrl, "agent") {
            policy = OutboundUrlPolicy.trusted { allowCleartext = true; pinnedHosts = setOf(baseUrl.host) }
            budget(listOf(RateWindow("minute", 60, 1.minutes)))
        }
        try {
            assertTrue("Trusted" in client.toString(), client.toString())
            assertTrue("WindowedCallBudget" in client.toString(), client.toString())
        } finally {
            client.close()
            client.close()
        }

        val closed = assertIs<HttpFailure.Closed>(runBlocking { withTimeout(5.seconds) { client.call(player) } }.exceptionOrNull())
        assertEquals("client", closed.component)
    }

    @Test
    fun `a UrlFetcher refuses a private address and closes twice`() {
        val request = HttpRequest(HttpMethod.Get, Url.parse("http://10.0.0.5/").getOrThrow())
        val fetcher = UrlFetcher("agent") {
            budget = CallBudget.Unlimited
            defaultMaxWait = 2.seconds
        }
        try {
            val result = runBlocking {
                withTimeout(5.seconds) {
                    fetcher.fetch(request) {
                        cost = 2
                        lane = Lane.Tracker
                        maxWait = 1.seconds
                    }
                }
            }
            assertEquals(RefusalReason.DeniedAddress, assertIs<TransportFailure.RefusedByPolicy>(result.exceptionOrNull()).reason)
            assertTrue("Untrusted" in fetcher.toString(), fetcher.toString())
        } finally {
            fetcher.close()
            fetcher.close()
        }

        val closed = assertIs<HttpFailure.Closed>(runBlocking { withTimeout(5.seconds) { fetcher.fetch(request) } }.exceptionOrNull())
        assertEquals("fetcher", closed.component)

        // The common factory, over a transport the consumer owns.
        val fake = FakeHttpTransport().on(RequestMatch.get("/a"), Step.respond(200, body = "feed"))
        val overFake = UrlFetcher(fake, OutboundUrlPolicy.untrusted())
        try {
            val fetched = runBlocking {
                withTimeout(5.seconds) { overFake.fetch(HttpRequest(HttpMethod.Get, Url.parse("https://files.example.test/a").getOrThrow())) }
            }.getOrThrow()
            assertEquals(200, fetched.status)
            assertEquals("feed", fetched.body().decodeToString())
        } finally {
            overFake.close()
        }
    }
}
