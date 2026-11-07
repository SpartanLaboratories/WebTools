package com.spartanlabs.webtools.feeds

import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.WebToolsDsl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The configuration of a [FeedFetcher]: the receiver of the factory's lambda. **Stable Core.** Not thread-safe; use it only
 * inside the lambda.
 */
@WebToolsDsl
public class FeedFetcherConfig internal constructor() {
    /** The parser; the platform's default (`RomeFeedParser()` on the JVM) by default. Implementing your own needs the opt-in of [FeedParser]. */
    public var parser: FeedParser = defaultFeedParser()

    /** Where parses run; `Dispatchers.Default` by default. A parse is CPU-bound and cannot be cancelled: do not give it a dispatcher that must stay responsive. */
    public var parseDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * How many parses of this fetcher run at once; [DEFAULT_MAX_CONCURRENT_PARSES] by default; at least 1. The heap a deployment
     * needs grows with the sum of this over every fetcher in the process (about 128 MB of headroom for two parses of 2 MiB documents).
     */
    public var maxConcurrentParses: Int = DEFAULT_MAX_CONCURRENT_PARSES

    /** The `Accept` header value; [DEFAULT_ACCEPT] by default. Not blank; only HTAB and U+0020 to U+007E. */
    public var accept: String = DEFAULT_ACCEPT

    /** The defaults. */
    public companion object {
        /** 2. */
        public const val DEFAULT_MAX_CONCURRENT_PARSES: Int = 2

        /** The default `Accept` list: the two feed types registered by their own specifications, RSS 1.0 at 0.9, the generic XML types at 0.8 and a wildcard at 0.1. */
        public const val DEFAULT_ACCEPT: String = "application/atom+xml, application/rss+xml, application/rdf+xml;q=0.9, application/xml;q=0.8, text/xml;q=0.8, */*;q=0.1"
    }

    /** @throws IllegalArgumentException if [maxConcurrentParses] is below 1 (`MSG_MAX_CONCURRENT_PARSES`) or [accept] is blank or not a sendable header value (`MSG_ACCEPT`). */
    internal fun resolve(): ResolvedFeedFetcher {
        require(maxConcurrentParses >= 1) { MSG_MAX_CONCURRENT_PARSES }
        require(isSendableNonBlank(accept)) { MSG_ACCEPT }
        return ResolvedFeedFetcher(parser, parseDispatcher, maxConcurrentParses, accept)
    }
}

/** The checked configuration a fetcher is built from. */
internal class ResolvedFeedFetcher(val parser: FeedParser, val parseDispatcher: CoroutineDispatcher, val maxConcurrentParses: Int, val accept: String)

/** What a fetcher is made of; the factories build one, so no consumer-shaped constructor exists. */
internal class FeedFetcherParts(val urlFetcher: UrlFetcher, val resolved: ResolvedFeedFetcher, val ownsUrlFetcher: Boolean)

// The value trimmed of SP and HTAB is not empty and every character is HTAB or U+0020 to U+007E: the rule HttpRequest enforces,
// which this module cannot call.
internal fun isSendableNonBlank(value: String): Boolean =
    value.trim { it == ' ' || it == '\t' }.isNotEmpty() && value.all { it == '\t' || it in ' '..'~' }
