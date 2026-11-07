package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.Boom
import com.spartanlabs.testing.support.webtools.feeds.FEED_URL
import com.spartanlabs.testing.support.webtools.feeds.GateParser
import com.spartanlabs.testing.support.webtools.feeds.RecordingDispatcher
import com.spartanlabs.testing.support.webtools.feeds.RecordingParser
import com.spartanlabs.testing.support.webtools.feeds.ThrowingParser
import com.spartanlabs.testing.support.webtools.feeds.fetcherOn
import com.spartanlabs.testing.support.webtools.feeds.respondWith
import com.spartanlabs.testing.support.webtools.feeds.rssBody
import com.spartanlabs.webtools.feeds.Feed
import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedFetch
import com.spartanlabs.webtools.feeds.FeedFetcher
import com.spartanlabs.webtools.feeds.FeedLimit
import com.spartanlabs.webtools.feeds.FeedParser
import com.spartanlabs.webtools.feeds.FeedRefusal
import com.spartanlabs.webtools.feeds.FeedValidators
import com.spartanlabs.webtools.feeds.NotAFeedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RedirectHop
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - the parse step with recording, gating and throwing parsers: the parser receives the body, the `Content-Type` value and the
 * response URL as the base; a `FeedFailure` a parser returns is the result, the same instance; a failure value that is not an
 * `HttpFailure` and an exception thrown by `parse` are `DecodeFailure`; an `Error` propagates; the parser runs on `parseDispatcher`;
 * at most `maxConcurrentParses` parses run at once; the permit is released after a failed or throwing parse; cancelling a fetch that
 * waits for a permit is thrown and starts no parse while a running parse finishes; no parser runs for a `304` or any other status.
 */
class FeedFetcherParseComponentTest {

    private val body = rssBody(1)
    private val other = Url.parse("https://other.example.test/moved.xml").getOrThrow()

    @Test
    fun `the parser receives the body, the Content-Type value and the response URL as the base`() = runTest {
        val clock = virtualClock()
        val parser = RecordingParser()
        val fake = FakeHttpTransport(clock.timeSource).on(
            RequestMatch.Any,
            Step.respond { _ ->
                HttpResponse(200, other) {
                    headers = HttpHeaders { add("Content-Type", "application/rss+xml; charset=utf-8") }
                    redirectChain = listOf(RedirectHop(FEED_URL, 301))
                    body(this@FeedFetcherParseComponentTest.body.encodeToByteArray())
                }
            },
        )
        fetcherOn(clock, fake) { this.parser = parser }.use { fetcher ->
            val fetched = assertIs<FeedFetch.Fetched>(fetcher.fetch(FEED_URL).getOrThrow())
            assertEquals("recorded", fetched.feed.title)
            val call = parser.calls.single()
            assertContentEquals(body.encodeToByteArray(), call.document)
            assertEquals("application/rss+xml; charset=utf-8", call.contentType)
            assertEquals(other.toString(), call.baseUrl)
            assertEquals(other, fetched.finalUrl)
        }
    }

    @Test
    fun `each FeedFailure leaf a parser returns is the result, the same instance`() = runTest {
        val clock = virtualClock()
        val leaves = listOf(
            FeedFailure.NotAFeed(NotAFeedReason.Html), FeedFailure.Malformed(), FeedFailure.Refused(FeedRefusal.Doctype), FeedFailure.LimitExceeded(FeedLimit.Entries),
        )
        for (leaf in leaves) {
            val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
            fetcherOn(clock, fake) { parser = RecordingParser(answer = { Result.failure(leaf) }) }.use { fetcher ->
                assertSame(leaf, fetcher.fetch(FEED_URL).exceptionOrNull())
            }
        }
    }

    @Test
    fun `a failure value that is not an HttpFailure becomes DecodeFailure`() = runTest {
        val clock = virtualClock()
        val cause = IllegalStateException("not an HttpFailure")
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        fetcherOn(clock, fake) { parser = RecordingParser(answer = { Result.failure(cause) }) }.use { fetcher ->
            val failure = assertIs<HttpFailure.DecodeFailure>(fetcher.fetch(FEED_URL).exceptionOrNull())
            assertSame(cause, failure.cause)
        }
    }

    @Test
    fun `an exception thrown by parse becomes DecodeFailure with the cause`() = runTest {
        val clock = virtualClock()
        val cause = IllegalStateException("thrown")
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        fetcherOn(clock, fake) { parser = ThrowingParser { cause } }.use { fetcher ->
            val failure = assertIs<HttpFailure.DecodeFailure>(fetcher.fetch(FEED_URL).exceptionOrNull())
            assertSame(cause, failure.cause)
        }
    }

