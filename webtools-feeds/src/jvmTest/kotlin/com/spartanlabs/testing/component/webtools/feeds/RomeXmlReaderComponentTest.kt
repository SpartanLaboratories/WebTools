package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.bytes
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.latin1
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.utf16be
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.utf16le
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.utf8
import com.spartanlabs.testing.support.webtools.feeds.PrologVectors
import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedLimits
import com.spartanlabs.webtools.feeds.looksLikeUtf16
import com.spartanlabs.webtools.feeds.openXmlReader
import com.spartanlabs.webtools.feeds.readWithRome
import com.spartanlabs.webtools.feeds.withoutLeadingAsciiWhitespace
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Level 2 - the three-step `XmlReader` recipe: leading ASCII white space is dropped only before an ASCII `<`; the UTF-16 marks
 * and markup pattern are detected and other starts are not; a UTF-16 document is read ignoring the header charset; a header
 * charset the JDK does not know is retried without the header; a declaration naming an unknown encoding is `Malformed`; a
 * document with no declaration and no charset is read as UTF-8.
 */
class RomeXmlReaderComponentTest {

    private val root = PrologVectors.RSS_ROOT

    @Test
    fun `leading ASCII white space is dropped only when an ASCII less-than follows it`() {
        assertContentEquals(utf8(root), withoutLeadingAsciiWhitespace(utf8(" \t\r\n$root")))
        val plain = utf8(root)
        assertSame(plain, withoutLeadingAsciiWhitespace(plain))
        val junk = utf8("  junk")
        assertSame(junk, withoutLeadingAsciiWhitespace(junk))
        val spaces = utf8("   ")
        assertSame(spaces, withoutLeadingAsciiWhitespace(spaces))
        val utf16 = utf16le(" $root", mark = false)
        assertSame(utf16, withoutLeadingAsciiWhitespace(utf16))
    }

    @Test
    fun `the UTF-16 marks and the UTF-16 markup pattern are detected and UTF-8, ISO-8859-1 and plain ASCII are not`() {
        assertTrue(looksLikeUtf16(bytes(0xFE, 0xFF, 0x00, 0x3C)))
        assertTrue(looksLikeUtf16(bytes(0xFF, 0xFE, 0x3C, 0x00)))
        assertTrue(looksLikeUtf16(utf16be("<?xml version=\"1.0\"?>", mark = false)))
        assertTrue(looksLikeUtf16(utf16le("<?xml version=\"1.0\"?>", mark = false)))
        assertFalse(looksLikeUtf16(utf8(root)))
        assertFalse(looksLikeUtf16(bytes(0xEF, 0xBB, 0xBF) + utf8(root)))
        assertFalse(looksLikeUtf16(latin1("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><r>é</r>")))
        assertFalse(looksLikeUtf16(utf16le(root, mark = false)), "a UTF-16 root without a declaration or a mark is not detected")
        assertFalse(looksLikeUtf16(ByteArray(0)))
        assertFalse(looksLikeUtf16(bytes(0xFE)))
    }

    @Test
    fun `a UTF-16 document is read ignoring the header charset`() {
        val document = utf16le("<?xml version=\"1.0\"?><r>é</r>")
        openXmlReader(document, "text/xml; charset=iso-8859-1").use { reader ->
            assertTrue(reader.encoding.uppercase().startsWith("UTF-16"), reader.encoding)
            assertEquals("<?xml version=\"1.0\"?><r>é</r>", reader.readText())
        }
        // Without a mark the raw constructor needs the declaration to name the encoding; the UTF-16 `<?` pattern alone is only the guess.
        openXmlReader(utf16be("<?xml version=\"1.0\" encoding=\"UTF-16\"?><r>é</r>", mark = false), "text/xml; charset=utf-8").use { reader ->
            assertEquals("<?xml version=\"1.0\" encoding=\"UTF-16\"?><r>é</r>", reader.readText())
        }
    }

    @Test
    fun `a header charset the JDK does not know is retried once without the header`() {
        openXmlReader(utf8("<r>é</r>"), "text/xml; charset=bogus-9").use { reader ->
            assertEquals("UTF-8", reader.encoding.uppercase())
            assertEquals("<r>é</r>", reader.readText())
        }
        openXmlReader(utf8("<?xml version=\"1.0\" encoding=\"UTF-8\"?><r>é</r>"), "text/xml; charset=bogus-9").use { reader ->
            assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><r>é</r>", reader.readText())
        }
    }

    @Test
    fun `a declaration naming an unknown encoding is Malformed`() {
        val result = readWithRome(utf8("<?xml version=\"1.0\" encoding=\"bogus-9\"?>$root"), "application/xml", FeedLimits.Defaults)
        assertIs<FeedFailure.Malformed>(result.exceptionOrNull())
        val withHeader = readWithRome(utf8("<?xml version=\"1.0\" encoding=\"bogus-9\"?>$root"), "application/xml; charset=utf-8", FeedLimits.Defaults)
        assertIs<FeedFailure.Malformed>(withHeader.exceptionOrNull())
    }

    @Test
    fun `a document with no declaration and no charset is read as UTF-8`() {
        openXmlReader(utf8("<r>é中</r>"), "text/xml").use { reader ->
            assertEquals("UTF-8", reader.encoding.uppercase())
            assertEquals("<r>é中</r>", reader.readText())
        }
        openXmlReader(utf8("<r>é</r>"), null).use { reader -> assertEquals("<r>é</r>", reader.readText()) }
    }
}
