package com.spartanlabs.testing.integration.webtools.feeds

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.feeds.ConditionalGetDispatcher
import com.spartanlabs.testing.support.webtools.feeds.captureFeedLogs
import com.spartanlabs.testing.support.webtools.feeds.feedEvents
import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedFetch
import com.spartanlabs.webtools.feeds.FeedFetcher
import com.spartanlabs.webtools.feeds.FeedFetcherConfig
import com.spartanlabs.webtools.feeds.FeedRefusal
import com.spartanlabs.webtools.feeds.FeedValidators
import com.spartanlabs.webtools.feeds.NotAFeedReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.testing.LoopbackFetchers
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Level 3 - `FeedFetcher(LoopbackFetchers.untrusted("feeds-test"))` over a `MockWebServer` on `127.0.0.1` with the conditional-GET
 * dispatcher and a real Rome parse: a `200` with validators then a conditional `304`, the headers reaching the server verbatim; a `304`
 * declaring a huge `Content-Length` returns promptly; a `301` then a `200` or a `304` reports the move; a `302` reports the final URL and no
 * move; served RSS 2.0 and Atom 1.0 bodies parse; a `410` is an `UpstreamError` and a `429` `RateLimited`; a body over 2 MiB is
 * `BodyTooLarge`; a web page is `NotAFeed(Html)` and a DOCTYPE `Refused(Doctype)` with one `WARN` line.
 */
class FeedFetcherLoopbackIntegrationTest {

    private val routes = ConditionalGetDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var fetcher: FeedFetcher

    @BeforeTest
    fun start() {
        server = MockWebServer().apply { dispatcher = routes; start(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0) }
        fetcher = FeedFetcher(LoopbackFetchers.untrusted("feeds-test"))
    }

    @AfterTest
    fun stop() {
        runCatching { fetcher.close() }
        runCatching { server.close() }
    }

    private fun <T> bounded(block: suspend () -> T): T = runBlocking { withTimeout(30.seconds) { block() } }

