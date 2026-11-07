package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.FeedModelFixtures
import com.spartanlabs.webtools.feeds.Feed
import com.spartanlabs.webtools.feeds.FeedEntry
import com.spartanlabs.webtools.feeds.MSG_LINK
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 2 - `Feed`: a factory with no lambda gives null fields and no entries; entries are copied when the feed is built;
 * a link that is not canonical is refused with a constant message; equality and hash are on the four fields; `toString`
 * prints presence and counts, the link as an origin, and no text.
 */
class FeedComponentTest {

    @Test
    fun `a factory with no lambda gives null fields and no entries`() {
        val feed = Feed()
        assertNull(feed.title)
        assertNull(feed.link)
        assertNull(feed.description)
        assertEquals(emptyList(), feed.entries)
    }

    @Test
    fun `entries are copied when the feed is built`() {
        val list = mutableListOf(FeedEntry("a"))
        val feed = Feed { entries = list }
        list.add(FeedEntry("b"))
        assertEquals(1, feed.entries.size)
        assertEquals("a", feed.entries[0].id)
    }

    @Test
    fun `a link that is not canonical is refused with a constant message`() {
        for (bad in listOf("/relative", "HTTPS://x.test/", "javascript:alert(1)", "https://u@x.test/", "https://x.test/a b", "https://x.test\\x")) {
            val e = assertFailsWith<IllegalArgumentException>(bad) { Feed { link = bad } }
            assertEquals(MSG_LINK, e.message, bad)
        }
        assertEquals("https://x.test/a#frag", Feed { link = "https://x.test/a#frag" }.link)
    }

    @Test
    fun `equality and hashCode are on the four fields`() {
        val a = FeedModelFixtures.feed(2)
        val b = FeedModelFixtures.feed(2)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, FeedModelFixtures.feed(1))
        assertNotEquals(a, Feed { title = a.title; link = a.link; description = "other"; entries = a.entries })
        assertNotEquals(a, Feed { title = a.title; link = null; description = a.description; entries = a.entries })
        assertNotEquals(a, Feed { title = "other"; link = a.link; description = a.description; entries = a.entries })
        assertNotEquals<Any>(a, "Feed")
    }

    @Test
    fun `toString prints presence and counts, the link as an origin, and no text`() {
        val text = FeedModelFixtures.feed(2).toString()
        assertEquals("Feed(title=present, link=https://links.example.test, description=present, entries=2)", text)
        assertFalse(text.contains("SENTINEL"))
        assertEquals("Feed(title=none, link=none, description=none, entries=0)", Feed().toString())
        assertTrue(Feed { link = "http://x.test:8080/PATH-SENTINEL?q" }.toString().contains("link=http://x.test:8080,"))
    }
}
