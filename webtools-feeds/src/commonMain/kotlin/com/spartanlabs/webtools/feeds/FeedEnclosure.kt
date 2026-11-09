package com.spartanlabs.webtools.feeds

import com.spartanlabs.webtools.http.WebToolsDsl

/**
 * An enclosure of a feed entry: a media file the entry points at. **Stable Core.** Built with
 * `FeedEnclosure(url) { mimeType = …; lengthBytes = … }`.
 *
 * [url] is an absolute `http` or `https` link with its fragment kept. [lengthBytes] is the declared size when the
 * feed gave a positive one, else `null`: a length of zero or less is unknown. Equality is on every field.
 * `toString()` prints the URL as scheme, host and port, and presence for the rest.
 */
public class FeedEnclosure internal constructor(
    /** An absolute `http` or `https` link to the media file. */
    public val url: String,
    /** The declared MIME type, or `null`. */
    public val mimeType: String?,
    /** The declared size in bytes, positive, or `null` when unknown. */
    public val lengthBytes: Long?,
) {
    /** Two [FeedEnclosure]s are equal when their [url], [mimeType] and [lengthBytes] are equal. */
    override fun equals(other: Any?): Boolean =
        other is FeedEnclosure && other.url == url && other.mimeType == mimeType && other.lengthBytes == lengthBytes

    /** Returns a hash code consistent with [equals]: it is computed from the same properties. */
    override fun hashCode(): Int = listOf(url, mimeType, lengthBytes).fold(0) { acc, v -> 31 * acc + v.hashCode() }

    /**
     * A diagnostic string for logs: the [url] as scheme, host and port, and whether [mimeType] and [lengthBytes] are
     * present, never their values. Its format is not part of the API and may change.
     */
    override fun toString(): String =
        "FeedEnclosure(url=${originOfLink(url)}, mimeType=${presence(mimeType)}, lengthBytes=${presence(lengthBytes)})"

    /**
     * Collects the optional fields of a [FeedEnclosure]. Not thread-safe; use it only inside the factory's lambda.
     *
     * **Stable Core.**
     */
    @WebToolsDsl
    public class Builder internal constructor() {
        /** The MIME type; `null` by default. */
        public var mimeType: String? = null

        /** The size in bytes; `null` by default. Positive when set. */
        public var lengthBytes: Long? = null
    }
}

/**
 * Builds a [FeedEnclosure]. **Stable Core.** The factory requires the invariants a value must hold and applies no
 * limit and no fallback: the URL is a canonical absolute link (lower-case `http` or `https` scheme, no white space,
 * control character or backslash, no user-info, a host of letters, digits, `-`, `.`, `_` or non-ASCII characters or
 * a bracketed IPv6 literal, a port of at most 65535).
 *
 * @param url the media file's link: a canonical absolute `http` or `https` link.
 * @param configure sets the optional fields; none when omitted.
 * @return the enclosure.
 * @throws IllegalArgumentException if [url] is not a canonical absolute link or `lengthBytes` is set and not
 *   positive; the message never echoes a value.
 */
public fun FeedEnclosure(url: String, configure: FeedEnclosure.Builder.() -> Unit = {}): FeedEnclosure {
    val b = FeedEnclosure.Builder().apply(configure)
    require(isCanonicalLink(url)) { MSG_LINK }
    val lengthBytes = b.lengthBytes
    require(lengthBytes == null || lengthBytes > 0) { MSG_ENCLOSURE_LENGTH }
    return FeedEnclosure(url, b.mimeType, lengthBytes)
}
