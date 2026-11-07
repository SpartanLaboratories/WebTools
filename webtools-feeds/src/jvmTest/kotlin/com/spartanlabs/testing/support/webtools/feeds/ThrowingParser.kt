package com.spartanlabs.testing.support.webtools.feeds

import com.spartanlabs.webtools.feeds.Feed
import com.spartanlabs.webtools.feeds.FeedInput
import com.spartanlabs.webtools.feeds.FeedParser
import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi

/** A parser that throws what [failure] gives it, on every parse. */
@OptIn(ExperimentalForInheritanceWebToolsApi::class)
class ThrowingParser(private val failure: () -> Throwable) : FeedParser {
    override fun parse(input: FeedInput): Result<Feed> = throw failure()
}
