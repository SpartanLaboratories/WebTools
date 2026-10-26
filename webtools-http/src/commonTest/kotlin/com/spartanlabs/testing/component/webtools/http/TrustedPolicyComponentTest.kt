package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.TransportSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `OutboundUrlPolicy.Trusted` through its factories and builder: the defaults; pinned hosts canonicalised
 * (lower-case, IPv6 without brackets, a mapped literal as dotted IPv4) and copied; every refused pin form throws the
 * constant message without echoing the entry; `settings` as a property and as a block (the order matters); `trusted(from)`
 * keeps what the block does not set; each property takes part in equality and `toString()`; `toString()` prints a count,
 * never a host; a `Trusted` never equals an `Untrusted`.
 */
class TrustedPolicyComponentTest {

    private val pinMessage = "pinnedHosts must hold only host names or IP literals, without a port, path or user-info."
    private val canary = "SECRET-CANARY-7f3a"

    @Test
    fun `the empty block and the parameterless call give the defaults`() {
        val policy = OutboundUrlPolicy.trusted {}
        assertEquals(OutboundUrlPolicy.trusted(), policy)
        assertEquals(emptySet(), policy.pinnedHosts)
        assertEquals(false, policy.allowCleartext)
        assertEquals(TransportSettings.TrustedDefaults, policy.settings)
    }

    @Test
    fun `pinned hosts are canonicalised and duplicates collapse`() {
        val policy = OutboundUrlPolicy.trusted { pinnedHosts = setOf("API.Example.com", "api.example.com") }
        assertEquals(setOf("api.example.com"), policy.pinnedHosts)
        val literals = OutboundUrlPolicy.trusted { pinnedHosts = setOf("[::1]", "::1", "[::ffff:10.0.0.5]") }
        assertEquals(setOf("::1", "10.0.0.5"), literals.pinnedHosts)
        val kept = OutboundUrlPolicy.trusted { pinnedHosts = setOf("api.example.com.", "10.0.0.5", "127.1") }
        assertEquals(setOf("api.example.com.", "10.0.0.5", "127.1"), kept.pinnedHosts)
    }

    @Test
    fun `the pinned set is copied at build and extended with plus-equals`() {
        val mutable = mutableSetOf("a.example")
        val policy = OutboundUrlPolicy.trusted { pinnedHosts = mutable }
        mutable += "b.example"
        assertEquals(setOf("a.example"), policy.pinnedHosts)
        val extended = OutboundUrlPolicy.trusted { pinnedHosts = setOf("a.example"); pinnedHosts += "b.example" }
        assertEquals(setOf("a.example", "b.example"), extended.pinnedHosts)
    }

    @Test
    fun `every refused pin form throws the constant message without echoing the entry`() {
        val refused = listOf(
            "", "a b", "api.example.com:443", "https://x", "a/b", "a@b", "müller.de", "xn--n3h.net", "[x]", "[::1", "::1]", "[]",
            "$canary:443", "[$canary",
        )
        refused.forEach { entry ->
            val failure = assertFailsWith<IllegalArgumentException>(entry) { OutboundUrlPolicy.trusted { pinnedHosts = setOf(entry) } }
            assertEquals(pinMessage, failure.message, entry)
            assertFalse(failure.message!!.contains(canary, ignoreCase = true), entry)
        }
    }

    @Test
    fun `settings as a block starts from the current value and keeps the other numbers`() {
        val policy = OutboundUrlPolicy.trusted { settings { callTimeout = 1.seconds } }
        assertEquals(TransportSettings(TransportSettings.TrustedDefaults) { callTimeout = 1.seconds }, policy.settings)
        assertEquals(10.seconds, policy.settings.connectTimeout)
        assertEquals(5, policy.settings.maxRedirects)
    }

    @Test
    fun `settings assigned then modified by a block modifies the assigned value`() {
        val x = TransportSettings(TransportSettings.UntrustedDefaults) { callTimeout = 2.seconds }
        val policy = OutboundUrlPolicy.trusted { settings = x; settings { maxRedirects = 0 } }
        assertEquals(2.seconds, policy.settings.callTimeout)
        assertEquals(0, policy.settings.maxRedirects)
        assertEquals(8, policy.settings.maxConcurrentRequests)
    }

    @Test
    fun `a bad settings block throws the settings message`() {
        val failure = assertFailsWith<IllegalArgumentException> { OutboundUrlPolicy.trusted { settings { maxRedirects = -1 } } }
        assertTrue(failure.message!!.contains("maxRedirects"), failure.message)
    }

    @Test
    fun `trusted from keeps what the block does not set and leaves from unchanged`() {
        val from = OutboundUrlPolicy.trusted { pinnedHosts = setOf("a.example"); allowCleartext = true; settings { maxRedirects = 1 } }
        val derived = OutboundUrlPolicy.trusted(from) { pinnedHosts += "b.example" }
        assertEquals(setOf("a.example", "b.example"), derived.pinnedHosts)
        assertEquals(true, derived.allowCleartext)
        assertEquals(from.settings, derived.settings)
        assertEquals(setOf("a.example"), from.pinnedHosts)
        assertEquals(from, OutboundUrlPolicy.trusted(from) {})
    }

    //region one test per property
    private fun onlyThisProperty(configure: OutboundUrlPolicy.Trusted.Builder.() -> Unit) {
        val base = OutboundUrlPolicy.trusted()
        val once = OutboundUrlPolicy.trusted(configure)
        val twice = OutboundUrlPolicy.trusted(configure)
        assertNotEquals(base, once)
        assertNotEquals(base.toString(), once.toString())
        assertEquals(once, twice)
        assertEquals(once.hashCode(), twice.hashCode())
    }

    @Test
    fun `pinnedHosts takes part in equality and toString`() = onlyThisProperty { pinnedHosts = setOf("a.example") }

    @Test
    fun `allowCleartext takes part in equality and toString`() = onlyThisProperty { allowCleartext = true }

    @Test
    fun `settings takes part in equality and toString`() = onlyThisProperty { settings { maxRedirects = 1 } }
    //endregion

    @Test
    fun `toString prints the count of pinned hosts and never a host`() {
        val policy = OutboundUrlPolicy.trusted { pinnedHosts = setOf("${canary.lowercase()}.example", "b.example"); allowCleartext = true }
        val text = policy.toString()
        assertTrue(text.startsWith("Trusted(pinnedHosts=2, allowCleartext=true, settings=TransportSettings("), text)
        assertFalse(text.contains(canary, ignoreCase = true), text)
    }

    @Test
    fun `a Trusted never equals an Untrusted with the same settings`() {
        val settings = TransportSettings(TransportSettings.TrustedDefaults) { maxRedirects = 2 }
        val trusted: OutboundUrlPolicy = OutboundUrlPolicy.trusted { this.settings = settings }
        val untrusted: OutboundUrlPolicy = OutboundUrlPolicy.untrusted { this.settings = settings }
        assertNotEquals(trusted, untrusted)
        assertEquals(trusted.settings, untrusted.settings)
    }
}