    @Test
    fun `a 200 with validators is followed by a conditional request that gets a 304, and the headers reach the server verbatim`() = bounded {
        val url = server.webtoolsUrl("/rss")
        val first = assertIs<FeedFetch.Fetched>(fetcher.fetch(url).getOrThrow())
        assertEquals(FeedValidators(routes.etag, routes.lastModified), first.validators)
        assertEquals(2, first.feed.entries.size)
        val firstRequest = assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals(FeedFetcherConfig.DEFAULT_ACCEPT, firstRequest.headers["Accept"])
        assertNull(firstRequest.headers["If-None-Match"])
        assertNull(firstRequest.headers["If-Modified-Since"])

        val second = assertIs<FeedFetch.NotModified>(fetcher.fetch(url, first.validators).getOrThrow())
        assertEquals(first.validators, second.validators)
        assertEquals(url, second.finalUrl)
        val secondRequest = assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals(routes.etag, secondRequest.headers["If-None-Match"])
        assertEquals(routes.lastModified, secondRequest.headers["If-Modified-Since"])
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a 304 that declares a huge Content-Length returns promptly`() = bounded {
        val started = TimeSource.Monotonic.markNow()
        val result = fetcher.fetch(server.webtoolsUrl("/big304"), FeedValidators("\"anything\"", null))
        val notModified = assertIs<FeedFetch.NotModified>(result.getOrThrow())
        assertEquals(routes.etag, notModified.validators.etag)
        assertTrue(started.elapsedNow() < 10.seconds, "took ${started.elapsedNow()}")
    }

    @Test
    fun `a 301 then a 200 reports the move, with finalUrl and the validators of the final response`() = bounded {
        val moved = server.webtoolsUrl("/moved")
        val fetched = assertIs<FeedFetch.Fetched>(fetcher.fetch(moved).getOrThrow())
        assertEquals(server.webtoolsUrl("/rss"), fetched.permanentlyMovedTo)
        assertEquals(server.webtoolsUrl("/rss"), fetched.finalUrl)
        assertEquals(FeedValidators(routes.etag, routes.lastModified), fetched.validators)
        assertEquals(2, fetched.feed.entries.size)
    }

    @Test
    fun `a 301 then a 304 reports the move on NotModified`() = bounded {
        val moved = server.webtoolsUrl("/moved")
        val notModified = assertIs<FeedFetch.NotModified>(fetcher.fetch(moved, FeedValidators(routes.etag, null)).getOrThrow())
        assertEquals(server.webtoolsUrl("/rss"), notModified.permanentlyMovedTo)
        assertEquals(server.webtoolsUrl("/rss"), notModified.finalUrl)
        assertEquals(FeedValidators(routes.etag, routes.lastModified), notModified.validators)
    }

    @Test
    fun `a 302 then a 200 reports the final URL and no move`() = bounded {
        val fetched = assertIs<FeedFetch.Fetched>(fetcher.fetch(server.webtoolsUrl("/temp")).getOrThrow())
        assertNull(fetched.permanentlyMovedTo)
        assertEquals(server.webtoolsUrl("/rss"), fetched.finalUrl)
    }

    @Test
    fun `a served RSS 2 0 body and a served Atom 1 0 body parse into the expected entries, ids and links`() = bounded {
        val rss = assertIs<FeedFetch.Fetched>(fetcher.fetch(server.webtoolsUrl("/rss")).getOrThrow()).feed
        assertEquals("Loopback RSS", rss.title)
        assertEquals(listOf("urn:item:1", "urn:item:2"), rss.entries.map { it.id })
        assertEquals(listOf("https://feeds.example.test/items/1", "https://feeds.example.test/items/2"), rss.entries.map { it.link })
        val atom = assertIs<FeedFetch.Fetched>(fetcher.fetch(server.webtoolsUrl("/atom")).getOrThrow()).feed
        assertEquals("Loopback Atom", atom.title)
        assertEquals("https://feeds.example.test/", atom.link)
        assertEquals(listOf("urn:atom:1"), atom.entries.map { it.id })
        assertEquals(listOf("https://feeds.example.test/atom/1"), atom.entries.map { it.link })
    }

    @Test
    fun `a 410 is an UpstreamError and a 429 is RateLimited`() = bounded {
        val gone = assertIs<UpstreamFailure.UpstreamError>(fetcher.fetch(server.webtoolsUrl("/gone")).exceptionOrNull())
        assertEquals(410, gone.status)
        val limited = assertIs<UpstreamFailure.RateLimited>(fetcher.fetch(server.webtoolsUrl("/throttled")).exceptionOrNull())
        assertEquals(30.seconds, limited.retryAfter)
    }

    @Test
    fun `a body over 2 MiB is BodyTooLarge`() = bounded {
        val failure = assertIs<TransportFailure.BodyTooLarge>(fetcher.fetch(server.webtoolsUrl("/huge")).exceptionOrNull())
        assertEquals(2L * 1024 * 1024, failure.limit)
    }

    @Test
    fun `a web page served as a 200 is NotAFeed Html and a body with a DOCTYPE is Refused Doctype with one WARN line`() = bounded {
        val page = assertIs<FeedFailure.NotAFeed>(fetcher.fetch(server.webtoolsUrl("/page")).exceptionOrNull())
        assertEquals(NotAFeedReason.Html, page.reason)
        val (result, events) = captureFeedLogs(Level.DEBUG) { fetcher.fetch(server.webtoolsUrl("/doctype")) }
        assertEquals(FeedRefusal.Doctype, assertIs<FeedFailure.Refused>(result.exceptionOrNull()).reason)
        val warns = feedEvents(events).filter { it.level == Level.WARN }
        assertEquals(1, warns.size)
        assertTrue(warns.single().formattedMessage.startsWith("refused document: reason=Doctype, target=http://127.0.0.1:"), warns.single().formattedMessage)
    }
}
