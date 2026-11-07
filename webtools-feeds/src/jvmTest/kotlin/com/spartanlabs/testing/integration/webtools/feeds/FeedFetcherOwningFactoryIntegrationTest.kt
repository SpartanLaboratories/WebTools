package com.spartanlabs.testing.integration.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.ConditionalGetDispatcher
import com.spartanlabs.webtools.feeds.FeedFetcher
import com.spartanlabs.webtools.feeds.untrusted
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import java.net.InetAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

/**
 * Level 3 - `FeedFetcher.untrusted` with its real `Untrusted` policy, no loopback relaxation, against a `MockWebServer` on `127.0.0.1`: a
 * loopback URL is refused with `RefusedByPolicy` and no request reaches the server; after `close` a fetch is `Closed("feed fetcher")` and
 * no request reaches the server; the policy overload refuses a host on a denied suffix before any network.
 */
class FeedFetcherOwningFactoryIntegrationTest {

    private lateinit var server: MockWebServer

    @BeforeTest
    fun start() {
        server = MockWebServer().apply { dispatcher = ConditionalGetDispatcher(); start(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0) }
    }

    @AfterTest
    fun stop() {
        runCatching { server.close() }
    }

    private fun <T> bounded(block: suspend () -> T): T = runBlocking { withTimeout(30.seconds) { block() } }

    @Test
    fun `a loopback URL is refused with RefusedByPolicy and no request reaches the server`() = bounded {
        FeedFetcher.untrusted("feeds-test").use { fetcher ->
            assertIs<TransportFailure.RefusedByPolicy>(fetcher.fetch(server.webtoolsUrl("/rss")).exceptionOrNull())
            assertIs<TransportFailure.RefusedByPolicy>(fetcher.fetch(Url.parse("http://localhost:${server.port}/rss").getOrThrow()).exceptionOrNull())
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `after close a fetch is Closed feed fetcher and no request reaches the server`() = bounded {
        val fetcher = FeedFetcher.untrusted("feeds-test")
        fetcher.close()
        val closed = assertIs<HttpFailure.Closed>(fetcher.fetch(server.webtoolsUrl("/rss")).exceptionOrNull())
        assertEquals("feed fetcher", closed.component)
        assertEquals("feed fetcher", assertIs<HttpFailure.Closed>(fetcher.fetch(Url.parse("https://feeds.example.test/rss").getOrThrow()).exceptionOrNull()).component)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the policy overload refuses a host on a denied suffix before any network`() = bounded {
        FeedFetcher.untrusted("feeds-test", OutboundUrlPolicy.untrusted { extraDeniedHostSuffixes = setOf("denied.test") }).use { fetcher ->
            assertIs<TransportFailure.RefusedByPolicy>(fetcher.fetch(Url.parse("https://feeds.denied.test/rss").getOrThrow()).exceptionOrNull())
            assertIs<TransportFailure.RefusedByPolicy>(fetcher.fetch(server.webtoolsUrl("/rss")).exceptionOrNull())
        }
        assertEquals(0, server.requestCount)
    }
}
