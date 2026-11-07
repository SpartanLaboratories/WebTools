package com.spartanlabs.webtools.feeds

/**
 * The conditional-GET validators of a feed: the `ETag` and the `Last-Modified` of its last response, held
 * **verbatim**. **Stable Core.** Store what [FeedFetch.validators] returns after every response and hand it back to
 * `FeedFetcher.fetch` on the next poll. A value loaded from your storage is held to the same rules as one read from
 * a response: SP and HTAB are trimmed at both ends; an empty value is `null`; the ETag has at most 256 and the
 * `Last-Modified` at most 64 characters; every character is U+0020 to U+007E (the request type refuses anything
 * else, so a stored non-ASCII tag would fail every later fetch); an ETag is never `*`. A value that breaks a rule
 * becomes `null`, never truncated, and a `Last-Modified` is never parsed or reformatted. Equality is on both
 * values; `toString()` prints whether each is present, never a value.
 *
 * @param etag the entity tag as the server sent it, quotes and `W/` included, or `null`.
 * @param lastModified the `Last-Modified` date as the server sent it, or `null`.
 */
public class FeedValidators(etag: String?, lastModified: String?) {
    /** The entity tag, verbatim, or `null`. */
    public val etag: String? = sanitised(etag, ETAG_MAX_CHARS, rejectStar = true)

    /** The `Last-Modified` date, verbatim, or `null`. */
    public val lastModified: String? = sanitised(lastModified, LAST_MODIFIED_MAX_CHARS, rejectStar = false)

    override fun equals(other: Any?): Boolean = other is FeedValidators && other.etag == etag && other.lastModified == lastModified
    override fun hashCode(): Int = 31 * etag.hashCode() + lastModified.hashCode()
    override fun toString(): String =
        "FeedValidators(etag=${if (etag == null) "absent" else "present"}, lastModified=${if (lastModified == null) "absent" else "present"})"

    /** The constant. */
    public companion object {
        /** Neither validator: an unconditional request. */
        public val None: FeedValidators = FeedValidators(null, null)
    }
}

internal const val ETAG_MAX_CHARS: Int = 256
internal const val LAST_MODIFIED_MAX_CHARS: Int = 64

// Trims SP and HTAB at both ends; null when empty, longer than maxChars, holding any char outside ' '..'~', or
// (when rejectStar) equal to "*". Never truncates.
private fun sanitised(raw: String?, maxChars: Int, rejectStar: Boolean): String? {
    if (raw == null) return null
    val value = raw.trim { it == ' ' || it == '\t' }
    if (value.isEmpty() || value.length > maxChars) return null
    if (value.any { it < ' ' || it > '~' }) return null
    if (rejectStar && value == "*") return null
    return value
}
