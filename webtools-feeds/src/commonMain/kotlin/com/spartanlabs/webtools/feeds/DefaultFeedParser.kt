package com.spartanlabs.webtools.feeds

/**
 * The platform's default [FeedParser], the value of `FeedFetcherConfig.parser` when a caller sets none: `RomeFeedParser()` on
 * the JVM. A platform fact with no type crossing a public signature, so an `expect` function rather than an `expect` class.
 */
internal expect fun defaultFeedParser(): FeedParser
