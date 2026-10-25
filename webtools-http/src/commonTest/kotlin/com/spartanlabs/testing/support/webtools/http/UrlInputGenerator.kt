package com.spartanlabs.testing.support.webtools.http

import kotlin.random.Random

/**
 * Generates `Url.parse` inputs from a seeded `kotlin.random.Random` (plan §9.1): about 70% structured
 * (half clean, half hostile), 20% mutations of corpus strings, 10% raw random text. Seeds: 7 for the
 * differential, 11 for the garbage test. A failure message prints the seed and the input, so a
 * disagreement is reproducible.
 */
object UrlInputGenerator {

    /**
     * The first [count] inputs for [seed]. The sequence is backed by one `Random`, so iterating it a
     * second time gives different inputs; iterate it once, or call [generate] again for the same inputs.
     *
     * @param seed the seed of the `Random` the sequence draws from.
     * @param count how many inputs the sequence yields.
     * @return a sequence of [count] inputs, deterministic for [seed] on a fresh iteration.
     */
    fun generate(seed: Int, count: Int): Sequence<String> {
        val random = Random(seed)
        return generateSequence { next(random) }.take(count)
    }

    /**
     * One input drawn from [random], which advances the caller's `Random`.
     *
     * @param random the source of randomness; its state moves on.
     * @return the input: structured (70%), a corpus mutation (20%) or raw text (10%).
     */
    fun next(random: Random): String {
        val roll = random.nextInt(100)
        return when {
            roll < 70 -> structured(random, clean = random.nextBoolean())
            roll < 90 -> mutated(random)
            else -> raw(random)
        }
    }

    //region pools
    private val cleanSchemes = listOf("http", "https", "HTTP", "HtTps")
    private val hostileSchemes = cleanSchemes + listOf("ftp", "htps", "httpſ", "ＨＴＴＰ", "https\u212A", "İttp", "")   // \u212A is KELVIN SIGN
    private val hostileSeparators = listOf("://", ":/", ":", ":///", ":////", ":\\\\", "//", ":/\\", "")
    private val userInfos = listOf("u@", "u:p@", "@", ":@", "a@b@", "x:80@")

    private val cleanNames = listOf(
        "example.com", "api.opendota.com", "localhost", "a.b.c", "exa_mple.com", "a-b.com", "localhost.", "EXAMPLE.COM",
        "127.0.0.1", "127.1", "0x7f.1", "2130706433", "1.2.3.4.5", "999.999.999.999", "h", UrlCorpus.label63,
    )
    private val hostileNames = listOf(
        UrlCorpus.label64, "a".repeat(250), "xn--n3h.net", "xn--abc-", "XN--N3H.net", "a.xn--b.c", "münchen.de",
        "☃.net", "%31%32%37.0.0.1", "a%2eb", "ｌｏｃａｌｈｏｓｔ",
        "１２７.0.0.1", "127.0.0.1。evil.example", "ⓛocalhost", "a..b", ".a", "a!b", "a\"b", "", "a_b",
    )
    private val cleanIpv6 = listOf(
        "[::1]", "[0:0:0:0:0:0:0:1]", "[0000:0000:0000:0000:0000:0000:0000:0001]", "[::]", "[0::]", "[::0:0]", "[2001:DB8::1]",
        "[2001:db8:0:0:1:0:0:1]", "[2001:0:0:1:0:0:0:1]", "[1:2:3:4:5:6:7::]", "[::ffff:127.0.0.1]", "[::ffff:7f00:1]",
        "[::FFFF:7F00:1]", "[::ffff:0:0]", "[::1.2.3.4]", "[64:ff9b::1.2.3.4]", "[1:2:3:4:5:6:1.2.3.4]",
    )
    private val hostileIpv6 = listOf(
        "[fe80::1%25eth0]", "[fe80::1%eth0]", "[::1", "::1", "[]", "[:::1]", "[1::2::3]", "[g::1]", "[12345::]", "[1.2.3.4]",
        "[::1.2.3.256]", "[::01.2.3.4]", "[::ffff:1.2.3]", "[1:2:3:4:5:6:7:8:9]", "[::1]x", "[v1.x]", "[1:2:3:4:5:6:7::8]",
        "[1:2:3:4:5:6:7::8:9]", "[a:b:c:d:e:f:0::1]",
    )
    // "" three times: a clean URL has no port a third of the time.
    private val cleanPorts = listOf("", "", "", "80", "443", "8080", "1", "65535", "0080")
    private val hostilePorts = cleanPorts + listOf("+80", "-1", "0", "08", "8o", "80:80", "８０", "99999999999", "+", "0x50", "8 0")

