package com.spartanlabs.webtools.feeds

// The entry identity rule: pure functions, total on every input, no state, no platform API. The rule is frozen: an
// id a consumer stored must stay the same for the same entry across releases; a new algorithm is a new prefix.

/** The UTF-16 units of a digest field after the cut; a constant of the frozen digest, not a `FeedLimits` value. */
internal const val DIGEST_FIELD_CHARS: Int = 4_096

/** The domain string every digest message starts with. */
internal const val DIGEST_DOMAIN: String = "webtools-feeds/entry-id/1"

/** The prefix of every digest id; a new algorithm is a new prefix. */
internal const val DIGEST_PREFIX: String = "webtools-feeds:v1:"

/** The length of every digest id: the prefix and 32 hex digits (50 characters). */
internal const val DIGEST_ID_CHARS: Int = DIGEST_PREFIX.length + 32

/**
 * Replaces every unpaired surrogate by U+FFFD. The JVM and js encoders differ on what they emit for a lone
 * surrogate, so the digest's UTF-8 bytes would differ by platform without this.
 */
internal fun replaceLoneSurrogates(s: String): String {
    var clean = true
    for (i in s.indices) {
        val n = s[i].code
        if (n in 0xD800..0xDBFF) {
            if (i + 1 >= s.length || s[i + 1].code !in 0xDC00..0xDFFF) { clean = false; break }
        } else if (n in 0xDC00..0xDFFF) {
            if (i == 0 || s[i - 1].code !in 0xD800..0xDBFF) { clean = false; break }
        }
    }
    if (clean) return s
    return buildString(s.length) {
        for (i in s.indices) {
            val n = s[i].code
            val ok = when {
                n in 0xD800..0xDBFF -> i + 1 < s.length && s[i + 1].code in 0xDC00..0xDFFF
                n in 0xDC00..0xDFFF -> i > 0 && s[i - 1].code in 0xD800..0xDBFF
                else -> true
            }
            append(if (ok) s[i] else '�')
        }
    }
}

// A digest field: trimmed of ASCII white space, absent when empty, cut to DIGEST_FIELD_CHARS without leaving a high
// surrogate at the cut (not trimmed again), lone surrogates replaced.
internal fun digestText(v: String?): String? {
    if (v == null) return null
    val t = trimAscii(v)
    if (t.isEmpty()) return null
    return truncate(t, DIGEST_FIELD_CHARS).ifEmpty { null }?.let { replaceLoneSurrogates(it) }
}

/**
 * The digest id of an entry with no usable guid or link: `webtools-feeds:v1:` and the first 16 bytes of the SHA-256
 * of a length-prefixed message, as 32 lower-case hex digits. Depends on no `FeedLimits` value.
 */
internal fun digestId(
    title: String?, summary: String?, content: String?, enclosureUrl: String?, publishedMillis: Long?,
): String {
    // One text field, the first present of: title (tag 't'), summary else content (tag 'b'), first enclosure URL
    // (tag 'e'). The date field (tag 'd') is the published time in whole epoch seconds, floored.
    val text: Pair<Char, String>? = digestText(title)?.let { 't' to it }
        ?: (digestText(summary) ?: digestText(content))?.let { 'b' to it }
        ?: digestText(enclosureUrl)?.let { 'e' to it }
    // Message layout: the domain; then "\n" + tag + <UTF-16 length of the value> + ":" + value for each present
    // field. The length prefix keeps a field boundary from moving: "a" + "b" never collides with "ab".
    val input = buildString {
        append(DIGEST_DOMAIN)
        if (text != null) append('\n').append(text.first).append(text.second.length).append(':').append(text.second)
        if (publishedMillis != null) {
            val seconds = publishedMillis.floorDiv(1000L).toString()
            append('\n').append('d').append(seconds.length).append(':').append(seconds)
        }
    }
    return DIGEST_PREFIX + sha256(input.encodeToByteArray()).toHex(16)
}

/**
 * The id rule: the guid trimmed of ASCII white space when it is not blank and at most [maxIdChars]; else the link
 * exactly as written (trimmed of C0 controls and spaces) when it is not blank, at most [maxIdChars], free of
 * forbidden characters and has no scheme or an `http`/`https` one; else the digest. A blank candidate counts as
 * absent, so the id never breaks the model's non-blank invariant.
 */
internal fun entryId(
    guid: String?, rawLink: String?, title: String?, summary: String?, content: String?,
    enclosureUrl: String?, publishedMillis: Long?, maxIdChars: Int,
): String {
    val g = guid?.let { trimAscii(it) }
    if (!g.isNullOrBlank() && g.length <= maxIdChars) return g
    val l = rawLink?.let { trimC0(it) }
    if (!l.isNullOrBlank() && l.length <= maxIdChars && !hasForbiddenChars(l) && !hasRefusedScheme(l)) return l
    return digestId(title, summary, content, enclosureUrl, publishedMillis)
}
