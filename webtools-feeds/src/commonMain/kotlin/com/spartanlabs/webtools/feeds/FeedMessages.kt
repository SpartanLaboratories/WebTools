package com.spartanlabs.webtools.feeds

// Every constant text of the module, so tests assert on them and none holds a value.

internal const val FEED_FETCHER_COMPONENT: String = "feed fetcher"
internal const val FEED_FETCHER_LOGGER_NAME: String = "com.spartanlabs.webtools.feeds.FeedFetcher"
internal const val MSG_MAX_CONCURRENT_PARSES: String = "maxConcurrentParses must be at least 1"
internal const val MSG_ACCEPT: String = "accept must be a non-blank header value of visible ASCII, space or tab"
internal const val MSG_ENTRY_ID: String = "id must not be blank"
internal const val MSG_LINK: String = "a link must be a canonical absolute http or https link"
internal const val MSG_ENCLOSURE_LENGTH: String = "lengthBytes must be positive when present"

/** The message of a `FeedLimits` member outside its range: it names the property and its bounds, never the value given. */
internal fun msgLimitRange(name: String, floor: Int, ceiling: Int): String = "$name must be at least $floor and at most $ceiling"