    private val pathSegments = listOf(
        ".", "..", "%2e", "%2E", ".%2e", "%2e.", "%2e%2e", "a", "b", "%20", "é", "日本", "😀", "%", "%zz",
        "%41", "%e9", "{x}", "a|b", "^", "`", "<>", "\"", "'", "[x]", "\uD800", "...a", "a..", "...", "~", "@", ":", "&", "=",
        "+", "%2F", "%5C", "%252e", "100%", "", "api", "matches", "123",
    )
    private val queryTokens = listOf(
        "&", "=", "+", "%", "%zz", "é", "😀", "'", "\"", "<", ">", "{", "|", "}", "[", "]", "^", "`", "\\", " ", "\t",
        "#", "?", "~", "*", "@", ";", ":", "/", "\$", "!", "\uD800", "\uDC00", "a=1", "k=v", "q", "api_key=abc123", "limit=20",
    )
    private val surroundingWhitespace = listOf(" ", "\t", "\n", "\r\n", "\u000C", "\u000B", "\u00A0")   // the last is NBSP (U+00A0), which neither parser trims

    private val significantAscii: String = "/\\?#@:[]%.&=+ -_~!\$'()*,;<>^`{|}\"\t\n\u000B\u0000\u007F"
    private val unicodePool = listOf(
        "é", "ß", "日", "本", "😀", "☃", "ſ", "İ", "ＨＴＴＰ", "１",
        "。", "．", "｡", "ⓛ", "\u212A", "\u00A0", "\u2028", "\u3000", "\u0085", "\u200B", "\uFEFF", "\uD800",   // U+212A KELVIN SIGN, U+00A0 NBSP, U+2028 LINE SEPARATOR, U+3000 IDEOGRAPHIC SPACE, U+0085 NEL, U+200B ZWSP, U+FEFF BOM
        "\uDC00", "٣",
    )
    private val fragments = listOf("xn--", "%2e", "::", "/./", "/../")
    private val prefixes = listOf("http://", "https://", "HTTP://", "http:/")
    //endregion

    private fun <T> Random.pick(list: List<T>): T = list[nextInt(list.size)]

    //region structured
    // Draw rates: surrounding whitespace 10% at either end (hostile only); user-info 25% (hostile only); host
    // 25% fixed IPv6 pool, 25% random IPv6, 50% names; port from the list (clean) or 50% list / 50% 0..99999
    // (hostile), plus an empty ":" 12.5% of the time (hostile); 0 to 5 path segments, a lone "/" half the
    // time when there is none; a query 50% of the time with 0 to 4 tokens; a fragment 25% with 0 to 2 pieces.
    private fun structured(random: Random, clean: Boolean): String = buildString {
        if (!clean && random.nextInt(10) == 0) append(random.pick(surroundingWhitespace))
        append(if (clean) random.pick(cleanSchemes) else random.pick(hostileSchemes))
        append(if (clean) "://" else random.pick(hostileSeparators))
        if (!clean && random.nextInt(4) == 0) append(random.pick(userInfos))
        append(host(random, clean))
        val port = if (clean) random.pick(cleanPorts) else if (random.nextBoolean()) random.pick(hostilePorts) else random.nextInt(0, 100_000).toString()
        if (port.isNotEmpty() || (!clean && random.nextInt(8) == 0)) append(':').append(port)
        val segments = random.nextInt(0, 6)
        repeat(segments) { append('/').append(random.pick(pathSegments)) }
        if (segments == 0 && random.nextBoolean()) append('/')
        if (random.nextBoolean()) {
            append('?')
            repeat(random.nextInt(0, 5)) { append(random.pick(queryTokens)) }
        }
        if (random.nextInt(4) == 0) {
            append('#')
            repeat(random.nextInt(0, 3)) { append(if (random.nextBoolean()) random.pick(pathSegments) else random.pick(queryTokens)) }
        }
        if (!clean && random.nextInt(10) == 0) append(random.pick(surroundingWhitespace))
    }

    private fun host(random: Random, clean: Boolean): String = when (random.nextInt(4)) {
        0 -> if (clean) random.pick(cleanIpv6) else random.pick(cleanIpv6 + hostileIpv6)
        1 -> randomIpv6(random, clean)
        else -> if (clean) random.pick(cleanNames) else random.pick(cleanNames + hostileNames)
    }

