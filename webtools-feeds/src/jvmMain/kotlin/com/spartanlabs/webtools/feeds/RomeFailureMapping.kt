package com.spartanlabs.webtools.feeds

import org.xml.sax.SAXParseException

private const val ROME_INVALID_DOCUMENT = "Invalid document"
private const val JAXP_MAX_ELEMENT_DEPTH = "JAXP00010006"
private const val JAXP_ELEMENT_ATTRIBUTE_LIMIT = "JAXP00010002"
private const val DOCTYPE_FEATURE_NAME = "disallow-doctype-decl"
private const val CAUSE_CHAIN_LIMIT = 8

/**
 * Maps what Rome, JDOM and the JDK throw to a [FeedFailure], by type and by the JDK's documented error code, never by a
 * message's wording; no message or cause is kept. In this order:
 * 1. exactly [IllegalArgumentException] (the class, not a subclass) with Rome's message `Invalid document` →
 *    `NotAFeed(UnknownFormat)` (a `NumberFormatException` from `<cloud port="abc">` is a subclass, carries document text
 *    and is `Malformed`);
 * 2. a [LimitReached] in the cause chain (at most 8 deep) → `LimitExceeded` of its limit;
 * 3. a `SAXParseException` in the chain whose message starts with `JAXP00010006` → `LimitExceeded(Depth)`,
 *    `JAXP00010002` → `LimitExceeded(Attributes)`; its message contains the `disallow-doctype-decl` feature name →
 *    `Refused(Doctype)` (Rome's own lock fired);
 * 4. anything else — every other `ParsingFeedException`, an `IOException` from `XmlReader`, any other runtime exception
 *    from the Rome region, the JAXP name-length and entity codes — → `Malformed`.
 */
internal fun Throwable.toFeedFailure(): FeedFailure {
    if (this::class == IllegalArgumentException::class && message == ROME_INVALID_DOCUMENT) {
        return FeedFailure.NotAFeed(NotAFeedReason.UnknownFormat)
    }
    val chain = generateSequence(this) { it.cause }.take(CAUSE_CHAIN_LIMIT).toList()
    chain.firstOrNull { it is LimitReached }?.let { return FeedFailure.LimitExceeded((it as LimitReached).limit) }
    for (link in chain) {
        if (link !is SAXParseException) continue
        val text = link.message ?: continue
        when {
            text.startsWith(JAXP_MAX_ELEMENT_DEPTH) -> return FeedFailure.LimitExceeded(FeedLimit.Depth)
            text.startsWith(JAXP_ELEMENT_ATTRIBUTE_LIMIT) -> return FeedFailure.LimitExceeded(FeedLimit.Attributes)
            text.contains(DOCTYPE_FEATURE_NAME) -> return FeedFailure.Refused(FeedRefusal.Doctype)
        }
    }
    return FeedFailure.Malformed()
}

/**
 * The one broad catch of the module: runs [block] (the `XmlReader` construction, `build` and `SyndFeedImpl`) and maps
 * what it throws. An [Exception] other than a cancellation becomes a mapped failure; a [StackOverflowError] (from
 * JDOM's serialiser on markup nested inside an RSS description or an Atom `xhtml` text, unreachable under the depth cap
 * on a stack of 256 KiB or more) becomes `LimitExceeded(Depth)` as a backstop; every other [Error] propagates.
 */
internal inline fun <T> inRomeRegion(block: () -> T): Result<T> =
    try {
        catchNonFatal(block).fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(it.toFeedFailure()) })
    } catch (_: StackOverflowError) {
        Result.failure(FeedFailure.LimitExceeded(FeedLimit.Depth))
    }
