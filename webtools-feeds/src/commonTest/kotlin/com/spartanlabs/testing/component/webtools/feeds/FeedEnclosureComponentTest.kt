package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.webtools.feeds.FeedEnclosure
import com.spartanlabs.webtools.feeds.MSG_ENCLOSURE_LENGTH
import com.spartanlabs.webtools.feeds.MSG_LINK
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Level 2 - `FeedEnclosure`: the url is required and must be canonical; a length is null or positive; the mime type
 * defaults to null; equality and hash are on the three fields; `toString` prints the origin and presence, never the path.
 */
class FeedEnclosureComponentTest {

    @Test
    fun `the url is required and must be canonical`() {
        assertEquals("https://cdn.test/a.mp3#x", FeedEnclosure("https://cdn.test/a.mp3#x").url)
        for (bad in listOf("", "a.mp3", "HTTP://cdn.test/a.mp3", "ftp://cdn.test/a.mp3", "https://cdn.test/a b.mp3", "https://u@cdn.test/a.mp3")) {
            val e = assertFailsWith<IllegalArgumentException>("'$bad'") { FeedEnclosure(bad) }
            assertEquals(MSG_LINK, e.message, bad)
        }
    }

    @Test
    fun `a length is null or positive`() {
        for (bad in listOf(0L, -1L)) {
            val e = assertFailsWith<IllegalArgumentException>(bad.toString()) { FeedEnclosure("https://cdn.test/a") { lengthBytes = bad } }
            assertEquals(MSG_ENCLOSURE_LENGTH, e.message)
        }
        assertEquals(Long.MAX_VALUE, FeedEnclosure("https://cdn.test/a") { lengthBytes = Long.MAX_VALUE }.lengthBytes)
        assertNull(FeedEnclosure("https://cdn.test/a").lengthBytes)
    }

    @Test
    fun `the mime type defaults to null`() {
        assertNull(FeedEnclosure("https://cdn.test/a").mimeType)
        assertEquals("audio/mpeg", FeedEnclosure("https://cdn.test/a") { mimeType = "audio/mpeg" }.mimeType)
    }

    @Test
    fun `equality and hashCode are on the three fields`() {
        val a = FeedEnclosure("https://cdn.test/a") { mimeType = "audio/mpeg"; lengthBytes = 5 }
        val b = FeedEnclosure("https://cdn.test/a") { mimeType = "audio/mpeg"; lengthBytes = 5 }
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, FeedEnclosure("https://cdn.test/b") { mimeType = "audio/mpeg"; lengthBytes = 5 })
        assertNotEquals(a, FeedEnclosure("https://cdn.test/a") { mimeType = "audio/ogg"; lengthBytes = 5 })
        assertNotEquals(a, FeedEnclosure("https://cdn.test/a") { mimeType = "audio/mpeg"; lengthBytes = 6 })
        assertNotEquals<Any>(a, "https://cdn.test/a")
    }

    @Test
    fun `toString prints the origin and presence, never the path`() {
        val text = FeedEnclosure("https://cdn.test:8443/PATH-SENTINEL/a.mp3?q=QUERY-SENTINEL") { lengthBytes = 5 }.toString()
        assertEquals("FeedEnclosure(url=https://cdn.test:8443, mimeType=none, lengthBytes=present)", text)
        assertFalse(text.contains("SENTINEL"))
    }
}
