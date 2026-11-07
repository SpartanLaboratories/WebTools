package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.FEED_URL
import com.spartanlabs.testing.support.webtools.feeds.fetcherOn
import com.spartanlabs.testing.support.webtools.feeds.rssBody
import com.spartanlabs.webtools.feeds.FeedFetch
import com.spartanlabs.webtools.feeds.FeedValidators
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.RedirectHop
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Level 2 - `permanentlyMovedTo` and `finalUrl` on scripted redirect chains: no redirect gives null; `A -301-> B` reports B on
 * `Fetched` with `finalUrl` B and on `NotModified` after a `304`; `A -308-> B -302-> C` reports B with `finalUrl` C; `A -302-> B -301-> C`
 * reports nothing; `A -301-> B -301-> C -307-> D` reports C; `http` to `https` on the same host is reported; a `301` landing on the
 * requested URL reports nothing, and a chain that ends in an error has no `FeedFetch`.
 */
class FeedFetcherMovesComponentTest {

    private val a = FEED_URL
    private val b = Url.parse("https://feeds.example.test/b").getOrThrow()
    private val c = Url.parse("https://other.example.test/c").getOrThrow()
    private val d = Url.parse("https://other.example.test/d").getOrThrow()
    private val body = rssBody(1)

    // A step answering `status` from `finalUrl` after the given hops (each `url -status->`).
    private fun chain(finalUrl: Url, status: Int = 200, vararg hops: Pair<Url, Int>): Step = Step.respond { _ ->
        HttpResponse(status, finalUrl) {
            headers = HttpHeaders { add("Content-Type", "application/rss+xml"); add("ETag", "\"v1\"") }
            redirectChain = hops.map { RedirectHop(it.first, it.second) }
            if (status == 200) body(this@FeedFetcherMovesComponentTest.body.encodeToByteArray())
        }
    }

    @Test
    fun `no redirect gives null`() = runTest {
        val fetch = assertIs<FeedFetch.Fetched>(fetchOn(this, chain(a)))
        assertNull(fetch.permanentlyMovedTo)
        assertEquals(a, fetch.finalUrl)
    }

    @Test
    fun `A 301 to B reports B on Fetched, with finalUrl B`() = runTest {
        val fetch = assertIs<FeedFetch.Fetched>(fetchOn(this, chain(b, 200, a to 301)))
        assertEquals(b, fetch.permanentlyMovedTo)
        assertEquals(b, fetch.finalUrl)
    }

    @Test
    fun `A 301 to B ending in a 304 reports B on NotModified`() = runTest {
        val fetch = assertIs<FeedFetch.NotModified>(fetchOn(this, chain(b, 304, a to 301), FeedValidators("\"v0\"", null)))
        assertEquals(b, fetch.permanentlyMovedTo)
        assertEquals(b, fetch.finalUrl)
        assertEquals("\"v1\"", fetch.validators.etag)
    }

    @Test
    fun `A 308 to B 302 to C reports B and finalUrl is C`() = runTest {
        val fetch = assertIs<FeedFetch.Fetched>(fetchOn(this, chain(c, 200, a to 308, b to 302)))
        assertEquals(b, fetch.permanentlyMovedTo)
        assertEquals(c, fetch.finalUrl)
    }

    @Test
    fun `A 302 to B 301 to C reports nothing`() = runTest {
        val fetch = assertIs<FeedFetch.Fetched>(fetchOn(this, chain(c, 200, a to 302, b to 301)))
        assertNull(fetch.permanentlyMovedTo)
        assertEquals(c, fetch.finalUrl)
    }

    @Test
    fun `A 301 to B 301 to C 307 to D reports C`() = runTest {
        val fetch = assertIs<FeedFetch.Fetched>(fetchOn(this, chain(d, 200, a to 301, b to 301, c to 307)))
        assertEquals(c, fetch.permanentlyMovedTo)
        assertEquals(d, fetch.finalUrl)
    }

    @Test
    fun `http to https on the same host is reported`() = runTest {
        val plain = Url.parse("http://feeds.example.test/rss").getOrThrow()
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, chain(a, 200, plain to 301))
        fetcherOn(clock, fake).use { fetcher ->
            val fetch = assertIs<FeedFetch.Fetched>(fetcher.fetch(plain).getOrThrow())
            assertEquals(a, fetch.permanentlyMovedTo)
            assertEquals(a, fetch.finalUrl)
        }
    }

    @Test
    fun `a 301 that lands on the requested URL reports nothing, and a chain that ends in an error has no FeedFetch`() = runTest {
        val back = assertIs<FeedFetch.Fetched>(fetchOn(this, chain(a, 200, a to 301)))
        assertNull(back.permanentlyMovedTo)
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, chain(b, 404, a to 301))
        fetcherOn(clock, fake).use { fetcher ->
            val failure = assertIs<UpstreamFailure.UpstreamError>(fetcher.fetch(a).exceptionOrNull())
            assertEquals(404, failure.status)
        }
    }

    private suspend fun fetchOn(scope: TestScope, step: Step, validators: FeedValidators = FeedValidators.None): FeedFetch {
        val clock = scope.virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, step)
        return fetcherOn(clock, fake).use { it.fetch(a, validators).getOrThrow() }
    }
}
