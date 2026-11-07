package com.spartanlabs.testing.deterministic.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.bytes
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.utf16be
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.utf16le
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.utf8
import com.spartanlabs.testing.support.webtools.feeds.PrologVectors
import com.spartanlabs.testing.support.webtools.feeds.PrologVectors.DOCTYPE
import com.spartanlabs.testing.support.webtools.feeds.PrologVectors.RSS_ROOT
import com.spartanlabs.webtools.feeds.PrologScan
import com.spartanlabs.webtools.feeds.PrologScanResult
import com.spartanlabs.webtools.feeds.UndecodableReason
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Level 4a - how the three readings of the prolog scan combine: a reading that reaches markup wins; when none does the byte
 * reading decides `Empty` or `NotMarkup`; the worst outcome wins among readings that reach markup; the whole leading run of
 * byte-order marks is skipped; the 64 KiB bound is exact.
 */
class PrologScanReadingsDeterministicTest {

    private val xml = "application/xml"
    private fun scan(document: ByteArray, contentType: String? = xml) = PrologScan.scan(document, contentType)

    @Test
    fun `a reading that reaches markup wins, so a UTF-16 document with no mark is NoDoctype`() {
        assertEquals(PrologScanResult.NoDoctype, scan(utf16le(RSS_ROOT, mark = false)))
        assertEquals(PrologScanResult.NoDoctype, scan(utf16be(RSS_ROOT, mark = false)))
        assertEquals(PrologScanResult.NoDoctype, scan(utf16le(PrologVectors.DECLARATION + RSS_ROOT, mark = false), "text/xml; charset=utf-16le"))
    }

    @Test
    fun `when no reading reaches markup the byte reading decides Empty`() {
        assertEquals(PrologScanResult.Empty, scan(utf8("  ")))
        assertEquals(PrologScanResult.Empty, scan(utf8("\r\n\t ")))
        assertEquals(PrologScanResult.Empty, scan(utf8(PrologVectors.DECLARATION + "<!-- only a comment -->")))
        assertEquals(PrologScanResult.Empty, scan(utf8("<?pi x?>  ")))
        assertEquals(PrologScanResult.Empty, scan(ByteArray(0), null))
    }

    @Test
    fun `NotMarkup when no reading reaches markup and the byte reading holds a significant byte that is not a less-than`() {
        assertEquals(PrologScanResult.NotMarkup, scan(utf8("hello")))
        assertEquals(PrologScanResult.NotMarkup, scan(utf8("  {\"a\": 1}"), "application/json"))
        assertEquals(PrologScanResult.NotMarkup, scan(utf8("<!-- c -->junk" + RSS_ROOT)))
        assertEquals(PrologScanResult.NotMarkup, scan(bytes(0xEF, 0xBB, 0xBF) + utf8("x")))
    }

    @Test
    fun `the worst outcome wins among readings that reach markup`() {
        // A UTF-16LE document without a mark reads as `<` and a NUL in the byte reading (NoDoctype) and as junk in the
        // big-endian reading (NotMarkup); the little-endian reading's outcome must win in every case.
        assertEquals(PrologScanResult.Undecodable(UndecodableReason.MalformedSequence), scan(utf16le("<!-- \uD800 -->" + RSS_ROOT, mark = false)))
        assertEquals(PrologScanResult.Doctype, scan(utf16le(DOCTYPE + RSS_ROOT, mark = false)))
        assertEquals(PrologScanResult.IllFormed, scan(utf16le("<!-- a -- b -->" + RSS_ROOT, mark = false)))
        assertEquals(PrologScanResult.HtmlDocument, scan(utf16le("<html></html>", mark = false)))
        assertEquals(PrologScanResult.NoDoctype, scan(utf16le(RSS_ROOT, mark = false)))
        // A UTF-8 document behind a UTF-16 mark: the byte reading reaches the markup, the 16-bit readings see junk.
        assertEquals(PrologScanResult.Doctype, scan(bytes(0xFE, 0xFF) + utf8(DOCTYPE + RSS_ROOT)))
        assertEquals(PrologScanResult.IllFormed, scan(bytes(0xFF, 0xFE) + utf8("<!-- a -- b -->" + RSS_ROOT)))
        assertEquals(PrologScanResult.HtmlDocument, scan(bytes(0xFF, 0xFE) + utf8("<html></html>")))
        // Within one byte reading a DOCTYPE outranks the junk that follows it, and an unlisted name outranks everything.
        assertEquals(PrologScanResult.Doctype, scan(utf8("<!-- c -->" + DOCTYPE + "junk")))
        assertEquals(PrologScanResult.Undecodable(UndecodableReason.UnlistedEncodingName), scan(utf8(DOCTYPE + RSS_ROOT), "application/xml; charset=utf-7"))
    }

    @Test
    fun `the whole leading run of byte-order marks is skipped`() {
        val bom8 = bytes(0xEF, 0xBB, 0xBF)
        assertEquals(PrologScanResult.NoDoctype, scan(bom8 + bom8 + bom8 + bom8 + bom8 + utf8(RSS_ROOT)))
        assertEquals(PrologScanResult.NoDoctype, scan(bom8 + bytes(0xFF, 0xFE) + utf8(RSS_ROOT)))
        assertEquals(PrologScanResult.Doctype, scan(bytes(0xFE, 0xFF) + bom8 + utf8(DOCTYPE + RSS_ROOT)))
        assertEquals(PrologScanResult.Empty, scan(bom8 + bytes(0xFF, 0xFE)))
    }

    @Test
    fun `the 64 KiB bound is exact`() {
        assertEquals(PrologScanResult.NoDoctype, scan(PrologVectors.rootAtOffset(65_534)))
        assertEquals(PrologScanResult.Undecodable(UndecodableReason.PrologTooLong), scan(PrologVectors.rootAtOffset(65_535)))
        assertEquals(PrologScan.MAX_PROLOG_BYTES, 65_536)
    }
}
