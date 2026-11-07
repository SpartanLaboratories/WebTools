package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.FeedModelFixtures
import com.spartanlabs.webtools.feeds.Feed
import com.spartanlabs.webtools.feeds.FeedFetch
import com.spartanlabs.webtools.feeds.FeedValidators
import com.spartanlabs.webtools.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 2 - `FeedFetch`: both leaves expose the shared properties through the interface; equality is on every field of
 * each leaf and a leaf never equals the other kind; `toString` prints the entry count, presence and origins and never a
 * path, a query or a validator value; an exhaustive `when` needs no `else`.
 */
class FeedFetchComponentTest {

    private val url = FeedModelFixtures.sentinelUrl
    private val moved = Url.parse("https://moved.example.test/PATH-SENTINEL/feed.xml?token=QUERY-SENTINEL").getOrThrow()
    private val validators = FeedValidators("\"TAG-SENTINEL\"", "Fri, 06 Nov 2026 04:25:27 GMT")
    private val feed = FeedModelFixtures.feed(3)

    @Test
    fun `both leaves expose the shared properties through the interface`() {
        val fetched: FeedFetch = FeedFetch.Fetched(feed, validators, url, moved)
        val notModified: FeedFetch = FeedFetch.NotModified(validators, url, null)
        assertEquals(validators, fetched.validators)
        assertEquals(url, fetched.finalUrl)
        assertEquals(moved, fetched.permanentlyMovedTo)
        assertEquals(validators, notModified.validators)
        assertEquals(url, notModified.finalUrl)
        assertNull(notModified.permanentlyMovedTo)
    }

    @Test
    fun `equality is on every field of each leaf and a leaf never equals the other kind`() {
        val a = FeedFetch.Fetched(feed, validators, url, moved)
        assertEquals(a, FeedFetch.Fetched(FeedModelFixtures.feed(3), validators, url, moved))
        assertEquals(a.hashCode(), FeedFetch.Fetched(FeedModelFixtures.feed(3), validators, url, moved).hashCode())
        assertNotEquals(a, FeedFetch.Fetched(FeedModelFixtures.feed(2), validators, url, moved))
        assertNotEquals(a, FeedFetch.Fetched(feed, FeedValidators.None, url, moved))
        assertNotEquals(a, FeedFetch.Fetched(feed, validators, moved, moved))
        assertNotEquals(a, FeedFetch.Fetched(feed, validators, url, null))

        val n = FeedFetch.NotModified(validators, url, moved)
        assertEquals(n, FeedFetch.NotModified(validators, url, moved))
        assertEquals(n.hashCode(), FeedFetch.NotModified(validators, url, moved).hashCode())
        assertNotEquals(n, FeedFetch.NotModified(FeedValidators.None, url, moved))
        assertNotEquals(n, FeedFetch.NotModified(validators, moved, moved))
        assertNotEquals(n, FeedFetch.NotModified(validators, url, null))

        assertNotEquals<FeedFetch>(a, n)
        assertNotEquals<FeedFetch>(n, FeedFetch.Fetched(Feed(), validators, url, moved))
    }

    @Test
    fun `toString prints the entry count, presence and origins, never a path, a query or a validator value`() {
        val fetched = FeedFetch.Fetched(feed, validators, url, moved).toString()
        assertEquals(
            "Fetched(entries=3, validators=FeedValidators(etag=present, lastModified=present), " +
                "finalUrl=https://feeds.example.test:443, permanentlyMovedTo=https://moved.example.test:443)",
            fetched,
        )
        val notModified = FeedFetch.NotModified(validators, url, null).toString()
        assertEquals("NotModified(validators=FeedValidators(etag=present, lastModified=present), finalUrl=https://feeds.example.test:443, permanentlyMovedTo=none)", notModified)
        for (text in listOf(fetched, notModified)) {
            assertFalse(text.contains("SENTINEL"), text)
            assertFalse(text.contains("feed.xml"), text)
            assertFalse(text.contains("token"), text)
            assertFalse(text.contains("2026"), text)
        }
    }

    @Test
    fun `an exhaustive when over the interface needs no else`() {
        val fetches: List<FeedFetch> = listOf(FeedFetch.Fetched(feed, validators, url, null), FeedFetch.NotModified(validators, url, null))
        val flags = fetches.map { fetch ->
            when (fetch) {
                is FeedFetch.Fetched -> true
                is FeedFetch.NotModified -> false
            }
        }
        assertEquals(listOf(true, false), flags)
        assertTrue(fetches.all { it.validators === validators })
    }
}
