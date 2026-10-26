package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.IpLiteralGenerator
import com.spartanlabs.testing.support.webtools.http.UrlCorpus
import com.spartanlabs.webtools.http.StrictIp
import com.spartanlabs.webtools.http.Url
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 4a - `StrictIp.parseV6` agrees with `Url`'s own IPv6 canonicalisation (`UrlHost.kt`, which this unit leaves
 * untouched) on the IPv6 inputs of `UrlCorpus` and on 20,000 seeded literals: when `Url` refuses `http://[L]/`,
 * `parseV6(L)` is `null`; when `Url` accepts, `parseV6(L)` is the address `Url.host` prints (an IPv4-mapped host is
 * printed as dotted IPv4 and is read back as the low four bytes of a mapped address). At least 2,000 generated literals
 * are accepted, so both outcomes are exercised. A disagreement prints the seed and the literal.
 */
class StrictIpUrlAgreementDeterministicTest {

    private val seed = 7
    private val count = 20_000

    // The bracket content of an input that starts "http://[": up to the first ']', or to the first '/' or the end when
    // the bracket is never closed.
    private fun bracketContent(input: String): String {
        val inner = input.removePrefix("http://[")
        val close = inner.indexOf(']')
        if (close >= 0) return inner.substring(0, close)
        val slash = inner.indexOf('/')
        return if (slash >= 0) inner.substring(0, slash) else inner
    }

    private fun corpusLiterals(): List<String> = UrlCorpus.allInputs.filter { it.startsWith("http://[") }.map { bracketContent(it) }

    // The bytes Url.host names: parsed by parseV6 when it holds ':', else the dotted host as a mapped address's low four bytes.
    private fun hostBytes(host: String): ByteArray {
        if (':' in host) return assertNotNull(StrictIp.parseV6(host), "Url printed a host parseV6 refuses: $host")
        val quad = assertNotNull(StrictIp.parseV4(host), "Url printed a host parseV4 refuses: $host")
        return ByteArray(16) { i -> if (i < 10) 0 else if (i < 12) 0xFF.toByte() else quad[i - 12] }
    }

    // Returns true when Url accepted the literal.
    private fun agree(literal: String, label: String): Boolean {
        val parsed = Url.parse("http://[$literal]/")
        val strict = StrictIp.parseV6(literal)
        val url = parsed.getOrNull()
        if (url == null) {
            assertNull(strict, "$label: Url refused <${UrlCorpus.visible(literal)}> but parseV6 accepted it")
            return false
        }
        assertNotNull(strict, "$label: Url accepted <${UrlCorpus.visible(literal)}> as ${url.host} but parseV6 refused it")
        assertContentEquals(hostBytes(url.host), strict, "$label: <${UrlCorpus.visible(literal)}> parsed to ${url.host}")
        return true
    }

    @Test
    fun `parseV6 agrees with Url on the corpus IPv6 inputs`() {
        val literals = corpusLiterals()
        assertTrue(literals.isNotEmpty(), "the corpus holds no bracketed input")
        literals.forEach { agree(it, "corpus") }
    }

    @Test
    fun `parseV6 agrees with Url on 20000 generated literals`() {
        var accepted = 0
        IpLiteralGenerator.ipv6(seed, count).forEachIndexed { index, literal ->
            if (agree(literal, "seed $seed literal #$index")) accepted++
        }
        assertTrue(accepted >= 2_000, "seed $seed: only $accepted of $count literals were accepted by Url")
    }
}
