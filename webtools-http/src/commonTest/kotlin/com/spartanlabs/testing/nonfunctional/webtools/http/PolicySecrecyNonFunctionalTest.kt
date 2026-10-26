package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.testing.support.webtools.http.UrlCorpus
import com.spartanlabs.testing.support.webtools.http.cidr
import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.AnswerVerdict
import com.spartanlabs.webtools.http.CidrBlock
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RedirectAdmission
import com.spartanlabs.webtools.http.TransportSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Level 4c - a canary placed in every position that can fail or print: a pinned host (valid and invalid), a suffix
 * (valid and invalid), a `CidrBlock.parse` text in each failure position that can hold text, an `Untrusted` policy's extra
 * range and suffix, an `admitRedirect` target and a URL a `GuardRefusal` came from. No builder or `CidrBlock.parse`
 * message, no `toString()` of a `Trusted`, an `Untrusted`, a `TransportSettings`, a `GuardRefusal`, an `AnswerVerdict` or
 * a `RedirectAdmission` refusal contains the canary (either case), an address or a range of the policy; every message is
 * one of the constants. (`CidrBlock.toString()` prints the block by design and is excluded.)
 */
class PolicySecrecyNonFunctionalTest {

    private val canary = UrlCorpus.CANARY
    private val lower = canary.lowercase()
    private val range = "10.9.8.7/32"

    private val constants: Set<String> = setOf(
        "pinnedHosts must hold only host names or IP literals, without a port, path or user-info.",
        "extraDeniedHostSuffixes must hold only dotted host-name suffixes (no IP literal, no leading or trailing dot).",
        "allowedPorts must not be empty; use null to allow any port.",
        "allowedPorts must hold only ports from 1 to 65535.",
        "A CIDR block must be written <address>/<prefix>.",
        "The CIDR block address must be a plain dotted-decimal IPv4 or an IPv6 address.",
        "The CIDR block prefix length must be a decimal number within the address family's width, without a sign or a leading zero.",
        "The CIDR block has bits set beyond its prefix length.",
    )

    private fun clean(text: String, label: String) {
        assertFalse(text.contains(canary, ignoreCase = true), "$label holds the canary: $text")
        assertFalse(text.contains("10.9.8.7"), "$label holds the range: $text")
    }

    @Test
    fun `no builder or parse message holds the canary and every message is a constant`() {
        val messages = mutableSetOf<String>()
        messages += assertFailsWith<IllegalArgumentException> { OutboundUrlPolicy.trusted { pinnedHosts = setOf("$canary:443") } }.message!!
        messages += assertFailsWith<IllegalArgumentException> { OutboundUrlPolicy.untrusted { extraDeniedHostSuffixes = setOf(".$canary") } }.message!!
        messages += assertFailsWith<IllegalArgumentException> { OutboundUrlPolicy.untrusted { extraDeniedHostSuffixes = setOf("$canary:1") } }.message!!
        listOf(canary, "$canary/8", "10.0.0.0/$canary", "10.0.0.5/8").forEach { text ->
            messages += CidrBlock.parse(text).exceptionOrNull()!!.message!!
        }
        messages.forEach { clean(it, "message") }
        assertTrue(messages.all { it in constants }, "not constants: ${messages - constants}")
        assertEquals(6, messages.size)
    }

    @Test
    fun `no policy or settings toString holds the canary or a range`() {
        val trusted = OutboundUrlPolicy.trusted { pinnedHosts = setOf("$lower.example") }
        val untrusted = OutboundUrlPolicy.untrusted { extraDeniedRanges = setOf(cidr(range)); extraDeniedHostSuffixes = setOf("$lower.example") }
        clean(trusted.toString(), "Trusted")
        clean(untrusted.toString(), "Untrusted")
        clean(TransportSettings.UntrustedDefaults.toString(), "TransportSettings")
    }

    @Test
    fun `no refusal toString holds the canary or a range`() {
        val guard = untrustedGuard { extraDeniedRanges = setOf(cidr(range)); extraDeniedHostSuffixes = setOf("$lower.example") }
        val previous = url("https://example.com/")
        listOf("http://$lower.example/", "http://$lower:6379/", "http://u@$lower/", "http://10.9.8.7/", "http://[$lower]/").forEach { target ->
            val refused = assertIs<RedirectAdmission.Refused>(guard.admitRedirect(target, previous), target)
            clean(refused.toString(), "RedirectAdmission $target")
            clean(refused.refusal.toString(), "GuardRefusal $target")
        }
        val hop = guard.checkHop(url("http://$lower.example:6379/"), null)
        clean(hop.toString(), "GuardRefusal from a hop")
        val answer = assertIs<AnswerVerdict.Refused>(guard.judgeAnswer(listOf(byteArrayOf(10, 9, 8, 7))))
        clean(answer.toString(), "AnswerVerdict")
        clean(answer.refusal.toString(), "AnswerVerdict refusal")
    }
}
