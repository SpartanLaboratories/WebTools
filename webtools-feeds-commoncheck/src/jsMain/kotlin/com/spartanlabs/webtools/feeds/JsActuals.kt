package com.spartanlabs.webtools.feeds

// The js target exists only to compile commonMain; nothing runs it, so logging is a no-op.
private object NoOpLog : Log {
    override fun debug(message: String, vararg args: Any?) = Unit
    override fun info(message: String, vararg args: Any?) = Unit
    override fun warn(message: String, vararg args: Any?) = Unit
}

internal actual fun logger(name: String): Log = NoOpLog

// No parser exists for js; the default says so with the failure every parser gives for a body it cannot read.
private object NoParser : FeedParser {
    override fun parse(input: FeedInput): Result<Feed> = Result.failure(FeedFailure.NotAFeed(NotAFeedReason.UnknownFormat))
}

internal actual fun defaultFeedParser(): FeedParser = NoParser
