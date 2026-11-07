package com.spartanlabs.testing.deterministic.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.bytes
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.latin1
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.utf16be
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.utf16le
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.utf8
import com.spartanlabs.testing.support.webtools.feeds.FeedExpectations
import com.spartanlabs.testing.support.webtools.feeds.FeedFixtures
import com.spartanlabs.testing.support.webtools.feeds.ParserFactory
import com.spartanlabs.webtools.feeds.DIGEST_PREFIX
import com.spartanlabs.webtools.feeds.Feed
import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedInput
import com.spartanlabs.webtools.feeds.FeedLimit
import com.spartanlabs.webtools.feeds.FeedLimits
import com.spartanlabs.webtools.feeds.FeedParser
import com.spartanlabs.webtools.feeds.NotAFeedReason
import com.spartanlabs.webtools.feeds.digestId
import com.spartanlabs.webtools.feeds.isCanonicalLink
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 4a - the conformance contract of a `FeedParser`: the seven fixtures map to their expected `Feed`; the mapping rules of
 * the model (ids, links, content, authors, dates, categories, enclosures, `xml:base`); the encodings; Rome-style repair; the
 * failure classification; the limits as data; and the port's own promises. Abstract: a parser behind the port adds a
 * `ParserFactory` and a three-line subclass naming it.
 */
abstract class FeedParserConformanceContract {

    /** The factory the parser under test is built through; a public no-argument class. */
    protected abstract val factory: Class<out ParserFactory>

    private fun parser(limits: FeedLimits = FeedLimits.Defaults): FeedParser = factory.getDeclaredConstructor().newInstance().create(limits)

    private fun parse(input: FeedInput, limits: FeedLimits = FeedLimits.Defaults): Result<Feed> = parser(limits).parse(input)
    private fun parse(text: String, limits: FeedLimits = FeedLimits.Defaults, contentType: String? = FeedFixtures.CONTENT_TYPE): Result<Feed> =
        parse(FeedFixtures.input(text, contentType), limits)

    private fun feed(text: String, limits: FeedLimits = FeedLimits.Defaults): Feed = parse(text, limits).getOrThrow()
    private fun entry(text: String, limits: FeedLimits = FeedLimits.Defaults) = feed(text, limits).entries.single()

    private fun rss(items: String, channel: String = "<title>t</title>", namespaces: String = ""): String =
        //language=XML
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"$namespaces><channel>$channel$items</channel></rss>"
    private fun item(body: String) = "<item>$body</item>"
    private fun atom(entries: String, head: String = "<title>t</title>", attributes: String = ""): String =
        //language=XML
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><feed xmlns=\"http://www.w3.org/2005/Atom\"$attributes>$head$entries</feed>"
    private fun atomEntry(body: String, attributes: String = "") = "<entry$attributes><id>urn:e</id>$body</entry>"

    private val dc = " xmlns:dc=\"http://purl.org/dc/elements/1.1/\""
    private val contentNs = " xmlns:content=\"http://purl.org/rss/1.0/modules/content/\""

    //region fixtures
    private fun fixtureMatches(name: String) {
        val parsed = parse(FeedFixtures.fixture(name)).getOrThrow()
        assertEquals(FeedExpectations.byName.getValue(name), parsed, name)
    }

    @Test fun `rss 0 90`() = fixtureMatches("rss090.rdf")
    @Test fun `rss 0 91 userland`() = fixtureMatches("rss091u.xml")
    @Test fun `rss 0 92`() = fixtureMatches("rss092.xml")
    @Test fun `rss 2 0`() = fixtureMatches("rss20.xml")
    @Test fun `rss 1 0`() = fixtureMatches("rss10.rdf")
    @Test fun `atom 0 3`() = fixtureMatches("atom03.xml")
    @Test fun `atom 1 0`() = fixtureMatches("atom10.xml")
    //endregion

