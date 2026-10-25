package com.spartanlabs.webtools.http

// The path and query canonicalisers of Url.parse, the dot-segment fold, and the two internal encoders
// that reproduce HttpUrl.Builder.addPathSegment and addQueryParameter for the facades.

private const val HEX: String = "0123456789ABCDEF"

private fun StringBuilder.appendEncoded(byte: Int) {
    append('%').append(HEX[byte shr 4]).append(HEX[byte and 0x0F])
}

// Percent-encodes every non-ASCII code point as UTF-8 and every ASCII character for which [encodes] is true.
// A '%' is never touched unless [encodes] says so. The input holds no unpaired surrogate (the scan or the
// encoder's own check has rejected it), so encodeToByteArray never substitutes.
private fun percentEncode(text: String, encodes: (Char) -> Boolean): String {
    if (text.none { it.code >= 0x80 || encodes(it) }) return text
    return buildString(text.length + 16) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.code < 0x80) {
                if (encodes(c)) appendEncoded(c.code) else append(c)
                i++
            } else {
                val width = if (c.isHighSurrogate()) 2 else 1
                text.substring(i, i + width).encodeToByteArray().forEach { appendEncoded(it.toInt() and 0xFF) }
                i += width
            }
        }
    }
}

//region parse-time canonicalisers
// The sets applied at parse time: HttpUrl's PATH_SEGMENT_ENCODE_SET and QUERY_ENCODE_SET minus what the scan
// has already removed (controls, space, DEL and backslash) and minus the delimiters the split consumed.
private fun encodedInPathSegment(c: Char): Boolean = c == '"' || c == '<' || c == '>' || c == '^' || c == '`' || c == '{' || c == '|' || c == '}'
private fun encodedInQuery(c: Char): Boolean = c == '"' || c == '\'' || c == '<' || c == '>'

private fun equalsAsciiIgnoreCase(a: String, b: String): Boolean =
    a.length == b.length && a.indices.all { HttpSyntax.asciiLower(a[it]) == HttpSyntax.asciiLower(b[it]) }

private fun isDotSegment(s: String): Boolean = s == "." || equalsAsciiIgnoreCase(s, "%2e")
private fun isDoubleDotSegment(s: String): Boolean =
    s == ".." || equalsAsciiIgnoreCase(s, "%2e.") || equalsAsciiIgnoreCase(s, ".%2e") || equalsAsciiIgnoreCase(s, "%2e%2e")

// HttpUrl's pop(): remove the last element; if it was empty and elements remain, the new last becomes "", else append "".
private fun popSegment(stack: MutableList<String>) {
    val removed = stack.removeAt(stack.lastIndex)
    if (removed.isEmpty() && stack.isNotEmpty()) stack[stack.lastIndex] = "" else stack.add("")
}

// A segment's canonical text is tested for the dot forms after encoding, as HttpUrl does.
internal fun canonicalPath(pathText: String): String {
    if (pathText.isEmpty()) return "/"
    val segments = pathText.substring(1).split('/')   // pathText starts with '/'
    val stack = arrayListOf("")
    segments.forEachIndexed { index, raw ->
        val segment = percentEncode(raw, ::encodedInPathSegment)
        when {
            isDotSegment(segment) -> Unit
            isDoubleDotSegment(segment) -> popSegment(stack)
            else -> {
                if (stack[stack.lastIndex].isEmpty()) stack[stack.lastIndex] = segment else stack.add(segment)
                if (index < segments.lastIndex) stack.add("")
            }
        }
    }
    return stack.joinToString(separator = "") { "/$it" }
}

internal fun canonicalQuery(queryText: String): String = percentEncode(queryText, ::encodedInQuery)
//endregion

//region encoders for the facades
private fun hasUnpairedSurrogate(text: String): Boolean {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) i++
        else if (c.isHighSurrogate() || c.isLowSurrogate()) return true
        i++
    }
    return false
}

// The complement of what HttpUrl.Builder.addPathSegment encodes (research F16): these characters stay raw.
private fun isRawInPathSegment(c: Char): Boolean =
    c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "!\$&'()*+,-.:;=@[]_~"

// The complement of what HttpUrl.Builder.addQueryParameter encodes (QUERY_COMPONENT_ENCODE_SET): `~` is encoded, `*` is not.
private fun isRawInQueryComponent(c: Char): Boolean = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "*-._"

/** The output of `HttpUrl.Builder.addPathSegment` for one segment, but a failure for `.`, `..` and an unpaired surrogate. */
internal fun encodePathSegment(segment: String): Result<String> = when {
    segment == "." || segment == ".." -> urlFailure(UrlProblem.DotSegment)
    hasUnpairedSurrogate(segment) -> urlFailure(UrlProblem.NotUnicode)
    else -> Result.success(percentEncode(segment) { !isRawInPathSegment(it) })
}

/** The output of `HttpUrl.Builder.addQueryParameter` for one name or value, but a failure for an unpaired surrogate. */
internal fun encodeQueryComponent(component: String): Result<String> = when {
    hasUnpairedSurrogate(component) -> urlFailure(UrlProblem.NotUnicode)
    else -> Result.success(percentEncode(component) { !isRawInQueryComponent(it) })
}
//endregion