    // Random groups with zero runs, an optional "::" at a random place, an optional dotted IPv4 suffix (10%
    // out-of-range octets when hostile), mapped forms, random upper case, random leading zeros.
    private fun randomIpv6(random: Random, clean: Boolean): String {
        val withGap = random.nextBoolean()
        val mapped = random.nextInt(5) == 0
        val quad = mapped || random.nextInt(4) == 0
        // 8 groups total; a dotted quad spends 2, a "::" must elide at least 1.
        val explicitTarget = when {
            clean && withGap -> random.nextInt(0, 7 - (if (quad) 1 else 0))
            clean -> 8 - (if (quad) 2 else 0)
            else -> random.nextInt(0, 10)
        }
        val groups = ArrayList<String>()
        repeat(explicitTarget) {
            val value = if (random.nextInt(3) == 0) 0 else random.nextInt(0, 0x10000)
            var text = value.toString(16)
            if (random.nextInt(4) == 0) text = text.padStart(4, '0')
            if (random.nextBoolean()) text = text.map { if (it in 'a'..'f') (it - 32) else it }.joinToString("")
            if (!clean && random.nextInt(20) == 0) text += "f"
            groups.add(text)
        }
        if (mapped) {
            groups.clear()
            groups.add("ffff")
        }
        val gapAt = if (withGap || mapped) random.nextInt(0, groups.size + 1) else -1
        val head = if (gapAt < 0) groups else groups.subList(0, gapAt)
        val tail = if (gapAt < 0) emptyList() else groups.subList(gapAt, groups.size)
        val sb = StringBuilder("[")
        sb.append(head.joinToString(":"))
        if (gapAt >= 0) sb.append("::")
        sb.append(tail.joinToString(":"))
        if (quad) {
            if (tail.isNotEmpty() || (gapAt < 0 && head.isNotEmpty())) sb.append(':')
            repeat(4) { index ->
                val octet = if (!clean && random.nextInt(10) == 0) random.nextInt(256, 1000) else random.nextInt(0, 256)
                if (index > 0) sb.append('.')
                sb.append(if (!clean && random.nextInt(20) == 0) "0$octet" else octet.toString())
            }
        }
        sb.append(']')
        return sb.toString()
    }
    //endregion

    //region mutation
    private fun mutated(random: Random): String {
        var text = random.pick(UrlCorpus.allInputs)
        repeat(random.nextInt(1, 4)) {
            val replacement = if (random.nextBoolean()) significantAscii[random.nextInt(significantAscii.length)].toString() else random.pick(unicodePool)
            val at = if (text.isEmpty()) 0 else random.nextInt(text.length + 1)
            text = when (random.nextInt(3)) {
                0 -> text.substring(0, at) + replacement + text.substring(at)
                1 -> if (at < text.length) text.substring(0, at) + text.substring(at + 1) else text
                else -> if (at < text.length) text.substring(0, at) + replacement + text.substring(at + 1) else text + replacement
            }
        }
        return text
    }
    //endregion

    //region raw random
    // Draw rates: a scheme prefix 70% of the time; 0 to 30 characters, each from one of eight classes with
    // equal probability (significant ASCII, a letter, a digit, the Unicode pool, a fragment, a C0 control, a
    // random BMP code unit, a random supplementary code point).
    private fun raw(random: Random): String = buildString {
        if (random.nextInt(10) < 7) append(random.pick(prefixes))
        repeat(random.nextInt(0, 31)) {
            when (random.nextInt(8)) {
                0 -> append(significantAscii[random.nextInt(significantAscii.length)])
                1 -> append('a' + random.nextInt(26))
                2 -> append('0' + random.nextInt(10))
                3 -> append(random.pick(unicodePool))
                4 -> append(random.pick(fragments))
                5 -> append(random.nextInt(0, 0x20).toChar())
                6 -> append(random.nextInt(0, 0x10000).toChar())
                else -> appendSupplementary(random.nextInt(0x10000, 0x110000))
            }
        }
    }

    // UTF-16 surrogate pair of a supplementary code point (RFC 2781 §2.1).
    private fun StringBuilder.appendSupplementary(codePoint: Int) {
        val offset = codePoint - 0x10000
        append((0xD800 + (offset shr 10)).toChar())
        append((0xDC00 + (offset and 0x3FF)).toChar())
    }
    //endregion
}
