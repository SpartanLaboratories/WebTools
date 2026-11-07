package com.spartanlabs.testing.deterministic.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments
import com.spartanlabs.testing.support.webtools.feeds.FeedFixtures
import com.spartanlabs.testing.support.webtools.feeds.PipelineOutcome
import com.spartanlabs.testing.support.webtools.feeds.PrologVectors
import com.spartanlabs.testing.support.webtools.feeds.RomePipelineProbe
import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.NotAFeedReason
import com.spartanlabs.webtools.feeds.PrologScan
import com.spartanlabs.webtools.feeds.PrologScanResult
import com.spartanlabs.webtools.feeds.RomeFeedParser
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Level 4a - the prolog scan against the real pipeline (seven `XmlReader` shapes, Rome's repair reader, the hardened JDK SAX
 * build): no catalogue row and no generated document reaches the DOCTYPE while the scan says `NoDoctype`; when the scan says
 * `Empty` or `NotMarkup` no shape parses the document; every catalogue row the research records as parsing is parsed by
 * `RomeFeedParser` when the scan says `NoDoctype`.
 */
class PrologScanRomeDifferentialDeterministicTest {

    /** The N rows whose pipeline column in the research reads "parses". */
    private val parsesInFindings = setOf(
        "N01", "N02", "N03", "N04", "N05", "N06", "N07", "N08", "N09", "N10", "N11", "N12", "N13", "N14", "N15", "N16",
        "N19", "N22", "N23", "N25", "N26", "N27", "N28", "N29", "N31", "N32",
    )

    private fun reachesDoctype(document: ByteArray, contentType: String?): Boolean =
        RomePipelineProbe.runAll(document, contentType).values.any { it == PipelineOutcome.ReachesDoctype }

    private fun anyShapeParses(document: ByteArray, contentType: String?): Boolean =
        RomePipelineProbe.runAll(document, contentType).values.any { it == PipelineOutcome.Parses }

    @Test
    fun `no catalogue row reaches the DOCTYPE in the pipeline while the scan says NoDoctype`() {
        val violations = PrologVectors.all.filter { PrologScan.scan(it.bytes, it.contentType) == PrologScanResult.NoDoctype && reachesDoctype(it.bytes, it.contentType) }
        assertTrue(violations.isEmpty(), violations.joinToString { it.id })
    }

    @Test
    fun `a seeded generator of 20,000 documents never reaches the DOCTYPE while the scan says NoDoctype`() {
        val generator = Generator(Random(69_2026))
        val violations = mutableListOf<String>()
        var checked = 0
        repeat(20_000) { i ->
            val (document, contentType) = generator.next()
            if (PrologScan.scan(document, contentType) == PrologScanResult.NoDoctype) {
                checked++
                if (reachesDoctype(document, contentType)) violations += "document $i"
            }
        }
        assertTrue(checked > 1_000, "the generator must produce many NoDoctype documents; it produced $checked")
        assertTrue(violations.isEmpty(), "${violations.size} violations: ${violations.take(10)}")
    }

    @Test
    fun `when the scan says Empty or NotMarkup, no shape of the pipeline parses the document`() {
        val catalogue = PrologVectors.all.filter {
            val r = PrologScan.scan(it.bytes, it.contentType)
            (r == PrologScanResult.Empty || r == PrologScanResult.NotMarkup) && anyShapeParses(it.bytes, it.contentType)
        }
        assertTrue(catalogue.isEmpty(), catalogue.joinToString { it.id })
        val generator = Generator(Random(1106))
        val violations = mutableListOf<Int>()
        var checked = 0
        repeat(20_000) { i ->
            val (document, contentType) = generator.next()
            val r = PrologScan.scan(document, contentType)
            if (r == PrologScanResult.Empty || r == PrologScanResult.NotMarkup) {
                checked++
                if (anyShapeParses(document, contentType)) violations += i
            }
        }
        assertTrue(checked > 500, "the generator must produce many non-markup documents; it produced $checked")
        assertTrue(violations.isEmpty(), "${violations.size} violations: ${violations.take(10)}")
    }

    @Test
    fun `every catalogue row the findings record as parses is parsed by RomeFeedParser when the scan says NoDoctype`() {
        val parser = RomeFeedParser()
        // "Parses" in the research is the XML pipeline's verdict; a well-formed root that is not a feed format (N19's `<htmlx/>`)
        // reaches Rome's format detection and is reported as an unknown format, which is the document being read, not refused.
        fun readByRome(result: Result<*>): Boolean =
            result.isSuccess || (result.exceptionOrNull() as? FeedFailure.NotAFeed)?.reason == NotAFeedReason.UnknownFormat
        val failed = PrologVectors.all.filter { it.id in parsesInFindings }.mapNotNull { row ->
            val scan = PrologScan.scan(row.bytes, row.contentType)
            val result = parser.parse(FeedFixtures.input(row.bytes, row.contentType))
            if (scan != PrologScanResult.NoDoctype || !readByRome(result)) "${row.id}: scan=$scan, result=${result.exceptionOrNull()?.message ?: "ok"}" else null
        }
        assertTrue(PrologVectors.all.filter { it.id in parsesInFindings && it.id != "N19" }.all { parser.parse(FeedFixtures.input(it.bytes, it.contentType)).isSuccess })
        assertTrue(failed.isEmpty(), failed.joinToString("\n"))
    }

