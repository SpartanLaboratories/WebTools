package com.spartanlabs.testing.nonfunctional.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.respondWith
import com.spartanlabs.testing.support.webtools.feeds.rssBody
import com.spartanlabs.webtools.feeds.FeedFetch
import com.spartanlabs.webtools.feeds.FeedFetcher
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Level 4c - the fetcher under real threads: `close` racing fetches never throws and every fetch returns a `Result` (a feed or
 * `Closed`); eight threads fetch and parse through one fetcher with two permits and every result is right.
 */
class FeedFetcherCloseRaceNonFunctionalTest {

    private fun url(path: String): Url = Url.parse("https://feeds.example.test$path").getOrThrow()

    @Test
    fun `close racing fetches never throws and every fetch returns a Result`() {
        val fake = FakeHttpTransport().on(RequestMatch.Any, Step.respond(200, HttpHeaders(), rssBody(3), delay = 5.milliseconds))
        val urlFetcher = UrlFetcher(fake, OutboundUrlPolicy.untrusted())
        val fetcher = FeedFetcher(urlFetcher)
        val start = CountDownLatch(1)
        val thrown = AtomicInteger()
        val results = AtomicInteger()
        val closedResults = AtomicInteger()
        val fetchers = List(8) { t ->
            Thread {
                start.await()
                repeat(40) {
                    try {
                        val result = runBlocking { fetcher.fetch(url("/t$t")) }
                        results.incrementAndGet()
                        val failure = result.exceptionOrNull()
                        if (failure is HttpFailure.Closed) closedResults.incrementAndGet()
                        else if (result.isFailure) thrown.incrementAndGet()
                    } catch (_: Throwable) {
                        thrown.incrementAndGet()
                    }
                }
            }.apply { start() }
        }
        val closers = List(4) {
            Thread {
                start.await()
                Thread.sleep(40L + it * 20)
                repeat(10) { runCatching { fetcher.close() }.onFailure { thrown.incrementAndGet() } }
            }.apply { start() }
        }
        start.countDown()
        (fetchers + closers).forEach { it.join(60_000) }
        assertEquals(0, thrown.get(), "a fetch or a close threw, or a fetch failed with something other than Closed")
        assertEquals(320, results.get(), "every fetch returned a Result")
        assertTrue(closedResults.get() > 0, "some fetches must have seen the closed fetcher")
        assertTrue(closedResults.get() < 320, "some fetches must have completed before the close")
        urlFetcher.close()
    }

    @Test
    fun `eight threads fetch and parse through one fetcher with two permits and every result is right`() {
        val fake = FakeHttpTransport()
        for (t in 0 until 8) fake.on(RequestMatch.get("/t$t"), respondWith(200, rssBody(t + 1, title = "Feed $t")))
        val urlFetcher = UrlFetcher(fake, OutboundUrlPolicy.untrusted())
        val fetcher = FeedFetcher(urlFetcher) { maxConcurrentParses = 2 }
        val wrong = AtomicInteger()
        val start = CountDownLatch(1)
        val threads = List(8) { t ->
            Thread {
                start.await()
                repeat(30) {
                    val result = runBlocking { fetcher.fetch(url("/t$t")) }
                    val fetched = result.getOrNull() as? FeedFetch.Fetched
                    if (fetched == null || fetched.feed.title != "Feed $t" || fetched.feed.entries.size != t + 1 || fetched.feed.entries.any { e -> !e.id.startsWith("urn:item:") }) wrong.incrementAndGet()
                }
            }.apply { start() }
        }
        start.countDown()
        threads.forEach { it.join(120_000) }
        assertEquals(0, wrong.get())
        assertEquals(240, fake.calls.size)
        fetcher.close()
        urlFetcher.close()
    }
}
