package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.cidr
import com.spartanlabs.webtools.http.MixedAnswers
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.TransportSettings
import com.spartanlabs.webtools.http.withSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - the `internal` `OutboundUrlPolicy.withSettings`: a `Trusted` or `Untrusted` with every property set comes
 * back as the same kind, equal to the original except `settings`; the original is unchanged; the identity derivation
 * equals the original; the call facades' use raises `maxConcurrentRequests` and leaves the per-host limit alone.
 */
class PolicyWithSettingsComponentTest {

    private val other = TransportSettings(TransportSettings.TrustedDefaults) { callTimeout = 7.seconds; maxRedirects = 2 }

    @Test
    fun `a Trusted keeps pins and cleartext and takes the new settings`() {
        val original = OutboundUrlPolicy.trusted { pinnedHosts = setOf("a.example"); allowCleartext = true; settings { maxRedirects = 1 } }
        val derived = assertIs<OutboundUrlPolicy.Trusted>(original.withSettings(other))
        assertEquals(other, derived.settings)
        assertEquals(original.pinnedHosts, derived.pinnedHosts)
        assertEquals(original.allowCleartext, derived.allowCleartext)
        assertEquals(OutboundUrlPolicy.trusted(original) { settings = other }, derived)
        assertEquals(1, original.settings.maxRedirects)
    }

    @Test
    fun `an Untrusted keeps ports ranges suffixes and mixedAnswers and takes the new settings`() {
        val original = OutboundUrlPolicy.untrusted {
            allowedPorts = setOf(443)
            extraDeniedRanges = setOf(cidr("10.0.0.0/8"))
            extraDeniedHostSuffixes = setOf("corp.example")
            mixedAnswers = MixedAnswers.DropDenied
        }
        val derived = assertIs<OutboundUrlPolicy.Untrusted>(original.withSettings(other))
        assertEquals(other, derived.settings)
        assertEquals(original.allowedPorts, derived.allowedPorts)
        assertEquals(original.extraDeniedRanges, derived.extraDeniedRanges)
        assertEquals(original.extraDeniedHostSuffixes, derived.extraDeniedHostSuffixes)
        assertEquals(MixedAnswers.DropDenied, derived.mixedAnswers)
        assertEquals(OutboundUrlPolicy.untrusted(original) { settings = other }, derived)
        assertEquals(TransportSettings.UntrustedDefaults, original.settings)
    }

    @Test
    fun `the identity derivation equals the original`() {
        val trusted = OutboundUrlPolicy.trusted { pinnedHosts = setOf("a.example") }
        assertEquals(trusted, trusted.withSettings(trusted.settings))
        val untrusted = OutboundUrlPolicy.untrusted { allowedPorts = null }
        assertEquals(untrusted, untrusted.withSettings(untrusted.settings))
        assertSame(trusted.settings, trusted.withSettings(trusted.settings).settings)
    }

    @Test
    fun `the facade raises concurrency to the burst and leaves the per-host limit`() {
        val policy: OutboundUrlPolicy = OutboundUrlPolicy.untrusted()
        val raised = policy.withSettings(TransportSettings(policy.settings) { maxConcurrentRequests = maxOf(maxConcurrentRequests, 32) })
        assertEquals(32, raised.settings.maxConcurrentRequests)
        assertEquals(2, raised.settings.maxConcurrentRequestsPerHost)
        assertEquals(8, policy.settings.maxConcurrentRequests)
    }
}
