package com.spartanlabs.webtools.feeds

// The text rules of the model: pure functions, total on every input, no state, no platform API.

/** TAB, LF, FF, CR and SPACE: the XML white space set plus form feed; a no-break space is not among them. */
internal fun isAsciiSpace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\u000C' || c == '\r'

/** Removes ASCII white space at both ends only. */
internal fun trimAscii(s: String): String {
    var start = 0
    var end = s.length
    while (start < end && isAsciiSpace(s[start])) start++
    while (end > start && isAsciiSpace(s[end - 1])) end--
    return if (start == 0 && end == s.length) s else s.substring(start, end)
}

/** Cuts [s] to at most [max] UTF-16 code units without leaving a high surrogate at the cut. */
internal fun truncate(s: String, max: Int): String {
    if (s.length <= max) return s
    var end = max
    if (end > 0 && s[end - 1].code in 0xD800..0xDBFF) end--
    return s.substring(0, end)
}

/** The text rule: trimmed of ASCII white space, blank is absent, cut to [maxChars] units, absent if that leaves nothing. */
internal fun cleanText(raw: String?, maxChars: Int): String? {
    if (raw == null) return null
    val t = trimAscii(raw)
    if (t.isEmpty()) return null
    return truncate(t, maxChars).ifEmpty { null }
}

/** An enclosure length is known only when positive; zero, a negative value and an absent one are unknown. */
internal fun enclosureLength(parsed: Long): Long? = if (parsed > 0) parsed else null

/** 0001-01-01T00:00:00Z in epoch milliseconds: the earliest date the model keeps. */
internal const val MIN_EPOCH_MILLIS: Long = -62_135_596_800_000L

/** 9999-12-31T23:59:59.999Z in epoch milliseconds: the latest date the model keeps. */
internal const val MAX_EPOCH_MILLIS: Long = 253_402_300_799_999L

/** Whether [ms] lies inside the years 1 to 9999. */
internal fun inSupportedYears(ms: Long): Boolean = ms in MIN_EPOCH_MILLIS..MAX_EPOCH_MILLIS
