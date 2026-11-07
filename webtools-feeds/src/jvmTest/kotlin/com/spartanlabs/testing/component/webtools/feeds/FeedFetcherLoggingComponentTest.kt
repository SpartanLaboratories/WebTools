package com.spartanlabs.testing.component.webtools.feeds

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.spartanlabs.testing.support.webtools.feeds.FEED_URL
import com.spartanlabs.testing.support.webtools.feeds.RecordingParser
import com.spartanlabs.testing.support.webtools.feeds.captureFeedLogs
import com.spartanlabs.testing.support.webtools.feeds.feedEvents
import com.spartanlabs.testing.support.webtools.feeds.fetcherOn
import com.spartanlabs.testing.support.webtools.feeds.respondWith
import com.spartanlabs.testing.support.webtools.feeds.rssBody
import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedRefusal
import com.spartanlabs.webtools.feeds.FeedValidators
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.virtualClock
import com.spartanlabs.webtools.http.TransportFailure
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Level 2 - the fetcher's logging on `com.spartanlabs.webtools.feeds.FeedFetcher`: creation logs one `INFO` line with kinds and
 * numbers; close logs one `INFO` line, once; a `Fetched` result logs one `DEBUG` line with the origin, status, bytes, entries and times;
 * a `NotModified` result logs the outcome and dashes for the entry count; a `Refused` document logs one `WARN` and every other failure
 * only the `DEBUG` line; a failure outcome names the failure's class, and an `UpstreamError` prints its status.
 */
class FeedFetcherLoggingComponentTest {

    private val body = rssBody(2)
    private val secretUrl = Url.parse("https://feeds.example.test/PATH-SENTINEL/feed.xml?token=QUERY-SENTINEL").getOrThrow()

    private fun ILoggingEvent.text(): String = formattedMessage

    @Test
    fun `creation logs one INFO line with kinds and numbers`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        val (_, events) = captureFeedLogs { fetcherOn(clock, fake) { parser = RecordingParser(); maxConcurrentParses = 3 } }
        val created = feedEvents(events).filter { it.text().startsWith("created") }
        assertEquals(1, created.size)
        assertEquals(Level.INFO, created.single().level)
        assertEquals("created: parser=RecordingParser, parseDispatcher=StandardTestDispatcherImpl, maxConcurrentParses=3, ownsUrlFetcher=false", created.single().text())
    }

    @Test
    fun `close logs one INFO line, once`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        val fetcher = fetcherOn(clock, fake)
        val (_, events) = captureFeedLogs { fetcher.close(); fetcher.close(); fetcher.close() }
        val closed = feedEvents(events).filter { it.text() == "closed" }
        assertEquals(1, closed.size)
        assertEquals(Level.INFO, closed.single().level)
    }

    @Test
    fun `a Fetched result logs one DEBUG line with the origin, status, bytes, entries and times`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body, HttpHeaders { add("ETag", "\"TAG-SENTINEL\"") }))
        fetcherOn(clock, fake).use { fetcher ->
            val (_, events) = captureFeedLogs { fetcher.fetch(secretUrl) }
            val lines = feedEvents(events).filter { it.text().startsWith("fetch:") }
            assertEquals(1, lines.size)
            val line = lines.single()
            assertEquals(Level.DEBUG, line.level)
            assertTrue(line.text().startsWith("fetch: target=https://feeds.example.test:443, outcome=Fetched, status=200, bytes=${body.length}, entries=2, parseMs="), line.text())
            assertTrue(Regex("parseMs=\\d+, elapsedMs=\\d+$").containsMatchIn(line.text()), line.text())
            assertFalse(line.text().contains("SENTINEL"))
            assertFalse(line.text().contains("feed.xml"))
        }
    }

    @Test
    fun `a NotModified result logs the outcome and dashes for the entry count`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(304))
        fetcherOn(clock, fake).use { fetcher ->
            val (_, events) = captureFeedLogs { fetcher.fetch(secretUrl, FeedValidators("\"TAG-SENTINEL\"", null)) }
            val line = feedEvents(events).single { it.text().startsWith("fetch:") }
            assertTrue(line.text().startsWith("fetch: target=https://feeds.example.test:443, outcome=NotModified, status=304, bytes=0, entries=-, parseMs=-, elapsedMs="), line.text())
            assertFalse(line.text().contains("SENTINEL"))
        }
    }

    @Test
    fun `a Refused document logs one WARN and every other failure only the DEBUG line`() = runTest {
        val clock = virtualClock()
        val doctype = "<?xml version=\"1.0\"?><!DOCTYPE rss SYSTEM \"http://127.0.0.1:1/x.dtd\"><rss version=\"2.0\"><channel><title>SENTINEL</title></channel></rss>"
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, doctype))
        fetcherOn(clock, fake).use { fetcher ->
            val (result, events) = captureFeedLogs { fetcher.fetch(secretUrl) }
            assertEquals(FeedRefusal.Doctype, (result.exceptionOrNull() as FeedFailure.Refused).reason)
            val mine = feedEvents(events)
            val warns = mine.filter { it.level == Level.WARN }
            assertEquals(1, warns.size)
            assertEquals("refused document: reason=Doctype, target=https://feeds.example.test:443", warns.single().text())
            assertEquals(1, mine.count { it.text().startsWith("fetch:") })
            assertTrue(mine.none { it.text().contains("SENTINEL") })
        }
        val malformed = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "<rss><channel>"))
        fetcherOn(clock, malformed).use { fetcher ->
            val (_, events) = captureFeedLogs { fetcher.fetch(FEED_URL) }
            val mine = feedEvents(events)
            assertTrue(mine.none { it.level == Level.WARN })
            assertEquals(1, mine.count { it.text().startsWith("fetch:") && it.text().contains("outcome=Malformed") })
        }
    }

    @Test
    fun `a failure outcome names the failure's class, and an UpstreamError prints its status`() = runTest {
        val clock = virtualClock()
        val gone = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(410, "gone"))
        fetcherOn(clock, gone).use { fetcher ->
            val (_, events) = captureFeedLogs { fetcher.fetch(FEED_URL) }
            val line = feedEvents(events).single { it.text().startsWith("fetch:") }
            assertTrue(line.text().contains("outcome=UpstreamError, status=410, bytes=4, entries=-, parseMs=-"), line.text())
        }
        val timeout = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.fail(TransportFailure.Timeout()))
        fetcherOn(clock, timeout).use { fetcher ->
            val (_, events) = captureFeedLogs { fetcher.fetch(FEED_URL) }
            val line = feedEvents(events).single { it.text().startsWith("fetch:") }
            assertTrue(line.text().contains("outcome=Timeout, status=-, bytes=-, entries=-, parseMs=-"), line.text())
        }
        val refused = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, body))
        fetcherOn(clock, refused).use { fetcher ->
            val (_, events) = captureFeedLogs { fetcher.fetch(Url.parse("http://10.0.0.5/").getOrThrow()) }
            val line = feedEvents(events).single { it.text().startsWith("fetch:") }
            assertTrue(line.text().startsWith("fetch: target=http://10.0.0.5:80, outcome=RefusedByPolicy, status=-"), line.text())
        }
    }
}
