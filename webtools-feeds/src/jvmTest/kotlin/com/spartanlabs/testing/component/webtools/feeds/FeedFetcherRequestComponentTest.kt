package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.FEED_URL
import com.spartanlabs.testing.support.webtools.feeds.fetcherOn
import com.spartanlabs.testing.support.webtools.feeds.respondWith
import com.spartanlabs.testing.support.webtools.feeds.rssBody
import com.spartanlabs.webtools.feeds.FeedFetcherConfig
import com.spartanlabs.webtools.feeds.FeedValidators
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.WindowedCallBudget
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - what the fetcher hands to the `UrlFetcher`, read from `fake.calls` and from a budget: an unconditional fetch sends a GET with
 * the default `Accept` and no conditional header; both validators are sent verbatim; only the known validator is sent; a configured
 * `Accept` replaces the default; the per-fetch block reaches `UrlFetcher`; a cost of two is charged to the budget.
 */
class FeedFetcherRequestComponentTest {

    private val body = rssBody(1)
    private val date = "Fri, 6 Nov 2026 04:25:27 GMT"

    @Test
    fun `an unconditional fetch sends a GET with the default Accept and no conditional header`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.get("/rss"), respondWith(200, body))
        fetcherOn(clock, fake).use { fetcher ->
            assertTrue(fetcher.fetch(FEED_URL).isSuccess)
            val request = fake.calls.single().request
            assertEquals(HttpMethod.Get, request.method)
            assertEquals(FEED_URL, request.url)
            assertEquals(FeedFetcherConfig.DEFAULT_ACCEPT, request.headers["Accept"])
            assertFalse("If-None-Match" in request.headers)
            assertFalse("If-Modified-Since" in request.headers)
            assertEquals(1, request.headers.size)
        }
    }

    @Test
    fun `both validators are sent verbatim`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource)
            .on(RequestMatch.Any, respondWith(200, body))
            .on(RequestMatch.header("If-None-Match", "W/\"a\", \"b\""), respondWith(304))
        fetcherOn(clock, fake).use { fetcher ->
            val result = fetcher.fetch(FEED_URL, FeedValidators("W/\"a\", \"b\"", date))
            assertTrue(result.isSuccess, result.toString())
            val headers = fake.calls.single().request.headers
            assertEquals(listOf("W/\"a\", \"b\""), headers.getAll("If-None-Match"))
            assertEquals(listOf(date), headers.getAll("If-Modified-Since"))
            assertEquals(listOf("Accept", "If-None-Match", "If-Modified-Since"), headers.entries().map { it.name })
        }
    }

    @Test
    fun `only the known validator is sent`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        fetcherOn(clock, fake).use { fetcher ->
            fetcher.fetch(FEED_URL, FeedValidators("\"a\"", null))
            fetcher.fetch(FEED_URL, FeedValidators(null, date))
            val first = fake.calls[0].request.headers
            assertEquals("\"a\"", first["If-None-Match"])
            assertFalse("If-Modified-Since" in first)
            val second = fake.calls[1].request.headers
            assertFalse("If-None-Match" in second)
            assertEquals(date, second["If-Modified-Since"])
        }
    }

    @Test
    fun `a configured Accept replaces the default`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        fetcherOn(clock, fake) { accept = "application/rss+xml" }.use { fetcher ->
            fetcher.fetch(FEED_URL)
            assertEquals(listOf("application/rss+xml"), fake.calls.single().request.headers.getAll("Accept"))
        }
    }

    @Test
    fun `the per-fetch block reaches UrlFetcher`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        fetcherOn(clock, fake).use { fetcher ->
            assertIs<TransportFailure.InvalidRequest>(fetcher.fetch(FEED_URL) { cost = 0 }.exceptionOrNull())
            assertIs<TransportFailure.InvalidRequest>(fetcher.fetch(FEED_URL, FeedValidators("\"a\"", null)) { maxWait = (-1).seconds }.exceptionOrNull())
            assertTrue(fake.calls.isEmpty())
        }
    }

    @Test
    fun `a cost of two is charged to the budget`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        val budget = WindowedCallBudget(listOf(RateWindow("minute", 2, 1.minutes))) { timeSource = clock.timeSource; wallClock = clock.wallClock }
        try {
            fetcherOn(clock, fake, urlConfigure = { this.budget = budget }).use { fetcher ->
                assertTrue(fetcher.fetch(FEED_URL) { cost = 2 }.isSuccess)
                val exhausted = fetcher.fetch(FEED_URL) { maxWait = 0.seconds }.exceptionOrNull()
                assertIs<HttpFailure.BudgetExhausted>(exhausted)
                assertEquals(1, fake.calls.size)
            }
        } finally {
            budget.close()
        }
    }
}
