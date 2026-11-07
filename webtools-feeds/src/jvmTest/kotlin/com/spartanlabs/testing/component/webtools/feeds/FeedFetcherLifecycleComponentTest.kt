package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.FEED_URL
import com.spartanlabs.testing.support.webtools.feeds.GateParser
import com.spartanlabs.testing.support.webtools.feeds.fetcherOn
import com.spartanlabs.testing.support.webtools.feeds.respondWith
import com.spartanlabs.testing.support.webtools.feeds.rssBody
import com.spartanlabs.webtools.feeds.FeedFetch
import com.spartanlabs.webtools.feeds.FeedFetcher
import com.spartanlabs.webtools.feeds.FeedFetcherConfig
import com.spartanlabs.webtools.feeds.FeedFetcherParts
import com.spartanlabs.webtools.feeds.RomeFeedParser
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - the fetcher's lifecycle: `close` closes the `UrlFetcher` only when the fetcher built it; `close` is idempotent and safe
 * from eight threads; a fetch after `close` returns `Closed("feed fetcher")` and reaches nothing; `close` during a running parse lets
 * it finish and its fetch return its result; `toString` names the parser kind, the numbers and the closed flag and never a URL;
 * `close` of a fetcher over a `UrlFetcher` the caller closed does not throw.
 */
class FeedFetcherLifecycleComponentTest {

    private val body = rssBody(1)

    @Test
    fun `close closes the UrlFetcher only when the fetcher built it`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        val given = UrlFetcher(fake, OutboundUrlPolicy.untrusted()) { timeSource = clock.timeSource }
        FeedFetcher(given) { parseDispatcher = clock.dispatcher }.close()
        assertTrue(given.fetch(HttpRequest(HttpMethod.Get, FEED_URL)).isSuccess, "the common factory never closes the fetcher it was given")

        val owned = UrlFetcher(fake, OutboundUrlPolicy.untrusted()) { timeSource = clock.timeSource }
        val resolved = FeedFetcherConfig().apply { parseDispatcher = clock.dispatcher }.resolve()
        FeedFetcher(FeedFetcherParts(owned, resolved, ownsUrlFetcher = true)).close()
        val closed = assertIs<HttpFailure.Closed>(owned.fetch(HttpRequest(HttpMethod.Get, FEED_URL)).exceptionOrNull())
        assertEquals("fetcher", closed.component)
        given.close()
    }

    @Test
    fun `close is idempotent and safe from eight threads`() {
        val fake = FakeHttpTransport().on(RequestMatch.Any, respondWith(200, body))
        val urlFetcher = UrlFetcher(fake, OutboundUrlPolicy.untrusted())
        val fetcher = FeedFetcher(urlFetcher)
        val start = CountDownLatch(1)
        val failures = AtomicInteger()
        val threads = List(8) {
            Thread {
                start.await()
                repeat(3) { runCatching { fetcher.close() }.onFailure { failures.incrementAndGet() } }
            }.apply { start() }
        }
        start.countDown()
        for (t in threads) t.join(10_000)
        assertEquals(0, failures.get())
        assertTrue(fetcher.toString().contains("closed=true"))
        urlFetcher.close()
    }

    @Test
    fun `a fetch after close returns Closed feed fetcher and reaches nothing`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        val fetcher = fetcherOn(clock, fake)
        fetcher.close()
        val closed = assertIs<HttpFailure.Closed>(fetcher.fetch(FEED_URL).exceptionOrNull())
        assertEquals("feed fetcher", closed.component)
        assertEquals("The feed fetcher is closed", closed.message)
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun `close during a running parse lets it finish and its fetch return its result`() {
        val pool = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
        try {
            runBlocking {
                withTimeout(10.seconds) {
                    val gate = GateParser()
                    val fake = FakeHttpTransport().on(RequestMatch.Any, respondWith(200, body))
                    val fetcher = FeedFetcher(UrlFetcher(fake, OutboundUrlPolicy.untrusted())) { parser = gate; parseDispatcher = pool }
                    val running = async(pool) { fetcher.fetch(FEED_URL) }
                    assertTrue(gate.awaitRunning(1))
                    fetcher.close()
                    assertEquals(1, gate.running, "close does not interrupt the parse")
                    gate.release()
                    assertIs<FeedFetch.Fetched>(running.await().getOrThrow())
                    assertEquals("feed fetcher", assertIs<HttpFailure.Closed>(fetcher.fetch(FEED_URL).exceptionOrNull()).component)
                }
            }
        } finally {
            pool.close()
        }
    }

    @Test
    fun `toString names the parser kind, the numbers and the closed flag, and never a URL`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        fetcherOn(clock, fake) { parser = RomeFeedParser(); maxConcurrentParses = 3 }.use { fetcher ->
            fetcher.fetch(FEED_URL)
            assertEquals("FeedFetcher(parser=RomeFeedParser, maxConcurrentParses=3, ownsUrlFetcher=false, closed=false)", fetcher.toString())
            assertFalse(fetcher.toString().contains("example.test"))
        }
        val fetcher = fetcherOn(clock, fake)
        fetcher.close()
        assertEquals("FeedFetcher(parser=RomeFeedParser, maxConcurrentParses=2, ownsUrlFetcher=false, closed=true)", fetcher.toString())
    }

    @Test
    fun `close of a fetcher over a UrlFetcher the caller closed does not throw`() {
        val fake = FakeHttpTransport().on(RequestMatch.Any, respondWith(200, body))
        val urlFetcher = UrlFetcher(fake, OutboundUrlPolicy.untrusted())
        val resolved = FeedFetcherConfig().resolve()
        val owning = FeedFetcher(FeedFetcherParts(urlFetcher, resolved, ownsUrlFetcher = true))
        urlFetcher.close()
        owning.close()
        val common = FeedFetcher(urlFetcher)
        common.close()
        assertTrue(owning.toString().contains("closed=true"))
        assertTrue(common.toString().contains("closed=true"))
    }
}
