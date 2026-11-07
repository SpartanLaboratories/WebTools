package com.spartanlabs.testing.gating.webtools.feeds

import com.spartanlabs.webtools.feeds.Feed
import com.spartanlabs.webtools.feeds.FeedEnclosure
import com.spartanlabs.webtools.feeds.FeedEntry
import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedFetch
import com.spartanlabs.webtools.feeds.FeedInput
import com.spartanlabs.webtools.feeds.FeedLimit
import com.spartanlabs.webtools.feeds.FeedLimits
import com.spartanlabs.webtools.feeds.FeedParser
import com.spartanlabs.webtools.feeds.FeedRefusal
import com.spartanlabs.webtools.feeds.FeedValidators
import com.spartanlabs.webtools.feeds.NotAFeedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Url
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Level 1 - the module's common API as a consumer uses it, compiled without any opt-in: the factories build the limits,
 * the model and the validators; a `FeedFetch` is matched without an `else` and a failure with one; the failure leaves
 * and their reasons are created and read; a `FeedParser` and a `FeedInput` appear in signatures.
 */
class FeedsConsumerGatingTest {

    @Test
    fun `the limits, the model and the validators are built with their factories`() {
        val limits = FeedLimits { maxEntries = 200 }
        assertEquals(200, limits.maxEntries)
        assertEquals(FeedLimits.DEFAULT_MAX_TEXT_CHARS, limits.maxTextChars)

        val entry = FeedEntry("id") { title = "t"; categories = listOf("c") }
        assertEquals("id", entry.id)
        assertEquals("t", entry.title)
        assertEquals(listOf("c"), entry.categories)

        val feed = Feed { entries = listOf(entry) }
        assertEquals(listOf(entry), feed.entries)
        assertNull(feed.title)

        val enclosure = FeedEnclosure("https://cdn.example.test/a.mp3") { lengthBytes = 5 }
        assertEquals("https://cdn.example.test/a.mp3", enclosure.url)
        assertEquals(5L, enclosure.lengthBytes)

        val validators = FeedValidators("\"a\"", null)
        assertEquals("\"a\"", validators.etag)
        assertNull(validators.lastModified)
    }

    @Test
    fun `a FeedFetch is matched without an else and a failure with one`() {
        val url = Url.parse("https://feeds.example.test/rss").getOrThrow()
        val fetches: List<FeedFetch> = listOf(
            FeedFetch.Fetched(Feed(), FeedValidators.None, url, null),
            FeedFetch.NotModified(FeedValidators("\"a\"", null), url, null),
        )
        val kinds = fetches.map { fetch ->
            when (fetch) {
                is FeedFetch.Fetched -> "fetched:${fetch.feed.entries.size}"
                is FeedFetch.NotModified -> "notModified:${fetch.validators.etag}"
            }
        }
        assertEquals(listOf("fetched:0", "notModified:\"a\""), kinds)

        val failures: List<HttpFailure> = listOf(
            FeedFailure.NotAFeed(NotAFeedReason.Html), FeedFailure.Malformed(),
            FeedFailure.Refused(FeedRefusal.Doctype), FeedFailure.LimitExceeded(FeedLimit.Entries), HttpFailure.Closed("feed fetcher"),
        )
        val described = failures.map { failure ->
            when (failure) {
                is FeedFailure.NotAFeed -> "notAFeed"
                is FeedFailure.Malformed -> "malformed"
                is FeedFailure.Refused -> "refused"
                is FeedFailure.LimitExceeded -> "limit"
                else -> "other"
            }
        }
        assertEquals(listOf("notAFeed", "malformed", "refused", "limit", "other"), described)
    }

    @Test
    fun `the failure leaves and their reasons are created and read`() {
        assertEquals(NotAFeedReason.UnknownFormat, FeedFailure.NotAFeed(NotAFeedReason.UnknownFormat).reason)
        assertEquals(FeedRefusal.PrologTooLong, FeedFailure.Refused(FeedRefusal.PrologTooLong).reason)
        assertEquals(FeedLimit.Depth, FeedFailure.LimitExceeded(FeedLimit.Depth).limit)
        assertNull(FeedFailure.Malformed().cause)

        val notAFeed = listOf(NotAFeedReason.Html, NotAFeedReason.Empty, NotAFeedReason.NotXml, NotAFeedReason.UnknownFormat).map {
            when (it) {
                NotAFeedReason.Html -> "html"
                NotAFeedReason.Empty -> "empty"
                NotAFeedReason.NotXml -> "notXml"
                NotAFeedReason.UnknownFormat -> "unknown"
                else -> "other"
            }
        }
        assertEquals(listOf("html", "empty", "notXml", "unknown"), notAFeed)

        val refusals = listOf(FeedRefusal.Doctype, FeedRefusal.UnsupportedEncoding, FeedRefusal.PrologTooLong, FeedRefusal.AmbiguousProlog).map {
            when (it) {
                FeedRefusal.Doctype -> "doctype"
                FeedRefusal.UnsupportedEncoding -> "encoding"
                FeedRefusal.PrologTooLong -> "long"
                FeedRefusal.AmbiguousProlog -> "ambiguous"
                else -> "other"
            }
        }
        assertEquals(listOf("doctype", "encoding", "long", "ambiguous"), refusals)

        val limits = listOf(FeedLimit.Entries, FeedLimit.Depth, FeedLimit.Attributes, FeedLimit.Elements, FeedLimit.NamespaceUris).map {
            when (it) {
                FeedLimit.Entries -> "entries"
                FeedLimit.Depth -> "depth"
                FeedLimit.Attributes -> "attributes"
                FeedLimit.Elements -> "elements"
                FeedLimit.NamespaceUris -> "namespaces"
                else -> "other"
            }
        }
        assertEquals(listOf("entries", "depth", "attributes", "elements", "namespaces"), limits)
    }

    @Test
    fun `a FeedParser and a FeedInput appear in signatures and a FeedInput is built`() {
        // Nothing implements the port here: a consumer only passes one on. The function reference proves the signature compiles.
        val parseWith: (FeedParser, FeedInput) -> Result<Feed> = ::parse
        assertNotNull(parseWith)

        val input = FeedInput("<rss/>".encodeToByteArray(), "application/rss+xml", Url.parse("https://feeds.example.test/rss").getOrThrow())
        assertEquals(6, input.documentSize)
        assertContentEquals("<rss/>".encodeToByteArray(), input.document())
        assertEquals("application/rss+xml", input.contentType)
        assertEquals("https://feeds.example.test/rss", input.baseUrl.toString())
    }

    private fun parse(parser: FeedParser, input: FeedInput): Result<Feed> = parser.parse(input)
}
