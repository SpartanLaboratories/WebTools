package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.webtools.feeds.Feed
import com.spartanlabs.webtools.feeds.FeedEnclosure
import com.spartanlabs.webtools.feeds.FeedEntry
import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedFetch
import com.spartanlabs.webtools.feeds.FeedFetcher
import com.spartanlabs.webtools.feeds.FeedFetcherConfig
import com.spartanlabs.webtools.feeds.FeedInput
import com.spartanlabs.webtools.feeds.FeedLimit
import com.spartanlabs.webtools.feeds.FeedLimits
import com.spartanlabs.webtools.feeds.FeedRefusal
import com.spartanlabs.webtools.feeds.FeedValidators
import com.spartanlabs.webtools.feeds.NotAFeedReason
import com.spartanlabs.webtools.feeds.RomeFeedParser
import com.spartanlabs.webtools.http.WebToolsDsl
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Level 2 - by reflection (the ABI dump records neither): each builder passed to a consumer's lambda carries `WebToolsDsl`; the
 * public classes are final, have no `copy` or `component` functions, and expose no Rome, JDOM or SAX type.
 */
class FeedsDslMarkerComponentTest {

    private val builders = listOf(FeedLimits.Builder::class.java, Feed.Builder::class.java, FeedEntry.Builder::class.java, FeedEnclosure.Builder::class.java, FeedFetcherConfig::class.java)

    private val publicClasses = listOf(
        Feed::class.java, FeedEntry::class.java, FeedEnclosure::class.java, FeedLimits::class.java, FeedInput::class.java, FeedValidators::class.java,
        FeedFetch.Fetched::class.java, FeedFetch.NotModified::class.java, FeedFailure.NotAFeed::class.java, FeedFailure.Malformed::class.java,
        FeedFailure.Refused::class.java, FeedFailure.LimitExceeded::class.java, NotAFeedReason::class.java, FeedRefusal::class.java, FeedLimit::class.java,
        FeedFetcherConfig::class.java, FeedFetcher::class.java, RomeFeedParser::class.java,
    ) + builders

    @Test
    fun `each builder passed to a consumer's lambda carries WebToolsDsl`() {
        for (builder in builders) assertTrue(builder.isAnnotationPresent(WebToolsDsl::class.java), builder.name)
    }

    @Test
    fun `the public classes are final, have no copy or component functions, and expose no Rome, JDOM or SAX type`() {
        val foreign = listOf("com.rometools.", "org.jdom2.", "org.xml.sax.", "org.slf4j.")
        for (c in publicClasses) {
            assertTrue(Modifier.isFinal(c.modifiers), "${c.name} must be final")
            val methods = c.declaredMethods.filter { Modifier.isPublic(it.modifiers) }
            assertTrue(methods.none { it.name == "copy" || it.name.startsWith("component") }, "${c.name} has data-class members")
            val types = methods.flatMap { listOf(it.returnType) + it.parameterTypes.toList() } +
                c.declaredFields.filter { Modifier.isPublic(it.modifiers) }.map { it.type } +
                c.declaredConstructors.filter { Modifier.isPublic(it.modifiers) }.flatMap { it.parameterTypes.toList() }
            val leaked = types.map { it.name }.filter { name -> foreign.any { name.startsWith(it) } }
            assertTrue(leaked.isEmpty(), "${c.name} exposes $leaked")
        }
    }
}
