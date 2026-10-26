package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.AddressRanges
import java.net.InetAddress
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Level 4a - the table is a superset of the JDK's own check: for 200,000 seeded four-byte and 200,000 sixteen-byte
 * addresses (seed 7; the sixteen-byte ones biased towards the interesting first bytes and the mapped prefix), whenever
 * any JDK predicate flags the address, `AddressRanges.deny` of the JDK object's own `address` bytes (4 for a collapsed
 * mapped address, as the guards pass them) is non-null. The JDK's output is never pinned. A failure prints the seed and
 * the address.
 */
class AddressRangesJdkSupersetDeterministicTest {

    private val seed = 7
    private val count = 200_000
    // Forced first bytes, each probing a region the JDK flags or the table treats specially: 0x00 the unspecified, loopback,
    // mapped and NAT64 space (::/8); 0xFE link-local and site-local (fe80::/10, fec0::/10); 0xFF multicast (ff00::/8); 0x20 and
    // 0x26 global unicast (2000::/3, where the table's explicit rows and the ISATAP rule live); 0xFC unique-local (fc00::/7).
    private val firstBytes = byteArrayOf(0x00, 0xFE.toByte(), 0xFF.toByte(), 0x20, 0x26, 0xFC.toByte())

    private fun jdkFlags(address: InetAddress): Boolean =
        address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress

    private fun check(bytes: ByteArray, index: Int) {
        val address = InetAddress.getByAddress(bytes)
        if (jdkFlags(address)) {
            assertNotNull(AddressRanges.deny(address.address), "seed $seed #$index: the JDK flags $address and the table allows it")
        }
    }

    @Test
    fun `whenever the JDK flags a four-byte address the table denies it`() {
        val random = Random(seed)
        var flagged = 0
        repeat(count) { index ->
            val bytes = random.nextBytes(4)
            if (jdkFlags(InetAddress.getByAddress(bytes))) flagged++
            check(bytes, index)
        }
        assertTrue(flagged > 0, "seed $seed: the JDK flagged no four-byte address")
    }

    @Test
    fun `whenever the JDK flags a sixteen-byte address the table denies it`() {
        val random = Random(seed)
        var flagged = 0
        repeat(count) { index ->
            val bytes = random.nextBytes(16)
            if (random.nextBoolean()) bytes[0] = firstBytes[random.nextInt(firstBytes.size)]
            // One array in ten gets the IPv4-mapped prefix ::ffff:0:0/96, so the JDK's collapse to an Inet4Address is exercised.
            if (random.nextInt(10) == 0) {
                for (i in 0..9) bytes[i] = 0
                bytes[10] = 0xFF.toByte()
                bytes[11] = 0xFF.toByte()
            }
            if (jdkFlags(InetAddress.getByAddress(bytes))) flagged++
            check(bytes, index)
        }
        assertTrue(flagged > 0, "seed $seed: the JDK flagged no sixteen-byte address")
    }
}