    /**
     * Documents mixing byte-order marks, white space, comments (well-formed, with `--`, unterminated, holding DOCTYPE text, lone
     * surrogates or legacy lead bytes), processing instructions, declarations naming listed, swallow-prone and unlisted
     * encodings, DOCTYPEs in several spellings, junk, and rss, html or no root, in UTF-8 or UTF-16 with or without a mark, under
     * random `Content-Type` values.
     */
    private class Generator(private val random: Random) {
        private val contentTypes = listOf(
            null, "application/xml", "text/xml", "text/html", "application/rss+xml", "text/xml; charset=utf-16le", "text/xml; charset=utf-16be",
            "application/xml; charset=euc-jp", "application/xml; charset=gb18030", "application/xml; charset=ibm037", "application/xml; charset=bogus-9",
            "application/xml; charset=windows-1252", "application/xml; charset=iso-8859-1", "text/xml; charset=utf-8",
        )
        private val encodingNames = listOf("UTF-8", "UTF-16", "ISO-8859-1", "Shift_JIS", "euc-jp", "gb18030", "ibm037", "UTF-7", "bogus-9", "windows-1252", "utf-16le")
        private val doctypes = listOf(PrologVectors.DOCTYPE, "<!DOCTYPE html>", "<!doctype rss>", "<!DocType rss [<!ENTITY x \"y\">]>", "<!ENTITY a \"b\">", "<!DOCTYPE>")
        private val roots = listOf(PrologVectors.RSS_ROOT, "<html><body>x</body></html>", "<htmlx/>", "<r/>", "")
        private val legacyBytes = listOf(byteArrayOf(0x8F.toByte()), byteArrayOf(0xE9.toByte()), byteArrayOf(0x81.toByte(), 0x30), byteArrayOf(0xA4.toByte(), 0xA2.toByte()), byteArrayOf(0x83.toByte(), 0x5C))

        fun next(): Pair<ByteArray, String?> {
            val utf16 = random.nextInt(10) < 3
            val littleEndian = random.nextBoolean()
            val parts = mutableListOf<ByteArray>()
            fun text(s: String) {
                parts += if (utf16) (if (littleEndian) ByteDocuments.utf16le(s, mark = false) else ByteDocuments.utf16be(s, mark = false)) else s.encodeToByteArray()
            }
            repeat(random.nextInt(0, 3)) {
                parts += when (random.nextInt(3)) { 0 -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()); 1 -> byteArrayOf(0xFE.toByte(), 0xFF.toByte()); else -> byteArrayOf(0xFF.toByte(), 0xFE.toByte()) }
            }
            if (random.nextInt(4) == 0) text(listOf(" ", "\n", "\r\n\t", "  \n ")[random.nextInt(4)])
            if (random.nextInt(3) > 0) {
                text(if (random.nextBoolean()) "<?xml version=\"1.0\"?>" else "<?xml version=\"1.0\" encoding=\"${encodingNames[random.nextInt(encodingNames.size)]}\"?>")
            }
            repeat(random.nextInt(0, 4)) {
                when (random.nextInt(9)) {
                    0 -> text("<!-- c -->")
                    1 -> text("<!-- a -- b -->")
                    2 -> text("<!-- <!DOCTYPE a> -->")
                    3 -> text("<!-- \uD800-->")
                    4 -> text("<!-- \uDC00 -->")
                    5 -> { text("<!-- "); if (!utf16) parts += legacyBytes[random.nextInt(legacyBytes.size)] else text("\uD83D"); text("-->") }
                    6 -> text("<?pi x?>")
                    7 -> text("<?xml-stylesheet encoding=\"${encodingNames[random.nextInt(encodingNames.size)]}\"?>")
                    else -> text("<!-- never closed ")
                }
            }
            if (random.nextInt(5) == 0) text(listOf("x", "&amp;", "junk", "{\"a\":1}")[random.nextInt(4)])
            if (random.nextInt(5) < 2) text(doctypes[random.nextInt(doctypes.size)])
            text(roots[random.nextInt(roots.size)])
            val size = parts.sumOf { it.size }
            val document = ByteArray(size)
            var at = 0
            for (p in parts) { p.copyInto(document, at); at += p.size }
            return document to contentTypes[random.nextInt(contentTypes.size)]
        }
    }
}
