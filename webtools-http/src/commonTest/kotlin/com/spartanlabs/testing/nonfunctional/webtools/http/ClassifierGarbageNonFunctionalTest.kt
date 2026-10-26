package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.testing.support.webtools.http.IpLiteralGenerator
import com.spartanlabs.testing.support.webtools.http.UrlCorpus
import com.spartanlabs.testing.support.webtools.http.UrlInputGenerator
import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.CidrBlock
import com.spartanlabs.webtools.http.HostRules
import com.spartanlabs.webtools.http.HostVerdict
import com.spartanlabs.webtools.http.HttpSyntax
import com.spartanlabs.webtools.http.RedirectAdmission
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.StrictIp
import com.spartanlabs.webtools.http.Url
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Level 4c - 150,000 seeded inputs (seed 11): 50,000 from `UrlInputGenerator`, 50,000 from `IpLiteralGenerator` and
 * 50,000 drawn from a pool of host-like and hostile characters. `HostRules.classify`, `StrictIp.parseV4`/`parseV6`,
 * `CidrBlock.parse` and `UrlGuard.admitRedirect` never throw, and every success keeps its invariant: a parsed quad prints
 * back to its text, a parsed IPv6 round-trips through `formatV6`, a parsed block re-parses from its `toString()`, a host
 * with a character outside the charset is `Refused(HostForm)`, a `Literal` is 4 or 16 bytes, and a redirect is `Admitted`
 * only when `Url.parse` accepts the target. A failure prints the seed and the visible input.
 */
class ClassifierGarbageNonFunctionalTest {

    private val seed = 11
    private val count = 50_000
    private val guard = untrustedGuard()
    private val previous = url("https://example.com/a")

    // "\u0000" is U+0000 NUL and "\uD800" is a lone high surrogate (both invisible, so escaped); "é" and "日" are visible.
    private val pool: String = "0123456789abcdefABCDEF:.%/[]-_ \t\u0000é日\uD800x"

    private fun fromPool(random: Random): String {
        val length = random.nextInt(41)
        return buildString(length) { repeat(length) { append(pool[random.nextInt(pool.length)]) } }
    }

    private fun inputs(): Sequence<String> {
        val random = Random(seed)
        return UrlInputGenerator.generate(seed, count) +
            IpLiteralGenerator.ipv4(seed, count / 2).asSequence() + IpLiteralGenerator.ipv6(seed, count / 2).asSequence() +
            generateSequence { fromPool(random) }.take(count)
    }

    private fun isHostChar(c: Char): Boolean = c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == ':'

    private fun check(index: Int, text: String) {
        val label = "seed $seed input #$index ${UrlCorpus.visible(text)}"
        try {
            val verdict = HostRules.classify(text, emptySet())
            val folded = text.map { HttpSyntax.asciiLower(it) }
            if (text.isEmpty() || folded.any { !isHostChar(it) }) {
                assertTrue(verdict is HostVerdict.Refused && verdict.reason == RefusalReason.HostForm, "$label: $verdict")
            }
            if (verdict is HostVerdict.Literal) assertTrue(verdict.address.size == 4 || verdict.address.size == 16, label)

            StrictIp.parseV4(text)?.let { bytes -> assertEquals(text, bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }, label) }
            StrictIp.parseV6(text)?.let { bytes -> assertContentEquals(bytes, StrictIp.parseV6(StrictIp.formatV6(bytes)), label) }
            CidrBlock.parse(text).onSuccess { block -> assertEquals(block, CidrBlock.parse(block.toString()).getOrNull(), label) }

            val admission = guard.admitRedirect(text, previous)
            if (admission is RedirectAdmission.Admitted) assertTrue(Url.parse(text).isSuccess, label)
        } catch (e: AssertionError) {
            throw e
        } catch (t: Throwable) {
            fail("$label threw $t")
        }
    }

    @Test
    fun `the classifier never throws and every success keeps its invariant`() {
        var total = 0
        inputs().forEachIndexed { index, text -> check(index, text); total++ }
        assertEquals(3 * count, total)
    }
}
