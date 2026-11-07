package com.spartanlabs.webtools.feeds

/** What the prolog scan found before the first element; only [NoDoctype] lets a parser run. */
internal sealed interface PrologScanResult {
    /** No reading holds anything but byte-order marks, white space, comments and processing instructions. */
    data object Empty : PrologScanResult

    /** The first significant unit of the byte reading is not `<`: JSON, plain text, junk. */
    data object NotMarkup : PrologScanResult

    /** The prolog is clean and the document starts with markup: a parser may run. */
    data object NoDoctype : PrologScanResult

    /** A web page: a DOCTYPE named `html` or an unprefixed `html` root element. */
    data object HtmlDocument : PrologScanResult

    /** The prolog is not well-formed: an unterminated comment or processing instruction, or `--` inside a comment. */
    data object IllFormed : PrologScanResult

    /** The document carries a DOCTYPE or another markup declaration before the root. */
    data object Doctype : PrologScanResult

    /** The prolog cannot be read the way the parser reads it. */
    data class Undecodable(val reason: UndecodableReason) : PrologScanResult
}

/** Why a prolog is [PrologScanResult.Undecodable]. */
internal enum class UndecodableReason { WideOrEbcdicPrefix, UnlistedEncodingName, PrologTooLong, MalformedSequence }

/**
 * Finds a DOCTYPE in a byte document before any parser runs, reading the prolog every way the parser could read it.
 * Stateless and total: [scan] never throws and makes at most three passes over at most [MAX_PROLOG_BYTES].
 */
internal object PrologScan {
    const val MAX_PROLOG_BYTES: Int = 64 * 1024
    private const val DECLARATION_WINDOW_BYTES = 4096

    /**
     * @param body the document as received.
     * @param contentType the response's `Content-Type` value, read only for `charset=` names, or `null`.
     * @return the worst outcome of the three readings, or, when no reading reaches markup, the byte reading's
     *   [PrologScanResult.Empty] or [PrologScanResult.NotMarkup].
     */
    fun scan(body: ByteArray, contentType: String?): PrologScanResult {
        val boundaries = bomBoundaries(body) ?: return PrologScanResult.Undecodable(UndecodableReason.PrologTooLong)
        val start = boundaries.last()
        if (hasUcs4Bom(body) || boundaries.any { startsWideOrEbcdic(body, it) }) {
            return PrologScanResult.Undecodable(UndecodableReason.WideOrEbcdicPrefix)
        }
        val names = buildList {
            addAll(contentTypeCharsets(contentType))
            for (reading in Reading.entries) for (boundary in boundaries) addAll(declaredEncodings(Units(body, boundary, reading)))
        }
        val listings = names.map(::listing)
        if (Listing.Unlisted in listings) return PrologScanResult.Undecodable(UndecodableReason.UnlistedEncodingName)
        val swallowProne = Listing.SwallowProne in listings
        val readings = Reading.entries.map { scanReading(Units(body, start, it), swallowProne) }
        // When no reading reaches markup the byte reading decides between Empty and NotMarkup: two bytes of white space
        // are Empty although each UTF-16 reading sees one unit that is not white space, because without a 0x3C byte no
        // reading can hold a '<'. Otherwise the worst outcome wins, so any reading that reaches markup wins, which keeps
        // a UTF-16 document without a mark parseable. Neither Empty nor NotMarkup lets a parser run.
        return if (readings.all { it == PrologScanResult.Empty || it == PrologScanResult.NotMarkup }) readings[0] else readings.reduce(::worse)
    }

    // Three readings of the same bytes (ASCII-compatible, UTF-16BE, UTF-16LE) because Rome's lenient XmlReader lets the
    // declaration beat the byte-order mark and the header, so no single encoding can be chosen first without a bypass;
    // the result is the worst of the three, and a body is "not markup" only when no reading finds markup.
    private fun rank(r: PrologScanResult): Int = when (r) {
        PrologScanResult.Empty -> 0
        PrologScanResult.NotMarkup -> 1
        PrologScanResult.NoDoctype -> 2
        PrologScanResult.HtmlDocument -> 3
        PrologScanResult.IllFormed -> 4
        PrologScanResult.Doctype -> 5
        is PrologScanResult.Undecodable -> 6
    }

