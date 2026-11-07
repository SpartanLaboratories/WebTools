package com.spartanlabs.webtools.feeds

// The link rule of the model: pure functions, total on every input, no state, no platform API. A rejected link is
// null; nothing here throws. Every scan is linear and the length check precedes it.

//region character rules
/** Trims every leading and trailing code unit up to U+0020 (WHATWG "C0 control or space"). */
internal fun trimC0(s: String): String {
    var start = 0
    var end = s.length
    while (start < end && s[start].code <= 0x20) start++
    while (end > start && s[end - 1].code <= 0x20) end--
    return if (start == 0 && end == s.length) s else s.substring(start, end)
}

/** True when [s] holds a char at or below U+0020, one from U+007F to U+009F, or a backslash. */
internal fun hasForbiddenChars(s: String): Boolean {
    for (c in s) {
        val n = c.code
        if (n <= 0x20 || n in 0x7F..0x9F || c == '\\') return true
    }
    return false
}

private fun isSchemeStart(c: Char) = c in 'a'..'z' || c in 'A'..'Z'
private fun isSchemeChar(c: Char) = isSchemeStart(c) || c in '0'..'9' || c == '+' || c == '-' || c == '.'

/** Index of the colon when [s] has one before any `/`, `?` or `#`, else -1. */
private fun schemeEnd(s: String): Int {
    for (i in s.indices) {
        val c = s[i]
        if (c == ':') return i
        if (c == '/' || c == '?' || c == '#') return -1
    }
    return -1
}

/** True when [s] has a colon in its first segment that is not the end of `http` or `https`. */
internal fun hasRefusedScheme(s: String): Boolean {
    val e = schemeEnd(s)
    if (e < 0) return false
    val scheme = s.substring(0, e)
    return !(scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true))
}

// No user-info; a host of letters, digits, '-', '.', '_' or chars at or above U+00A0, or a bracketed IPv6 literal;
// a port of at most five digits and at most 65535 (an empty port after ':' is allowed).
private fun validAuthority(a: String): Boolean {
    if (a.isEmpty() || a.indexOf('@') >= 0) return false
    val port: String?
    if (a[0] == '[') {
        val close = a.indexOf(']')
        if (close < 2) return false
        val lit = a.substring(1, close)
        if (lit.indexOf(':') < 0 ||
            !lit.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }
        ) return false
        val rest = a.substring(close + 1)
        if (rest.isNotEmpty() && rest[0] != ':') return false
        port = if (rest.isEmpty()) null else rest.substring(1)
    } else {
        val c = a.lastIndexOf(':')
        val host = if (c >= 0) a.substring(0, c) else a
        port = if (c >= 0) a.substring(c + 1) else null
        if (host.isEmpty() ||
            !host.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '.' || it == '_' || it.code >= 0xA0 }
        ) return false
    }
    if (!port.isNullOrEmpty()) {
        if (port.length > 5 || !port.all { it in '0'..'9' } || port.toInt() > 65_535) return false
    }
    return true
}
//endregion

//region RFC 3986 decomposition and resolution
private class Ref(val scheme: String?, val authority: String?, val path: String, val query: String?, val fragment: String?)

/** RFC 3986 Appendix B decomposition without a regex; null when a colon in the first segment is not a scheme. */
private fun decompose(s: String): Ref? {
    var rest = s
    var fragment: String? = null
    val h = rest.indexOf('#')
    if (h >= 0) { fragment = rest.substring(h + 1); rest = rest.substring(0, h) }
    var query: String? = null
    val q = rest.indexOf('?')
    if (q >= 0) { query = rest.substring(q + 1); rest = rest.substring(0, q) }
    var scheme: String? = null
    val e = schemeEnd(rest)
    if (e >= 0) {
        if (e == 0 || !isSchemeStart(rest[0]) || !rest.substring(0, e).all { isSchemeChar(it) }) return null
        scheme = rest.substring(0, e).lowercase()
        rest = rest.substring(e + 1)
    }
    var authority: String? = null
    if (rest.startsWith("//")) {
        val end = rest.indexOf('/', 2).let { if (it < 0) rest.length else it }
        authority = rest.substring(2, end)
        rest = rest.substring(end)
    }
    return Ref(scheme, authority, rest, query, fragment)
}

