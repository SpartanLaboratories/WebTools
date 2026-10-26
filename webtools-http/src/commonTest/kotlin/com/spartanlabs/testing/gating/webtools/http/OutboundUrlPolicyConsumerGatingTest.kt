package com.spartanlabs.testing.gating.webtools.http

import com.spartanlabs.webtools.http.CidrBlock
import com.spartanlabs.webtools.http.MixedAnswers
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// A policy and a reason in a function signature: a consumer writes exactly this without any opt-in.
private fun describe(policy: OutboundUrlPolicy, reason: RefusalReason): String = "${policy.settings.maxRedirects}:${reason.name}"

/**
 * Level 1 - the permanent consumer of the outbound-URL policy API, compiled in `commonTest` **without any opt-in**. It
 * builds both policies through all four factories, derives settings from both default sets, unwraps a `CidrBlock.parse`
 * failure with `fold`, matches a policy with a `when` that has no `else` (the sealed pair is the contract) and
 * `mixedAnswers` with a `when` that keeps an `else`, and passes a policy and a `RefusalReason` through a function
 * signature. Compiling it proves the API is reachable without `ExperimentalForInheritanceWebToolsApi`; finding this class
 * in `gatingTest`'s result XML proves the level task reaches the commonTest source set.
 */
class OutboundUrlPolicyConsumerGatingTest {

    @Test
    fun `both policies build through every factory`() {
        val trusted = OutboundUrlPolicy.trusted { pinnedHosts = setOf("api.example.com") }
        val untrusted = OutboundUrlPolicy.untrusted {
            allowedPorts = null
            extraDeniedRanges += CidrBlock.parse("10.0.0.0/8").getOrThrow()
            mixedAnswers = MixedAnswers.DropDenied
            settings { callTimeout = 20.seconds }
        }
        val trustedAgain = OutboundUrlPolicy.trusted(trusted) { allowCleartext = true }
        val untrustedAgain = OutboundUrlPolicy.untrusted(untrusted) { allowedPorts = setOf(443) }
        assertEquals(setOf("api.example.com"), trustedAgain.pinnedHosts)
        assertNull(untrusted.allowedPorts)
        assertEquals(setOf(443), untrustedAgain.allowedPorts)
        assertEquals(20.seconds, untrustedAgain.settings.callTimeout)
        assertEquals(1, untrusted.extraDeniedRanges.size)
    }

    @Test
    fun `settings derive from both default sets`() {
        val trusted = TransportSettings.TrustedDefaults
        val untrusted = TransportSettings.UntrustedDefaults
        val derived = TransportSettings(untrusted) { maxRedirects = 1 }
        assertEquals(5, trusted.maxRedirects)
        assertEquals(3, untrusted.maxRedirects)
        assertEquals(1, derived.maxRedirects)
    }

    @Test
    fun `a CidrBlock parse failure is unwrapped with fold`() {
        val described = CidrBlock.parse("10.0.0.5/8").fold(onSuccess = { "block $it" }, onFailure = { "refused" })
        assertEquals("refused", described)
        assertEquals("block 10.0.0.0/8", CidrBlock.parse("10.0.0.0/8").fold(onSuccess = { "block $it" }, onFailure = { "refused" }))
    }

    @Test
    fun `a when over a policy needs no else and a when over mixedAnswers keeps one`() {
        val policies: List<OutboundUrlPolicy> = listOf(OutboundUrlPolicy.trusted(), OutboundUrlPolicy.untrusted())
        val kinds = policies.map { policy ->
            when (policy) {
                is OutboundUrlPolicy.Trusted -> "trusted:${policy.pinnedHosts.size}"
                is OutboundUrlPolicy.Untrusted -> "untrusted:${policy.allowedPorts?.size}"
            }
        }
        assertEquals(listOf("trusted:0", "untrusted:4"), kinds)
        val mixed = when (OutboundUrlPolicy.untrusted().mixedAnswers) {
            MixedAnswers.Reject -> "reject"
            else -> "other"
        }
        assertEquals("reject", mixed)
    }

    @Test
    fun `a policy and a reason pass through a signature`() {
        assertEquals("5:Scheme", describe(OutboundUrlPolicy.trusted(), RefusalReason.Scheme))
        assertTrue(describe(OutboundUrlPolicy.untrusted(), RefusalReason.DeniedAddress).startsWith("3:"))
    }
}