    private fun worse(a: PrologScanResult, b: PrologScanResult): PrologScanResult = if (rank(b) > rank(a)) b else a

    //region byte-level prefixes
    /** Offsets after each byte-order mark in the leading run (0 first); null when the run reaches the byte bound. */
    private fun bomBoundaries(b: ByteArray): List<Int>? {
        val out = mutableListOf(0)
        var p = 0
        while (p < MAX_PROLOG_BYTES) {
            val n = when {
                b.matches(p, 0xFE, 0xFF) || b.matches(p, 0xFF, 0xFE) -> 2
                b.matches(p, 0xEF, 0xBB, 0xBF) -> 3
                else -> 0
            }
            if (n == 0) return out
            p += n
            out += p
        }
        return null
    }

    private fun hasUcs4Bom(b: ByteArray): Boolean =
        b.matches(0, 0x00, 0x00, 0xFE, 0xFF) || b.matches(0, 0xFF, 0xFE, 0x00, 0x00) ||
            b.matches(0, 0x00, 0x00, 0xFF, 0xFE) || b.matches(0, 0xFE, 0xFF, 0x00, 0x00)

    private fun startsWideOrEbcdic(b: ByteArray, at: Int): Boolean =
        b.matches(at, 0, 0, 0, 0x3C) || b.matches(at, 0x3C, 0, 0, 0) || b.matches(at, 0, 0, 0x3C, 0) ||
            b.matches(at, 0, 0x3C, 0, 0) || b.matches(at, 0x4C, 0x6F, 0xA7, 0x94)

    private fun ByteArray.matches(at: Int, vararg expected: Int): Boolean {
        if (at + expected.size > size) return false
        for (k in expected.indices) if ((this[at + k].toInt() and 0xFF) != expected[k]) return false
        return true
    }
    //endregion

    //region encoding names
    private fun contentTypeCharsets(contentType: String?): List<String> {
        if (contentType == null) return emptyList()
        val out = mutableListOf<String>()
        var from = 0
        while (true) {
            val at = contentType.indexOf("charset", from, ignoreCase = true)
            if (at < 0) return out
            var i = at + "charset".length
            from = i
            while (i < contentType.length && (contentType[i] == ' ' || contentType[i] == '\t')) i++
            if (i >= contentType.length || contentType[i] != '=') continue
            i++
            while (i < contentType.length && (contentType[i] == ' ' || contentType[i] == '\t')) i++
            if (i < contentType.length && (contentType[i] == '"' || contentType[i] == '\'')) i++
            val nameStart = i
            while (i < contentType.length && contentType[i] !in ";, \t\"'") i++
            if (i > nameStart) out += contentType.substring(nameStart, i)
        }
    }

    /** Every `encoding="..."` before the first `>` of a declaration-shaped start (`<?`), within the first 4096 bytes. */
    private fun declaredEncodings(u: Units): List<String> {
        val n = minOf(u.size, DECLARATION_WINDOW_BYTES / u.reading.stride)
        if (n < 2 || u[0] != '<'.code || u[1] != '?'.code) return emptyList()
        var gt = -1
        for (i in 0 until n) if (u[i] == '>'.code) { gt = i; break }
        if (gt < 0) return emptyList()
        val text = buildString(gt) { for (i in 0 until gt) append(if (u[i] < 0x80) u[i].toChar() else '�') }
        val out = mutableListOf<String>()
        var from = 0
        while (true) {
            val at = text.indexOf("encoding", from, ignoreCase = true)
            if (at < 0) return out
            var i = at + "encoding".length
            from = i
            while (i < text.length && isRegexSpace(text[i])) i++
            if (i >= text.length || text[i] != '=') continue
            i++
            while (i < text.length && isRegexSpace(text[i])) i++
            if (i >= text.length || (text[i] != '"' && text[i] != '\'')) continue
            val close = text.indexOf(text[i], i + 1)
            if (close < 0) continue
            out += text.substring(i + 1, close).trim()
        }
    }

