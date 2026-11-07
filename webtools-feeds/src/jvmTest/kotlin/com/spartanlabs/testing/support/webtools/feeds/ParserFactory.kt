package com.spartanlabs.testing.support.webtools.feeds

import com.spartanlabs.webtools.feeds.FeedLimits
import com.spartanlabs.webtools.feeds.FeedParser
import com.spartanlabs.webtools.feeds.RomeFeedParser

/** Builds the parser a contract class runs against; a parser behind the port adds one and a three-line subclass. */
fun interface ParserFactory {
    fun create(limits: FeedLimits): FeedParser
}

/** The supplied parser. A public no-argument class, so a child JVM can build it by name. */
class RomeParserFactory : ParserFactory {
    override fun create(limits: FeedLimits): FeedParser = RomeFeedParser(limits)
}
