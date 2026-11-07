package com.spartanlabs.webtools.feeds

import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi

/**
 * Parses the bytes of one feed document into a [Feed]: the parser port of this module. `RomeFeedParser` is the
 * supplied implementation on the JVM; a consumer supplies its own through `FeedFetcherConfig.parser`.
 *
 * **Experimental (implement).** Implementing it needs `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`; calling
 * it and passing one are Stable Core. An implementation must:
 * - be **thread-safe**: [parse] is called from several threads at once, on the fetcher's `parseDispatcher`, and
 *   never suspends;
 * - **never throw for a bad document**: return `Result.failure` with a [FeedFailure] leaf (a constant message, no
 *   cause, no document content). A failure that is not an `HttpFailure`, or an exception thrown out of [parse], is
 *   reported by the fetcher as `HttpFailure.DecodeFailure`;
 * - **refuse every DOCTYPE**, in any encoding the document could be read in, before it reads further
 *   (`FeedFailure.Refused`), and never resolve an external entity, DTD, include or schema location;
 * - **honour every [FeedLimits] member**: exceeding `maxEntries`, `maxDepth`, `maxAttributesPerElement`,
 *   `maxElements` or `maxNamespaceUris` fails with `LimitExceeded`; the text, id, link, category and enclosure
 *   limits truncate, fall back or drop;
 * - build every [Feed], [FeedEntry] and [FeedEnclosure] through their factories, with absolute `http`/`https` links
 *   or `null`, and a never-blank id;
 * - tell a web page, an empty body and a body that is not XML (`NotAFeed`) from malformed XML (`Malformed`) where
 *   it can;
 * - treat a parse as non-cancellable and bound its own CPU: the fetcher cannot interrupt one.
 */
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public interface FeedParser {
    /**
     * Parses one document.
     *
     * @param input the document, the response's `Content-Type` value and the base URL for relative links.
     * @return the feed, or a failure; never throws for a bad document.
     */
    public fun parse(input: FeedInput): Result<Feed>
}