    private fun isRegexSpace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r'

    private enum class Listing { Safe, SwallowProne, Unlisted }

    private val SAFE_EXACT = setOf(
        "utf-8", "utf8", "us-ascii", "ascii", "latin1", "koi8-r", "koi8-u",
        "shift_jis", "sjis", "windows-31j", "euc-kr", "gbk", "gb2312", "big5",
        "utf-16", "utf-16be", "utf-16le",
    )

    /** Encodings whose JDK decoder drops the ASCII byte that follows a malformed lead byte. */
    private val SWALLOW_PRONE = setOf("euc-jp", "gb18030")

    private fun listing(raw: String): Listing {
        val n = raw.trim().lowercase()
        if (n in SAFE_EXACT) return Listing.Safe
        if (n in SWALLOW_PRONE) return Listing.SwallowProne
        fun digits(prefix: String, min: Int, max: Int) = n.startsWith(prefix) && n.length - prefix.length in min..max &&
            n.substring(prefix.length).all { it in '0'..'9' }
        val single = digits("iso-8859-", 1, 2) || digits("iso8859-", 1, 2) || digits("iso_8859-", 1, 2) ||
            (n.startsWith("windows-125") && n.length == 12 && n[11] in '0'..'9') ||
            (n.startsWith("cp125") && n.length == 6 && n[5] in '0'..'9')
        return if (single) Listing.Safe else Listing.Unlisted
    }
    //endregion

    //region the prolog walk
    private enum class Reading(val stride: Int, val littleEndian: Boolean) {
        AsciiCompatible(1, false), Utf16Be(2, false), Utf16Le(2, true),
    }

    private class Units(private val bytes: ByteArray, private val start: Int, val reading: Reading) {
        val size: Int = maxOf(0, (bytes.size - start) / reading.stride)

        /** The highest unit index the walk has read. */
        var reach: Int = -1
            private set

        operator fun get(i: Int): Int {
            if (i > reach) reach = i
            return peek(i)
        }

        fun peek(i: Int): Int {
            val p = start + i * reading.stride
            if (reading.stride == 1) return bytes[p].toInt() and 0xFF
            val first = bytes[p].toInt() and 0xFF
            val second = bytes[p + 1].toInt() and 0xFF
            return if (reading.littleEndian) (second shl 8) or first else (first shl 8) or second
        }
    }

    private fun isSpace(c: Int) = c == 0x20 || c == 0x09 || c == 0x0A || c == 0x0D

    private fun isNameStart(c: Int) = c == ':'.code || c == '_'.code || c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c >= 0x80

    // The decoder-divergence rule applies to a reading that ended NoDoctype or NotMarkup: both are outcomes where the
    // walk trusted what it read (a junk unit after a closed comment may be a dash the decoder would have dropped).
    private fun scanReading(u: Units, swallowProne: Boolean): PrologScanResult {
        val result = walk(u)
        return if ((result == PrologScanResult.NoDoctype || result == PrologScanResult.NotMarkup) && decoderMayHaveDiverged(u, swallowProne)) {
            PrologScanResult.Undecodable(UndecodableReason.MalformedSequence)
        } else {
            result
        }
    }

    /**
     * True when a decoder could have dropped or replaced a unit inside the region the walk read: an unpaired surrogate
     * in a 16-bit reading, or (for the two encodings whose JDK decoder swallows the byte after a malformed lead byte)
     * any byte at or above 0x80 in the byte reading.
     */
    private fun decoderMayHaveDiverged(u: Units, swallowProne: Boolean): Boolean {
        val last = minOf(u.reach, u.size - 1)
        if (u.reading.stride == 1) {
            return swallowProne && (0..last).any { u.peek(it) >= 0x80 }
        }
        var i = 0
        while (i <= last) {
            val c = u.peek(i)
            when {
                c in 0xD800..0xDBFF -> if (i + 1 < u.size && u.peek(i + 1) in 0xDC00..0xDFFF) i++ else return true
                c in 0xDC00..0xDFFF -> return true
            }
            i++
        }
        return false
    }

