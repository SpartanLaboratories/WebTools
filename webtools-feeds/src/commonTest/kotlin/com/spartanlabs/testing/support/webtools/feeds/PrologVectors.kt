package com.spartanlabs.testing.support.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.bytes
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.latin1
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.repeatBytes
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.utf16be
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.utf16le
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.utf8
import com.spartanlabs.webtools.feeds.PrologScanResult
import com.spartanlabs.webtools.feeds.PrologScanResult.Doctype
import com.spartanlabs.webtools.feeds.PrologScanResult.Empty
import com.spartanlabs.webtools.feeds.PrologScanResult.HtmlDocument
import com.spartanlabs.webtools.feeds.PrologScanResult.IllFormed
import com.spartanlabs.webtools.feeds.PrologScanResult.NoDoctype
import com.spartanlabs.webtools.feeds.PrologScanResult.NotMarkup
import com.spartanlabs.webtools.feeds.PrologScanResult.Undecodable
import com.spartanlabs.webtools.feeds.UndecodableReason.MalformedSequence
import com.spartanlabs.webtools.feeds.UndecodableReason.PrologTooLong
import com.spartanlabs.webtools.feeds.UndecodableReason.UnlistedEncodingName
import com.spartanlabs.webtools.feeds.UndecodableReason.WideOrEbcdicPrefix

/** One catalogue row: the document, the `Content-Type` the scan is given (`null` for none) and the expected scan result. */
internal class PrologVector(val id: String, val bytes: ByteArray, val contentType: String?, val expected: PrologScanResult)

/**
 * The 135-row catalogue of the prolog scan, by family: N, documents with no DOCTYPE (N17 empty, N18, N20 and N21 not markup);
 * D, documents with a DOCTYPE or another markup declaration; H, web pages; M, ill-formed prologs; U, prologs that cannot be read
 * the way the parser reads them; E, edge cases of empty and non-markup bodies and the size bound. Documents are built from the
 * recipe parts below; the long rows are generated.
 */
internal object PrologVectors {
    //language=XML
    const val RSS_ROOT: String = "<rss version=\"2.0\"><channel><title>t</title></channel></rss>"
    //language=XML
    const val ATOM_ROOT: String = "<feed xmlns=\"http://www.w3.org/2005/Atom\"><title>t</title></feed>"
    const val DOCTYPE: String = "<!DOCTYPE rss SYSTEM \"http://127.0.0.1:1/x.dtd\">"
    const val DECLARATION: String = "<?xml version=\"1.0\"?>"
    private const val XML = "application/xml"

    private val bom8 = bytes(0xEF, 0xBB, 0xBF)
    private val bomBe = bytes(0xFE, 0xFF)
    private val bomLe = bytes(0xFF, 0xFE)

    private fun utf32be(text: String): ByteArray = ByteArray(text.length * 4).also { out -> for (i in text.indices) { out[4 * i + 2] = (text[i].code ushr 8).toByte(); out[4 * i + 3] = (text[i].code and 0xFF).toByte() } }
    private fun utf32le(text: String): ByteArray = ByteArray(text.length * 4).also { out -> for (i in text.indices) { out[4 * i] = (text[i].code and 0xFF).toByte(); out[4 * i + 1] = (text[i].code ushr 8).toByte() } }

    /** A UTF-8 comment of [padding] `x` bytes followed by the rss root, so the root starts at byte `padding + 7`. */
    fun rootAtOffset(offset: Int): ByteArray = utf8("<!--" + "x".repeat(offset - 7) + "-->" + RSS_ROOT)

    private fun row(id: String, bytes: ByteArray, contentType: String?, expected: PrologScanResult) = PrologVector(id, bytes, contentType, expected)