    //region mapping
    @Test
    fun `an rss guid is the id even when it is not a permalink`() {
        assertEquals("urn:fixture:rss20:1", parse(FeedFixtures.fixture("rss20.xml")).getOrThrow().entries[0].id)
        val e = entry(rss(item("<guid isPermaLink=\"false\">g-7</guid><link>https://x.test/p</link>")))
        assertEquals("g-7", e.id)
        assertEquals("https://x.test/p", e.link)
    }

    @Test
    fun `an item with neither guid nor link gets a digest id that is the same on every parse`() {
        val document = rss(item("<title>Hello</title><pubDate>Fri, 06 Nov 2026 04:25:27 GMT</pubDate>"))
        val first = entry(document)
        assertEquals(digestId("Hello", null, null, null, FeedExpectations.T1), first.id)
        assertEquals(first.id, entry(document).id)
        assertTrue(first.id.startsWith(DIGEST_PREFIX))
    }

    @Test
    fun `a permalink guid that is not a url is not the entry link`() {
        val e = entry(rss(item("<guid>g-7</guid><title>t</title>")))
        assertEquals("g-7", e.id)
        assertNull(e.link)
        val absolute = entry(rss(item("<guid>https://x.test/p/1</guid>")))
        assertEquals("https://x.test/p/1", absolute.id)
        assertEquals("https://x.test/p/1", absolute.link)
    }

    @Test
    fun `an atom feed's link is its alternate and never its self link`() {
        assertEquals("https://fixtures.example.test/atom10/", parse(FeedFixtures.fixture("atom10.xml")).getOrThrow().link)
        val selfOnly = feed(atom("", head = "<title>t</title><link rel=\"self\" href=\"https://x.test/feed.xml\"/>"))
        assertNull(selfOnly.link)
        val noRel = feed(atom("", head = "<title>t</title><link rel=\"self\" href=\"https://x.test/feed.xml\"/><link href=\"https://x.test/\"/>"))
        assertEquals("https://x.test/", noRel.link)
    }

    @Test
    fun `an atom entry with only a self link has no link`() {
        val e = entry(atom(atomEntry("<title>t</title><link rel=\"self\" href=\"https://x.test/e/1\"/>")))
        assertNull(e.link)
        assertEquals("urn:e", e.id)
    }

    @Test
    fun `content encoded is the content and the description the summary`() {
        val e = parse(FeedFixtures.fixture("rss20.xml")).getOrThrow().entries[0]
        assertEquals("Summary one", e.summary)
        assertEquals("<p>Content one</p>", e.content)
        val built = entry(rss(item("<guid>g</guid><description>S</description><content:encoded>&lt;b&gt;C&lt;/b&gt;</content:encoded>"), namespaces = contentNs))
        assertEquals("S", built.summary)
        assertEquals("<b>C</b>", built.content)
    }

    @Test
    fun `dc creator wins over author and dc date over pubDate`() {
        val e = entry(rss(item("<guid>g</guid><author>a@x.test (A)</author><dc:creator>Creator</dc:creator><pubDate>Fri, 06 Nov 2026 04:25:27 GMT</pubDate><dc:date>2026-11-05T18:00:00Z</dc:date>"), namespaces = dc))
        assertEquals("Creator", e.author)
        assertEquals(FeedExpectations.T2, e.publishedAtEpochMillis)
        val plain = entry(rss(item("<guid>g</guid><author>a@x.test (A)</author><pubDate>Fri, 06 Nov 2026 04:25:27 GMT</pubDate>")))
        assertEquals("a@x.test (A)", plain.author)
        assertEquals(FeedExpectations.T1, plain.publishedAtEpochMillis)
    }

    @Test
    fun `dc subject elements are the categories of an rss 1 0 entry`() {
        val parsed = parse(FeedFixtures.fixture("rss10.rdf")).getOrThrow()
        assertEquals(listOf("Tech", "News"), parsed.entries[0].categories)
        assertEquals(emptyList(), parsed.entries[1].categories)
    }