    private fun walk(u: Units): PrologScanResult {
        val limit = MAX_PROLOG_BYTES / u.reading.stride
        val end = minOf(u.size, limit)
        val capped = u.size > limit
        val tooLong = PrologScanResult.Undecodable(UndecodableReason.PrologTooLong)
        var i = if (u.reading.stride == 2 && end > 0 && u[0] == 0xFEFF) 1 else 0
        while (true) {
            // Running out of units with the prolog unfinished: too long when the bound cut the read, else nothing but
            // white space, comments and processing instructions was found.
            if (i >= end) return if (capped) tooLong else PrologScanResult.Empty
            val c = u[i]
            if (isSpace(c)) { i++; continue }
            // The first significant unit is not '<': the body is not markup in this reading.
            if (c != '<'.code) return PrologScanResult.NotMarkup
            if (i + 1 >= end) return if (capped) tooLong else PrologScanResult.NoDoctype
            val c1 = u[i + 1]
            when {
                c1 == '?'.code -> {
                    val j = u.find(i + 2, end, "?>")
                    if (j < 0) return if (capped) tooLong else PrologScanResult.IllFormed
                    i = j + 2
                }
                c1 == '!'.code -> {
                    if (i + 3 < end && u[i + 2] == '-'.code && u[i + 3] == '-'.code) {
                        val j = u.find(i + 4, end, "--")
                        if (j < 0 || j + 2 >= end) return if (capped) tooLong else PrologScanResult.IllFormed
                        if (u[j + 2] != '>'.code) return PrologScanResult.IllFormed
                        i = j + 3
                    } else if (u.matchesIgnoreCase(i + 2, end, "DOCTYPE")) {
                        var k = i + 9
                        while (k < end && isSpace(u[k])) k++
                        val name = StringBuilder()
                        while (k < end && !isSpace(u[k]) && u[k] != '>'.code && u[k] != '['.code) {
                            name.append(if (u[k] < 0x80) u[k].toChar() else '�')
                            k++
                        }
                        return if (name.toString().equals("html", ignoreCase = true)) PrologScanResult.HtmlDocument else PrologScanResult.Doctype
                    } else {
                        val c2 = if (i + 2 < end) u[i + 2] else -1
                        return if (c2 in 'a'.code..'z'.code || c2 in 'A'.code..'Z'.code) PrologScanResult.Doctype else PrologScanResult.NoDoctype
                    }
                }
                isNameStart(c1) -> {
                    var k = i + 1
                    val name = StringBuilder()
                    while (k < end && !isSpace(u[k]) && u[k] != '/'.code && u[k] != '>'.code) {
                        name.append(if (u[k] < 0x80) u[k].toChar() else '�')
                        k++
                    }
                    return if (name.toString().equals("html", ignoreCase = true)) PrologScanResult.HtmlDocument else PrologScanResult.NoDoctype
                }
                else -> return PrologScanResult.NoDoctype
            }
        }
    }

    private fun Units.find(from: Int, end: Int, pattern: String): Int {
        var i = from
        while (i + pattern.length <= end) {
            var k = 0
            while (k < pattern.length && this[i + k] == pattern[k].code) k++
            if (k == pattern.length) return i
            i++
        }
        return -1
    }

    private fun Units.matchesIgnoreCase(at: Int, end: Int, upperPattern: String): Boolean {
        if (at + upperPattern.length > end) return false
        for (k in upperPattern.indices) {
            var c = this[at + k]
            if (c in 'a'.code..'z'.code) c -= 32
            if (c != upperPattern[k].code) return false
        }
        return true
    }
    //endregion
}
