package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.FEED_URL
import com.spartanlabs.testing.support.webtools.feeds.fetcherOn
import com.spartanlabs.testing.support.webtools.feeds.respondWith
import com.spartanlabs.testing.support.webtools.feeds.rssBody
import com.spartanlabs.webtools.feeds.FeedFetcher
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.WindowedCallBudget
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - every failure of the `UrlFetcher` is the fetch's result, the same instance: a policy refusal (`RefusedByPolicy`, no
 * call), a throttle (`RateLimited` with the `Retry-After`), an exhausted budget, the transport failures, and `Closed("fetcher")` of a
 * `UrlFetcher` the caller closed.
 */
class FeedFetcherPassThroughComponentTest {

    private val body = rssBody(1)

    @Test
    fun `a policy refusal is RefusedByPolicy and costs nothing`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        fetcherOn(clock, fake).use { fetcher ->
            val refused = assertIs<TransportFailure.RefusedByPolicy>(fetcher.fetch(Url.parse("http://10.0.0.5/").getOrThrow()).exceptionOrNull())
            assertEquals(RefusalReason.DeniedAddress, refused.reason)
            assertTrue(fake.calls.isEmpty())
        }
    }

    @Test
    fun `a throttle is RateLimited with the Retry-After`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(429, "slow", HttpHeaders { add("Retry-After", "30") }))
        fetcherOn(clock, fake).use { fetcher ->
            val limited = assertIs<UpstreamFailure.RateLimited>(fetcher.fetch(FEED_URL).exceptionOrNull())
            assertEquals(30.seconds, limited.retryAfter)
            assertEquals(429, limited.status)
        }
    }

    @Test
    fun `an exhausted budget is BudgetExhausted`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        val budget = WindowedCallBudget(listOf(RateWindow("minute", 1, 1.minutes))) { timeSource = clock.timeSource; wallClock = clock.wallClock }
        try {
            fetcherOn(clock, fake, urlConfigure = { this.budget = budget }).use { fetcher ->
                assertTrue(fetcher.fetch(FEED_URL).isSuccess)
                assertIs<HttpFailure.BudgetExhausted>(fetcher.fetch(FEED_URL) { maxWait = 0.seconds }.exceptionOrNull())
            }
        } finally {
            budget.close()
        }
    }

    @Test
    fun `transport failures pass through`() = runTest {
        val clock = virtualClock()
        val failures = listOf(
            TransportFailure.Timeout(), TransportFailure.DnsFailed("feeds.example.test"), TransportFailure.BodyTooLarge(2L * 1024 * 1024, null), TransportFailure.TooManyRedirects(3),
        )
        for (failure in failures) {
            val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.fail(failure))
            fetcherOn(clock, fake).use { fetcher ->
                val result = fetcher.fetch(FEED_URL)
                assertSame(failure, result.exceptionOrNull(), failure::class.simpleName)
            }
        }
    }

    @Test
    fun `a UrlFetcher the caller closed gives Closed fetcher, not the feed fetcher's own`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        val urlFetcher = UrlFetcher(fake, OutboundUrlPolicy.untrusted()) { timeSource = clock.timeSource; wallClock = clock.wallClock }
        val fetcher = FeedFetcher(urlFetcher) { parseDispatcher = clock.dispatcher }
        urlFetcher.close()
        val closed = assertIs<HttpFailure.Closed>(fetcher.fetch(FEED_URL).exceptionOrNull())
        assertEquals("fetcher", closed.component)
        fetcher.close()
        assertEquals("feed fetcher", assertIs<HttpFailure.Closed>(fetcher.fetch(FEED_URL).exceptionOrNull()).component)
    }

    @Test
    fun `the failure is the same instance the UrlFetcher returned`() = runTest {
        val clock = virtualClock()
        val timeout = TransportFailure.Timeout()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.fail(timeout))
        fetcherOn(clock, fake).use { fetcher ->
            assertSame(timeout, fetcher.fetch(FEED_URL).exceptionOrNull())
            assertSame(timeout, fetcher.fetch(FEED_URL).exceptionOrNull())
        }
    }
}
