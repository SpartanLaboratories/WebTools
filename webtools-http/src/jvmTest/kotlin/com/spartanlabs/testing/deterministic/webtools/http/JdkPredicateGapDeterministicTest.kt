package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.inet
import com.spartanlabs.webtools.http.AddressRanges
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Level 4a - the 26 non-public addresses of the parent research F7 are all denied by `AddressRanges.deny`: the ten the JDK
 * predicates catch and the sixteen they miss. Which of the 26 the predicates (`isAnyLocalAddress`, `isLoopbackAddress`,
 * `isLinkLocalAddress`, `isSiteLocalAddress`, `isMulticastAddress`) flag is recorded as the documentation of the gap; the
 * only JDK-dependent assertion is that the gap exists.
 */
class JdkPredicateGapDeterministicTest {

    private val caughtByJdk = listOf("127.0.0.1", "0.0.0.0", "10.0.0.1", "169.254.169.254", "224.0.0.1", "::1", "::", "fe80::1", "fec0::1", "ff02::1")
    private val missedByJdk = listOf(
        "100.64.0.1", "192.0.0.1", "198.18.0.1", "192.0.2.1", "240.0.0.1", "255.255.255.255", "fd00::1", "fc00::1", "::7f00:1",
        "64:ff9b::7f00:1", "2002:7f00:1::", "2001:db8::1", "100::1", "::ffff:0:7f00:1", "2001::1", "5f00::1",
    )

    private fun jdkFlags(address: InetAddress): Boolean =
        address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress

    @Test
    fun `all 26 addresses are denied by the table`() {
        (caughtByJdk + missedByJdk).forEach { text -> assertNotNull(AddressRanges.deny(inet(text).address), "$text is allowed") }
        assertTrue(caughtByJdk.size + missedByJdk.size == 26)
    }

    @Test
    fun `the JDK predicates miss part of the table`() {
        val flagged = (caughtByJdk + missedByJdk).filter { jdkFlags(inet(it)) }
        val missed = (caughtByJdk + missedByJdk) - flagged.toSet()
        println("JDK ${System.getProperty("java.version")} predicates flag ${flagged.size} of 26: $flagged; missed: $missed")
        assertTrue(missed.isNotEmpty(), "the JDK predicates flagged every address: the gap closed")
    }
}
