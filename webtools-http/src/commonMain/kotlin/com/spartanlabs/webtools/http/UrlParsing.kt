package com.spartanlabs.webtools.http

// The parse pipeline of Url.parse: trim, scan, scheme, authority, host and port, then path and query.
// Each step is a pure function returning a value or a Result, joined with flatMap; none throws, and the
// first failing step decides the kind of problem.

/** The parts of a parsed URL, canonical, before they become a [Url]. */
internal class ParsedUrl(val scheme: String, val host: String, val port: Int, val encodedPath: String, val encodedQuery: String?)

internal fun defaultPortOf(scheme: String): Int = if (scheme == "https") 443 else 80

internal fun parseUrlText(text: String): Result<ParsedUrl> =
    trimUrlText(text)
        .flatMap { checkUrlCharacters(it) }
        .flatMap { splitScheme(it) }
        .flatMap { splitAuthority(it) }
        .flatMap { parseAuthority(it) }
        .map { assemble(it) }

private class SchemeSplit(val scheme: String, val afterSeparator: String)
private class AuthoritySplit(val scheme: String, val authority: String, val tail: String)
private class HostAndPort(val scheme: String, val host: String, val port: Int, val tail: String)

//region trim and scan
private fun isTrimmedEnd(c: Char): Boolean = c == '\t' || c == '\n' || c == '\u000C' || c == '\r' || c == ' '

private fun trimUrlText(text: String): Result<String> {
    val start = text.indexOfFirst { !isTrimmedEnd(it) }
    if (start < 0) return urlFailure(UrlProblem.Empty)
    return Result.success(text.substring(start, text.indexOfLast { !isTrimmedEnd(it) } + 1))
}

// C0 and the space, DEL and C1 and NBSP, and the other Unicode White_Space code points.
private fun isUrlSpace(c: Char): Boolean {
    val code = c.code
    return code <= 0x20 || code in 0x7F..0xA0 || code == 0x1680 || code in 0x2000..0x200A ||
        code == 0x2028 || code == 0x2029 || code == 0x202F || code == 0x205F || code == 0x3000
}

// One scan, left to right; the first offending character decides the kind.
private fun checkUrlCharacters(text: String): Result<String> {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '\\' -> return urlFailure(UrlProblem.Backslash)
            isUrlSpace(c) -> return urlFailure(UrlProblem.WhitespaceOrControl)
            c.isHighSurrogate() ->
                if (i + 1 < text.length && text[i + 1].isLowSurrogate()) i++ else return urlFailure(UrlProblem.NotUnicode)
            c.isLowSurrogate() -> return urlFailure(UrlProblem.NotUnicode)
        }
        i++
    }
    return Result.success(text)
}
//endregion

//region scheme and authority
// ASCII letters compared by hand: Kotlin's ignoreCase folds U+017F to 's' and U+212A to 'k'.
private fun startsWithAsciiIgnoreCase(text: String, prefix: String): Boolean =
    text.length >= prefix.length && prefix.indices.all { HttpSyntax.asciiLower(text[it]) == prefix[it] }

private fun splitScheme(text: String): Result<SchemeSplit> = when {
    startsWithAsciiIgnoreCase(text, "https://") -> Result.success(SchemeSplit("https", text.substring(8)))
    startsWithAsciiIgnoreCase(text, "http://") -> Result.success(SchemeSplit("http", text.substring(7)))
    else -> urlFailure(UrlProblem.Scheme)
}

// The authority is the text up to the first slash, question mark or hash; an at-sign in it is user-info.
private fun splitAuthority(split: SchemeSplit): Result<AuthoritySplit> {
    val rest = split.afterSeparator
    val end = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
    val authority = rest.substring(0, end)
    return if ('@' in authority) urlFailure(UrlProblem.UserInfo)
    else Result.success(AuthoritySplit(split.scheme, authority, rest.substring(end)))
}
//endregion

//region host and port
// Splits "[...]" or a name from the port text; the host keeps its brackets for canonicalHost.
private fun splitHostAndPort(authority: String): Result<Pair<String, String?>> =
    if (authority.startsWith('[')) {
        val close = authority.indexOf(']')
        when {
            close < 0 -> urlFailure(UrlProblem.Host)
            close == authority.length - 1 -> Result.success(authority to null)
            authority[close + 1] == ':' -> Result.success(authority.substring(0, close + 1) to authority.substring(close + 2))
            else -> urlFailure(UrlProblem.Host)
        }
    } else {
        val colon = authority.indexOf(':')
        if (colon < 0) Result.success(authority to null)
        else Result.success(authority.substring(0, colon) to authority.substring(colon + 1))
    }

// Digits only (a sign is rejected), leading zeros allowed, 1..65535; absent or empty is the default.
private fun parsePort(portText: String?, default: Int): Result<Int> = when {
    portText.isNullOrEmpty() -> Result.success(default)
    !portText.all { it in '0'..'9' } -> urlFailure(UrlProblem.Port)
    else -> portText.trimStart('0').let { digits ->
        if (digits.isEmpty() || digits.length > 5) urlFailure(UrlProblem.Port)
        else digits.toInt().let { if (it in 1..65535) Result.success(it) else urlFailure(UrlProblem.Port) }
    }
}

// The port is judged before the host, so `h:0` is a Port failure whatever the host is.
private fun parseAuthority(split: AuthoritySplit): Result<HostAndPort> =
    splitHostAndPort(split.authority).flatMap { (hostText, portText) ->
        parsePort(portText, defaultPortOf(split.scheme)).flatMap { port ->
            canonicalHost(hostText).map { host -> HostAndPort(split.scheme, host, port, split.tail) }
        }
    }
//endregion

// The fragment is dropped (from the first hash); the first question mark starts the query.
private fun assemble(parts: HostAndPort): ParsedUrl {
    val withoutFragment = parts.tail.substringBefore('#')
    val queryStart = withoutFragment.indexOf('?')
    val pathText = if (queryStart < 0) withoutFragment else withoutFragment.substring(0, queryStart)
    val queryText = if (queryStart < 0) null else withoutFragment.substring(queryStart + 1)
    return ParsedUrl(parts.scheme, parts.host, parts.port, canonicalPath(pathText), queryText?.let { canonicalQuery(it) })
}