    @Test
    fun `an atom category is its term, else its label`() {
        assertEquals(listOf("tech", "News"), parse(FeedFixtures.fixture("atom10.xml")).getOrThrow().entries[0].categories)
        val e = entry(atom(atomEntry("<category term=\"a\" label=\"A\"/><category label=\"B\"/><category scheme=\"https://x.test/s\"/>")))
        assertEquals(listOf("a", "B"), e.categories)
    }

    @Test
    fun `enclosures of rss and atom, with a length of zero, negative or absent unknown`() {
        val rss092 = parse(FeedFixtures.fixture("rss092.xml")).getOrThrow()
        assertEquals(12_345L, rss092.entries[0].enclosures[0].lengthBytes)
        assertNull(rss092.entries[1].enclosures[0].lengthBytes)
        val e = entry(
            rss(
                item(
                    "<guid>g</guid>" +
                        "<enclosure url=\"https://cdn.test/a.mp3\" length=\"-5\" type=\"audio/mpeg\"/>" +
                        "<enclosure url=\"https://cdn.test/b.mp3\" type=\"audio/mpeg\"/>" +
                        "<enclosure url=\"https://cdn.test/c.mp3\" length=\"abc\"/>",
                ),
            ),
        )
        assertEquals(listOf("https://cdn.test/a.mp3", "https://cdn.test/b.mp3", "https://cdn.test/c.mp3"), e.enclosures.map { it.url })
        assertTrue(e.enclosures.all { it.lengthBytes == null })
        assertEquals("audio/mpeg", e.enclosures[0].mimeType)
        assertNull(e.enclosures[2].mimeType)
        val atom = parse(FeedFixtures.fixture("atom10.xml")).getOrThrow().entries[0].enclosures.single()
        assertEquals("https://cdn.example.test/atom10/one.mp3", atom.url)
        assertEquals(4_096L, atom.lengthBytes)
        assertEquals("audio/mpeg", atom.mimeType)
    }

    @Test
    fun `a relative link resolves against the base URL`() {
        val e = entry(rss(item("<guid>g</guid><link>/post/1?x#y</link>")))
        assertEquals("https://fixtures.example.test/post/1?x#y", e.link)
        val sibling = entry(rss(item("<guid>g</guid><link>post/1</link>")))
        assertEquals("https://fixtures.example.test/post/1", sibling.link)
        val withoutBase = parse(FeedFixtures.input(rss(item("<guid>g</guid><link>/post/1</link>")), base = null)).getOrThrow().entries.single()
        assertNull(withoutBase.link)
        assertEquals("g", withoutBase.id)
    }

    @Test
    fun `an atom feed's and entry's xml base apply in turn`() {
        val document = atom(
            atomEntry("<title>t</title><link href=\"post-1.html#c\"/>", attributes = " xml:base=\"2026/\""),
            head = "<title>t</title><link rel=\"alternate\" href=\".\"/>",
            attributes = " xml:base=\"/blog/\"",
        )
        val parsed = feed(document)
        assertEquals("https://fixtures.example.test/blog/", parsed.link)
        assertEquals("https://fixtures.example.test/blog/2026/post-1.html#c", parsed.entries.single().link)
        val broken = feed(atom(atomEntry("<link href=\"post-1.html\"/>"), attributes = " xml:base=\"ftp://other.test/\""))
        assertNull(broken.entries.single().link)
    }

    @Test
    fun `a javascript link is null and the entry is kept`() {
        val e = entry(rss(item("<guid>g</guid><title>t</title><link>javascript:alert(1)</link>")))
        assertNull(e.link)
        assertEquals("g", e.id)
        assertEquals("t", e.title)
        // Without a guid Rome reports the link text as the item's uri, so it serves as the (opaque) id; the link stays null.
        val onlyLink = entry(rss(item("<title>t</title><link>javascript:alert(1)</link>")))
        assertNull(onlyLink.link)
        assertTrue(onlyLink.id.isNotBlank())
        assertEquals("t", onlyLink.title)
    }

