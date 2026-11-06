package com.spartanlabs.testing.gating.webtools.http

import com.spartanlabs.webtools.http.Endpoint
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RefusalReason
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
import kotlin.time.Duration.Companion.seconds

/**
 * Level 1 - the call facades on their production defaults as a consumer uses them, compiled **without any opt-in**: the common factory builds a
 * client over a fake transport and a call returns a found value; the JVM owning factory builds a client that closes twice and refuses a call
 * afterwards; `UrlFetcher("agent")` refuses a private address before any socket and closes twice. No server and no network are involved.
 */
class UpstreamClientConsumerGatingTest {

    private val baseUrl: Url = Url.parse("https://api.example.test/v1/").getOrThrow()
    private val player: Endpoint<String> = Endpoint("player", { response -> Result.success(response.body().decodeToString()) })

    @Test
    fun `the common factory builds a client over a fake transport and a call returns a found value`() {
        val fake = FakeHttpTransport().on(RequestMatch.get("/v1/players/7"), Step.respond(200, body = "Ada"))
        val client = UpstreamClient(fake, baseUrl)

        val lookup = runBlocking { withTimeout(5.seconds) { client.call(player) { path("players", "7") } } }.getOrThrow()

        assertEquals(Lookup.Found("Ada"), lookup)
        assertEquals(1, fake.calls.size)
        client.close()
    }

    @Test
    fun `the owning factory builds a client that closes twice`() {
        val client = UpstreamClient(baseUrl, "agent") {
            policy = OutboundUrlPolicy.trusted { allowCleartext = true; pinnedHosts = setOf(baseUrl.host) }
        }
        assertTrue("Trusted" in client.toString(), client.toString())

        client.close()
        client.close()

        val closed = assertIs<HttpFailure.Closed>(runBlocking { withTimeout(5.seconds) { client.call(player) } }.exceptionOrNull())
        assertEquals("client", closed.component)
    }

    @Test
    fun `a UrlFetcher refuses a private address and closes twice`() {
        val fetcher = UrlFetcher("agent")
        val request = HttpRequest(HttpMethod.Get, Url.parse("http://10.0.0.5/").getOrThrow())

        val refused = assertIs<TransportFailure.RefusedByPolicy>(runBlocking { withTimeout(5.seconds) { fetcher.fetch(request) } }.exceptionOrNull())
        assertEquals(RefusalReason.DeniedAddress, refused.reason)

        fetcher.close()
        fetcher.close()

        val closed = assertIs<HttpFailure.Closed>(runBlocking { withTimeout(5.seconds) { fetcher.fetch(request) } }.exceptionOrNull())
        assertEquals("fetcher", closed.component)
    }
}