    @Test
    fun `an Error thrown by parse propagates`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        fetcherOn(clock, fake) { parser = ThrowingParser { Boom() } }.use { fetcher ->
            assertFailsWith<Boom> { fetcher.fetch(FEED_URL) }
        }
    }

    @Test
    fun `the parser runs on parseDispatcher`() = runTest {
        val clock = virtualClock()
        val recording = RecordingDispatcher(clock.dispatcher)
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        fetcherOn(clock, fake) { parseDispatcher = recording; parser = RecordingParser() }.use { fetcher ->
            assertTrue(fetcher.fetch(FEED_URL).isSuccess)
            assertTrue(recording.dispatched >= 1, "the parse must be dispatched to parseDispatcher")
        }
        val plain = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(304))
        val idle = RecordingDispatcher(clock.dispatcher)
        fetcherOn(clock, plain) { parseDispatcher = idle; parser = RecordingParser() }.use { fetcher ->
            assertIs<FeedFetch.NotModified>(fetcher.fetch(FEED_URL, FeedValidators("\"a\"", null)).getOrThrow())
            assertEquals(0, idle.dispatched, "a 304 dispatches nothing")
        }
    }

    // A parser that blocks must run on real threads: the virtual clock's dispatcher has one and would deadlock.
    private fun <T> withPool(block: suspend CoroutineScope.(CoroutineDispatcher) -> T): T {
        val pool: ExecutorCoroutineDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
        try {
            return runBlocking { withTimeout(10.seconds) { block(pool) } }
        } finally {
            pool.close()
        }
    }

    // A fetcher whose fake counts each response it serves down on [served], so a test knows a fetch is past the transport and at the
    // parse permit before it asserts that no parse began.
    private fun realFetcher(parser: FeedParser, pool: CoroutineDispatcher, permits: Int, served: CountDownLatch): FeedFetcher {
        val fake = FakeHttpTransport().on(
            RequestMatch.Any,
            Step.respond { request ->
                HttpResponse(200, request.url) { body(this@FeedFetcherParseComponentTest.body.encodeToByteArray()) }.also { served.countDown() }
            },
        )
        return FeedFetcher(UrlFetcher(fake, OutboundUrlPolicy.untrusted())) { this.parser = parser; parseDispatcher = pool; maxConcurrentParses = permits }
    }

    // How long a test waits for a parse it expects never to begin; a parse that wrongly began would trip the gate within it.
    private val noParseWithinMillis = 500L

    @Test
    fun `at most maxConcurrentParses parses run at once`(): Unit = withPool { pool ->
        val gate = GateParser()
        val served = CountDownLatch(3)
        realFetcher(gate, pool, permits = 2, served = served).use { fetcher ->
            val fetches = List(3) { async(pool) { fetcher.fetch(FEED_URL) } }
            assertTrue(gate.awaitRunning(2), "two parses must reach the gate")
            assertTrue(served.await(5, TimeUnit.SECONDS), "all three responses must have been served")
            assertFalse(gate.awaitBegun(3, noParseWithinMillis), "the third parse waits for a permit")
            assertEquals(2, gate.running)
            assertEquals(2, gate.begun)
            gate.release()
            for (f in fetches) assertIs<FeedFetch.Fetched>(f.await().getOrThrow())
            assertEquals(2, gate.maxConcurrent)
            assertEquals(3, gate.begun)
        }
    }

    @Test
    fun `the permit is released after a failed or throwing parse`() = runTest {
        val clock = virtualClock()
        val calls = AtomicInteger()
        val parser = RecordingParser(answer = {
            when (calls.incrementAndGet()) {
                1 -> Result.failure(FeedFailure.Malformed())
                2 -> throw IllegalStateException("second")
                else -> Result.success(Feed { title = "third" })
            }
        })
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        fetcherOn(clock, fake) { this.parser = parser; maxConcurrentParses = 1 }.use { fetcher ->
            assertIs<FeedFailure.Malformed>(fetcher.fetch(FEED_URL).exceptionOrNull())
            assertIs<HttpFailure.DecodeFailure>(fetcher.fetch(FEED_URL).exceptionOrNull())
            assertEquals("third", assertIs<FeedFetch.Fetched>(fetcher.fetch(FEED_URL).getOrThrow()).feed.title)
        }
    }

    @Test
    fun `cancelling a fetch that waits for a permit is thrown and starts no parse, and a running parse finishes`(): Unit = withPool { pool ->
        val gate = GateParser()
        val served = CountDownLatch(2)
        realFetcher(gate, pool, permits = 1, served = served).use { fetcher ->
            val running = async(pool) { fetcher.fetch(FEED_URL) }
            assertTrue(gate.awaitRunning(1))
            val waiting = async(pool) { fetcher.fetch(FEED_URL) }
            assertTrue(served.await(5, TimeUnit.SECONDS), "the second response must have been served")
            assertFalse(gate.awaitBegun(2, noParseWithinMillis), "the second fetch waits for the permit")
            waiting.cancel()
            // await returns only once the cancelled fetch has completed, so no parse of it can begin afterwards.
            assertFailsWith<CancellationException> { waiting.await() }
            assertEquals(1, gate.begun, "a cancelled wait starts no parse")
            gate.release()
            assertIs<FeedFetch.Fetched>(running.await().getOrThrow())
        }
    }

    @Test
    fun `no parser runs for a 304 or for any other status`() = runTest {
        val clock = virtualClock()
        val parser = RecordingParser()
        for (status in listOf(304, 204, 206, 301, 404, 500)) {
            val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(status, if (status == 204) "" else body))
            fetcherOn(clock, fake) { this.parser = parser }.use { fetcher ->
                fetcher.fetch(FEED_URL, FeedValidators("\"a\"", null))
                fetcher.fetch(FEED_URL)
            }
        }
        assertTrue(parser.calls.isEmpty())
    }
}
