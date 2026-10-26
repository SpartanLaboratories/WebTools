package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.IpLiteralGenerator
import com.spartanlabs.testing.support.webtools.http.UrlCorpus
import com.spartanlabs.webtools.http.StrictIp
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Level 4a - `StrictIp` versus the JDK's literal parser (seed 7; 100,000 strings per family): every string `StrictIp`
 * accepts is a literal the JDK reads to the same bytes without a lookup (an IPv6 string is handed bracketed, so an
 * unbracketed non-literal never reaches the resolver; a 16-byte mapped array is compared with the JDK's collapsed
 * `Inet4Address` through its last four bytes), the family matches and `toString()` starts with `/` (no host name). For
 * IPv6 strings `StrictIp` refuses, the bracketed form either parses (the JDK is looser) or throws `UnknownHostException`,
 * nothing else. The strict parser is a subset of the JDK's: whatever it accepts, the JDK connects to the same address.
 */
class StrictIpJdkDifferentialDeterministicTest {

    private val seed = 7
    private val count = 100_000

    private fun label(index: Int, text: String) = "seed $seed #$index <${UrlCorpus.visible(text)}>"

    @Test
    fun `every accepted IPv4 string is the same address to the JDK`() {
        var accepted = 0
        IpLiteralGenerator.ipv4(seed, count).forEachIndexed { index, text ->
            val mine = StrictIp.parseV4(text) ?: return@forEachIndexed
            accepted++
            val theirs = InetAddress.getByName(text)
            assertContentEquals(mine, theirs.address, label(index, text))
            assertEquals(4, theirs.address.size, label(index, text))
            assertTrue(theirs.toString().startsWith("/"), label(index, text) + " -> $theirs")
        }
        println("seed $seed: $accepted IPv4 strings accepted")
        assertTrue(accepted >= 25_000, "seed $seed: only $accepted IPv4 strings were accepted")
    }

    @Test
    fun `every accepted IPv6 string is the same address to the JDK and every refused one is a literal or unknown`() {
        var accepted = 0
        var jdkLooser = 0
        IpLiteralGenerator.ipv6(seed, count).forEachIndexed { index, text ->
            val mine = StrictIp.parseV6(text)
            if (mine == null) {
                try {
                    InetAddress.getByName("[$text]")
                    jdkLooser++
                } catch (_: UnknownHostException) {
                    // refused by both
                } catch (t: Throwable) {
                    fail(label(index, text) + " made the JDK throw $t")
                }
                return@forEachIndexed
            }
            accepted++
            val theirs = InetAddress.getByName("[$text]")
            val theirBytes = theirs.address
            when (theirBytes.size) {
                16 -> assertContentEquals(mine, theirBytes, label(index, text))
                4 -> assertContentEquals(mine.copyOfRange(12, 16), theirBytes, label(index, text) + " (collapsed mapped address)")
                else -> fail(label(index, text) + " gave ${theirBytes.size} bytes")
            }
            assertTrue(theirs.toString().startsWith("/"), label(index, text) + " -> $theirs")
        }
        println("seed $seed: $accepted IPv6 strings accepted; the JDK accepted $jdkLooser that StrictIp refused")
        assertTrue(accepted >= 10_000, "seed $seed: only $accepted IPv6 strings were accepted")
    }
}
