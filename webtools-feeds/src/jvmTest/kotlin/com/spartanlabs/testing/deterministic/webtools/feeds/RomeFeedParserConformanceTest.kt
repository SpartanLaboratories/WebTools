package com.spartanlabs.testing.deterministic.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.ParserFactory
import com.spartanlabs.testing.support.webtools.feeds.RomeParserFactory

/** Level 4a - `RomeFeedParser` against the conformance contract. */
class RomeFeedParserConformanceTest : FeedParserConformanceContract() {
    override val factory: Class<out ParserFactory> = RomeParserFactory::class.java
}
