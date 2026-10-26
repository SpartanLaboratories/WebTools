package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.addr
import com.spartanlabs.testing.support.webtools.http.cidr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Level 2 - `CidrBlock` as a value: `toString()` is the canonical block (never a dotted quad inside IPv6), equality and
 * hash are on family, bytes and prefix, and the `internal` `contains` matches by prefix within one family only.
 */
class CidrBlockComponentTest {

    @Test
    fun `toString is the canonical block`() {
        mapOf(
            "10.0.0.0/8" to "10.0.0.0/8",
            "0.0.0.0/0" to "0.0.0.0/0",
            "192.0.2.1/32" to "192.0.2.1/32",
            "fd00::/8" to "fd00::/8",
            "::/0" to "::/0",
            "2001:db8::/32" to "2001:db8::/32",
            "::1/128" to "::1/128",
            "::ffff:10.0.0.0/104" to "::ffff:a00:0/104",
        ).forEach { (text, canonical) -> assertEquals(canonical, cidr(text).toString(), text) }
    }

    @Test
    fun `equality and hash are on family bytes and prefix`() {
        assertEquals(cidr("10.0.0.0/8"), cidr("10.0.0.0/8"))
        assertEquals(cidr("10.0.0.0/8").hashCode(), cidr("10.0.0.0/8").hashCode())
        assertNotEquals(cidr("10.0.0.0/8"), cidr("10.0.0.0/9"))
        assertNotEquals(cidr("0.0.0.0/0"), cidr("::/0"))
        assertNotEquals(cidr("::ffff:a00:0/104"), cidr("10.0.0.0/8"))
        assertEquals(cidr("FD00::/8"), cidr("fd00::/8"))
    }

    @Test
    fun `a slash-8 contains its first and last address and not the neighbours`() {
        val block = cidr("10.0.0.0/8")
        assertTrue(block.contains(addr("10.0.0.0")))
        assertTrue(block.contains(addr("10.255.255.255")))
        assertFalse(block.contains(addr("9.255.255.255")))
        assertFalse(block.contains(addr("11.0.0.0")))
    }

    @Test
    fun `a slash-0 contains every address of its family and none of the other`() {
        val v4 = cidr("0.0.0.0/0")
        assertTrue(v4.contains(addr("0.0.0.0")))
        assertTrue(v4.contains(addr("255.255.255.255")))
        assertTrue(v4.contains(addr("93.184.216.34")))
        assertFalse(v4.contains(addr("::1")))
        val v6 = cidr("::/0")
        assertTrue(v6.contains(addr("::")))
        assertTrue(v6.contains(addr("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff")))
        assertFalse(v6.contains(addr("1.2.3.4")))
    }

    @Test
    fun `a slash-32 contains exactly one address`() {
        val block = cidr("192.0.2.1/32")
        assertTrue(block.contains(addr("192.0.2.1")))
        assertFalse(block.contains(addr("192.0.2.0")))
        assertFalse(block.contains(addr("192.0.2.2")))
    }

    @Test
    fun `an IPv6 block matches by prefix bits`() {
        val block = cidr("fc00::/7")
        assertTrue(block.contains(addr("fc00::")))
        assertTrue(block.contains(addr("fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff")))
        assertFalse(block.contains(addr("fe00::")))
        assertFalse(block.contains(addr("fbff::")))
    }

    @Test
    fun `an array of an unusual length is contained by nothing`() {
        val odd = ByteArray(5)
        assertFalse(cidr("0.0.0.0/0").contains(odd))
        assertFalse(cidr("::/0").contains(odd))
    }
}
