package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.addr
import com.spartanlabs.webtools.http.AddressRanges
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Level 4a - the controls of the address table: the boundary addresses just outside every IPv4 row and every explicit
 * IPv6 row are allowed, the edges of `2000::/3` are denied as `not-global-unicast`, an embedded IPv4 (mapped or NAT64)
 * is judged by its IPv4 row, the two rules that are not registry rows (`168.63.129.16/32`, ISATAP with its mask) hold,
 * the published cloud endpoints are denied, and an array of an unusual length is denied.
 */
class AddressRangesControlsDeterministicTest {

    private fun classOf(address: String): String = assertNotNull(AddressRanges.deny(addr(address)), "$address is allowed").rangeClass

    private fun allowed(address: String) = assertNull(AddressRanges.deny(addr(address)), "$address is denied")

    @Test
    fun `the IPv4 boundary addresses and public controls are allowed`() {
        listOf(
            "1.0.0.0", "9.255.255.255", "11.0.0.0", "100.63.255.255", "100.128.0.0", "126.255.255.255", "128.0.0.0",
            "169.253.255.255", "169.255.0.0", "172.15.255.255", "172.32.0.0", "191.255.255.255", "192.0.1.0", "192.0.1.255",
            "192.0.3.0", "192.31.195.255", "192.31.197.0", "192.52.192.255", "192.52.194.0", "192.88.98.255", "192.88.100.0",
            "192.167.255.255", "192.169.0.0", "192.175.47.255", "192.175.49.0", "198.17.255.255", "198.20.0.0", "198.51.99.255",
            "198.51.101.0", "203.0.112.255", "203.0.114.0", "223.255.255.255",
            "8.8.8.8", "1.1.1.1", "93.184.216.34", "168.63.129.15", "168.63.129.17",
        ).forEach { allowed(it) }
    }

    @Test
    fun `the IPv6 boundary addresses and public controls are allowed`() {
        listOf(
            "2000::", "3fff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "2000:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "2001:200::", "2001:db7:ffff:ffff:ffff:ffff:ffff:ffff", "2001:db9::",
            "2001:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "2003::", "2620:4f:7fff:ffff:ffff:ffff:ffff:ffff", "2620:4f:8001::",
            "3ffe:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "3fff:1000::",
            "2606:4700:4700::1111", "2001:4860:4860::8888", "2a00:1450:4001::1", "2001:200::1", "::ffff:808:808", "64:ff9b::808:808",
        ).forEach { allowed(it) }
    }

    @Test
    fun `the edges outside global unicast are denied`() {
        assertEquals("not-global-unicast", classOf("1fff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"))
        assertEquals("not-global-unicast", classOf("4000::"))
    }

    @Test
    fun `an embedded IPv4 is judged by its IPv4 row`() {
        assertEquals("loopback", classOf("::ffff:127.0.0.1"))
        assertEquals("private-use", classOf("64:ff9b::a00:1"))
        assertEquals("link-local", classOf("::ffff:a9fe:a9fe"))
        assertEquals("loopback", classOf("64:ff9b::7f00:1"))
        assertEquals("this-host", classOf("::ffff:0:0"))
        assertEquals("this-host", classOf("64:ff9b::"))
    }

    @Test
    fun `the Azure platform endpoint is denied by its own row`() {
        assertEquals("azure-wireserver", classOf("168.63.129.16"))
    }

    @Test
    fun `an ISATAP interface identifier is denied under the mask and an explicit row wins`() {
        listOf("2a01:4f8:1:1:0:5efe:7f00:1", "2a01:4f8:1:1:100:5efe:7f00:1", "2a01:4f8:1:1:200:5efe:7f00:1", "2a01:4f8:1:1:300:5efe:7f00:1")
            .forEach { assertEquals("isatap", classOf(it), it) }
        allowed("2a01:4f8:1:1:400:5efe:7f00:1")
        allowed("2a01:4f8:1:1:0:5eff:7f00:1")
        assertEquals("documentation", classOf("2001:db8:1:1:0:5efe:7f00:1"))
        assertEquals("not-global-unicast", classOf("fe80::5efe:7f00:1"))
    }

    @Test
    fun `the cloud endpoints are denied`() {
        assertEquals("link-local", classOf("169.254.169.254"))
        assertEquals("link-local", classOf("169.254.170.2"))
        assertEquals("shared-address-space", classOf("100.100.100.200"))
        assertEquals("ietf-protocol-assignments", classOf("192.0.0.192"))
        assertEquals("not-global-unicast", classOf("fd00:ec2::254"))
        assertEquals("azure-wireserver", classOf("168.63.129.16"))
    }

    @Test
    fun `an array of an unusual length is denied`() {
        listOf(0, 1, 3, 5, 8, 17).forEach { length ->
            assertEquals("unusual-length", assertNotNull(AddressRanges.deny(ByteArray(length)), "length $length").rangeClass, "length $length")
        }
    }
}
