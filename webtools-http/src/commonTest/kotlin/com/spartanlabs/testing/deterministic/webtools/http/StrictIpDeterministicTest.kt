package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.StrictIp
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Level 4a - `StrictIp`: `parseV4` accepts exactly the strict dotted quads and refuses every short, padded, hex, signed,
 * padded-with-space or non-ASCII-digit form; `parseV6` accepts RFC 4291 text (one `::`, an optional strict quad last)
 * and refuses zones, brackets, prefixes, too many or too few groups; `formatV6` prints RFC 5952 (first longest zero run,
 * a single zero group not compressed, never a dotted IPv4); 2,000 seeded arrays round-trip through format and parse.
 */
class StrictIpDeterministicTest {

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun `parseV4 accepts the strict dotted quads`() {
        assertContentEquals(bytes(0, 0, 0, 0), StrictIp.parseV4("0.0.0.0"))
        assertContentEquals(bytes(1, 2, 3, 4), StrictIp.parseV4("1.2.3.4"))
        assertContentEquals(bytes(10, 20, 30, 40), StrictIp.parseV4("10.20.30.40"))
        assertContentEquals(bytes(100, 64, 0, 1), StrictIp.parseV4("100.64.0.1"))
        assertContentEquals(bytes(203, 0, 113, 255), StrictIp.parseV4("203.0.113.255"))
        assertContentEquals(bytes(255, 255, 255, 255), StrictIp.parseV4("255.255.255.255"))
    }

    @Test
    fun `parseV4 refuses every other form`() {
        // "１" is U+FF11 FULLWIDTH DIGIT ONE and "٤" is U+0664 ARABIC-INDIC DIGIT FOUR, both visible and kept literal.
        listOf(
            "", "1", "1.2", "1.2.3", "1.2.3.4.5", "256.1.1.1", "1.2.3.256", "01.2.3.4", "1.02.3.4", "1.2.3.04", "00.0.0.0",
            "0x7f.0.0.1", "1.2.3.4.", ".1.2.3.4", "1..2.3", "1.2.3.", "+1.2.3.4", "-1.2.3.4", " 1.2.3.4", "1.2.3.4 ", "1.2.3.4a",
            "1.2.3.1000", "1.2.3.4/8", "１.2.3.4", "1.2.3.٤",
        ).forEach { assertNull(StrictIp.parseV4(it), it) }
    }

    @Test
    fun `parseV6 accepts RFC 4291 text`() {
        mapOf(
            "::" to "00000000000000000000000000000000",
            "::1" to "00000000000000000000000000000001",
            "1::" to "00010000000000000000000000000000",
            "1:2:3:4:5:6:7:8" to "00010002000300040005000600070008",
            "1:2:3:4:5:6:7::" to "00010002000300040005000600070000",
            "::2:3:4:5:6:7:8" to "00000002000300040005000600070008",
            "2001:db8::1" to "20010db8000000000000000000000001",
            "2001:DB8::1" to "20010db8000000000000000000000001",
            "0:0:0:0:0:0:0:0" to "00000000000000000000000000000000",
            "::ffff:1.2.3.4" to "00000000000000000000ffff01020304",
            "::1.2.3.4" to "00000000000000000000000001020304",
            "64:ff9b::1.2.3.4" to "0064ff9b000000000000000001020304",
            "1:2:3:4:5:6:1.2.3.4" to "00010002000300040005000601020304",
            "0000:0000:0000:0000:0000:ffff:7f00:0001" to "00000000000000000000ffff7f000001",
            "1::2:3" to "00010000000000000000000000020003",
        ).forEach { (text, expected) -> assertEquals(expected, hex(assertNotNull(StrictIp.parseV6(text), text)), text) }
    }

    @Test
    fun `parseV6 refuses every other form`() {
        // "١" is U+0661 ARABIC-INDIC DIGIT ONE, visible and kept literal.
        listOf(
            "", ":", ":::", "::::", "1:2:3:4:5:6:7", "1:2:3:4:5:6:7:8:9", "1:2:3:4:5:6:7::8", "1::2::3", ":1:2:3:4:5:6:7",
            "1:2:3:4:5:6:7:", "12345::", "g::1", "::1%eth0", "::1%25eth0", "[::1]", "::1/128", "::1.2.3", "::1.2.3.4.5",
            "::01.2.3.4", "::1.2.3.256", "1.2.3.4::", "::1.2.3.4:1", "::ffff:1.2.3.4:5", "1.2.3.4", " ::1", "::1 ", "::fffg", "::١",
        ).forEach { assertNull(StrictIp.parseV6(it), it) }
    }

    @Test
    fun `formatV6 prints RFC 5952`() {
        assertEquals("::", StrictIp.formatV6(ByteArray(16)))
        assertEquals("::1", StrictIp.formatV6(StrictIp.parseV6("::1")!!))
        assertEquals("2001:db8::1", StrictIp.formatV6(StrictIp.parseV6("2001:db8::1")!!))
        assertEquals("1:2:3:4:5:6:7:0", StrictIp.formatV6(StrictIp.parseV6("1:2:3:4:5:6:7:0")!!))
        assertEquals("2001:0:0:1::1", StrictIp.formatV6(StrictIp.parseV6("2001:0:0:1:0:0:0:1")!!))
        assertEquals("2001::1:0:0:1:1", StrictIp.formatV6(StrictIp.parseV6("2001:0:0:1:0:0:1:1")!!))
        assertEquals("100::1:ffff:ffff:ffff:ffff", StrictIp.formatV6(StrictIp.parseV6("100:0:0:1:ffff:ffff:ffff:ffff")!!))
        assertEquals("::ffff:7f00:1", StrictIp.formatV6(StrictIp.parseV6("::ffff:127.0.0.1")!!))
        assertFailsWith<IllegalArgumentException> { StrictIp.formatV6(ByteArray(15)) }
    }

    @Test
    fun `two thousand seeded arrays round-trip through formatV6 and parseV6`() {
        val random = Random(7)
        repeat(2_000) { index ->
            val groups = IntArray(8) { random.nextInt(0x10000) }
            // Force runs of zero groups in: a run of 1..7 zero groups at a random start, in most arrays.
            if (random.nextInt(4) != 0) {
                val start = random.nextInt(8)
                val length = 1 + random.nextInt(8 - start)
                for (g in start until start + length) groups[g] = 0
            }
            if (random.nextInt(8) == 0) {
                val start = random.nextInt(7)
                groups[start] = 0
                groups[start + 1] = 0
            }
            val bytes = ByteArray(16) { i -> (if (i % 2 == 0) groups[i / 2] shr 8 else groups[i / 2]).toByte() }
            val text = StrictIp.formatV6(bytes)
            assertContentEquals(bytes, StrictIp.parseV6(text), "seed 7 array #$index printed as $text")
        }
    }
}
