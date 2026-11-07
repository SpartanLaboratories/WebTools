package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.FEED_URL
import com.spartanlabs.testing.support.webtools.feeds.fetcherOn
import com.spartanlabs.testing.support.webtools.feeds.respondWith
import com.spartanlabs.testing.support.webtools.feeds.rssBody
import com.spartanlabs.testing.support.webtools.feeds.validatorHeaders
import com.spartanlabs.webtools.feeds.FeedFetch
import com.spartanlabs.webtools.feeds.FeedValidators
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Level 2 - what each status becomes: a `200` and a `203` are parsed into `Fetched` with the response's validators and the request URL
 * as `finalUrl`; `None` when neither validator comes; a repeated `ETag` line counts as absent; unusable validators are null; a `304` to a
 * conditional request is `NotModified` keeping, replacing or nulling the sent validators as the `304` carries them; a `304` to an
 * unconditional request and every other status are `UpstreamError`s that keep the status, the headers and the body.
 */
class FeedFetcherStatusComponentTest {

    private val body = rssBody(2)
    private val date = "Fri, 06 Nov 2026 04:25:27 GMT"
    private val sent = FeedValidators("\"old\"", "Thu, 05 Nov 2026 00:00:00 GMT")

    private fun FakeHttpTransport.answering(status: Int, headers: HttpHeaders = HttpHeaders(), body: String = this@FeedFetcherStatusComponentTest.body) =
        on(RequestMatch.Any, respondWith(status, body, headers))

