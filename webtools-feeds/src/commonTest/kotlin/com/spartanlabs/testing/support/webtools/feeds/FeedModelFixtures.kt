package com.spartanlabs.testing.support.webtools.feeds

import com.spartanlabs.webtools.feeds.Feed
import com.spartanlabs.webtools.feeds.FeedEnclosure
import com.spartanlabs.webtools.feeds.FeedEntry
import com.spartanlabs.webtools.http.Url

/** Small builders of model values for the tests. */
object FeedModelFixtures {

    /** A feed URL with a path and a query sentinel, so a `toString` that leaks either is caught. */
    const val SENTINEL_URL_TEXT: String = "https://feeds.example.test/PATH-SENTINEL/feed.xml?token=QUERY-SENTINEL"

    /** [SENTINEL_URL_TEXT] parsed. */
    val sentinelUrl: Url = Url.parse(SENTINEL_URL_TEXT).getOrThrow()

    /** A canonical link with a path sentinel. */
    const val SENTINEL_LINK: String = "https://links.example.test/PATH-SENTINEL/post?q=QUERY-SENTINEL#frag"

    /** An enclosure on [SENTINEL_LINK]'s host. */
    fun enclosure(url: String = "https://cdn.example.test/PATH-SENTINEL/a.mp3", lengthBytes: Long? = 1_024L): FeedEnclosure =
        FeedEnclosure(url) { mimeType = "audio/mpeg"; this.lengthBytes = lengthBytes }

    /** An entry with every field present, [id] being the only variable part. */
    fun entry(id: String = "urn:entry:1"): FeedEntry = FeedEntry(id) {
        title = "TITLE-SENTINEL"
        link = SENTINEL_LINK
        summary = "SUMMARY-SENTINEL"
        content = "CONTENT-SENTINEL"
        author = "AUTHOR-SENTINEL"
        publishedAtEpochMillis = 1_700_000_000_000L
        updatedAtEpochMillis = 1_700_000_001_000L
        categories = listOf("CATEGORY-SENTINEL")
        enclosures = listOf(enclosure())
    }

    /** A feed with every field present and [count] entries. */
    fun feed(count: Int = 2): Feed = Feed {
        title = "FEED-TITLE-SENTINEL"
        link = "https://links.example.test/PATH-SENTINEL/"
        description = "DESCRIPTION-SENTINEL"
        entries = (1..count).map { entry("urn:entry:$it") }
    }
}
