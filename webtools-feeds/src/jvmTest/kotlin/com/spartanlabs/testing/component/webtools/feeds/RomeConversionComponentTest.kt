package com.spartanlabs.testing.component.webtools.feeds

import com.rometools.rome.feed.WireFeed
import com.rometools.rome.feed.atom.Entry
import com.rometools.rome.feed.module.DCModuleImpl
import com.rometools.rome.feed.module.DCSubjectImpl
import com.rometools.rome.feed.rss.Channel
import com.rometools.rome.feed.synd.SyndCategoryImpl
import com.rometools.rome.feed.synd.SyndContentImpl
import com.rometools.rome.feed.synd.SyndEnclosureImpl
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndEntryImpl
import com.rometools.rome.feed.synd.SyndFeedImpl
import com.rometools.rome.feed.synd.SyndLinkImpl
import com.spartanlabs.webtools.feeds.DIGEST_PREFIX
import com.spartanlabs.webtools.feeds.Feed
import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedLimit
import com.spartanlabs.webtools.feeds.FeedLimits
import com.spartanlabs.webtools.feeds.MAX_EPOCH_MILLIS
import com.spartanlabs.webtools.feeds.MIN_EPOCH_MILLIS
import com.spartanlabs.webtools.feeds.RomeRead
import com.spartanlabs.webtools.feeds.digestId
import com.spartanlabs.webtools.feeds.toFeed
import com.spartanlabs.webtools.http.Url
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 2 - the bean-to-model conversion over Rome beans built in code, under `FeedLimits`: the entry cap, the text rule on
 * the channel fields, the feed link per format, the id rule, the guid-derived link, relative links and the `xml:base` chain,
 * the date range, categories, enclosures, summary and content, the digest's enclosure input, the entry with nothing, and
 * hostile strings.
 */
class RomeConversionComponentTest {

    private val base = Url.parse("https://example.org/feeds/a.xml").getOrThrow()
    private val defaults = FeedLimits.Defaults

    private fun rssWire(): WireFeed = Channel("rss_2.0")

    private fun synd(type: String = "rss_2.0", vararg entries: SyndEntry): SyndFeedImpl = SyndFeedImpl().apply {
        feedType = type
        this.entries = entries.toList()
    }

    private fun entry(configure: SyndEntryImpl.() -> Unit = {}): SyndEntryImpl = SyndEntryImpl().apply(configure)

    private fun content(value: String?): SyndContentImpl = SyndContentImpl().apply { this.value = value }

    private fun convert(feed: SyndFeedImpl, limits: FeedLimits = defaults, wire: WireFeed = rssWire(), baseUrl: Url? = base): Result<Feed> =
        RomeRead(wire, feed).toFeed(limits, baseUrl)

    private fun first(feed: SyndFeedImpl, limits: FeedLimits = defaults, wire: WireFeed = rssWire(), baseUrl: Url? = base) =
        convert(feed, limits, wire, baseUrl).getOrThrow().entries.single()

    @Test
    fun `more entries than maxEntries fail with LimitExceeded Entries before any is converted`() {
        val three = synd("rss_2.0", entry { uri = "a" }, entry { uri = "b" }, entry { uri = "c" })
        val failure = convert(three, FeedLimits { maxEntries = 2 }).exceptionOrNull()
        assertEquals(FeedLimit.Entries, assertIs<FeedFailure.LimitExceeded>(failure).limit)
        assertEquals(3, convert(three, FeedLimits { maxEntries = 3 }).getOrThrow().entries.size)
    }

    @Test
    fun `title, description and author are trimmed, blank is null and text is cut at maxTextChars without splitting a surrogate pair`() {
        val limits = FeedLimits { maxTextChars = 16 }
        val feed = synd("rss_2.0", entry { uri = "e"; author = "  Author  "; title = "   " }).apply {
            title = "  " + "a".repeat(15) + "😀"
            description = " \t\n"
        }
        val converted = convert(feed, limits).getOrThrow()
        assertEquals("a".repeat(15), converted.title)
        assertNull(converted.description)
        assertEquals("Author", converted.entries.single().author)
        assertNull(converted.entries.single().title)
    }

    @Test
    fun `an atom feed's link is its first alternate and an rss feed's link is getLink`() {
        val atom = synd("atom_1.0").apply {
            link = "https://example.org/feed.xml"
            links = listOf(
                SyndLinkImpl().apply { rel = "self"; href = "https://example.org/feed.xml" },
                SyndLinkImpl().apply { rel = "alternate"; href = "https://example.org/" },
                SyndLinkImpl().apply { href = "https://example.org/second" },
            )
        }
        assertEquals("https://example.org/", convert(atom, wire = com.rometools.rome.feed.atom.Feed("atom_1.0")).getOrThrow().link)
        val noAlternate = synd("atom_1.0").apply { links = listOf(SyndLinkImpl().apply { rel = "self"; href = "https://example.org/feed.xml" }) }
        assertNull(convert(noAlternate).getOrThrow().link)
        val rss = synd("rss_2.0").apply { link = "https://example.org/rss/" }
        assertEquals("https://example.org/rss/", convert(rss).getOrThrow().link)
        assertNull(convert(synd("rss_2.0").apply { link = "javascript:alert(1)" }).getOrThrow().link)
    }