    @Test
    fun `a 200 is parsed and Fetched carries the feed, the response's validators and the request URL as finalUrl`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).answering(200, validatorHeaders("\"v1\"", date))
        fetcherOn(clock, fake).use { fetcher ->
            val fetched = assertIs<FeedFetch.Fetched>(fetcher.fetch(FEED_URL).getOrThrow())
            assertEquals(listOf("urn:item:1", "urn:item:2"), fetched.feed.entries.map { it.id })
            assertEquals(FeedValidators("\"v1\"", date), fetched.validators)
            assertEquals(FEED_URL, fetched.finalUrl)
            assertNull(fetched.permanentlyMovedTo)
        }
    }

    @Test
    fun `a 203 is parsed like a 200`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).answering(203, validatorHeaders("\"v1\"", date))
        fetcherOn(clock, fake).use { fetcher ->
            val fetched = assertIs<FeedFetch.Fetched>(fetcher.fetch(FEED_URL).getOrThrow())
            assertEquals(2, fetched.feed.entries.size)
            assertEquals("\"v1\"", fetched.validators.etag)
        }
    }

    @Test
    fun `a 200 with neither validator gives None, so the caller drops stale ones`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).answering(200, HttpHeaders { add("Content-Type", "application/rss+xml") })
        fetcherOn(clock, fake).use { fetcher ->
            val fetched = assertIs<FeedFetch.Fetched>(fetcher.fetch(FEED_URL, sent).getOrThrow())
            assertEquals(FeedValidators.None, fetched.validators)
        }
    }

    @Test
    fun `a 200 that repeats the ETag line gives a null etag and keeps a single Last-Modified`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).answering(200, HttpHeaders { add("ETag", "\"a\""); add("ETag", "\"b\""); add("Last-Modified", date) })
        fetcherOn(clock, fake).use { fetcher ->
            assertEquals(FeedValidators(null, date), assertIs<FeedFetch.Fetched>(fetcher.fetch(FEED_URL).getOrThrow()).validators)
        }
    }

    @Test
    fun `a 200 with unusable validators gives null`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).answering(200, HttpHeaders { add("ETag", "\"" + "a".repeat(300) + "\""); add("Last-Modified", "a".repeat(65)) })
        fetcherOn(clock, fake).use { fetcher ->
            val long = assertIs<FeedFetch.Fetched>(fetcher.fetch(FEED_URL).getOrThrow()).validators
            assertNull(long.etag, "300 characters")
            assertNull(long.lastModified, "65 characters")
        }
        val star = FakeHttpTransport(clock.timeSource).answering(200, HttpHeaders { add("ETag", "*"); add("Last-Modified", date) })
        fetcherOn(clock, star).use { fetcher -> assertEquals(FeedValidators(null, date), assertIs<FeedFetch.Fetched>(fetcher.fetch(FEED_URL).getOrThrow()).validators) }
        val nonAscii = FakeHttpTransport(clock.timeSource).answering(200, HttpHeaders { add("ETag", "\"café\""); add("Last-Modified", date) })
        fetcherOn(clock, nonAscii).use { fetcher ->
            val fetched = assertIs<FeedFetch.Fetched>(fetcher.fetch(FEED_URL).getOrThrow())
            assertEquals(FeedValidators(null, date), fetched.validators)
        }
    }

    @Test
    fun `a 304 to a conditional request is NotModified and keeps the sent validators it does not replace`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).answering(304, body = "")
        fetcherOn(clock, fake).use { fetcher ->
            val notModified = assertIs<FeedFetch.NotModified>(fetcher.fetch(FEED_URL, sent).getOrThrow())
            assertEquals(sent, notModified.validators)
            assertEquals(FEED_URL, notModified.finalUrl)
            assertNull(notModified.permanentlyMovedTo)
        }
    }

    @Test
    fun `a 304 that carries an ETag replaces it and keeps the sent Last-Modified`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).answering(304, HttpHeaders { add("ETag", "\"new\"") }, body = "")
        fetcherOn(clock, fake).use { fetcher ->
            assertEquals(FeedValidators("\"new\"", sent.lastModified), assertIs<FeedFetch.NotModified>(fetcher.fetch(FEED_URL, sent).getOrThrow()).validators)
        }
    }

    @Test
    fun `a 304 that carries an unusable ETag makes it null`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).answering(304, HttpHeaders { add("ETag", "*") }, body = "")
        fetcherOn(clock, fake).use { fetcher ->
            assertEquals(FeedValidators(null, sent.lastModified), assertIs<FeedFetch.NotModified>(fetcher.fetch(FEED_URL, sent).getOrThrow()).validators)
        }
    }

    @Test
    fun `a 304 that repeats a header counts it as absent`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).answering(304, HttpHeaders { add("ETag", "\"a\""); add("ETag", "\"b\""); add("Last-Modified", date) }, body = "")
        fetcherOn(clock, fake).use { fetcher ->
            assertEquals(FeedValidators(sent.etag, date), assertIs<FeedFetch.NotModified>(fetcher.fetch(FEED_URL, sent).getOrThrow()).validators)
        }
    }

    @Test
    fun `a 304 to an unconditional request is an UpstreamError 304`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).answering(304, HttpHeaders { add("ETag", "\"a\"") }, body = "")
        fetcherOn(clock, fake).use { fetcher ->
            val failure = assertIs<UpstreamFailure.UpstreamError>(fetcher.fetch(FEED_URL).exceptionOrNull())
            assertEquals(304, failure.status)
            assertEquals("\"a\"", failure.headers["ETag"])
        }
    }

    @Test
    fun `every other 2xx and a 300 is an UpstreamError that keeps the status, the headers and the body`() = runTest {
        val clock = virtualClock()
        for (status in listOf(201, 202, 204, 205, 206, 226, 300)) {
            val fake = FakeHttpTransport(clock.timeSource).answering(status, HttpHeaders { add("X-Marker", "m") }, body = if (status == 204 || status == 205) "" else body)
            fetcherOn(clock, fake).use { fetcher ->
                val failure = assertIs<UpstreamFailure.UpstreamError>(fetcher.fetch(FEED_URL, sent).exceptionOrNull(), "status $status")
                assertEquals(status, failure.status)
                assertEquals("m", failure.headers["X-Marker"])
                assertEquals(if (status == 204 || status == 205) 0 else body.length, failure.bodySize, "status $status")
            }
        }
    }

    @Test
    fun `404, 410 and 500 are UpstreamErrors with the status readable`() = runTest {
        val clock = virtualClock()
        for (status in listOf(404, 410, 500)) {
            val fake = FakeHttpTransport(clock.timeSource).answering(status, body = "error page")
            fetcherOn(clock, fake).use { fetcher ->
                val failure = assertIs<UpstreamFailure.UpstreamError>(fetcher.fetch(FEED_URL).exceptionOrNull(), "status $status")
                assertEquals(status, failure.status)
                assertEquals("error page", failure.body().decodeToString())
            }
        }
    }
}
