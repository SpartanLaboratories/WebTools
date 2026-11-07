package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.FEED_URL
import com.spartanlabs.webtools.feeds.FeedFetcher
import com.spartanlabs.webtools.feeds.MSG_MAX_CONCURRENT_PARSES
import com.spartanlabs.webtools.feeds.untrusted
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.Url
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - the JVM owning factories, with no network: `untrusted(userAgent)` builds a fetcher that owns its `UrlFetcher`; a
 * configuration out of range is refused before anything is built; a user agent the transport refuses is an `IllegalArgumentException`
 * and nothing stays open; the policy overload applies the policy (a denied host suffix gives `RefusedByPolicy` before any network).
 */
class FeedFetcherOwningFactoryComponentTest {

    private val agent = "feeds-component/1.0"

    @Test
    fun `untrusted userAgent builds a fetcher that owns its UrlFetcher`(): Unit = runBlocking {
        val fetcher = FeedFetcher.untrusted(agent) { maxConcurrentParses = 1 }
        assertEquals("FeedFetcher(parser=RomeFeedParser, maxConcurrentParses=1, ownsUrlFetcher=true, closed=false)", fetcher.toString())
        fetcher.close()
        assertTrue(fetcher.toString().endsWith("closed=true)"))
        assertEquals("feed fetcher", assertIs<HttpFailure.Closed>(fetcher.fetch(FEED_URL).exceptionOrNull()).component)
    }

    @Test
    fun `a configuration out of range is refused before anything is built`() {
        val e = assertFailsWith<IllegalArgumentException> { FeedFetcher.untrusted(agent) { maxConcurrentParses = 0 } }
        assertEquals(MSG_MAX_CONCURRENT_PARSES, e.message)
        assertFailsWith<IllegalArgumentException> { FeedFetcher.untrusted(agent, OutboundUrlPolicy.untrusted()) { accept = " " } }
    }

    @Test
    fun `a user agent the transport refuses is an IllegalArgumentException and nothing stays open`() {
        for (bad in listOf("", " agent", "agent ", "café/1.0", "a\nb")) {
            assertFailsWith<IllegalArgumentException>("'$bad'") { FeedFetcher.untrusted(bad) }
            assertFailsWith<IllegalArgumentException>("'$bad' with a policy") { FeedFetcher.untrusted(bad, OutboundUrlPolicy.untrusted()) }
        }
    }

    @Test
    fun `the policy overload applies the policy`(): Unit = runBlocking {
        FeedFetcher.untrusted(agent, OutboundUrlPolicy.untrusted { extraDeniedHostSuffixes = setOf("denied.test") }).use { fetcher ->
            val refused = withTimeout(5.seconds) { fetcher.fetch(Url.parse("https://feeds.denied.test/rss").getOrThrow()) }
            assertIs<TransportFailure.RefusedByPolicy>(refused.exceptionOrNull())
            val range = withTimeout(5.seconds) { fetcher.fetch(Url.parse("http://192.168.1.9/rss").getOrThrow()) }
            assertEquals(RefusalReason.DeniedAddress, assertIs<TransportFailure.RefusedByPolicy>(range.exceptionOrNull()).reason)
        }
    }
}
