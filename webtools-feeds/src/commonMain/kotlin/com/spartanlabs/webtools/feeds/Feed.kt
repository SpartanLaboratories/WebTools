package com.spartanlabs.webtools.feeds

import com.spartanlabs.webtools.http.WebToolsDsl

/**
 * A parsed feed: its channel fields and its entries in document order. **Stable Core.** Built with
 * `Feed { title = …; entries = … }`, by `RomeFeedParser` and by a parser of your own.
 *
 * [link] is an absolute `http` or `https` link with its fragment kept, else `null`. [description] is untrusted
 * markup. Equality is on every field. `toString()` prints presence and counts, never text, and the link as scheme,
 * host and port.
 */
public class Feed internal constructor(
    /** The feed's title, or `null`. */
    public val title: String?,
    /** An absolute `http` or `https` link to the feed's web page, or `null`. */
    public val link: String?,
    /** The feed's description: untrusted markup, or `null`. */
    public val description: String?,
    entries: List<FeedEntry>,
) {
    /** The entries, in document order. */
    public val entries: List<FeedEntry> = entries.toList()

    /** Two [Feed]s are equal when their [title], [link], [description] and [entries] are equal. */
    override fun equals(other: Any?): Boolean =
        other is Feed && other.title == title && other.link == link && other.description == description && other.entries == entries

    /** Returns a hash code consistent with [equals]: it is computed from the same properties. */
    override fun hashCode(): Int = listOf(title, link, description, entries).fold(0) { acc, v -> 31 * acc + v.hashCode() }

    /**
     * A diagnostic string for logs: whether [title] and [description] are present, the number of [entries] and the
     * [link] as scheme, host and port, never any text. Its format is not part of the API and may change.
     */
    override fun toString(): String =
        "Feed(title=${presence(title)}, link=${link?.let(::originOfLink) ?: "none"}, description=${presence(description)}, entries=${entries.size})"

    /**
     * Collects the fields of a [Feed]. Not thread-safe; use it only inside the factory's lambda.
     *
     * **Stable Core.**
     */
    @WebToolsDsl
    public class Builder internal constructor() {
        /** The title; `null` by default. */
        public var title: String? = null

        /** The link; `null` by default. When set, a canonical absolute `http` or `https` link. */
        public var link: String? = null

        /** The description; `null` by default. */
        public var description: String? = null

        /** The entries; empty by default. */
        public var entries: List<FeedEntry> = emptyList()
    }
}

/**
 * Builds a [Feed]. **Stable Core.** The factory requires the invariants a value must hold and applies no limit and
 * no fallback: a link, when set, is a canonical absolute link (lower-case `http` or `https` scheme, no white space,
 * control character or backslash, no user-info, a host of letters, digits, `-`, `.`, `_` or non-ASCII characters or
 * a bracketed IPv6 literal, a port of at most 65535).
 *
 * @param configure sets the fields; none when omitted.
 * @return the feed.
 * @throws IllegalArgumentException if the link is not a canonical absolute link; the message never echoes a value.
 */
public fun Feed(configure: Feed.Builder.() -> Unit = {}): Feed {
    val b = Feed.Builder().apply(configure)
    val link = b.link
    require(link == null || isCanonicalLink(link)) { MSG_LINK }
    return Feed(b.title, link, b.description, b.entries)
}
