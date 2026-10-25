package com.spartanlabs.webtools.http

// Host canonicalisation: an ASCII name, or an IPv6 literal decoded over split parts and printed in RFC
// 5952 form.

// A host as the authority holds it: "[...]" or a name. Returns the canonical host (IPv6 without brackets).
internal fun canonicalHost(hostText: String): Result<String> =
    if (hostText.startsWith('[')) canonicalIpv6(hostText.substring(1, hostText.length - 1)) else canonicalName(hostText)

//region name
// `%` or non-ASCII is HostEncoding; then lower-case; then the character set, the lengths and the xn-- rule.
private fun canonicalName(text: String): Result<String> {
    if (text.any { it == '%' || it.code >= 0x80 }) return urlFailure(UrlProblem.HostEncoding)
    val host = buildString(text.length) { text.forEach { append(HttpSyntax.asciiLower(it)) } }
    val labels = host.removeSuffix(".").split('.')
    return when {
        !host.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' || it == '.' } -> urlFailure(UrlProblem.Host)
        // DNS limits: a label is 1..63 octets, a name 1..253 (the trailing dot counts), as HttpUrl's canonicalizeHost enforces.
        host.length !in 1..253 -> urlFailure(UrlProblem.Host)
        labels.any { it.length !in 1..63 } -> urlFailure(UrlProblem.Host)
        labels.any { it.startsWith("xn--") } -> urlFailure(UrlProblem.HostIdn)
        else -> Result.success(host)
    }
}
//endregion

//region IPv6
private fun canonicalIpv6(inner: String): Result<String> = decodeIpv6(inner).map { formatIpv6(it) }

// Decoded over split parts, never over an index into a fixed array, so no input can overrun.
private fun decodeIpv6(text: String): Result<IntArray> {
    if (':' !in text) return urlFailure(UrlProblem.Host)
    val gap = text.indexOf("::")
    if (gap >= 0 && text.indexOf("::", gap + 1) >= 0) return urlFailure(UrlProblem.Host)
    val head = (if (gap >= 0) text.substring(0, gap) else text).splitGroups()
    val tail = if (gap >= 0) text.substring(gap + 2).splitGroups() else emptyList()
    val headGroups = parseGroups(head, allowQuadAtEnd = gap < 0).getOrElse { return urlFailure(UrlProblem.Host) }
    val tailGroups = parseGroups(tail, allowQuadAtEnd = gap >= 0).getOrElse { return urlFailure(UrlProblem.Host) }
    val explicit = headGroups.size + tailGroups.size
    return when {
        gap >= 0 && explicit <= 7 -> Result.success((headGroups + List(8 - explicit) { 0 } + tailGroups).toIntArray())
        gap < 0 && explicit == 8 -> Result.success(headGroups.toIntArray())
        else -> urlFailure(UrlProblem.Host)
    }
}

private fun String.splitGroups(): List<String> = if (isEmpty()) emptyList() else split(':')

// A part is 1..4 hex digits; the last part of the address may be a dotted IPv4 quad (two groups).
private fun parseGroups(parts: List<String>, allowQuadAtEnd: Boolean): Result<List<Int>> {
    val groups = ArrayList<Int>(parts.size + 1)
    parts.forEachIndexed { index, part ->
        if (allowQuadAtEnd && index == parts.lastIndex && '.' in part) {
            val quad = parseIpv4Quad(part) ?: return urlFailure(UrlProblem.Host)
            groups.add((quad[0] shl 8) or quad[1])
            groups.add((quad[2] shl 8) or quad[3])
        } else {
            groups.add(parseHexGroup(part) ?: return urlFailure(UrlProblem.Host))
        }
    }
    return Result.success(groups)
}

private fun isHexDigit(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

private fun parseHexGroup(part: String): Int? =
    if (part.length in 1..4 && part.all { isHexDigit(it) }) part.toInt(16) else null

private fun parseIpv4Quad(part: String): IntArray? {
    val pieces = part.split('.')
    if (pieces.size != 4) return null
    val values = pieces.map { piece ->
        // 1..3 decimal digits, at most 255; a leading zero is rejected, as HttpUrl does (no octal reading).
        if (piece.length !in 1..3 || !piece.all { it in '0'..'9' } || (piece.length > 1 && piece[0] == '0')) return null
        piece.toInt().also { if (it > 255) return null }
    }
    return values.toIntArray()
}

// RFC 5952: lower-case hex, no leading zeros, the first longest run of two or more zero groups becomes "::";
// an IPv4-mapped address (groups 0..4 zero, group 5 = ffff) is printed as dotted IPv4.
private fun formatIpv6(g: IntArray): String {
    if ((0..4).all { g[it] == 0 } && g[5] == 0xFFFF) {
        return "${g[6] shr 8}.${g[6] and 0xFF}.${g[7] shr 8}.${g[7] and 0xFF}"
    }
    var bestStart = -1
    var bestLength = 0
    var i = 0
    while (i < 8) {
        if (g[i] != 0) { i++; continue }
        var j = i
        while (j < 8 && g[j] == 0) j++
        if (j - i > bestLength) { bestStart = i; bestLength = j - i }
        i = j
    }
    fun hex(range: IntRange): String = range.joinToString(":") { g[it].toString(16) }
    return if (bestLength < 2) hex(0..7) else hex(0 until bestStart) + "::" + hex((bestStart + bestLength) until 8)
}
//endregion
