package com.spartanlabs.webtools.feeds

/**
 * The supplied [FeedParser]: RSS 0.90 to 2.0, RSS 1.0 and Atom 0.3 and 1.0 over Rome 2.1.0. **Stable Core**, usable
 * without an opt-in; no Rome type is part of its API.
 *
 * Hardening, in order: the DOCTYPE prolog scan (no parser runs on a DOCTYPE, on a web page, on an empty or non-XML
 * body, or on a prolog it cannot read the way the parser reads it); then Rome with a SAX parser of its own per parse,
 * DOCTYPE and external entities off, the JDK's depth and attribute limits and a counting handler for elements and
 * namespace URIs, all from [limits]; then the conversion under the remaining [FeedLimits]. Rome's ampersand and entity
 * repair and its encoding detection apply. Documents with XML-illegal control characters are `Malformed`.
 *
 * How it builds each [FeedEntry]: the id is the guid or Atom id, else the entry's link as written, else the frozen
 * `webtools-feeds:v1:` digest; text is trimmed and cut at `maxTextChars`; links are resolved against [FeedInput.baseUrl] (the
 * response URL when `FeedFetcher` calls it) and the Atom `xml:base` values, and are `null` when not absolute `http`/`https`; dates are `null` when unparseable or outside
 * the years 1 to 9999; categories are trimmed, exact duplicates dropped and capped; enclosures with an unusable URL are dropped,
 * repeated URLs dropped and the rest capped, and a length of zero or less is unknown.
 *
 * Immutable and thread-safe: every [parse] creates its own reader, input, builder and SAX parser, and sets no process-wide
 * configuration of Rome, JDOM or the JDK (no shared SAX factory, no system property, no default changed). The one shared
 * state a parse grows is JDOM's process-wide namespace cache, by at most `maxNamespaceUris` new URIs per document. It logs nothing of its own; Rome itself logs rarely, under `com.rometools.*`
 * loggers. A parse is CPU-bound and cannot be cancelled. Needs about 128 MB of heap headroom for two concurrent parses of the
 * largest document an `Untrusted` transport admits (2 MiB).
 *
 * @param limits the bounds to apply.
 */
public class RomeFeedParser(private val limits: FeedLimits) : FeedParser {
    /** A parser with the default [FeedLimits]. */
    public constructor() : this(FeedLimits())

    /**
     * Parses one document.
     *
     * @param input the document.
     * @return the feed, or `NotAFeed`, `Malformed`, `Refused` or `LimitExceeded`; never throws for a bad document. An
     *   exception out of this module's own conversion (a defect) propagates.
     */
    override fun parse(input: FeedInput): Result<Feed> {
        val document = input.documentView()
        PrologScan.scan(document, input.contentType).toFailureOrNull()?.let { return Result.failure(it) }
        return readWithRome(document, input.contentType, limits).flatMap { it.toFeed(limits, input.baseUrl) }
    }
}
