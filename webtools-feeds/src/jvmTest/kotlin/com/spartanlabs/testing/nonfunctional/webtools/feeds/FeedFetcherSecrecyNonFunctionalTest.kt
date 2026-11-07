package com.spartanlabs.testing.nonfunctional.webtools.feeds

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.spartanlabs.testing.support.webtools.feeds.FeedModelFixtures
import com.spartanlabs.testing.support.webtools.feeds.captureFeedLogs
import com.spartanlabs.testing.support.webtools.feeds.feedEvents
import com.spartanlabs.testing.support.webtools.feeds.fetcherOn
import com.spartanlabs.testing.support.webtools.feeds.respondWith
import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedFetch
import com.spartanlabs.webtools.feeds.FeedInput
import com.spartanlabs.webtools.feeds.FeedLimit
import com.spartanlabs.webtools.feeds.FeedLimits
import com.spartanlabs.webtools.feeds.FeedRefusal
import com.spartanlabs.webtools.feeds.NotAFeedReason
import com.spartanlabs.webtools.feeds.RomeFeedParser
import com.spartanlabs.webtools.feeds.FeedValidators
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 4c - nothing secret reaches a log line or a `toString`: the fetch URL is `https://feeds.example.test/PATH-SENTINEL/feed.xml?token=QUERY-SENTINEL`,
 * the validators, the body and the documents carry their own sentinels, and the module's logger is captured at `DEBUG`. The `Fetched` and
 * `NotModified` lines, each `FeedFailure` outcome (the `Refused` `WARN` included), the pass-through failures and every `toString` hold no
 * sentinel; no event carries a `Throwable` or an argument that is not a number, a name or an origin.
 */
class FeedFetcherSecrecyNonFunctionalTest {

    private val url = Url.parse("https://feeds.example.test/PATH-SENTINEL/feed.xml?token=QUERY-SENTINEL").getOrThrow()
    private val validators = FeedValidators("\"TAG-SENTINEL\"", "Fri, 06 Nov 2026 04:25:27 GMT")
    //language=XML
    private val body = "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>TITLE-SENTINEL</title><item><guid>GUID-SENTINEL</guid><title>ITEM-SENTINEL</title></item></channel></rss>"
    private val headers = HttpHeaders { add("Content-Type", "application/rss+xml"); add("ETag", "\"TAG-SENTINEL\""); add("Last-Modified", "Fri, 06 Nov 2026 04:25:27 GMT") }

    private fun assertClean(events: List<ILoggingEvent>, case: String) {
        val mine = feedEvents(events)
        assertTrue(mine.isNotEmpty(), "$case: no events captured")
        for (event in mine) {
            val text = event.formattedMessage
            assertFalse(text.contains("SENTINEL"), "$case: $text")
            assertFalse(text.contains("feed.xml") || text.contains("token=") || text.contains("2026"), "$case: $text")
        }
    }

