package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.cidr
import com.spartanlabs.webtools.http.MixedAnswers
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.TransportSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `OutboundUrlPolicy.Untrusted` through its factories and builder: the defaults; the port rules with their
 * two constant messages; suffixes canonicalised and copied, every refused suffix form throwing the constant message
 * without an echo; `extraDeniedRanges` extended and copied; `mixedAnswers`; `settings` as a property and as a block;
 * `untrusted(from)`; each property takes part in equality and `toString()`; `toString()` prints the sorted ports and the
 * counts, never a range or a suffix; set equality is order-free.
 */
class UntrustedPolicyComponentTest {

    private val emptyMessage = "allowedPorts must not be empty; use null to allow any port."
    private val rangeMessage = "allowedPorts must hold only ports from 1 to 65535."
    private val suffixMessage = "extraDeniedHostSuffixes must hold only dotted host-name suffixes (no IP literal, no leading or trailing dot)."
    private val canary = "SECRET-CANARY-7f3a"

    @Test
    fun `the empty block and the parameterless call give the defaults`() {
        val policy = OutboundUrlPolicy.untrusted {}
        assertEquals(OutboundUrlPolicy.untrusted(), policy)
        assertEquals(setOf(80, 443, 8080, 8443), policy.allowedPorts)
        assertEquals(emptySet(), policy.extraDeniedRanges)
        assertEquals(emptySet(), policy.extraDeniedHostSuffixes)
        assertEquals(MixedAnswers.Reject, policy.mixedAnswers)
        assertEquals(TransportSettings.UntrustedDefaults, policy.settings)
    }

    @Test
    fun `allowedPorts null means any port and the boundaries are accepted`() {
        assertNull(OutboundUrlPolicy.untrusted { allowedPorts = null }.allowedPorts)
        assertEquals(setOf(1, 65535), OutboundUrlPolicy.untrusted { allowedPorts = setOf(1, 65535) }.allowedPorts)
    }

    @Test
    fun `an empty port set and a port outside the range are refused with their messages`() {
        assertEquals(emptyMessage, assertFailsWith<IllegalArgumentException> { OutboundUrlPolicy.untrusted { allowedPorts = emptySet() } }.message)
        listOf(0, 65536, -1).forEach { port ->
            val failure = assertFailsWith<IllegalArgumentException>("$port") { OutboundUrlPolicy.untrusted { allowedPorts = setOf(port) } }
            assertEquals(rangeMessage, failure.message, "$port")
        }
    }

    @Test
    fun `suffixes are canonicalised and copied`() {
        assertEquals(setOf("corp.example"), OutboundUrlPolicy.untrusted { extraDeniedHostSuffixes = setOf("Corp.Example") }.extraDeniedHostSuffixes)
        assertEquals(setOf("corp"), OutboundUrlPolicy.untrusted { extraDeniedHostSuffixes = setOf("corp") }.extraDeniedHostSuffixes)
        val mutable = mutableSetOf("corp.example")
        val policy = OutboundUrlPolicy.untrusted { extraDeniedHostSuffixes = mutable }
        mutable += "other.example"
        assertEquals(setOf("corp.example"), policy.extraDeniedHostSuffixes)
    }

    @Test
    fun `every refused suffix form throws the constant message without echoing the entry`() {
        listOf("", ".corp", "corp.", "10.0", "1.2.3.4", "::1", "[::1]", "a b", "xn--n3h.net", "a..b", "$canary:1", ".$canary").forEach { entry ->
            val failure = assertFailsWith<IllegalArgumentException>(entry) { OutboundUrlPolicy.untrusted { extraDeniedHostSuffixes = setOf(entry) } }
            assertEquals(suffixMessage, failure.message, entry)
            assertFalse(failure.message!!.contains(canary, ignoreCase = true), entry)
        }
    }

    @Test
    fun `extra ranges are extended with plus-equals and the stored set is a copy`() {
        val block = cidr("10.0.0.0/8")
        val policy = OutboundUrlPolicy.untrusted { extraDeniedRanges += block }
        assertEquals(setOf(block), policy.extraDeniedRanges)
        val mutable = mutableSetOf(block)
        val copied = OutboundUrlPolicy.untrusted { extraDeniedRanges = mutable }
        mutable += cidr("192.168.0.0/16")
        assertEquals(setOf(block), copied.extraDeniedRanges)
    }

    @Test
    fun `mixedAnswers is kept`() {
        assertEquals(MixedAnswers.DropDenied, OutboundUrlPolicy.untrusted { mixedAnswers = MixedAnswers.DropDenied }.mixedAnswers)
    }

    @Test
    fun `settings as a block starts from the Untrusted defaults`() {
        val policy = OutboundUrlPolicy.untrusted { settings { callTimeout = 1.seconds } }
        assertEquals(TransportSettings(TransportSettings.UntrustedDefaults) { callTimeout = 1.seconds }, policy.settings)
        assertEquals(5.seconds, policy.settings.connectTimeout)
    }

