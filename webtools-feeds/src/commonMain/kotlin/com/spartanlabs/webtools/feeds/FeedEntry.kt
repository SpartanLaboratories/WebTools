package com.spartanlabs.webtools.feeds

import com.spartanlabs.webtools.http.WebToolsDsl

/**
 * One entry of a feed. **Stable Core.** Built with `FeedEntry(id) { title = …; link = … }`, by `RomeFeedParser` and
 * by a parser of your own.
 *
 * **What every entry guarantees** (the factory enforces it, whichever parser built the entry):
 * - [id] is never blank.
 * - [link], when present, is a canonical absolute `http` or `https` link with its fragment kept. `Url` is not used: it drops
 *   fragments and refuses non-ASCII hosts.
 * - every enclosure has a canonical absolute URL and a [FeedEnclosure.lengthBytes] that is `null` or positive.
 *
 * Whichever parser built the entry, treat [summary] and [content] as **untrusted markup** (HTML, XHTML or text, the format
 * does not say) and sanitise them before rendering.
 *
 * **As `RomeFeedParser` builds it** (a parser of your own guarantees only the invariants above):
 * - [id] is the guid or Atom id, else the entry's link as written, else a digest `webtools-feeds:v1:<32 hex digits>` of its
 *   title, summary or content, enclosure URL and published date. The rule is frozen: an id you stored stays the same for the
 *   same entry across releases, and does not change when the feed moves host or protocol. An entry with nothing to hash has
 *   the constant digest of nothing, so several can share an id.
 * - [publishedAtEpochMillis] and [updatedAtEpochMillis] are `null` when absent, unparseable or outside the years 1 to 9999.
 * - [categories] hold no exact duplicates; [enclosures] hold no two with the same URL.
 *
 * Never use the dates to decide whether an entry is new: use [id]. Equality is on every field. `toString()` prints counts and
 * presence, never text, and links as scheme, host and port.
 */
public class FeedEntry internal constructor(
    /** The entry's identity; never blank. */
    public val id: String,
    /** The title, or `null`. */
    public val title: String?,
    /** An absolute `http` or `https` link, fragment kept, or `null`. */
    public val link: String?,
    /** The summary: untrusted markup, or `null`. */
    public val summary: String?,
    /** The content: untrusted markup, or `null`. */
    public val content: String?,
    /** The author, or `null`. */
    public val author: String?,
    /** When the entry was published, in epoch milliseconds, or `null`. */
    public val publishedAtEpochMillis: Long?,
    /** When the entry was last updated, in epoch milliseconds, or `null`. */
    public val updatedAtEpochMillis: Long?,
    categories: List<String>,
    enclosures: List<FeedEnclosure>,
) {
    /** The categories, in document order. */
    public val categories: List<String> = categories.toList()

    /** The enclosures, in document order. */
    public val enclosures: List<FeedEnclosure> = enclosures.toList()

    override fun equals(other: Any?): Boolean =
        other is FeedEntry && other.id == id && other.title == title && other.link == link && other.summary == summary &&
            other.content == content && other.author == author && other.publishedAtEpochMillis == publishedAtEpochMillis &&
            other.updatedAtEpochMillis == updatedAtEpochMillis && other.categories == categories && other.enclosures == enclosures

    override fun hashCode(): Int =
        listOf(id, title, link, summary, content, author, publishedAtEpochMillis, updatedAtEpochMillis, categories, enclosures)
            .fold(0) { acc, v -> 31 * acc + v.hashCode() }

    override fun toString(): String =
        "FeedEntry(idChars=${id.length}, title=${presence(title)}, link=${link?.let(::originOfLink) ?: "none"}, " +
            "summary=${presence(summary)}, content=${presence(content)}, author=${presence(author)}, " +
            "publishedAt=${presence(publishedAtEpochMillis)}, updatedAt=${presence(updatedAtEpochMillis)}, " +
            "categories=${categories.size}, enclosures=${enclosures.size})"

    /**
     * Collects the optional fields of a [FeedEntry]. Not thread-safe; use it only inside the factory's lambda.
     *
     * **Stable Core.**
     */
    @WebToolsDsl
    public class Builder internal constructor() {
        /** The title; `null` by default. */
        public var title: String? = null

        /** The link; `null` by default. When set, a canonical absolute `http` or `https` link. */
        public var link: String? = null

        /** The summary; `null` by default. */
        public var summary: String? = null

        /** The content; `null` by default. */
        public var content: String? = null

        /** The author; `null` by default. */
        public var author: String? = null

        /** The published date in epoch milliseconds; `null` by default. */
        public var publishedAtEpochMillis: Long? = null

        /** The updated date in epoch milliseconds; `null` by default. */
        public var updatedAtEpochMillis: Long? = null

        /** The categories; empty by default. */
        public var categories: List<String> = emptyList()

        /** The enclosures; empty by default. */
        public var enclosures: List<FeedEnclosure> = emptyList()
    }
}

/**
 * Builds a [FeedEntry]. **Stable Core.** The factory requires the invariants a value must hold and applies no limit
 * and no fallback: a link, when set, is a canonical absolute link (lower-case `http` or `https` scheme, no white
 * space, control character or backslash, no user-info, a host of letters, digits, `-`, `.`, `_` or non-ASCII
 * characters or a bracketed IPv6 literal, a port of at most 65535).
 *
 * @param id the entry's identity: not blank.
 * @param configure sets the optional fields; none when omitted.
 * @return the entry.
 * @throws IllegalArgumentException if [id] is blank or the link is not a canonical absolute link; the message never
 *   echoes a value.
 */
public fun FeedEntry(id: String, configure: FeedEntry.Builder.() -> Unit = {}): FeedEntry {
    val b = FeedEntry.Builder().apply(configure)
    require(id.isNotBlank()) { MSG_ENTRY_ID }
    val link = b.link
    require(link == null || isCanonicalLink(link)) { MSG_LINK }
    return FeedEntry(id, b.title, link, b.summary, b.content, b.author, b.publishedAtEpochMillis, b.updatedAtEpochMillis, b.categories, b.enclosures)
}

/**
 * What the model's `toString()` prints for an optional field: `present` or `none`, never the value.
 *
 * @param value the field's value.
 * @return `none` when [value] is `null`, else `present`.
 */
internal fun presence(value: Any?): String = if (value == null) "none" else "present"