    @Test
    fun `dates, rfc 822 and w3c forms parse, unparseable and out-of-range years are null`() {
        assertEquals(FeedExpectations.T1, entry(rss(item("<guid>g</guid><pubDate>Fri, 06 Nov 2026 04:25:27 GMT</pubDate>"))).publishedAtEpochMillis)
        assertEquals(FeedExpectations.T1, entry(rss(item("<guid>g</guid><pubDate>Fri, 06 Nov 2026 06:25:27 +0200</pubDate>"))).publishedAtEpochMillis)
        val atomDates = entry(atom(atomEntry("<published>2026-11-06T04:25:27Z</published><updated>2026-11-05T18:00:00Z</updated>")))
        assertEquals(FeedExpectations.T1, atomDates.publishedAtEpochMillis)
        assertEquals(FeedExpectations.T2, atomDates.updatedAtEpochMillis)
        assertNull(entry(rss(item("<guid>g</guid><pubDate>not a date</pubDate>"))).publishedAtEpochMillis)
        assertNull(entry(rss(item("<guid>g</guid><pubDate>20030610</pubDate>"))).publishedAtEpochMillis, "a digit string Rome reads as year 20030610")
        assertNull(entry(atom(atomEntry("<published>20000-01-01T00:00:00Z</published>"))).publishedAtEpochMillis)
        assertNull(entry(rss(item("<guid>g</guid>"))).publishedAtEpochMillis)
    }

    @Test
    fun `an absent title, summary and author are null, never empty`() {
        val e = entry(rss(item("<guid>g</guid>")))
        assertNull(e.title)
        assertNull(e.summary)
        assertNull(e.content)
        assertNull(e.author)
        val blank = entry(rss(item("<guid>g</guid><title>   </title><description></description><author>\t</author>")))
        assertNull(blank.title)
        assertNull(blank.summary)
        assertNull(blank.author)
        val channel = feed(rss("", channel = "<title></title>"))
        assertNull(channel.title)
        assertNull(channel.description)
    }
    //endregion

    //region encodings
    private val accented = rss(item("<guid>g</guid><title>café</title>"))

    @Test
    fun `utf-8 with and without a byte-order mark`() {
        val plain = parse(FeedFixtures.fixture("rss20.xml")).getOrThrow()
        val marked = parse(FeedFixtures.input(bytes(0xEF, 0xBB, 0xBF) + FeedFixtures.bytes("rss20.xml"))).getOrThrow()
        assertEquals(plain, marked)
        assertEquals("café", entry(accented).title)
    }

    @Test
    fun `utf-16 little-endian with a mark, big-endian with a mark, and little-endian without one`() {
        val text = FeedFixtures.bytes("rss20.xml").decodeToString().replace("encoding=\"UTF-8\"", "encoding=\"UTF-16\"")
        val expected = FeedExpectations.rss20
        assertEquals(expected, parse(FeedFixtures.input(utf16le(text))).getOrThrow())
        assertEquals(expected, parse(FeedFixtures.input(utf16be(text))).getOrThrow())
        assertEquals(expected, parse(FeedFixtures.input(utf16le(text, mark = false))).getOrThrow())
        assertEquals("café", parse(FeedFixtures.input(utf16le(accented.replace("encoding=\"UTF-8\"", "encoding=\"UTF-16\"")))).getOrThrow().entries.single().title)
    }

    @Test
    fun `iso-8859-1 by declaration, behind a leading newline and by header charset`() {
        val declared = latin1(accented.replace("encoding=\"UTF-8\"", "encoding=\"ISO-8859-1\""))
        assertEquals("café", parse(FeedFixtures.input(declared)).getOrThrow().entries.single().title)
        assertEquals("café", parse(FeedFixtures.input(latin1("\n  ") + declared)).getOrThrow().entries.single().title)
        val byHeader = latin1(accented.substringAfter("?>"))
        assertEquals("café", parse(FeedFixtures.input(byHeader, contentType = "text/xml; charset=iso-8859-1")).getOrThrow().entries.single().title)
    }

    @Test
    fun `a header charset the JDK does not know is recovered`() {
        // iso-8859-12 is on the scan's allowlist by its shape and is not a charset the JDK knows.
        val parsed = parse(FeedFixtures.input(utf8(accented), contentType = "text/xml; charset=iso-8859-12")).getOrThrow()
        assertEquals("café", parsed.entries.single().title)
    }