    @Test
    fun `settings assigned then modified by a block modifies the assigned value`() {
        val x = TransportSettings(TransportSettings.TrustedDefaults) { callTimeout = 2.seconds }
        val policy = OutboundUrlPolicy.untrusted { settings = x; settings { maxRedirects = 0 } }
        assertEquals(2.seconds, policy.settings.callTimeout)
        assertEquals(0, policy.settings.maxRedirects)
        assertEquals(16, policy.settings.maxConcurrentRequests)
        val failure = assertFailsWith<IllegalArgumentException> { OutboundUrlPolicy.untrusted { settings { maxResponseBytes = 0 } } }
        assertTrue(failure.message!!.contains("maxResponseBytes"), failure.message)
    }

    @Test
    fun `untrusted from keeps everything the block does not set`() {
        val from = OutboundUrlPolicy.untrusted {
            allowedPorts = setOf(443)
            extraDeniedRanges = setOf(cidr("10.0.0.0/8"))
            extraDeniedHostSuffixes = setOf("corp.example")
            mixedAnswers = MixedAnswers.DropDenied
            settings { maxRedirects = 1 }
        }
        val derived = OutboundUrlPolicy.untrusted(from) { allowedPorts = null }
        assertNull(derived.allowedPorts)
        assertEquals(from.extraDeniedRanges, derived.extraDeniedRanges)
        assertEquals(from.extraDeniedHostSuffixes, derived.extraDeniedHostSuffixes)
        assertEquals(MixedAnswers.DropDenied, derived.mixedAnswers)
        assertEquals(from.settings, derived.settings)
        assertEquals(setOf(443), from.allowedPorts)
        assertEquals(from, OutboundUrlPolicy.untrusted(from) {})
    }

    //region one test per property
    private fun onlyThisProperty(configure: OutboundUrlPolicy.Untrusted.Builder.() -> Unit) {
        val base = OutboundUrlPolicy.untrusted()
        val once = OutboundUrlPolicy.untrusted(configure)
        val twice = OutboundUrlPolicy.untrusted(configure)
        assertNotEquals(base, once)
        assertNotEquals(base.toString(), once.toString())
        assertEquals(once, twice)
        assertEquals(once.hashCode(), twice.hashCode())
    }

    @Test
    fun `allowedPorts null takes part in equality and toString`() = onlyThisProperty { allowedPorts = null }

    @Test
    fun `allowedPorts set takes part in equality and toString`() = onlyThisProperty { allowedPorts = setOf(80) }

    @Test
    fun `extraDeniedRanges takes part in equality and toString`() = onlyThisProperty { extraDeniedRanges = setOf(cidr("10.0.0.0/8")) }

    @Test
    fun `extraDeniedHostSuffixes takes part in equality and toString`() = onlyThisProperty { extraDeniedHostSuffixes = setOf("corp.example") }

    @Test
    fun `mixedAnswers takes part in equality and toString`() = onlyThisProperty { mixedAnswers = MixedAnswers.DropDenied }

    @Test
    fun `settings takes part in equality and toString`() = onlyThisProperty { settings { maxRedirects = 1 } }
    //endregion

    @Test
    fun `toString prints the sorted ports or any, the counts and never a range or a suffix`() {
        val policy = OutboundUrlPolicy.untrusted {
            allowedPorts = setOf(8443, 80, 443, 8080)
            extraDeniedRanges = setOf(cidr("10.9.8.7/32"))
            extraDeniedHostSuffixes = setOf("${canary.lowercase()}.example")
        }
        val text = policy.toString()
        assertTrue(
            text.startsWith("Untrusted(allowedPorts=[80, 443, 8080, 8443], extraDeniedRanges=1, extraDeniedHostSuffixes=1, mixedAnswers=Reject, settings=TransportSettings("),
            text,
        )
        assertFalse(text.contains("10.9.8.7"), text)
        assertFalse(text.contains(canary, ignoreCase = true), text)
        assertTrue(OutboundUrlPolicy.untrusted { allowedPorts = null }.toString().startsWith("Untrusted(allowedPorts=any, "))
    }

    @Test
    fun `equality is order-free for the sets`() {
        val a = OutboundUrlPolicy.untrusted {
            allowedPorts = setOf(80, 443)
            extraDeniedRanges = setOf(cidr("10.0.0.0/8"), cidr("192.168.0.0/16"))
            extraDeniedHostSuffixes = setOf("a.example", "b.example")
        }
        val b = OutboundUrlPolicy.untrusted {
            allowedPorts = setOf(443, 80)
            extraDeniedRanges = setOf(cidr("192.168.0.0/16"), cidr("10.0.0.0/8"))
            extraDeniedHostSuffixes = setOf("b.example", "a.example")
        }
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
