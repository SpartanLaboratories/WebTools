package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.FeedFixtures
import com.spartanlabs.webtools.feeds.CountingSaxHandler
import com.spartanlabs.webtools.feeds.FeedLimit
import com.spartanlabs.webtools.feeds.FeedLimits
import com.spartanlabs.webtools.feeds.LimitReached
import com.spartanlabs.webtools.feeds.RomeFeedParser
import org.jdom2.DefaultJDOMFactory
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Level 2 - `CountingSaxHandler`, driven by a JDK SAX parser: elements up to the limit pass and the next throws
 * `LimitReached(Elements)`; distinct namespace URIs up to the limit pass and the next throws `LimitReached(NamespaceUris)`; a
 * URI declared again is not counted again; two handlers count independently; a fresh handler per document starts every count
 * at zero.
 */
class CountingSaxHandlerComponentTest {

    private fun parseWith(handler: CountingSaxHandler, document: String) {
        val reader = SAXParserFactory.newDefaultInstance().apply { isNamespaceAware = true }.newSAXParser().xmlReader
        reader.contentHandler = handler
        reader.parse(InputSource(StringReader(document)))
    }

    private fun handler(limits: FeedLimits) = CountingSaxHandler(DefaultJDOMFactory(), limits)

    private fun elements(count: Int): String = "<r>" + "<e/>".repeat(count - 1) + "</r>"

    private fun namespaces(count: Int): String = "<r>" + (1..count).joinToString("") { "<e xmlns:n$it=\"urn:ns:$it\"/>" } + "</r>"

    @Test
    fun `elements up to the limit pass and the next throws LimitReached Elements`() {
        val limits = FeedLimits { maxElements = 20 }
        parseWith(handler(limits), elements(20))
        val e = assertFailsWith<LimitReached> { parseWith(handler(limits), elements(21)) }
        assertEquals(FeedLimit.Elements, e.limit)
    }

    @Test
    fun `distinct namespace URIs up to the limit pass and the next throws LimitReached NamespaceUris`() {
        val limits = FeedLimits { maxNamespaceUris = 5 }
        parseWith(handler(limits), namespaces(5))
        val e = assertFailsWith<LimitReached> { parseWith(handler(limits), namespaces(6)) }
        assertEquals(FeedLimit.NamespaceUris, e.limit)
    }

    @Test
    fun `a URI declared again is not counted again`() {
        val limits = FeedLimits { maxNamespaceUris = 2 }
        val repeated = "<r xmlns:a=\"urn:a\">" + "<e xmlns:a=\"urn:a\" xmlns:b=\"urn:b\"/>".repeat(50) + "</r>"
        parseWith(handler(limits), repeated)
    }

    @Test
    fun `two handlers count independently`() {
        val limits = FeedLimits { maxElements = 20 }
        val first = handler(limits)
        val second = handler(limits)
        parseWith(first, elements(15))
        parseWith(second, elements(15))
        assertFailsWith<LimitReached> { parseWith(first, elements(6)) }
        parseWith(handler(limits), elements(20))
    }

    @Test
    fun `a parse with a fresh handler per document starts every count at zero`() {
        val parser = RomeFeedParser(FeedLimits { maxElements = 150 })
        val document = "<rss version=\"2.0\"><channel>" + "<item/>".repeat(98) + "</channel></rss>"
        val first = parser.parse(FeedFixtures.input(document))
        val second = parser.parse(FeedFixtures.input(document))
        assertTrue(first.isSuccess, first.toString())
        assertTrue(second.isSuccess, second.toString())
        assertEquals(98, second.getOrThrow().entries.size)
    }
}
