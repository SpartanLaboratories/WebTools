package com.spartanlabs.webtools.http

/**
 * Strict IP-literal text to bytes and back. Strict means one spelling per address: no short or numeric IPv4 form (`127.1`,
 * `2130706433`), no leading zero, no hex or octal, no brackets, no zone. Pure, stateless, JVM-free.
 */
internal object StrictIp {
    /**
     * Exactly four decimal parts, each 1..3 ASCII digits, no leading zero unless the part is `0`, at most 255.
     *
     * @return the four bytes, or `null` when [text] is not that grammar.
     */
    fun parseV4(text: String): ByteArray? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        val out = ByteArray(4)
        for (i in 0 until 4) out[i] = (v4Part(parts[i]) ?: return null).toByte()
        return out
    }

    private fun v4Part(part: String): Int? {
        if (part.length !in 1..3 || !part.all { it in '0'..'9' } || (part.length > 1 && part[0] == '0')) return null
        return part.toInt().takeIf { it <= 255 }
    }

    /**
     * RFC 4291 text: groups of 1..4 hex digits (either case), at most one `::` standing for at least one group, an optional
     * strict dotted quad as the last 32 bits, no brackets, no zone.
     *
     * @return the 16 bytes (an IPv4-mapped literal stays 16 bytes), or `null` when [text] is not that grammar.
     */
    fun parseV6(text: String): ByteArray? {
        if (':' !in text || !text.all { it == ':' || it == '.' || isHex(it) }) return null
        val gap = text.indexOf("::")
        if (gap >= 0 && text.indexOf("::", gap + 1) >= 0) return null
        val head = groupsOf(if (gap >= 0) text.substring(0, gap) else text, quadAllowedLast = gap < 0) ?: return null
        val tail = if (gap >= 0) { groupsOf(text.substring(gap + 2), quadAllowedLast = true) ?: return null } else emptyList()
        val explicit = head.size + tail.size
        val groups = when {
            gap >= 0 && explicit <= 7 -> head + List(8 - explicit) { 0 } + tail
            gap < 0 && explicit == 8 -> head
            else -> return null
        }
        return ByteArray(16) { i -> (if (i % 2 == 0) groups[i / 2] shr 8 else groups[i / 2]).toByte() }
    }

    // The groups of a ':'-separated run; a dotted quad may be the last part only when quadAllowedLast, and counts as two.
    private fun groupsOf(text: String, quadAllowedLast: Boolean): List<Int>? {
        if (text.isEmpty()) return emptyList()
        val parts = text.split(':')
        val out = ArrayList<Int>(parts.size + 1)
        for (index in parts.indices) {
            val part = parts[index]
            if (quadAllowedLast && index == parts.lastIndex && '.' in part) {
                val quad = parseV4(part) ?: return null
                out.add(((quad[0].toInt() and 0xFF) shl 8) or (quad[1].toInt() and 0xFF))
                out.add(((quad[2].toInt() and 0xFF) shl 8) or (quad[3].toInt() and 0xFF))
            } else {
                out.add(hexGroup(part) ?: return null)
            }
        }
        return out
    }

    private fun isHex(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    private fun hexGroup(part: String): Int? = if (part.length in 1..4 && part.all { isHex(it) }) part.toInt(16) else null

    /**
     * RFC 5952: lower case, no leading zeros, the first longest run of two or more zero groups as `::`, never a dotted IPv4
     * (so `::ffff:7f00:1`, not `::ffff:127.0.0.1`).
     *
     * @param bytes exactly 16 bytes.
     * @return the text.
     * @throws IllegalArgumentException if [bytes] is not 16 bytes long (a programmer error).
     */
    fun formatV6(bytes: ByteArray): String {
        require(bytes.size == 16) { "An IPv6 address has 16 bytes." }
        val g = IntArray(8) { ((bytes[2 * it].toInt() and 0xFF) shl 8) or (bytes[2 * it + 1].toInt() and 0xFF) }
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
}
