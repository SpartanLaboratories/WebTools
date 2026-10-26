package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.HostRules
import com.spartanlabs.webtools.http.HostVerdict
import com.spartanlabs.webtools.http.RefusalReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * Level 4a - `HostRules.classify` fed directly with text `Url.parse` never lets through: slashes, spaces, tabs, zones,
 * fullwidth digits, the empty string, stray punctuation, upper-case names (folded ASCII-only: a Kelvin sign is not a
 * `k`), empty labels, and the policy suffixes at a label boundary only. A `Literal` carries 4 bytes for a dotted quad
 * and 16 for IPv6 (a mapped literal is not collapsed).
 */
class HostRulesDirectInputDeterministicTest {

    private val none = emptySet<String>()

    private fun refusedAs(host: String, reason: RefusalReason, suffixes: Set<String> = none) {
        val verdict = HostRules.classify(host, suffixes)
        assertEquals(reason, assertIs<HostVerdict.Refused>(verdict, host).reason, host)
    }

    @Test
    fun `text the parser never lets through is HostForm`() {
        // "０" is U+FF10 FULLWIDTH DIGIT ZERO, visible and kept literal.
        listOf("127.0.0.1/0", "127.0.0.1 foo", "127.0.0.1\tfoo", "fe80::1%eth0", "０.0.0.0", "", "-%").forEach {
            refusedAs(it, RefusalReason.HostForm)
        }
    }

    @Test
    fun `upper case is folded ASCII-only`() {
        refusedAs("LOCALHOST", RefusalReason.ReservedName)
        refusedAs("0X7F.1", RefusalReason.HostForm)
        refusedAs("LOCALHOST.", RefusalReason.ReservedName)
        assertSame(HostVerdict.Name, HostRules.classify("A.B.C", none))
        // "K" is U+212A KELVIN SIGN, a look-alike of K; the ASCII-only fold leaves it outside the host charset.
        refusedAs("Kelvin.example", RefusalReason.HostForm)
    }

    @Test
    fun `an empty label is HostForm`() {
        listOf(".", "..", "a..b", ".a", "a..").forEach { refusedAs(it, RefusalReason.HostForm) }
    }

    @Test
    fun `a Literal carries 4 bytes for a dotted quad and 16 for IPv6`() {
        assertEquals(4, assertIs<HostVerdict.Literal>(HostRules.classify("10.0.0.5", none)).address.size)
        assertEquals(16, assertIs<HostVerdict.Literal>(HostRules.classify("::1", none)).address.size)
        assertEquals(16, assertIs<HostVerdict.Literal>(HostRules.classify("::ffff:1.2.3.4", none)).address.size)
    }

    @Test
    fun `a policy suffix is honoured at a label boundary only`() {
        val suffixes = setOf("corp.example")
        refusedAs("x.corp.example", RefusalReason.ReservedName, suffixes)
        refusedAs("corp.example", RefusalReason.ReservedName, suffixes)
        assertSame(HostVerdict.Name, HostRules.classify("xcorp.example", suffixes))
    }
}
