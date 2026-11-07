package com.spartanlabs.testing.nonfunctional.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.ParserFactory
import com.spartanlabs.testing.support.webtools.feeds.RomeParserFactory

/** Level 4c - `RomeFeedParser` against the hardening contract. */
class RomeFeedParserHardeningTest : FeedParserHardeningContract() {
    override val factory: Class<out ParserFactory> = RomeParserFactory::class.java
}
