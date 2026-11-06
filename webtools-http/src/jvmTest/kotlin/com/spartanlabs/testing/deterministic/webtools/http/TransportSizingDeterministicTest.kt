package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.PermitRecordingBudget
import com.spartanlabs.webtools.http.CallBudget
import com.spartanlabs.webtools.http.CidrBlock
import com.spartanlabs.webtools.http.MixedAnswers
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.TransportSettings
import com.spartanlabs.webtools.http.WindowedCallBudget
import com.spartanlabs.webtools.http.sizedFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.minutes

/**
 * Level 4a - the transport sizing `OutboundUrlPolicy.sizedFor(budget)`: a `WindowedCallBudget`'s `maxBurst` raises both concurrency limits to at
 * least the burst and never lowers either; a burst below both limits changes nothing; an `Untrusted` policy keeps its kind and every other
 * property; a budget that is not a `WindowedCallBudget` leaves the policy as the very same instance.
 */
class TransportSizingDeterministicTest {

    // Runs `block` with a budget whose `maxBurst` is `burst` (one minute window of that limit), and closes it.
    private fun <T> withBurst(burst: Int, block: (WindowedCallBudget) -> T): T =
        WindowedCallBudget(listOf(RateWindow("minute", burst, 1.minutes))).use { budget ->
            assertEquals(burst, budget.maxBurst)
            block(budget)
        }

    private fun limitsOf(policy: OutboundUrlPolicy): Pair<Int, Int> = policy.settings.maxConcurrentRequests to policy.settings.maxConcurrentRequestsPerHost

    private fun trusted(total: Int, perHost: Int): OutboundUrlPolicy.Trusted =
        OutboundUrlPolicy.trusted { settings { maxConcurrentRequests = total; maxConcurrentRequestsPerHost = perHost } }

    @Test
    fun `Trusted defaults are raised to the burst on both limits`() {
        val defaults = OutboundUrlPolicy.trusted()
        assertEquals(16 to 5, limitsOf(defaults))
        assertEquals(TransportSettings.TrustedDefaults, defaults.settings)

        val sized = withBurst(8) { defaults.sizedFor(it) }

        assertEquals(16 to 8, limitsOf(sized))
    }

    @Test
    fun `a value above the burst is never lowered`() {
        val policy = trusted(total = 32, perHost = 12)

        assertEquals(32 to 12, limitsOf(withBurst(8) { policy.sizedFor(it) }))
        assertEquals(40 to 40, limitsOf(withBurst(40) { policy.sizedFor(it) }))
        assertEquals(32 to 20, limitsOf(withBurst(20) { policy.sizedFor(it) }))
    }

    @Test
    fun `a burst below both limits changes nothing`() {
        val defaults = OutboundUrlPolicy.trusted()
        assertEquals(defaults, withBurst(4) { defaults.sizedFor(it) })
        assertEquals(defaults, withBurst(5) { defaults.sizedFor(it) })

        val pinned = OutboundUrlPolicy.trusted { pinnedHosts = setOf("api.example.test"); allowCleartext = true }
        assertEquals(pinned, withBurst(1) { pinned.sizedFor(it) })
    }

    @Test
    fun `Untrusted keeps its kind and every other property`() {
        val range = CidrBlock.parse("203.0.113.0/24").getOrThrow()
        val policy = OutboundUrlPolicy.untrusted {
            allowedPorts = setOf(443, 8443)
            extraDeniedRanges = setOf(range)
            mixedAnswers = MixedAnswers.DropDenied
        }
        assertEquals(8 to 2, limitsOf(policy))

        val sized = assertIs<OutboundUrlPolicy.Untrusted>(withBurst(20) { policy.sizedFor(it) })

        assertEquals(20 to 20, limitsOf(sized))
        assertEquals(setOf(443, 8443), sized.allowedPorts)
        assertEquals(setOf(range), sized.extraDeniedRanges)
        assertEquals(policy.extraDeniedHostSuffixes, sized.extraDeniedHostSuffixes)
        assertEquals(MixedAnswers.DropDenied, sized.mixedAnswers)
        // Nothing but the two limits differs: putting them back gives the original settings.
        val restored = TransportSettings(sized.settings) { maxConcurrentRequests = 8; maxConcurrentRequestsPerHost = 2 }
        assertEquals(policy.settings, restored)
    }

    @Test
    fun `a budget that is not a WindowedCallBudget leaves the policy as it is`() {
        val policy = OutboundUrlPolicy.trusted()

        assertSame(policy, policy.sizedFor(CallBudget.Unlimited))
        // A wrapper is not a `WindowedCallBudget`, whatever it wraps.
        withBurst(20) { windowed -> assertSame(policy, policy.sizedFor(PermitRecordingBudget(windowed))) }
    }
}
