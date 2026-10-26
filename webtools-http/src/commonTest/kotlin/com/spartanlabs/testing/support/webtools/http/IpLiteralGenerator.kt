package com.spartanlabs.testing.support.webtools.http

import kotlin.random.Random

/** Seeded IP-literal-shaped strings, valid and invalid, for the agreement and differential tests. */
internal object IpLiteralGenerator {
    private const val HEX = "0123456789abcdef"

    /** [count] dotted-style strings: mostly four parts, sometimes one to five; parts with leading zeros, above 255, hex or empty. */
    fun ipv4(seed: Int, count: Int): List<String> {
        val random = Random(seed)
        return List(count) {
            val parts = if (random.nextInt(10) < 7) 4 else 1 + random.nextInt(5)
            (1..parts).joinToString(".") { ipv4Part(random) }
        }
    }

    private fun ipv4Part(random: Random): String {
        val roll = random.nextInt(100)
        return when {
            roll < 88 -> random.nextInt(256).toString()
            roll < 92 -> "0" + random.nextInt(100)
            roll < 95 -> (256 + random.nextInt(744)).toString()
            roll < 97 -> "0x" + random.nextInt(256).toString(16)
            else -> ""
        }
    }

    /** [count] IPv6-style strings without brackets: zero to nine groups, an optional "::", an optional dotted quad last, one in ten mutated. */
    fun ipv6(seed: Int, count: Int): List<String> {
        val random = Random(seed)
        return List(count) { mutate(random, shape(random), random.nextInt(10) == 0) }
    }

    private fun group(random: Random): String {
        if (random.nextInt(10) < 3) return listOf("0", "00", "000", "0000").random(random)
        val length = if (random.nextInt(20) == 0) 5 else 1 + random.nextInt(4)
        val digits = (1..length).map { HEX[random.nextInt(16)] }.joinToString("")
        return if (random.nextBoolean()) digits else digits.uppercase()
    }

    private fun quad(random: Random): String =
        (1..4).joinToString(".") { if (random.nextInt(20) == 0) "0" + random.nextInt(10) else random.nextInt(256).toString() }

    private fun shape(random: Random): String {
        val count = if (random.nextInt(10) < 8) 1 + random.nextInt(8) else random.nextInt(10)
        val parts = MutableList(count) { group(random) }
        if (count > 0 && random.nextInt(8) == 0) parts[count - 1] = quad(random)
        if (random.nextInt(10) >= 7) return parts.joinToString(":")
        val gapAt = random.nextInt(parts.size + 1)
        return parts.subList(0, gapAt).joinToString(":") + "::" + parts.subList(gapAt, parts.size).joinToString(":")
    }

    private fun mutate(random: Random, text: String, mutated: Boolean): String =
        if (!mutated) text else when (random.nextInt(8)) {
            0 -> ":$text"
            1 -> "$text:"
            2 -> "$text%eth0"
            3 -> text.replaceFirst("::", ":::")
            4 -> "[$text]"
            5 -> "$text "
            6 -> text.replace(':', ';')
            else -> text + "g"
        }
}
