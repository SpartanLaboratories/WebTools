package com.spartanlabs.testing.support.webtools.feeds

import com.spartanlabs.webtools.feeds.Feed
import com.spartanlabs.webtools.feeds.FeedEnclosure
import com.spartanlabs.webtools.feeds.FeedEntry

/** The `Feed` each fixture of [FeedFixtures] must parse into, with the default limits and the fixtures' base URL. */
object FeedExpectations {
    /** 2026-11-06T04:25:27Z. */
    const val T1: Long = 1_793_939_127_000L

    /** 2026-11-05T18:00:00Z. */
    const val T2: Long = 1_793_901_600_000L

    /** 2026-11-04T09:30:00Z. */
    const val T3: Long = 1_793_784_600_000L

    val rss090: Feed = Feed {
        title = "Fixture 0.90"
        link = "https://fixtures.example.test/rss090/"
        description = "An RSS 0.90 channel"
        entries = listOf(
            FeedEntry("https://fixtures.example.test/rss090/1") { title = "Item one"; link = "https://fixtures.example.test/rss090/1" },
            FeedEntry("https://fixtures.example.test/rss090/2") { title = "Item two"; link = "https://fixtures.example.test/rss090/2" },
        )
    }

    val rss091u: Feed = Feed {
        title = "Fixture 0.91"
        link = "https://fixtures.example.test/rss091/"
        description = "An RSS 0.91 Userland channel"
        entries = listOf(
            FeedEntry("https://fixtures.example.test/rss091/1") { title = "Item one"; link = "https://fixtures.example.test/rss091/1"; summary = "Plain summary one" },
            FeedEntry("https://fixtures.example.test/rss091/2") { title = "Item two"; link = "https://fixtures.example.test/rss091/2" },
        )
    }

    val rss092: Feed = Feed {
        title = "Fixture 0.92"
        link = "https://fixtures.example.test/rss092/"
        description = "An RSS 0.92 channel"
        entries = listOf(
            FeedEntry("https://fixtures.example.test/rss092/1") {
                title = "Item one"
                link = "https://fixtures.example.test/rss092/1"
                summary = "Summary <b>one</b>"
                categories = listOf("Tech", "News")
                enclosures = listOf(FeedEnclosure("https://cdn.example.test/rss092/one.mp3") { mimeType = "audio/mpeg"; lengthBytes = 12_345 })
            },
            FeedEntry("https://fixtures.example.test/rss092/2") {
                title = "Item two"
                link = "https://fixtures.example.test/rss092/2"
                enclosures = listOf(FeedEnclosure("https://cdn.example.test/rss092/two.mp3") { mimeType = "audio/mpeg" })
            },
        )
    }

    val rss20: Feed = Feed {
        title = "Fixture 2.0"
        link = "https://fixtures.example.test/rss20/"
        description = "An RSS 2.0 channel"
        entries = listOf(
            FeedEntry("urn:fixture:rss20:1") {
                title = "Item one"
                link = "https://fixtures.example.test/rss20/1"
                summary = "Summary one"
                content = "<p>Content one</p>"
                author = "one@example.test (Author One)"
                publishedAtEpochMillis = T1
                categories = listOf("Tech", "News")
                enclosures = listOf(FeedEnclosure("https://cdn.example.test/rss20/one.mp3") { mimeType = "audio/mpeg"; lengthBytes = 2_048 })
            },
            FeedEntry("https://fixtures.example.test/rss20/2") {
                title = "Item two"
                link = "https://fixtures.example.test/rss20/2"
                summary = "Summary two"
                author = "Creator Two"
                publishedAtEpochMillis = T2
            },
            FeedEntry("https://fixtures.example.test/rss20/3") {
                link = "https://fixtures.example.test/rss20/3"
                summary = "Summary three, no title and no link"
            },
        )
    }

    val rss10: Feed = Feed {
        title = "Fixture 1.0"
        link = "https://fixtures.example.test/rss10/"
        description = "An RSS 1.0 channel"
        entries = listOf(
            FeedEntry("https://fixtures.example.test/rss10/1") {
                title = "Item one"
                link = "https://fixtures.example.test/rss10/1"
                summary = "Summary one"
                author = "Creator One"
                publishedAtEpochMillis = T1
                categories = listOf("Tech", "News")
            },
            FeedEntry("urn:fixture:rss10:2") { title = "Item two"; link = "https://fixtures.example.test/rss10/2" },
        )
    }

    val atom03: Feed = Feed {
        title = "Fixture Atom 0.3"
        link = "https://fixtures.example.test/atom03/"
        description = "An Atom 0.3 feed"
        entries = listOf(
            FeedEntry("urn:fixture:atom03:1") {
                title = "Entry one"
                link = "https://fixtures.example.test/atom03/1"
                summary = "Summary one"
                content = "Content one"
                author = "Author One"
                publishedAtEpochMillis = T2
            },
            FeedEntry("urn:fixture:atom03:2") { title = "Entry two"; link = "https://fixtures.example.test/atom03/2"; publishedAtEpochMillis = T3 },
        )
    }

    val atom10: Feed = Feed {
        title = "Fixture Atom 1.0"
        link = "https://fixtures.example.test/atom10/"
        description = "An Atom 1.0 feed"
        entries = listOf(
            FeedEntry("urn:fixture:atom10:1") {
                title = "Entry one"
                link = "https://fixtures.example.test/atom10/1"
                summary = "Summary one"
                content = "<p>Content one</p>"
                author = "Author One"
                publishedAtEpochMillis = T3
                updatedAtEpochMillis = T2
                categories = listOf("tech", "News")
                enclosures = listOf(FeedEnclosure("https://cdn.example.test/atom10/one.mp3") { mimeType = "audio/mpeg"; lengthBytes = 4_096 })
            },
            FeedEntry("urn:fixture:atom10:2") { title = "Entry two"; link = "https://fixtures.example.test/atom10/2"; updatedAtEpochMillis = T2 },
        )
    }

    /** Fixture name to its expected feed. */
    val byName: Map<String, Feed> = mapOf(
        "rss090.rdf" to rss090, "rss091u.xml" to rss091u, "rss092.xml" to rss092, "rss20.xml" to rss20,
        "rss10.rdf" to rss10, "atom03.xml" to atom03, "atom10.xml" to atom10,
    )
}