    @Test
    fun `an entry's id is the trimmed guid, else the link as written, else the digest`() {
        assertEquals("tag:x,2026:1", first(synd("rss_2.0", entry { uri = "  tag:x,2026:1 \n"; link = "https://example.org/p" })).id)
        assertEquals("/p/1", first(synd("rss_2.0", entry { uri = ""; link = "/p/1" })).id)
        assertEquals("/p/1", first(synd("rss_2.0", entry { link = " /p/1 " })).id)
        val digest = first(synd("rss_2.0", entry { title = "Hello"; link = "javascript:alert(1)" })).id
        assertEquals(digestId("Hello", null, null, null, null), digest)
        assertTrue(first(synd("rss_2.0", entry { uri = "g".repeat(2_049); title = "T" })).id.startsWith(DIGEST_PREFIX))
    }

    @Test
    fun `a link equal to the guid is refused when it is relative and kept when absolute`() {
        assertNull(first(synd("rss_2.0", entry { uri = "posts/1"; link = "posts/1" })).link)
        assertEquals("https://x.test/p", first(synd("rss_2.0", entry { uri = "https://x.test/p"; link = "https://x.test/p" })).link)
        assertEquals("https://example.org/feeds/posts/1", first(synd("rss_2.0", entry { uri = "g-7"; link = "posts/1" })).link)
        assertNull(first(synd("rss_2.0", entry { uri = "g-7"; link = "g-7" })).link)
    }

    @Test
    fun `a relative link resolves against the base URL and is null without one`() {
        assertEquals("https://example.org/post/1", first(synd("rss_2.0", entry { uri = "e"; link = "/post/1" })).link)
        assertEquals("https://example.org/feeds/a.xml#c", first(synd("rss_2.0", entry { uri = "e"; link = "#c" })).link)
        assertNull(first(synd("rss_2.0", entry { uri = "e"; link = "/post/1" }), baseUrl = null).link)
        assertEquals("https://x.test/abs", first(synd("rss_2.0", entry { uri = "e"; link = "https://x.test/abs" }), baseUrl = null).link)
    }

    @Test
    fun `the atom feed's and entry's xml base apply in turn and a base that cannot resolve makes relative links null`() {
        fun atomWire(feedBase: String?, entryBase: String?): WireFeed = com.rometools.rome.feed.atom.Feed("atom_1.0").apply {
            xmlBase = feedBase
            entries = listOf(Entry().apply { xmlBase = entryBase })
        }
        val feed = synd("atom_1.0", entry { uri = "e"; link = "post-1.html#c" })
        assertEquals("https://example.org/blog/2026/post-1.html#c", first(feed, wire = atomWire("/blog/", "2026/")).link)
        assertEquals("https://example.org/blog/post-1.html#c", first(feed, wire = atomWire("/blog/", null)).link)
        assertEquals("https://example.org/feeds/2026/post-1.html#c", first(feed, wire = atomWire(null, "2026/")).link)
        assertNull(first(feed, wire = atomWire("ftp://other.test/", null)).link)
        assertNull(first(feed, wire = atomWire("/blog/", "javascript:x")).link)
        assertEquals("https://example.org/blog/", convert(synd("atom_1.0").apply { links = listOf(SyndLinkImpl().apply { rel = "alternate"; href = "." }) }, wire = atomWire("/blog/", null)).getOrThrow().link)
    }

    @Test
    fun `dates inside the years 1 to 9999 are kept and others are null`() {
        fun dates(published: Long, updated: Long) = first(synd("atom_1.0", entry { uri = "e"; publishedDate = Date(published); updatedDate = Date(updated) }))
        val inside = dates(MIN_EPOCH_MILLIS, MAX_EPOCH_MILLIS)
        assertEquals(MIN_EPOCH_MILLIS, inside.publishedAtEpochMillis)
        assertEquals(MAX_EPOCH_MILLIS, inside.updatedAtEpochMillis)
        val outside = dates(MIN_EPOCH_MILLIS - 1, MAX_EPOCH_MILLIS + 1)
        assertNull(outside.publishedAtEpochMillis)
        assertNull(outside.updatedAtEpochMillis)
        val absent = first(synd("rss_2.0", entry { uri = "e" }))
        assertNull(absent.publishedAtEpochMillis)
        assertNull(absent.updatedAtEpochMillis)
    }

