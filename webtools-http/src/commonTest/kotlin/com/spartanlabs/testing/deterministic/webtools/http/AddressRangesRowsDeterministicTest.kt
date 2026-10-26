package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.RegistryRows
import com.spartanlabs.testing.support.webtools.http.addr
import com.spartanlabs.webtools.http.AddressRanges
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Level 4a - every row of the IANA registries (`RegistryRows`) is denied by `AddressRanges.deny` at its first and its
 * last address with exactly the class the table reports (a nested row's edge belongs to the more specific row). The IPv4
 * first and last addresses are also re-derived from the block with bit arithmetic independent of `CidrBlock`. The row
 * counts pin the table's size: 28 IPv4 entries (26 registry prefixes plus the two non-registry rows) and 25 IPv6 rows.
 */
class AddressRangesRowsDeterministicTest {

    private fun classOf(address: String): String =
        assertNotNull(AddressRanges.deny(addr(address)), "$address is allowed").rangeClass

    // The first and last address of an IPv4 block, from the block text alone, as unsigned 32-bit arithmetic over a Long.
    private fun v4Edges(block: String): Pair<String, String> {
        val (address, prefix) = block.split('/')
        val value = address.split('.').fold(0L) { acc, part -> (acc shl 8) or part.toLong() }
        val hostBits = 32 - prefix.toInt()
        val first = if (hostBits == 32) 0L else (value shr hostBits) shl hostBits
        val last = first or ((1L shl hostBits) - 1)
        fun dotted(v: Long) = (3 downTo 0).joinToString(".") { ((v shr (8 * it)) and 0xFF).toString() }
        return dotted(first) to dotted(last)
    }

    @Test
    fun `every IPv4 row is denied at both edges with its class`() {
        RegistryRows.ipv4.forEach { row ->
            assertEquals(row.firstClass, classOf(row.first), "${row.block} first ${row.first}")
            assertEquals(row.lastClass, classOf(row.last), "${row.block} last ${row.last}")
        }
    }

    @Test
    fun `the IPv4 edges of the table are the block edges`() {
        RegistryRows.ipv4.forEach { row ->
            val (first, last) = v4Edges(row.block)
            assertEquals(first, row.first, row.block)
            assertEquals(last, row.last, row.block)
        }
    }

    @Test
    fun `every IPv6 row is denied at both edges with its class`() {
        RegistryRows.ipv6.forEach { row ->
            assertEquals(row.firstClass, classOf(row.first), "${row.block} first ${row.first}")
            assertEquals(row.lastClass, classOf(row.last), "${row.block} last ${row.last}")
        }
    }

    @Test
    fun `the table lists 28 IPv4 entries and 25 IPv6 rows`() {
        assertEquals(28, RegistryRows.ipv4.size)
        assertEquals(26, RegistryRows.ipv4.count { it.block != "224.0.0.0/4" && it.block != "168.63.129.16/32" })
        assertEquals(25, RegistryRows.ipv6.size)
    }

    @Test
    fun `multicast is denied at both edges`() {
        assertEquals("multicast", classOf("224.0.0.0"))
        assertEquals("multicast", classOf("239.255.255.255"))
    }
}
