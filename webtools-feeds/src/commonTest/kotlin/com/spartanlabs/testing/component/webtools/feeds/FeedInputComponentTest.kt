package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.FeedModelFixtures
import com.spartanlabs.webtools.feeds.FeedInput
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Level 2 - `FeedInput`: the document is copied in and read back as a copy; the content type and base URL may be null;
 * `toString` prints the size, presence and the base URL as an origin, never content, a content type or a path; the
 * internal view is the stored array with the document's bytes.
 */
class FeedInputComponentTest {

    //language=XML
    private val document = "<rss>BODY-SENTINEL</rss>".encodeToByteArray()

    @Test
    fun `the document is copied in`() {
        val array = document.copyOf()
        val input = FeedInput(array, null, null)
        array[0] = 'X'.code.toByte()
        assertContentEquals(document, input.document())
    }

    @Test
    fun `the document is read back as a copy`() {
        val input = FeedInput(document, null, null)
        val first = input.document()
        val second = input.document()
        assertNotSame(first, second)
        assertContentEquals(document, first)
        assertContentEquals(document, second)
        first[0] = 'X'.code.toByte()
        assertContentEquals(document, input.document())
        assertEquals(document.size, input.documentSize)
    }

    @Test
    fun `the content type and base URL may be null`() {
        val input = FeedInput(document, null, null)
        assertNull(input.contentType)
        assertNull(input.baseUrl)
        val full = FeedInput(document, "application/rss+xml; charset=utf-8", FeedModelFixtures.sentinelUrl)
        assertEquals("application/rss+xml; charset=utf-8", full.contentType)
        assertEquals(FeedModelFixtures.sentinelUrl, full.baseUrl)
    }

    @Test
    fun `toString prints the size, presence and the base URL as an origin, never content, a content type or a path`() {
        val text = FeedInput(document, "application/rss+xml; charset=CT-SENTINEL", FeedModelFixtures.sentinelUrl).toString()
        assertEquals("FeedInput(documentSize=${document.size}, contentType=present, baseUrl=https://feeds.example.test:443)", text)
        assertFalse(text.contains("SENTINEL"))
        assertFalse(text.contains("feed.xml"))
        assertEquals("FeedInput(documentSize=0, contentType=none, baseUrl=none)", FeedInput(ByteArray(0), null, null).toString())
    }

    @Test
    fun `the internal view is the stored array and has the document's bytes`() {
        val input = FeedInput(document, null, null)
        assertSame(input.documentView(), input.documentView())
        assertContentEquals(document, input.documentView())
        assertNotSame(document, input.documentView())
    }
}