    @Test
    fun `the Fetched and NotModified lines hold no path, no query, no header value and no body text`() = runTest {
        val clock = virtualClock()
        val fetched = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body, headers))
        val (first, firstEvents) = captureFeedLogs { fetcherOn(clock, fetched).use { it.fetch(url, validators) } }
        assertIs<FeedFetch.Fetched>(first.getOrThrow())
        assertClean(firstEvents, "Fetched")
        val notModified = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(304, "", headers))
        val (second, secondEvents) = captureFeedLogs { fetcherOn(clock, notModified).use { it.fetch(url, validators) } }
        assertIs<FeedFetch.NotModified>(second.getOrThrow())
        assertClean(secondEvents, "NotModified")
    }

    @Test
    fun `each FeedFailure outcome, the Refused WARN included, holds none of them`() = runTest {
        val clock = virtualClock()
        val documents = mapOf(
            "NotAFeed" to "<html><body>PAGE-SENTINEL</body></html>",
            "Malformed" to "<rss><SENTINEL-ELEMENT SENTINEL-ATTR=\"SENTINEL-VALUE\">SENTINEL-TEXT</rss>",
            "Refused" to "<!DOCTYPE SENTINEL-DOCTYPE SYSTEM \"http://127.0.0.1:1/SENTINEL.dtd\">$body",
            "LimitExceeded" to "<rss version=\"2.0\"><channel>" + "<SENTINEL-ITEM/>".repeat(100) + "</channel></rss>",
        )
        for ((case, document) in documents) {
            val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, document, headers))
            val (result, events) = captureFeedLogs { fetcherOn(clock, fake) { if (case == "LimitExceeded") parser = RomeFeedParser(FeedLimits { maxElements = 50 }) }.use { it.fetch(url, validators) } }
            val failure = assertIs<FeedFailure>(result.exceptionOrNull(), case)
            assertFalse(failure.message!!.contains("SENTINEL"), case)
            assertClean(events, case)
            if (case == "Refused") assertEquals(1, feedEvents(events).count { it.level == Level.WARN })
        }
    }

    @Test
    fun `pass-through failures hold none of them`() = runTest {
        val clock = virtualClock()
        val refused = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        val (policy, policyEvents) = captureFeedLogs { fetcherOn(clock, refused).use { it.fetch(Url.parse("http://10.0.0.5/PATH-SENTINEL?q=QUERY-SENTINEL").getOrThrow(), validators) } }
        assertIs<TransportFailure.RefusedByPolicy>(policy.exceptionOrNull())
        assertClean(policyEvents, "policy refusal")

        val timeout = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.fail(TransportFailure.Timeout()))
        val (transport, transportEvents) = captureFeedLogs { fetcherOn(clock, timeout).use { it.fetch(url, validators) } }
        assertIs<TransportFailure.Timeout>(transport.exceptionOrNull())
        assertClean(transportEvents, "transport failure")

        val upstream = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(500, "BODY-SENTINEL", HttpHeaders { add("X-Secret", "HEADER-SENTINEL") }))
        val (error, errorEvents) = captureFeedLogs { fetcherOn(clock, upstream).use { it.fetch(url, validators) } }
        assertIs<UpstreamFailure.UpstreamError>(error.exceptionOrNull())
        assertClean(errorEvents, "upstream error")
    }

    @Test
    fun `the toString of FeedFetch, Feed, FeedEntry, FeedEnclosure, FeedValidators, FeedInput, FeedFetcher and every failure's message hold no sentinel`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body, headers))
        fetcherOn(clock, fake).use { fetcher ->
            val fetched = assertIs<FeedFetch.Fetched>(fetcher.fetch(url, validators).getOrThrow())
            val texts = listOf(
                fetched.toString(), fetched.feed.toString(), fetched.feed.entries.single().toString(), FeedModelFixtures.entry("GUID-SENTINEL").toString(),
                FeedModelFixtures.enclosure().toString(), validators.toString(), FeedInput(body.encodeToByteArray(), "text/xml; charset=CT-SENTINEL", url).toString(),
                fetcher.toString(), FeedFetch.NotModified(validators, url, url).toString(),
            )
            for (text in texts) assertFalse(text.contains("SENTINEL") || text.contains("feed.xml") || text.contains("token="), text)
        }
        val failures = listOf(
            FeedFailure.NotAFeed(NotAFeedReason.Html), FeedFailure.Malformed(),
            FeedFailure.Refused(FeedRefusal.Doctype), FeedFailure.LimitExceeded(FeedLimit.Entries),
        )
        for (failure in failures) {
            assertFalse(failure.message!!.contains("SENTINEL"))
            assertNull(failure.cause)
        }
    }

    @Test
    fun `no event carries a Throwable or an argument that is not a number, a name or an origin`() = runTest {
        val clock = virtualClock()
        val allowed = Regex("^(-|\\d+|[A-Za-z][A-Za-z0-9_$.]*|(true|false)|https?://[^/?#\\s]+)$")
        val transports = listOf(
            FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body, headers)),
            FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(304, "", headers)),
            FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "<!DOCTYPE x>$body", headers)),
            FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(500, "BODY-SENTINEL")),
            FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.fail(TransportFailure.Timeout())),
        )
        for (fake in transports) {
            val (_, events) = captureFeedLogs { fetcherOn(clock, fake).use { fetcher -> fetcher.fetch(url, validators); fetcher.close() } }
            for (event in feedEvents(events)) {
                assertNull(event.throwableProxy, event.formattedMessage)
                for (argument in event.argumentArray.orEmpty()) {
                    val text = argument.toString()
                    assertTrue(argument is Number || allowed.matches(text), "argument '$text' in: ${event.formattedMessage}")
                }
            }
        }
    }
}
