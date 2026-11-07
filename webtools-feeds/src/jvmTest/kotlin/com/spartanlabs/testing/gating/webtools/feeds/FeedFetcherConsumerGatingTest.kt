package com.spartanlabs.testing.gating.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.FEED_URL
import com.spartanlabs.testing.support.webtools.feeds.respondWith
import com.spartanlabs.testing.support.webtools.feeds.rssBody
import com.spartanlabs.webtools.feeds.FeedFetch
import com.spartanlabs.webtools.feeds.FeedFetcher
import com.spartanlabs.webtools.feeds.FeedFetcherConfig
import com.spartanlabs.webtools.feeds.FeedInput
import com.spartanlabs.webtools.feeds.FeedLimits
import com.spartanlabs.webtools.feeds.FeedValidators
import com.spartanlabs.webtools.feeds.RomeFeedParser
import com.spartanlabs.webtools.feeds.untrusted
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 1 - the fetcher and the supplied parser on production defaults, compiled without an opt-in, with no server and no
 * network: `RomeFeedParser` is built with both constructors and parses; a configuration lambda for the fetcher compiles; the common
 * factory builds a fetcher over a fake and a conditional fetch is not modified; the owning factory refuses a private address, closes
 * twice and refuses a fetch afterwards; the owning factory with a policy of its own builds a fetcher.
 */
class FeedFetcherConsumerGatingTest {

    private val oneItem = rssBody(1)

    @Test
    fun `RomeFeedParser is built with both constructors and parses a feed`() {
        val input = FeedInput(oneItem.encodeToByteArray(), "application/rss+xml", FEED_URL)
        val byDefault = RomeFeedParser().parse(input).getOrThrow()
        val byLimits = RomeFeedParser(FeedLimits { maxEntries = 10 }).parse(input).getOrThrow()
        assertEquals(listOf("urn:item:1"), byDefault.entries.map { it.id })
        assertEquals(byDefault, byLimits)
    }

    @Test
    fun `a configuration lambda for the fetcher compiles`() {
        val configure: FeedFetcherConfig.() -> Unit = {
            maxConcurrentParses = 3
            accept = "application/rss+xml"
            parseDispatcher = Dispatchers.Default
        }
        assertNotNull(configure)
        assertEquals(2, FeedFetcherConfig.DEFAULT_MAX_CONCURRENT_PARSES)
        assertTrue(FeedFetcherConfig.DEFAULT_ACCEPT.startsWith("application/atom+xml"))
    }

    @Test
    fun `the common factory builds a fetcher over a fake and a conditional fetch is not modified`(): Unit = runBlocking {
        // A stub added later wins, so the conditional answer is added after the general one.
        val fake = FakeHttpTransport()
            .on(RequestMatch.Any, respondWith(200, oneItem, HttpHeaders { add("ETag", "\"v1\"") }))
            .on(RequestMatch.header("If-None-Match", "\"v1\""), respondWith(304, headers = HttpHeaders { add("ETag", "\"v1\"") }))
        UrlFetcher(fake, OutboundUrlPolicy.untrusted()).use { urlFetcher ->
            val fetcher = FeedFetcher(urlFetcher) { maxConcurrentParses = 1 }
            val first = assertIs<FeedFetch.Fetched>(fetcher.fetch(FEED_URL).getOrThrow())
            assertEquals("\"v1\"", first.validators.etag)
            val second = fetcher.fetch(FEED_URL, first.validators).getOrThrow()
            assertIs<FeedFetch.NotModified>(second)
            assertEquals(first.validators, second.validators)
            fetcher.close()
            assertTrue(urlFetcher.fetch(HttpRequest(HttpMethod.Get, FEED_URL)).isSuccess, "the common factory never closes the UrlFetcher it was given")
        }
    }

    @Test
    fun `the owning factory refuses a private address, closes twice and refuses a fetch afterwards`(): Unit = runBlocking {
        val fetcher = FeedFetcher.untrusted("feeds-gating/1.0")
        val refused = withTimeout(5.seconds) { fetcher.fetch(Url.parse("http://10.0.0.5/").getOrThrow()) }
        assertEquals(RefusalReason.DeniedAddress, assertIs<TransportFailure.RefusedByPolicy>(refused.exceptionOrNull()).reason)
        fetcher.close()
        fetcher.close()
        val closed = assertIs<HttpFailure.Closed>(fetcher.fetch(FEED_URL).exceptionOrNull())
        assertEquals("feed fetcher", closed.component)
    }

    @Test
    fun `the owning factory with a policy of its own builds a fetcher`() {
        FeedFetcher.untrusted("feeds-gating/1.0", OutboundUrlPolicy.untrusted { extraDeniedHostSuffixes = setOf("denied.test") }).use { fetcher ->
            assertTrue(fetcher.toString().contains("ownsUrlFetcher=true"), fetcher.toString())
            assertTrue(fetcher.toString().contains("parser=RomeFeedParser"), fetcher.toString())
        }
    }
}