    @Test
    fun `an xml feed served as text html parses`() {
        assertEquals(FeedExpectations.rss20, parse(FeedFixtures.fixture("rss20.xml", contentType = "text/html")).getOrThrow())
        assertEquals(FeedExpectations.atom10, parse(FeedFixtures.fixture("atom10.xml", contentType = null)).getOrThrow())
    }
    //endregion

    //region repair
    @Test
    fun `bare ampersands and nbsp are repaired, in CDATA too`() {
        val e = entry(rss(item("<guid>g</guid><title>Q & A &</title><description><![CDATA[x &nbsp; y & z]]></description><author>a&nbsp;b</author>")))
        assertEquals("Q & A &", e.title)
        assertEquals("x &#160; y & z", e.summary)
        assertEquals("a b", e.author)
    }

    @Test
    fun `an unknown named entity is Malformed`() {
        assertIs<FeedFailure.Malformed>(parse(rss(item("<guid>g</guid><title>a &bogus; b</title>"))).exceptionOrNull())
    }
    //endregion

    //region failures
    @Test
    fun `truncated, mismatched, two-root and invalid-character documents are Malformed`() {
        val whole = rss(item("<guid>g</guid>"))
        assertIs<FeedFailure.Malformed>(parse(whole.substring(0, whole.length - 10)).exceptionOrNull(), "truncated")
        assertIs<FeedFailure.Malformed>(parse("<rss version=\"2.0\"><channel><title>t</channel></rss>").exceptionOrNull(), "mismatched")
        assertIs<FeedFailure.Malformed>(parse(whole + whole.substringAfter("?>")).exceptionOrNull(), "two roots")
        assertIs<FeedFailure.Malformed>(parse(rss(item("<guid>g</guid><title>a\u0001b</title>"))).exceptionOrNull(), "U+0001")
        assertIs<FeedFailure.Malformed>(parse(rss(item("<guid>g</guid><title>a&#1;b</title>"))).exceptionOrNull(), "&#1;")
    }

    @Test
    fun `an unknown root, rss version 3 0 and atom without its namespace are NotAFeed UnknownFormat`() {
        for (document in listOf("<foo><bar/></foo>", "<rss version=\"3.0\"><channel><title>t</title></channel></rss>", "<feed><title>t</title></feed>")) {
            val failure = assertIs<FeedFailure.NotAFeed>(parse(document).exceptionOrNull(), document)
            assertEquals(NotAFeedReason.UnknownFormat, failure.reason, document)
        }
    }

    @Test
    fun `html, json, plain text, an empty body and a body of only a byte-order mark are NotAFeed with their reasons`() {
        fun reason(document: ByteArray, contentType: String?) = assertIs<FeedFailure.NotAFeed>(parse(FeedFixtures.input(document, contentType)).exceptionOrNull()).reason
        assertEquals(NotAFeedReason.Html, reason(utf8("<!DOCTYPE html><html><body>x</body></html>"), "text/html"))
        assertEquals(NotAFeedReason.Html, reason(utf8("<html><body>x</body></html>"), "text/html"))
        assertEquals(NotAFeedReason.NotXml, reason(utf8("{\"a\": 1}"), "application/json"))
        assertEquals(NotAFeedReason.NotXml, reason(utf8("plain text"), "text/plain"))
        assertEquals(NotAFeedReason.Empty, reason(ByteArray(0), "application/xml"))
        assertEquals(NotAFeedReason.Empty, reason(bytes(0xEF, 0xBB, 0xBF), "application/xml"))
    }
    //endregion

    //region limits as data
    @Test
    fun `maxEntries at the limit and over it, with a tightened limit of three`() {
        val three = FeedLimits { maxEntries = 3 }
        fun items(n: Int) = rss((1..n).joinToString("") { item("<guid>g$it</guid>") })
        assertEquals(3, feed(items(3), three).entries.size)
        val failure = parse(items(4), three).exceptionOrNull()
        assertEquals(FeedLimit.Entries, assertIs<FeedFailure.LimitExceeded>(failure).limit)
        assertEquals(4, feed(items(4)).entries.size)
    }