    val normal: List<PrologVector> = listOf(
        row("N01", utf8(RSS_ROOT), XML, NoDoctype),
        row("N02", utf8(DECLARATION + RSS_ROOT), XML, NoDoctype),
        row("N03", bom8 + utf8(DECLARATION + ATOM_ROOT), "application/atom+xml", NoDoctype),
        row("N04", utf16le(DECLARATION + RSS_ROOT), XML, NoDoctype),
        row("N05", utf16be(DECLARATION + RSS_ROOT), XML, NoDoctype),
        row("N06", utf16le(RSS_ROOT, mark = false), "text/xml; charset=utf-16le", NoDoctype),
        row("N07", utf8("  \n" + DECLARATION + RSS_ROOT), XML, NoDoctype),
        row("N08", utf8("<!-- c -->" + DECLARATION + RSS_ROOT), XML, NoDoctype),
        row("N09", utf8(DECLARATION + "<!-- c --><?xml-stylesheet type=\"text/xsl\" href=\"s.xsl\"?>" + RSS_ROOT), XML, NoDoctype),
        row("N10", utf8("<!-- <!DOCTYPE a> -->" + RSS_ROOT), XML, NoDoctype),
        row("N11", utf8("<?pi <!DOCTYPE a> ?>" + RSS_ROOT), XML, NoDoctype),
        row("N12", latin1("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><!-- é -->" + RSS_ROOT), "application/xml; charset=iso-8859-1", NoDoctype),
        row("N13", utf8(RSS_ROOT), "text/xml; charset=windows-1252", NoDoctype),
        row("N14", utf8(RSS_ROOT), "application/rss+xml; CHARSET=\"UTF-8\"", NoDoctype),
        row("N15", utf8("<?xml version=\"1.0\" encoding=\"Shift_JIS\"?><!-- ") + bytes(0x83, 0x5C) + utf8(" -->" + RSS_ROOT), XML, NoDoctype),
        row("N16", utf8(RSS_ROOT), null, NoDoctype),
        row("N17", ByteArray(0), XML, Empty),
        row("N18", utf8("{\"a\": 1}"), "application/json", NotMarkup),
        row("N19", utf8("<htmlx/>"), XML, NoDoctype),
        row("N20", utf8("&amp;" + DOCTYPE + RSS_ROOT), XML, NotMarkup),
        row("N21", utf8("junk" + DOCTYPE + RSS_ROOT), XML, NotMarkup),
        row("N22", utf8("<!---->" + RSS_ROOT), XML, NoDoctype),
        row("N23", utf8(RSS_ROOT), "text/html", NoDoctype),
        row("N24", utf8("<?xml version=\"1.0\"" + " ".repeat(4100) + " encoding=\"ibm037\"?>" + RSS_ROOT), XML, NoDoctype),
        row("N25", utf16le("<!-- 😀 -->" + RSS_ROOT), XML, NoDoctype),
        row("N26", utf8(RSS_ROOT), "application/xml; charset=euc-jp", NoDoctype),
        row("N27", utf8(RSS_ROOT), "application/xml; charset=gb18030", NoDoctype),
        row("N28", utf8("<!-- ") + bytes(0xE9, 0x96) + utf8(" -->" + RSS_ROOT), "application/xml; charset=windows-1252", NoDoctype),
        row("N29", utf8("<!-- ") + bytes(0xA4, 0xA4) + utf8(" -->" + RSS_ROOT), "application/xml; charset=big5", NoDoctype),
        row("N30", repeatBytes(bom8, 5) + utf8(RSS_ROOT), XML, NoDoctype),
        row("N31", rootAtOffset(65_534), XML, NoDoctype),
        row("N32", utf8("<!-- <!DOCTYPE html> -->" + RSS_ROOT), XML, NoDoctype),
    )

