package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.webtools.feeds.FeedFetcherConfig
import com.spartanlabs.webtools.feeds.RomeFeedParser
import com.spartanlabs.webtools.feeds.defaultFeedParser
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** Level 2 - the JVM defaults of `FeedFetcherConfig`: the default parser is a `RomeFeedParser` and the default parse dispatcher `Dispatchers.Default`. */
class FeedFetcherConfigDefaultsComponentTest {

    @Test
    fun `the default parser on the JVM is a RomeFeedParser`() {
        assertIs<RomeFeedParser>(FeedFetcherConfig().parser)
        assertIs<RomeFeedParser>(defaultFeedParser())
        assertNotSame(FeedFetcherConfig().parser, FeedFetcherConfig().parser, "each configuration gets its own instance")
    }

    @Test
    fun `the default parse dispatcher is Dispatchers Default`() {
        assertSame(Dispatchers.Default, FeedFetcherConfig().parseDispatcher)
        assertSame(Dispatchers.Default, FeedFetcherConfig().resolve().parseDispatcher)
    }
}
