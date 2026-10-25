package com.spartanlabs.webtools.http

/**
 * The shared predicates of RFC 9110 syntax, as `Char` comparisons with no regex and no JVM API. The
 * value types check with them; a transport and the test fixtures reuse them. Protocol text never goes
 * through `lowercase()` or `equals(ignoreCase = true)`, which fold U+212A (Kelvin) to `k` and U+0131 to
 * `i`; the fold here is ASCII-only.
 */
internal object HttpSyntax {
    private const val TOKEN_SPECIALS: String = "!#\$%&'*+-.^_`|~"
    private val CREDENTIAL_NAMES: List<String> = listOf("Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie")

    /** True for the 77 `tchar`s: `A-Z a-z 0-9` and the 15 specials. */
    fun isTokenChar(c: Char): Boolean = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in TOKEN_SPECIALS

    /** The index of the first non-`tchar`, or -1 when every character is one (also -1 for empty text). */
    fun firstNonTokenIndex(text: CharSequence): Int = text.indexOfFirst { !isTokenChar(it) }

    /** True when [text] is a non-empty run of `tchar`s. */
    fun isToken(text: CharSequence): Boolean = text.isNotEmpty() && firstNonTokenIndex(text) < 0

    /** Removes leading and trailing SP and HTAB, nothing else. */
    fun trimOws(value: String): String = value.trim { it == ' ' || it == '\t' }

    /** The container rule: no U+000A, U+000D or U+0000 (applied after [trimOws]). */
    fun isFieldValue(value: String): Boolean = value.none { it == '\r' || it == '\n' || it == '\u0000' }

    /** The sendable rule: every character is U+0009 or in U+0020..U+007E. */
    fun isSendableValue(value: String): Boolean = value.all { it == '\t' || it in ' '..'~' }

    /** What a transport does with a received value: each CR, LF and NUL becomes SP, then [trimOws]. */
    fun sanitizeReceivedValue(raw: String): String =
        trimOws(raw.map { if (it == '\r' || it == '\n' || it == '\u0000') ' ' else it }.joinToString(""))

    /** `A-Z` to `a-z`, nothing else. */
    fun asciiLower(c: Char): Char = if (c in 'A'..'Z') c + ('a' - 'A') else c

    /** Header-name equality: same length and [asciiLower]-equal at each index. */
    fun equalsName(a: String, b: String): Boolean =
        a.length == b.length && a.indices.all { asciiLower(a[it]) == asciiLower(b[it]) }

    /** True when [name] is `Authorization`, `Proxy-Authorization`, `Cookie` or `Set-Cookie`, in any case. */
    fun isCredentialName(name: String): Boolean = CREDENTIAL_NAMES.any { equalsName(it, name) }

    /**
     * -1 when [text] is an RFC 9110 media type (its visible-ASCII subset, no leading or trailing
     * whitespace); otherwise the index at which it stops being one: the offending character, the start of
     * trailing whitespace, or the text length when the text ends too early.
     */
    fun mediaTypeProblemIndex(text: String): Int {
        val typeEnd = tokenEnd(text, 0)
        if (typeEnd == 0 || typeEnd >= text.length || text[typeEnd] != '/') return typeEnd
        val subtypeStart = typeEnd + 1
        val subtypeEnd = tokenEnd(text, subtypeStart)
        if (subtypeEnd == subtypeStart) return subtypeStart
        var pos = subtypeEnd
        while (pos < text.length) {
            val semi = skipOws(text, pos)
            if (semi >= text.length) return pos            // trailing whitespace
            if (text[semi] != ';') return semi
            val paramStart = skipOws(text, semi + 1)
            // Plan Amendment 1: text ending right after ';' is a valid empty parameter (-1); text ending in OWS
            // after ';' is trailing whitespace, refused at its start (semi + 1).
            if (paramStart >= text.length) return if (paramStart == semi + 1) -1 else semi + 1
            if (text[paramStart] == ';') { pos = paramStart; continue }   // an empty parameter
            val nameEnd = tokenEnd(text, paramStart)
            if (nameEnd == paramStart) return paramStart
            if (nameEnd >= text.length || text[nameEnd] != '=') return nameEnd
            val valueStart = nameEnd + 1
            val quoted = valueStart < text.length && text[valueStart] == '"'
            val valueEnd = if (quoted) quotedEnd(text, valueStart) else tokenEnd(text, valueStart)
            if (valueEnd < 0) return -1 - valueEnd         // quotedEnd reports a failure as -1 - index
            if (valueEnd == valueStart) return valueStart  // an empty token value
            pos = valueEnd
        }
        return -1
    }

    private fun tokenEnd(text: String, from: Int): Int {
        var i = from
        while (i < text.length && isTokenChar(text[i])) i++
        return i
    }

    private fun skipOws(text: String, from: Int): Int {
        var i = from
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        return i
    }

    // The index after the closing quote, or -1 - (failing index).
    private fun quotedEnd(text: String, open: Int): Int {
        var i = open + 1
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> return i + 1
                c == '\\' -> {
                    val escaped = text.getOrNull(i + 1)
                    if (escaped == null || !(escaped == '\t' || escaped in ' '..'~')) return -1 - (i + 1)
                    i += 2
                }
                c == '\t' || c == ' ' || c == '!' || c in '#'..'[' || c in ']'..'~' -> i++
                else -> return -1 - i
            }
        }
        return -1 - text.length
    }
}