    @Test
    fun `text, id and link limits truncate, fall back or drop`() {
        val limits = FeedLimits { maxTextChars = 16; maxIdChars = 50; maxLinkChars = 32 }
        val e = entry(rss(item("<guid>${"g".repeat(51)}</guid><link>https://x.test/${"p".repeat(40)}</link><title>${"t".repeat(20)}</title>")), limits)
        assertEquals("t".repeat(16), e.title)
        assertNull(e.link, "a 55-character link is over the cap of 32")
        assertTrue(e.id.startsWith(DIGEST_PREFIX), "a 51-character guid and a 55-character link both fall through to the digest")
        val linkAsId = entry(rss(item("<guid>${"g".repeat(51)}</guid><link>https://x.test/${"p".repeat(30)}</link>")), limits)
        assertNull(linkAsId.link, "44 characters is over the link cap")
        assertEquals("https://x.test/${"p".repeat(30)}", linkAsId.id, "but within the id cap, so the link as written is the id")
        val fitting = entry(rss(item("<guid>${"g".repeat(50)}</guid><link>https://x.test/p</link>")), limits)
        assertEquals("g".repeat(50), fitting.id)
        assertEquals("https://x.test/p", fitting.link)
        val linkId = entry(rss(item("<guid>${"g".repeat(51)}</guid><link>https://x.test/p</link>")), limits)
        assertEquals("https://x.test/p", linkId.id)
    }

    @Test
    fun `category and enclosure caps`() {
        val limits = FeedLimits { maxCategoriesPerEntry = 1; maxEnclosuresPerEntry = 1 }
        val document = rss(item("<guid>g</guid><category>a</category><category>b</category><enclosure url=\"https://cdn.test/a\"/><enclosure url=\"https://cdn.test/b\"/>"))
        val capped = entry(document, limits)
        assertEquals(listOf("a"), capped.categories)
        assertEquals(listOf("https://cdn.test/a"), capped.enclosures.map { it.url })
        val open = entry(document)
        assertEquals(listOf("a", "b"), open.categories)
        assertEquals(2, open.enclosures.size)
    }
    //endregion

    //region the port
    @Test
    fun `the input document is not modified`() {
        for (name in FeedFixtures.names) {
            val input = FeedFixtures.fixture(name)
            parse(input).getOrThrow()
            assertContentEquals(FeedFixtures.bytes(name), input.document(), name)
        }
    }

    @Test
    fun `the same input gives equal feeds`() {
        val p = parser()
        for (name in FeedFixtures.names) {
            val input = FeedFixtures.fixture(name)
            assertEquals(p.parse(input).getOrThrow(), p.parse(input).getOrThrow(), name)
        }
    }

    @Test
    fun `an empty channel and an atom feed without entries are valid with no entries`() {
        val empty = feed("<rss version=\"2.0\"><channel/></rss>")
        assertEquals(emptyList(), empty.entries)
        assertNull(empty.title)
        val atomFeed = feed(atom(""))
        assertEquals(emptyList(), atomFeed.entries)
        assertEquals("t", atomFeed.title)
    }

    @Test
    fun `every parsed entry id is non-blank and every link canonical across all fixtures`() {
        for (name in FeedFixtures.names) {
            val parsed = parse(FeedFixtures.fixture(name)).getOrThrow()
            parsed.link?.let { assertTrue(isCanonicalLink(it), "$name feed link") }
            assertTrue(parsed.entries.isNotEmpty(), name)
            for (e in parsed.entries) {
                assertTrue(e.id.isNotBlank(), "$name id")
                e.link?.let { assertTrue(isCanonicalLink(it), "$name entry link") }
                for (enclosure in e.enclosures) assertTrue(isCanonicalLink(enclosure.url), "$name enclosure")
            }
        }
    }
    //endregion
}
