package com.spartanlabs.testing.nonfunctional.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.bytes
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.latin1
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.utf16be
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.utf16le
import com.spartanlabs.testing.support.webtools.feeds.ByteDocuments.utf8
import com.spartanlabs.testing.support.webtools.feeds.HostileDocuments
import com.spartanlabs.testing.support.webtools.feeds.HostileParseJvm
import com.spartanlabs.testing.support.webtools.feeds.ParserFactory
import com.spartanlabs.webtools.feeds.DIGEST_PREFIX
import com.spartanlabs.webtools.feeds.Feed
import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedInput
import com.spartanlabs.webtools.feeds.FeedLimit
import com.spartanlabs.webtools.feeds.FeedLimits
import com.spartanlabs.webtools.feeds.FeedParser
import com.spartanlabs.webtools.feeds.FeedRefusal
import com.spartanlabs.webtools.feeds.NotAFeedReason
import com.spartanlabs.webtools.http.Url
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Level 4c - the hardening contract of a `FeedParser`: it never throws for garbage, is safe to share across threads, resolves nothing
 * external (DTDs, entities, includes, stylesheets, schema locations, links), refuses a DOCTYPE in every encoding and position quickly,
 * classifies non-feeds, enforces every structural and data limit, and keeps document content out of its failures; the 2 MiB hostile
 * shapes run in a child JVM at `-Xmx128m` with two concurrent parses and must exit 0 with the expected result lines. Abstract: a
 * parser behind the port adds a `ParserFactory` and a three-line subclass naming it. A failing assertion names the case and the
 * result kind, never document content.
 */
abstract class FeedParserHardeningContract {

    /** The factory the parser under test is built through; a public no-argument class, so a child JVM can build it by name. */
    protected abstract val factory: Class<out ParserFactory>

    private val base = Url.parse("https://feeds.example.test/feed.xml").getOrThrow()

    private fun parser(limits: FeedLimits = FeedLimits.Defaults): FeedParser = factory.getDeclaredConstructor().newInstance().create(limits)
    private fun parse(document: ByteArray, contentType: String? = "application/rss+xml", limits: FeedLimits = FeedLimits.Defaults): Result<Feed> =
        parser(limits).parse(FeedInput(document, contentType, base))
    private fun parse(text: String, contentType: String? = "application/rss+xml", limits: FeedLimits = FeedLimits.Defaults): Result<Feed> = parse(utf8(text), contentType, limits)

    private fun kind(result: Result<Feed>): String = result.fold({ "ok:${it.entries.size}" }, { it::class.simpleName ?: "?" })
    private fun rss(items: String, channel: String = "<title>t</title>", namespaces: String = ""): String = "<rss version=\"2.0\"$namespaces><channel>$channel$items</channel></rss>"
    private fun item(body: String) = "<item>$body</item>"