    val doctype: List<PrologVector> = listOf(
        row("D01", utf8(DOCTYPE + RSS_ROOT), XML, Doctype),
        row("D02", utf8(DECLARATION + DOCTYPE + RSS_ROOT), XML, Doctype),
        row("D03", utf8("<!DOCTYPE rss [<!ENTITY x \"y\">]>" + RSS_ROOT), XML, Doctype),
        row("D04", utf8("<!DOCTYPE rss [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>" + RSS_ROOT), XML, Doctype),
        row("D05", utf8(DECLARATION + "<!-- c --><?pi x?>" + DOCTYPE + RSS_ROOT), XML, Doctype),
        row("D06", utf8("  \r\t" + DOCTYPE + RSS_ROOT), XML, Doctype),
        row("D07", utf8("<!doctype rss>" + RSS_ROOT), XML, Doctype),
        row("D08", utf8("<!DocType rss>" + RSS_ROOT), XML, Doctype),
        row("D09", utf8("<!DOCTYPE>" + RSS_ROOT), XML, Doctype),
        row("D10", utf8("<!DOCTYPErss>" + RSS_ROOT), XML, Doctype),
        row("D11", utf8("<!ENTITY a \"b\">" + RSS_ROOT), XML, Doctype),
        row("D12", utf8("<!ELEMENT rss ANY>" + RSS_ROOT), XML, Doctype),
        row(
            "D13",
            utf8("<!DOCTYPE rss PUBLIC \"-//Netscape Communications//DTD RSS 0.91//EN\" \"http://my.netscape.com/publish/formats/rss-0.91.dtd\"><rss version=\"0.91\"><channel><title>t</title></channel></rss>"),
            XML, Doctype,
        ),
        row("D14", utf8("<!-- <!DOCTYPE a> -->" + DOCTYPE + RSS_ROOT), XML, Doctype),
        row("D15", bom8 + utf8(DOCTYPE + RSS_ROOT), XML, Doctype),
        row("D16", bom8 + bom8 + utf8(DECLARATION + DOCTYPE + RSS_ROOT), XML, Doctype),
        row("D17", bom8 + bom8 + utf8(DECLARATION + DOCTYPE + RSS_ROOT), "text/html", Doctype),
        row("D18", bomBe + utf8(DOCTYPE + RSS_ROOT), "text/xml", Doctype),
        row("D19", bomLe + utf8(DOCTYPE + RSS_ROOT), "text/html", Doctype),
        row("D20", bomLe + utf8(DOCTYPE + RSS_ROOT), null, Doctype),
        row("D21", bomBe + utf8(DOCTYPE + RSS_ROOT), "text/plain", Doctype),
        row("D22", utf16le(DECLARATION + DOCTYPE + RSS_ROOT), XML, Doctype),
        row("D23", utf16be(DECLARATION + DOCTYPE + RSS_ROOT), XML, Doctype),
        row("D24", utf16le(DOCTYPE + RSS_ROOT, mark = false), "text/xml; charset=utf-16le", Doctype),
        row("D25", utf16le(DOCTYPE + RSS_ROOT, mark = false), "text/xml; Charset=utf-16le", Doctype),
        row("D26", utf16be("<?xml version=\"1.0\" encoding=\"UTF-16\"?>" + DOCTYPE + RSS_ROOT, mark = false), XML, Doctype),
        row("D27", utf16le("<?xml version=\"1.0\" encoding=\"UTF-16\"?>" + DOCTYPE + RSS_ROOT, mark = false), XML, Doctype),
        row("D28", utf16le(DECLARATION + DOCTYPE + RSS_ROOT, mark = false), XML, Doctype),
        row("D29", utf16be(DECLARATION + DOCTYPE + RSS_ROOT, mark = false), XML, Doctype),
        row("D30", utf8("<?xml-stylesheet encoding=\"UTF-16\"?>" + DOCTYPE + RSS_ROOT), XML, Doctype),
        row("D31", bom8 + utf8("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + DOCTYPE + RSS_ROOT), XML, Doctype),
        row("D32", utf8("<!DOCTYPE\t\r\nrss>" + RSS_ROOT), XML, Doctype),
        row("D33", utf8(DECLARATION + "<!--" + "x".repeat(60_000) + "-->" + DOCTYPE + RSS_ROOT), XML, Doctype),
        row("D34", utf8("<?xml version=\"1.1\"?>" + DOCTYPE + RSS_ROOT), XML, Doctype),
        row("D35", utf8("<?xml version=\"1.0\" encoding=\"Shift_JIS\"?><!-- ") + bytes(0x83, 0x5C) + utf8(" -->" + DOCTYPE + RSS_ROOT), XML, Doctype),
        row("D36", utf16le("<!doctype rss>" + RSS_ROOT), XML, Doctype),
        row("D37", utf8("<?xml version=\"1.0\" encoding='utf-8'?>" + DOCTYPE + RSS_ROOT), XML, Doctype),
        row("D38", utf8("<!-- ") + bytes(0x8F) + utf8(" -->" + DOCTYPE + RSS_ROOT), "application/xml; charset=euc-jp", Doctype),
        row("D39", utf16le("<!-- 😀 -->" + DOCTYPE + RSS_ROOT), XML, Doctype),
        row("D40", utf8("<!DOCTYPE htmlx>" + RSS_ROOT), "text/html", Doctype),
    )

    val html: List<PrologVector> = listOf(
        row("H01", utf8("<!DOCTYPE html><html><body>x</body></html>"), "text/html", HtmlDocument),
        row("H02", utf8("<!doctype html><html></html>"), "text/html; charset=utf-8", HtmlDocument),
        row("H03", utf8("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\"><html></html>"), "text/html", HtmlDocument),
        row(
            "H04",
            utf8("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\"></html>"),
            "text/html", HtmlDocument,
        ),
        row("H05", utf8("<html><body>x</body></html>"), "text/html", HtmlDocument),
        row("H06", utf8(DECLARATION + "<HTML></HTML>"), "text/html", HtmlDocument),
        row("H07", bom8 + utf8("<!DOCTYPE html><html></html>"), "text/html", HtmlDocument),
        row("H08", utf16le("<!DOCTYPE html><html></html>"), "text/html", HtmlDocument),
        row("H09", utf8("<!DOCTYPE html [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><html></html>"), "text/html", HtmlDocument),
        row("H10", utf8("<!-- c --><!DOCTYPE html><html></html>"), "text/html", HtmlDocument),
        row("H11", utf8("<html><head><title>502 Bad Gateway</title></head><body><h1>502</h1></body></html>"), "text/html", HtmlDocument),
        row("H12", utf8("<!DOCTYPE   HtMl   ><html></html>"), "text/html", HtmlDocument),
    )

    val malformed: List<PrologVector> = listOf(
        row("M01", utf8("<!-- a -- b -->" + RSS_ROOT), XML, IllFormed),
        row("M02", utf8("<!-- a --->" + DOCTYPE + RSS_ROOT), XML, IllFormed),
        row("M03", utf8("<!-- a ----->" + DOCTYPE + RSS_ROOT), XML, IllFormed),
        row("M04", utf8("<!-- " + DOCTYPE), XML, IllFormed),
        row("M05", utf8("<?pi " + DOCTYPE), XML, IllFormed),
        row("M06", utf8("<!-- a --- ><?xml version=\"1.0\"?><!----><!--><!DOCTYPE rss SYSTEM \"x\">"), XML, IllFormed),
        row("M07", utf8("<!-->" + DOCTYPE + RSS_ROOT), XML, IllFormed),
        row("M08", utf8("<!--->" + DOCTYPE + RSS_ROOT), XML, IllFormed),
        row("M09", utf16le("<!-- a -- b -->" + RSS_ROOT), XML, IllFormed),
        row("M10", utf8("<?xml version=\"1.0\"" + RSS_ROOT), XML, IllFormed),
    )

    val undecodable: List<PrologVector> = listOf(
        row("U01", utf32be(DOCTYPE + RSS_ROOT), "application/xml; charset=utf-32", Undecodable(WideOrEbcdicPrefix)),
        row("U02", utf32le(RSS_ROOT), XML, Undecodable(WideOrEbcdicPrefix)),
        row("U03", bytes(0x00, 0x00, 0xFE, 0xFF) + utf32be(DOCTYPE + RSS_ROOT), "application/xml; charset=utf-32", Undecodable(WideOrEbcdicPrefix)),
        row("U04", bytes(0xFF, 0xFE, 0x00, 0x00) + utf32le(RSS_ROOT), "application/xml; charset=utf-32", Undecodable(WideOrEbcdicPrefix)),
        // `<?xm` in EBCDIC, then EBCDIC bytes of the rest of a declaration naming cp1047 and a DOCTYPE.
        row("U05", bytes(0x4C, 0x6F, 0xA7, 0x94, 0x93, 0x40, 0xA5, 0x85, 0x99, 0xA2, 0x89, 0x96, 0x95, 0x7E, 0x7F, 0xF1, 0x4B, 0xF0, 0x7F, 0x6F, 0x6E, 0x4C, 0x5A, 0xC4, 0xD6, 0xC3, 0xE3, 0xE8, 0xD7, 0xC5, 0x40, 0x99, 0xA2, 0xA2, 0x6E), XML, Undecodable(WideOrEbcdicPrefix)),
        // `<!DOCTYPE rss>` in EBCDIC (IBM037), no declaration.
        row("U06", bytes(0x4C, 0x5A, 0xC4, 0xD6, 0xC3, 0xE3, 0xE8, 0xD7, 0xC5, 0x40, 0x99, 0xA2, 0xA2, 0x6E), "application/xml; charset=ibm037", Undecodable(UnlistedEncodingName)),
        row("U07", utf8("+ADw-!DOCTYPE rss SYSTEM \"x\"+AD4-" + RSS_ROOT), "application/xml; charset=utf-7", Undecodable(UnlistedEncodingName)),
        row("U08", utf8("<?xml version=\"1.0\" encoding=\"UTF-7\"?>" + RSS_ROOT), XML, Undecodable(UnlistedEncodingName)),
        row("U09", utf8("<?xml version=\"1.0\" encoding=\"ISO-2022-JP\"?>" + DOCTYPE + RSS_ROOT), XML, Undecodable(UnlistedEncodingName)),
        row("U10", utf8("<?xml version=\"1.0\" encoding=\"ibm037\"?>" + DOCTYPE + RSS_ROOT), XML, Undecodable(UnlistedEncodingName)),
        row("U11", utf8(RSS_ROOT), "application/xml; charset=bogus-9", Undecodable(UnlistedEncodingName)),
        row("U12", utf8(RSS_ROOT), "application/xml; charset=utf-32", Undecodable(UnlistedEncodingName)),
        row("U13", utf8(RSS_ROOT), "application/xml; charset=utf-8; charset=ibm037", Undecodable(UnlistedEncodingName)),
        row("U14", utf8(RSS_ROOT), "application/xml; foo=\"charset=ibm037\"", Undecodable(UnlistedEncodingName)),
        row("U15", utf8("<?xml-stylesheet encoding=\"UTF-7\"?>" + DOCTYPE + RSS_ROOT), XML, Undecodable(UnlistedEncodingName)),
        row("U16", bom8 + utf8("<?xml version=\"1.0\" encoding=\"UTF-32\"?>" + RSS_ROOT), XML, Undecodable(UnlistedEncodingName)),
        row("U17", utf8("<!--" + "x".repeat(70_000) + "-->" + RSS_ROOT), XML, Undecodable(PrologTooLong)),
        row("U18", utf8("<!--" + "x".repeat(70_000) + "-->" + DOCTYPE + RSS_ROOT), XML, Undecodable(PrologTooLong)),
        row("U19", rootAtOffset(65_535), XML, Undecodable(PrologTooLong)),
        row("U20", utf8("<!--" + "x".repeat(70_000)), XML, Undecodable(PrologTooLong)),
        row("U21", utf16le("<!--" + "x".repeat(40_000) + "-->" + RSS_ROOT), XML, Undecodable(PrologTooLong)),
        row("U22", repeatBytes(bomBe, 35_000) + utf8(RSS_ROOT), XML, Undecodable(PrologTooLong)),
        row("U23", utf16le("<!-- \uD800-->x<!-- -->" + DOCTYPE + RSS_ROOT), XML, Undecodable(MalformedSequence)),
        row("U24", utf16be("<!-- \uD800-->x<!-- -->" + DOCTYPE + RSS_ROOT), XML, Undecodable(MalformedSequence)),
        row("U25", utf16le("<!-- \uDC00 -->" + RSS_ROOT), XML, Undecodable(MalformedSequence)),
        row("U26", utf16le("<!-- \uD83Da -->" + RSS_ROOT), XML, Undecodable(MalformedSequence)),
        row("U27", utf8("<!-- ") + bytes(0x8F) + utf8("-->x<!-- -->" + DOCTYPE + RSS_ROOT), "application/xml; charset=euc-jp", Undecodable(MalformedSequence)),
        row("U28", utf8("<?xml version=\"1.0\" encoding=\"EUC-JP\"?><!-- ") + bytes(0x8F) + utf8("-->" + RSS_ROOT), XML, Undecodable(MalformedSequence)),
        row("U29", utf8("<!-- ") + bytes(0xA4, 0xA2) + utf8(" -->" + RSS_ROOT), "application/xml; charset=euc-jp", Undecodable(MalformedSequence)),
        row("U30", utf8("<!-- ") + bytes(0xC4, 0xE3) + utf8(" -->" + RSS_ROOT), "application/xml; charset=gb18030", Undecodable(MalformedSequence)),
        row("U31", utf8("<?xml version=\"1.0\" encoding=\"GB18030\"?><!-- ") + bytes(0x81, 0x30) + utf8("-->x<!-- -->" + DOCTYPE + RSS_ROOT), XML, Undecodable(MalformedSequence)),
    )

    val extra: List<PrologVector> = listOf(
        row("E01", ByteArray(0), XML, Empty),
        row("E02", utf8(" "), XML, Empty),
        row("E03", bom8, XML, Empty),
        row("E04", bomLe, XML, Empty),
        row("E05", utf8("  "), XML, Empty),
        row("E06", utf8("hello"), XML, NotMarkup),
        row("E07", bom8 + utf8("{\"a\": 1}"), XML, NotMarkup),
        row("E08", utf8("x<rss/>"), XML, NotMarkup),
        row("E09", utf8("<"), XML, NoDoctype),
        row("E10", utf8(" ".repeat(70_000)), XML, Undecodable(PrologTooLong)),
    )

    /** Every row, in catalogue order. */
    val all: List<PrologVector> = normal + doctype + html + malformed + undecodable + extra
}