/** RFC 3986 section 5.2.4, over an index into the input and a single output buffer (linear time). */
internal fun removeDotSegments(path: String): String {
    val out = StringBuilder(path.length)
    var i = 0
    val n = path.length
    // Drops the last segment of the output (and its leading slash), as `/..` does.
    fun dropLastSegment() {
        val cut = maxOf(out.lastIndexOf("/"), 0)
        out.deleteRange(cut, out.length)
    }
    while (i < n) {
        when {
            path.startsWith("../", i) -> i += 3
            path.startsWith("./", i) -> i += 2
            path.startsWith("/./", i) -> i += 2
            path.startsWith("/.", i) && i + 2 == n -> { out.append('/'); i = n }
            path.startsWith("/../", i) -> { i += 3; dropLastSegment() }
            path.startsWith("/..", i) && i + 3 == n -> { dropLastSegment(); out.append('/'); i = n }
            (path.startsWith(".", i) && i + 1 == n) || (path.startsWith("..", i) && i + 2 == n) -> i = n
            else -> {
                val next = path.indexOf('/', i + 1).let { if (it < 0) n else it }
                out.appendRange(path, i, next)
                i = next
            }
        }
    }
    return out.toString()
}

// RFC 3986 section 5.2.3: the base path up to and including its last slash, then the reference path.
private fun merge(base: Ref, refPath: String): String {
    if (base.authority != null && base.path.isEmpty()) return "/$refPath"
    val i = base.path.lastIndexOf('/')
    return (if (i >= 0) base.path.substring(0, i + 1) else "") + refPath
}

private fun recompose(scheme: String, authority: String?, path: String, query: String?, fragment: String?): String =
    buildString {
        append(scheme).append(':')
        if (authority != null) append("//").append(authority)
        append(path)
        if (query != null) append('?').append(query)
        if (fragment != null) append('#').append(fragment)
    }

/**
 * The link rule: an absolute `http` or `https` link, else null. [raw] is trimmed of C0 controls and spaces, refused
 * above [maxChars] or with a forbidden character, and when it is a relative reference and [allowRelative] holds it
 * is resolved against [base] by RFC 3986 section 5.2; the result is at most [maxChars]. An absolute input keeps
 * everything but the scheme's case as written, the fragment included.
 */
internal fun resolveLink(raw: String?, base: String?, maxChars: Int, allowRelative: Boolean = true): String? {
    if (raw == null) return null
    val s = trimC0(raw)
    if (s.isEmpty() || s.length > maxChars || hasForbiddenChars(s)) return null
    val r = decompose(s) ?: return null
    val result: String
    if (r.scheme != null) {
        if ((r.scheme != "http" && r.scheme != "https") || r.authority == null || !validAuthority(r.authority)) return null
        result = r.scheme + s.substring(r.scheme.length)       // scheme lower-cased, the rest as written
    } else {
        if (!allowRelative) return null
        val b = base?.let { trimC0(it) }?.takeIf { it.length <= maxChars && !hasForbiddenChars(it) }?.let { decompose(it) }
        if (b == null || b.scheme == null || (b.scheme != "http" && b.scheme != "https") ||
            b.authority == null || !validAuthority(b.authority)
        ) return null
        // RFC 3986 section 5.2.2, strict, in its three cases: a reference with its own authority takes only the base
        // scheme; an empty path keeps the base path and, without a query of its own, the base query; otherwise an
        // absolute path is dot-normalised and a relative one is merged with the base path first. The base fragment is
        // never used.
        if (r.authority != null) {
            if (!validAuthority(r.authority)) return null
            result = recompose(b.scheme, r.authority, removeDotSegments(r.path), r.query, r.fragment)
        } else if (r.path.isEmpty()) {
            result = recompose(b.scheme, b.authority, b.path, r.query ?: b.query, r.fragment)
        } else {
            val p = if (r.path.startsWith("/")) removeDotSegments(r.path) else removeDotSegments(merge(b, r.path))
            result = recompose(b.scheme, b.authority, p, r.query, r.fragment)
        }
    }
    return result.takeIf { it.length <= maxChars }
}
//endregion

//region derived rules
/**
 * Whether [s] is a link exactly as the link rule would return it: a lower-case `http` or `https` scheme, no white
 * space, control character or backslash, no user-info, and a host and port that pass the rule. The invariant the
 * model's factories require of a link.
 */
internal fun isCanonicalLink(s: String): Boolean = resolveLink(s, null, Int.MAX_VALUE, allowRelative = false) == s

/**
 * The base for links below an element carrying `xml:base`: [parent] when there is no `xml:base`, else the
 * `xml:base` resolved against [parent]. An `xml:base` that fails to resolve gives null, so relative links below
 * it become null rather than falling back to the document URL, which could put a link on the wrong host.
 */
internal fun narrowBase(parent: String?, xmlBase: String?, maxChars: Int): String? =
    if (xmlBase == null || xmlBase.isBlank()) parent else resolveLink(xmlBase, parent, maxChars)
//endregion