    private fun <T> withServer(block: (MockWebServer, Url) -> T): T {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse.Builder().code(200).body("<!ENTITY x \"y\">").build()
        }
        server.start(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0)
        try {
            return block(server, Url.parse("http://127.0.0.1:${server.port}/").getOrThrow())
        } finally {
            server.close()
        }
    }

    private fun assertLimit(limit: FeedLimit, result: Result<Feed>, case: String) {
        val failure = result.exceptionOrNull()
        assertTrue(failure is FeedFailure.LimitExceeded && failure.limit == limit, "$case: expected LimitExceeded(${limit.name}), got ${kind(result)}")
    }

    //region in process
    @Test
    fun `parse never throws for a thousand seeded garbage inputs`() {
        val p = parser()
        val random = Random(4_0)
        repeat(1_000) { i ->
            val bytes = ByteArray(random.nextInt(0, 2_000)) { random.nextInt(256).toByte() }
            val result = p.parse(FeedInput(bytes, if (i % 3 == 0) null else "application/xml", base))
            assertTrue(result.isSuccess || result.exceptionOrNull() is FeedFailure, "garbage $i: ${kind(result)}")
        }
    }

    @Test
    fun `eight threads sharing one parser get their own results`() {
        val p = parser()
        val start = CountDownLatch(1)
        val wrong = java.util.concurrent.atomic.AtomicInteger()
        val threads = List(8) { t ->
            val document = utf8(rss((1..5).joinToString("") { item("<guid>t$t-$it</guid><title>Thread $t</title>") }, channel = "<title>Feed $t</title>"))
            Thread {
                start.await()
                repeat(50) {
                    val feed = p.parse(FeedInput(document, "application/rss+xml", base)).getOrNull()
                    if (feed == null || feed.title != "Feed $t" || feed.entries.size != 5 || feed.entries.any { e -> e.title != "Thread $t" }) wrong.incrementAndGet()
                }
            }.apply { start() }
        }
        start.countDown()
        threads.forEach { it.join(30_000) }
        assertEquals(0, wrong.get())
    }

    @Test
    fun `external entities and a DTD are refused and nothing is requested`() = withServer { server, loopback ->
        val documents = mapOf(
            "external DTD" to "<!DOCTYPE rss SYSTEM \"${loopback}x.dtd\">" + rss(""),
            "external general entity" to "<!DOCTYPE rss [<!ENTITY x SYSTEM \"${loopback}x.txt\">]>" + rss(item("<title>&x;</title>")),
            "external parameter entity" to "<!DOCTYPE rss [<!ENTITY % p SYSTEM \"${loopback}p.dtd\"> %p;]>" + rss(""),
        )
        for ((case, document) in documents) {
            val result = parse(document)
            assertTrue(result.exceptionOrNull().let { it is FeedFailure.Refused && it.reason == FeedRefusal.Doctype }, "$case: ${kind(result)}")
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `xi include, stylesheet processing instructions and schema locations are not followed`() = withServer { server, loopback ->
        parse("<?xml-stylesheet type=\"text/xsl\" href=\"${loopback}s.xsl\"?>" + rss(item("<title>t</title>")))
        parse(rss(item("<title><xi:include href=\"${loopback}x.xml\" parse=\"text\"/></title>"), namespaces = " xmlns:xi=\"http://www.w3.org/2001/XInclude\""))
        parse(rss(item("<xi:include href=\"${loopback}x.xml\"/>"), namespaces = " xmlns:xi=\"http://www.w3.org/2001/XInclude\""))
        parse(rss(item("<title>t</title>"), namespaces = " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"urn:x ${loopback}s.xsd\" xsi:noNamespaceSchemaLocation=\"${loopback}n.xsd\""))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `links, xml base and enclosures that point at the loopback server are not requested`() = withServer { server, loopback ->
        //language=XML
        val atom = "<feed xmlns=\"http://www.w3.org/2005/Atom\" xml:base=\"$loopback\"><title>t</title><link href=\"${loopback}page\"/>" +
            "<entry><id>urn:e</id><title>e</title><link href=\"e/1\"/><link rel=\"enclosure\" href=\"${loopback}a.mp3\" length=\"5\"/></entry></feed>"
        val feed = parse(atom, "application/atom+xml").getOrThrow()
        assertEquals("${loopback}e/1", feed.entries.single().link)
        assertEquals("${loopback}a.mp3", feed.entries.single().enclosures.single().url)
        parse(rss(item("<link>${loopback}p</link><enclosure url=\"${loopback}b.mp3\" length=\"5\" type=\"audio/mpeg\"/>")))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `billion laughs and a quadratic blowup are refused as a DOCTYPE, quickly`() {
        val laughs = buildString {
            append("<!DOCTYPE rss [<!ENTITY lol \"lol\">")
            for (i in 1..9) append("<!ENTITY lol$i \"").append("&lol${i - 1};".replace("lol0", "lol").repeat(10)).append("\">")
            append("]>").append(rss(item("<title>&lol9;</title>")))
        }
        val quadratic = "<!DOCTYPE rss [<!ENTITY a \"" + "a".repeat(50_000) + "\">]>" + rss(item("<title>" + "&a;".repeat(20_000) + "</title>"))
        for ((case, document) in listOf("billion laughs" to laughs, "quadratic blowup" to quadratic)) {
            val started = TimeSource.Monotonic.markNow()
            val result = parse(document)
            assertTrue(result.exceptionOrNull().let { it is FeedFailure.Refused && it.reason == FeedRefusal.Doctype }, "$case: ${kind(result)}")
            assertTrue(started.elapsedNow() < 5.seconds, "$case took ${started.elapsedNow()}")
        }
    }

    @Test
    fun `a DOCTYPE in UTF-16, behind a byte-order mark and after comments is refused`() {
        val doctype = "<!DOCTYPE rss SYSTEM \"http://127.0.0.1:1/x.dtd\">" + rss("")
        val documents = mapOf(
            "UTF-16LE with a mark" to utf16le(doctype),
            "UTF-16BE with a mark" to utf16be(doctype),
            "UTF-16LE without a mark" to utf16le("<?xml version=\"1.0\" encoding=\"UTF-16\"?>$doctype", mark = false),
            "UTF-8 behind a mark" to bytes(0xEF, 0xBB, 0xBF) + utf8(doctype),
            "after comments" to utf8("<!-- a --><?xml-stylesheet href=\"x\"?><!-- <!DOCTYPE a> -->$doctype"),
            "after a declaration and white space" to utf8("<?xml version=\"1.0\"?>\n\n  \t$doctype"),
            "lower case" to utf8("<!doctype rss>" + rss("")),
        )
        for ((case, document) in documents) {
            val result = parse(document)
            assertTrue(result.exceptionOrNull().let { it is FeedFailure.Refused && it.reason == FeedRefusal.Doctype }, "$case: ${kind(result)}")
        }
    }

    @Test
    fun `html with and without a DOCTYPE is NotAFeed Html`() {
        for ((case, document) in mapOf("with" to "<!DOCTYPE html><html><body>x</body></html>", "without" to "<html><head><title>x</title></head><body>x</body></html>", "upper case" to "<HTML></HTML>")) {
            val result = parse(document, "text/html")
            assertTrue(result.exceptionOrNull().let { it is FeedFailure.NotAFeed && it.reason == NotAFeedReason.Html }, "$case: ${kind(result)}")
        }
    }

    @Test
    fun `json, plain text and an empty body are NotAFeed`() {
        fun reason(document: ByteArray, contentType: String?): NotAFeedReason? = (parse(document, contentType).exceptionOrNull() as? FeedFailure.NotAFeed)?.reason
        assertEquals(NotAFeedReason.NotXml, reason(utf8("{\"items\": []}"), "application/json"))
        assertEquals(NotAFeedReason.NotXml, reason(utf8("just some text"), "text/plain"))
        assertEquals(NotAFeedReason.Empty, reason(ByteArray(0), "application/xml"))
        assertEquals(NotAFeedReason.Empty, reason(utf8("   \n"), null))
    }

    @Test
    fun `invalid XML characters are Malformed`() {
        assertIs<FeedFailure.Malformed>(parse(rss(item("<title>a\u0001b</title>"))).exceptionOrNull(), "U+0001")
        assertIs<FeedFailure.Malformed>(parse(rss(item("<title>a&#1;b</title>"))).exceptionOrNull(), "&#1;")
        assertIs<FeedFailure.Malformed>(parse(rss(item("<title>a\u000Bb</title>"))).exceptionOrNull(), "U+000B")
    }

    @Test
    fun `utf-16 and iso-8859-1 documents parse`() {
        val text = "<?xml version=\"1.0\" encoding=\"UTF-16\"?>" + rss(item("<guid>g</guid><title>café</title>"))
        assertEquals("café", parse(utf16le(text)).getOrThrow().entries.single().title)
        assertEquals("café", parse(utf16be(text)).getOrThrow().entries.single().title)
        val latin = latin1("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + rss(item("<guid>g</guid><title>café</title>")))
        assertEquals("café", parse(latin).getOrThrow().entries.single().title)
    }

    @Test
    fun `a javascript link is null`() {
        val feed = parse(rss(item("<guid>g</guid><link>javascript:alert(1)</link><enclosure url=\"javascript:alert(2)\"/>"), channel = "<title>t</title><link>JAVASCRIPT:alert(3)</link>")).getOrThrow()
        assertNull(feed.link)
        assertNull(feed.entries.single().link)
        assertTrue(feed.entries.single().enclosures.isEmpty())
    }

    @Test
    fun `depth 256 parses and 257 is LimitExceeded Depth`() {
        assertTrue(parse(HostileDocuments.rssNested(256)).isSuccess, kind(parse(HostileDocuments.rssNested(256))))
        assertLimit(FeedLimit.Depth, parse(HostileDocuments.rssNested(257)), "257 levels")
        assertTrue(parse(HostileDocuments.rssNested(100), limits = FeedLimits { maxDepth = 100 }).isSuccess)
        assertLimit(FeedLimit.Depth, parse(HostileDocuments.rssNested(101), limits = FeedLimits { maxDepth = 100 }), "101 levels is over a tightened limit of 100")
    }

    @Test
    fun `100 attributes parse and 101 is LimitExceeded Attributes`() {
        assertTrue(parse(HostileDocuments.attributesOnOneElement(100)).isSuccess)
        assertLimit(FeedLimit.Attributes, parse(HostileDocuments.attributesOnOneElement(101)), "101 attributes")
        assertTrue(parse(HostileDocuments.attributesOnOneElement(10), limits = FeedLimits { maxAttributesPerElement = 10 }).isSuccess)
        assertLimit(FeedLimit.Attributes, parse(HostileDocuments.attributesOnOneElement(11), limits = FeedLimits { maxAttributesPerElement = 10 }), "11 over a limit of 10")
    }

    @Test
    fun `64 distinct namespace URIs parse, 65 is LimitExceeded NamespaceUris, and a URI declared many times counts once`() {
        assertTrue(parse(HostileDocuments.namespaces(64)).isSuccess, kind(parse(HostileDocuments.namespaces(64))))
        assertLimit(FeedLimit.NamespaceUris, parse(HostileDocuments.namespaces(65)), "65 URIs")
        val repeated = utf8(rss("<e xmlns:n=\"urn:same\"/>".repeat(500)))
        assertTrue(parse(repeated, limits = FeedLimits { maxNamespaceUris = 2 }).isSuccess)
    }

    @Test
    fun `an xmlns flood on one element is LimitExceeded Attributes`() {
        assertLimit(FeedLimit.Attributes, parse(HostileDocuments.xmlnsFloodOnOneElement(200)), "200 xmlns on one element")
    }

    @Test
    fun `elements at a tightened limit parse and one more is LimitExceeded Elements`() {
        val limits = FeedLimits { maxElements = 100 }
        assertTrue(parse(HostileDocuments.elements(100), limits = limits).isSuccess)
        assertLimit(FeedLimit.Elements, parse(HostileDocuments.elements(101), limits = limits), "101 elements")
    }

    @Test
    fun `1,000 entries parse and 1,001 are LimitExceeded Entries`() {
        assertEquals(1_000, parse(HostileDocuments.items(1_000)).getOrThrow().entries.size)
        assertLimit(FeedLimit.Entries, parse(HostileDocuments.items(1_001)), "1,001 items")
    }

    @Test
    fun `a text node over maxTextChars is cut without splitting a surrogate pair`() {
        val title = "x".repeat(FeedLimits.DEFAULT_MAX_TEXT_CHARS - 1) + "😀" + "tail"
        val entry = parse(rss(item("<guid>g</guid><title>$title</title>"))).getOrThrow().entries.single()
        assertEquals(FeedLimits.DEFAULT_MAX_TEXT_CHARS - 1, entry.title!!.length)
        assertFalse(entry.title!!.last().isHighSurrogate())
    }

    @Test
    fun `a 5 MB id falls back to the link or the digest`() {
        val guid = "g".repeat(5_000_000)
        val withLink = parse(rss(item("<guid>$guid</guid><link>https://x.test/p</link>"))).getOrThrow().entries.single()
        assertEquals("https://x.test/p", withLink.id)
        val withoutLink = parse(rss(item("<guid>$guid</guid><title>T</title>"))).getOrThrow().entries.single()
        assertTrue(withoutLink.id.startsWith(DIGEST_PREFIX), withoutLink.id.take(60))
    }

    @Test
    fun `no failure holds document content or a cause`() {
        val documents = listOf(
            "<SENTINEL-ROOT/>",
            rss(item("<title>SENTINEL-TEXT</title><e SENTINEL-ATTR=\"SENTINEL-VALUE\" x=\"SENTINEL-X\"></title></item>")),
            "<!DOCTYPE SENTINEL-DT SYSTEM \"http://127.0.0.1:1/SENTINEL.dtd\">" + rss(""),
            rss(item("<title>SENTINEL &SENTINELENTITY; </title>")),
            HostileDocuments.rssNested(300).decodeToString().replace("<a>", "<SENTINEL-DEEP>").replace("</a>", "</SENTINEL-DEEP>"),
            rss("<e " + (0 until 150).joinToString(" ") { "SENTINEL$it=\"v\"" } + "/>"),
            "<rss version=\"2.0\"><channel><title>t</title><cloud domain=\"x\" port=\"SENTINEL-PORT\" path=\"/\" registerProcedure=\"p\" protocol=\"soap\"/></channel></rss>",
            "<html><body>SENTINEL-PAGE</body></html>",
        )
        for (document in documents) {
            val failure = parse(document).exceptionOrNull()
            assertIs<FeedFailure>(failure, "document ${documents.indexOf(document)} must fail")
            assertNull(failure.cause, "document ${documents.indexOf(document)}")
            assertFalse(failure.message!!.contains("SENTINEL"), "document ${documents.indexOf(document)}: ${failure::class.simpleName}")
            assertFalse(failure.toString().contains("SENTINEL"))
        }
    }
    //endregion

    //region child JVM
    private fun gate(case: String) {
        val expected = HostileDocuments.byName.getValue(case).second
        val outcome = HostileParseJvm.run(factory, case, parses = 2)
        assertEquals(0, outcome.exitCode, "$case: exit ${outcome.exitCode} after ${outcome.elapsed}, lines ${outcome.lines}")
        assertEquals(listOf(expected, expected), outcome.lines, case)
        assertTrue(outcome.elapsed < 30.seconds, "$case took ${outcome.elapsed}")
        println("hardening gate: $case -> ${outcome.lines.first()} in ${outcome.elapsed.inWholeMilliseconds} ms (child JVM, -Xmx128m, 2 parses)")
    }

    @Test fun `an item flood is LimitExceeded Elements`() = gate("item-flood")
    @Test fun `an atom entry flood is LimitExceeded Elements`() = gate("entry-flood")
    @Test fun `an a-element flood in the channel is LimitExceeded Elements`() = gate("a-flood-channel")
    @Test fun `an a-element flood in an item is LimitExceeded Elements`() = gate("a-flood-item")
    @Test fun `an a-element-with-text flood is LimitExceeded Elements`() = gate("a-text-flood")
    @Test fun `10,000 attributes on each element is LimitExceeded Attributes`() = gate("attributes-10000")
    @Test fun `exactly 100 attributes per element on a 2 MiB document is accepted`() = gate("attributes-100")
    @Test fun `101 attributes per element is LimitExceeded Attributes`() = gate("attributes-101")
    @Test fun `rss nested 290,000 levels is LimitExceeded Depth`() = gate("rss-nested-290000")
    @Test fun `atom xhtml nested 5,000 deep is LimitExceeded Depth`() = gate("atom-xhtml-5000")
    @Test fun `an rss description nested 5,000 deep is LimitExceeded Depth`() = gate("rss-description-5000")
    @Test fun `distinct xmlns URIs spread over elements is LimitExceeded NamespaceUris`() = gate("xmlns-distinct")
    @Test fun `one xmlns URI declared on 99,000 elements is accepted`() = gate("xmlns-repeated")
    @Test fun `a 2 MiB text node is accepted`() = gate("text-2mib")
    @Test fun `400 realistic items are accepted with 400 entries`() = gate("realistic-400")
    @Test fun `1,000 items are accepted with 1,000 entries`() = gate("items-1000")
    @Test fun `1,001 items are LimitExceeded Entries`() = gate("items-1001")
    @Test fun `a 2 MiB id is accepted`() = gate("id-2mib")
    //endregion
}
