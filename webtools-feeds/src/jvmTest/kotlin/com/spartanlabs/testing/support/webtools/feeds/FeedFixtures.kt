package com.spartanlabs.testing.support.webtools.feeds

import com.spartanlabs.webtools.feeds.FeedInput
import com.spartanlabs.webtools.http.Url

/** The seven synthetic feed documents under `feeds/` on the class path, read as bytes. */
object FeedFixtures {
    /** The `Content-Type` the fixtures are parsed with unless a test says otherwise. */
    const val CONTENT_TYPE: String = "application/xml"

    /** The base URL the fixtures are parsed with. */
    val baseUrl: Url = Url.parse("https://fixtures.example.test/feed.xml").getOrThrow()

    /** The fixture names, in format order. */
    val names: List<String> = listOf("rss090.rdf", "rss091u.xml", "rss092.xml", "rss20.xml", "rss10.rdf", "atom03.xml", "atom10.xml")

    /** The bytes of `feeds/<name>`, as a fresh array. */
    fun bytes(name: String): ByteArray =
        checkNotNull(FeedFixtures::class.java.getResourceAsStream("/feeds/$name")) { "fixture feeds/$name is not on the class path" }.use { it.readBytes() }

    /** A [FeedInput] over `feeds/<name>` with [CONTENT_TYPE] and [baseUrl]. */
    fun fixture(name: String, contentType: String? = CONTENT_TYPE, base: Url? = baseUrl): FeedInput = FeedInput(bytes(name), contentType, base)

    /** A [FeedInput] over a document built in code. */
    fun input(document: ByteArray, contentType: String? = CONTENT_TYPE, base: Url? = baseUrl): FeedInput = FeedInput(document, contentType, base)

    /** A [FeedInput] over UTF-8 text built in code. */
    fun input(text: String, contentType: String? = CONTENT_TYPE, base: Url? = baseUrl): FeedInput = FeedInput(text.encodeToByteArray(), contentType, base)
}
