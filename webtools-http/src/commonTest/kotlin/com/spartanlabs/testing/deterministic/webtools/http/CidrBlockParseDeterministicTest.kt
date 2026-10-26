package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.CidrBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Level 4a - `CidrBlock.parse`: the accepted blocks with their canonical text; the refusals by kind (shape, address,
 * prefix, host bits), each with exactly its constant message; `10.0.0.5/8` is refused, not masked.
 */
class CidrBlockParseDeterministicTest {

    private val shape = "A CIDR block must be written <address>/<prefix>."
    private val address = "The CIDR block address must be a plain dotted-decimal IPv4 or an IPv6 address."
    private val prefix =
        "The CIDR block prefix length must be a decimal number within the address family's width, without a sign or a leading zero."
    private val hostBits = "The CIDR block has bits set beyond its prefix length."
    private val constants = setOf(shape, address, prefix, hostBits)

    private fun refusal(text: String): String {
        val failure = assertNotNull(CidrBlock.parse(text).exceptionOrNull(), "accepted: $text")
        assertIs<IllegalArgumentException>(failure, text)
        val message = failure.message ?: ""
        assertTrue(message in constants, "$text: not a constant message: $message")
        return message
    }

    @Test
    fun `accepted blocks print canonically`() {
        mapOf(
            "10.0.0.0/8" to "10.0.0.0/8",
            "0.0.0.0/0" to "0.0.0.0/0",
            "192.0.2.1/32" to "192.0.2.1/32",
            "172.16.0.0/12" to "172.16.0.0/12",
            "fd00::/8" to "fd00::/8",
            "::/0" to "::/0",
            "2001:db8::/32" to "2001:db8::/32",
            "::1/128" to "::1/128",
            "FD00::/8" to "fd00::/8",
            "2001:0db8:0000:0000:0000:0000:0000:0000/32" to "2001:db8::/32",
        ).forEach { (text, canonical) -> assertEquals(canonical, CidrBlock.parse(text).getOrThrow().toString(), text) }
    }

    @Test
    fun `a text without one slash is a shape failure`() {
        listOf("", "10.0.0.0", "10.0.0.0/8/9").forEach { assertEquals(shape, refusal(it), it) }
    }

    @Test
    fun `an empty address is an address failure and an empty prefix a prefix failure`() {
        // Plan Amendment 1: a shape failure is a text without exactly one slash; the empty parts belong to their own kinds.
        assertEquals(address, refusal("/8"))
        assertEquals(prefix, refusal("10.0.0.0/"))
    }

    @Test
    fun `a lenient or malformed address is an address failure`() {
        listOf("10.0.0/8", "010.0.0.0/8", "256.0.0.0/8", "127.1/8", "[::1]/128", "::1%eth0/128", "fe80::1%25eth0/64", "::g/8", " 10.0.0.0/8")
            .forEach { assertEquals(address, refusal(it), it) }
    }

    @Test
    fun `a prefix outside the width or not plain decimal is a prefix failure`() {
        // "１" is U+FF11 FULLWIDTH DIGIT ONE, visible and kept literal.
        listOf("10.0.0.0/33", "::/129", "10.0.0.0/08", "10.0.0.0/+8", "10.0.0.0/-1", "10.0.0.0/8 ", "10.0.0.0/１", "10.0.0.0/1000")
            .forEach { assertEquals(prefix, refusal(it), it) }
    }

    @Test
    fun `a bit set after the prefix is a host-bits failure and is never masked`() {
        listOf("10.0.0.5/8", "192.0.2.1/24", "fd00::1/8", "::1/127").forEach { assertEquals(hostBits, refusal(it), it) }
        assertTrue(CidrBlock.parse("10.0.0.5/8").isFailure)
    }
}