    @Test
    fun `categories are the format's own, name else label, then the dc subjects, trimmed, de-duplicated and capped`() {
        val e = entry {
            uri = "e"
            categories = listOf(
                SyndCategoryImpl().apply { name = "  Tech " },
                SyndCategoryImpl().apply { label = "News" },
                SyndCategoryImpl().apply { name = "Tech" },
                SyndCategoryImpl().apply { name = "   " },
            )
            modules = listOf(DCModuleImpl().apply { subjects = listOf(DCSubjectImpl().apply { value = " Science " }, DCSubjectImpl().apply { value = "News" }, DCSubjectImpl().apply { value = "" }) })
        }
        assertEquals(listOf("Tech", "News", "Science"), first(synd("rss_2.0", e)).categories)
        assertEquals(listOf("Tech", "News"), first(synd("rss_2.0", e), FeedLimits { maxCategoriesPerEntry = 2 }).categories)
        assertEquals(emptyList(), first(synd("rss_2.0", e), FeedLimits { maxCategoriesPerEntry = 0 }).categories)
    }

    @Test
    fun `enclosures are resolved, dropped when invalid or repeated, capped, with a length of zero or less unknown`() {
        fun enclosure(url: String?, length: Long = 0, type: String? = null) = SyndEnclosureImpl().apply { this.url = url; this.length = length; this.type = type }
        val e = entry {
            uri = "e"
            enclosures = listOf(
                enclosure("media/a.mp3", 1_024, " audio/mpeg "),
                enclosure("javascript:alert(1)", 5),
                enclosure(null, 5),
                enclosure("https://example.org/feeds/media/a.mp3", 99),
                enclosure("https://cdn.example.test/b.ogg", 0),
                enclosure("https://cdn.example.test/c.ogg", -5),
            )
        }
        val converted = first(synd("rss_2.0", e)).enclosures
        assertEquals(listOf("https://example.org/feeds/media/a.mp3", "https://cdn.example.test/b.ogg", "https://cdn.example.test/c.ogg"), converted.map { it.url })
        assertEquals(1_024L, converted[0].lengthBytes)
        assertEquals("audio/mpeg", converted[0].mimeType)
        assertNull(converted[1].lengthBytes)
        assertNull(converted[1].mimeType)
        assertNull(converted[2].lengthBytes)
        assertEquals(1, first(synd("rss_2.0", e), FeedLimits { maxEnclosuresPerEntry = 1 }).enclosures.size)
        assertEquals(0, first(synd("rss_2.0", e), FeedLimits { maxEnclosuresPerEntry = 0 }).enclosures.size)
    }

    @Test
    fun `the summary is the description and the content is the first content`() {
        val e = entry {
            uri = "e"
            description = content(" <p>Summary</p> ")
            contents = listOf(content("<p>First</p>"), content("<p>Second</p>"))
        }
        val converted = first(synd("rss_2.0", e))
        assertEquals("<p>Summary</p>", converted.summary)
        assertEquals("<p>First</p>", converted.content)
        val none = first(synd("rss_2.0", entry { uri = "e"; description = content("  ") }))
        assertNull(none.summary)
        assertNull(none.content)
    }

    @Test
    fun `the digest takes the first enclosure's URL as written`() {
        val e = entry {
            enclosures = listOf(SyndEnclosureImpl().apply { url = " media/a.mp3 " }, SyndEnclosureImpl().apply { url = "https://cdn.example.test/b.mp3" })
        }
        val converted = first(synd("rss_2.0", e))
        assertEquals(digestId(null, null, null, " media/a.mp3 ", null), converted.id)
        assertEquals(digestId(null, null, null, "media/a.mp3", null), converted.id)
        assertEquals("https://example.org/feeds/media/a.mp3", converted.enclosures[0].url)
    }

    @Test
    fun `an entry with nothing at all is kept with the constant digest id`() {
        val converted = convert(synd("rss_2.0", entry(), entry())).getOrThrow()
        assertEquals(2, converted.entries.size)
        for (e in converted.entries) {
            assertEquals("webtools-feeds:v1:b2025d24e4ca603fe646f1d2df2ea6e5", e.id)
            assertNull(e.title)
            assertNull(e.link)
            assertEquals(emptyList(), e.categories)
        }
    }

    @Test
    fun `hostile strings still build valid model values`() {
        val e = entry {
            uri = "g".repeat(5_000)
            link = "https://x.test/a\u0001b"
            title = "\u0000\u0001" + "t".repeat(10_000_000) + "\uD83D"
            description = content("\uDC00 lone low")
            author = "\u0007"
        }
        val converted = first(synd("rss_2.0", e))
        assertTrue(converted.id.startsWith(DIGEST_PREFIX))
        assertNull(converted.link)
        assertEquals(FeedLimits.DEFAULT_MAX_TEXT_CHARS, converted.title!!.length)
        assertEquals("\uDC00 lone low", converted.summary)
        assertEquals("\u0007", converted.author)
        val cut = first(synd("rss_2.0", entry { uri = "e"; title = "x".repeat(FeedLimits.DEFAULT_MAX_TEXT_CHARS - 1) + "😀" }))
        assertEquals(FeedLimits.DEFAULT_MAX_TEXT_CHARS - 1, cut.title!!.length)
    }
}
