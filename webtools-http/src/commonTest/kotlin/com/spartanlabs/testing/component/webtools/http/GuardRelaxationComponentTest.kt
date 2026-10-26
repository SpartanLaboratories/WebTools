package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.addr
import com.spartanlabs.testing.support.webtools.http.cidr
import com.spartanlabs.testing.support.webtools.http.outcome
import com.spartanlabs.testing.support.webtools.http.trustedGuard
import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.GuardRelaxation
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.UrlGuard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull

/**
 * Level 2 - `GuardRelaxation.LoopbackForTests` exempts exactly plan §6.6's table: the host `localhost`, the loopback
 * addresses (a mapped `127.0.0.0/8` included, NAT64 excluded) and the port rule for `localhost`, `127.0.0.1` and `::1`;
 * every other rule stays live, `extraDeniedRanges` and the policy suffixes included. `None` refuses all of the exempt
 * cases; `Trusted` is the same with and without it.
 */
class GuardRelaxationComponentTest {

    private val relaxed = untrustedGuard(GuardRelaxation.LoopbackForTests)
    private val strict = untrustedGuard()

    private fun hop(guard: UrlGuard, text: String): String = guard.checkHop(url(text), null).outcome()

    @Test
    fun `localhost and the loopback literals are allowed on any port`() {
        listOf("http://localhost:9999/", "http://127.0.0.1:9999/", "http://[::1]:9999/").forEach { assertEquals("allowed", hop(relaxed, it), it) }
    }

    @Test
    fun `another loopback address is allowed on a default port only`() {
        assertEquals("allowed", hop(relaxed, "http://127.0.0.2/"))
        assertEquals("Port", hop(relaxed, "http://127.0.0.2:9999/"))
    }

    @Test
    fun `a mapped loopback literal is allowed`() {
        assertEquals("allowed", hop(relaxed, "http://[::ffff:127.0.0.1]/"))
    }

    @Test
    fun `the other localhost names stay reserved`() {
        assertEquals("ReservedName", hop(relaxed, "http://foo.localhost/"))
        assertEquals("ReservedName", hop(relaxed, "http://localhost./"))
    }

    @Test
    fun `NAT64 loopback and the private rows stay denied`() {
        listOf("http://[64:ff9b::7f00:1]/", "http://10.0.0.5/", "http://169.254.169.254/").forEach { assertEquals("DeniedAddress", hop(relaxed, it), it) }
    }

    @Test
    fun `the host form rule stays live`() {
        assertEquals("HostForm", hop(relaxed, "http://0x7f.1/"))
    }

    @Test
    fun `the other reserved names stay live`() {
        assertEquals("ReservedName", hop(relaxed, "http://printer.local/"))
        assertEquals("ReservedName", hop(relaxed, "http://intranet/"))
    }

    @Test
    fun `the downgrade rule stays live`() {
        assertEquals("InsecureRedirect", relaxed.checkHop(url("http://localhost/"), url("https://localhost/")).outcome())
    }

    @Test
    fun `an extra range still refuses loopback with its own class`() {
        val guard = untrustedGuard(GuardRelaxation.LoopbackForTests) { extraDeniedRanges = setOf(cidr("127.0.0.0/8")) }
        val refusal = assertNotNull(guard.checkHop(url("http://127.0.0.1/"), null))
        assertEquals("DeniedAddress", refusal.outcome())
        assertEquals("extra-denied-range", refusal.rangeClass)
    }

    @Test
    fun `an extra suffix localhost still refuses localhost`() {
        val guard = untrustedGuard(GuardRelaxation.LoopbackForTests) { extraDeniedHostSuffixes = setOf("localhost") }
        assertEquals("ReservedName", hop(guard, "http://localhost/"))
    }

    @Test
    fun `checkAddress exempts loopback only`() {
        listOf("127.0.0.1", "::1", "::ffff:127.0.0.1").forEach { assertNull(relaxed.checkAddress(addr(it)), it) }
        listOf("64:ff9b::7f00:1", "10.0.0.1", "::2").forEach { assertNotNull(relaxed.checkAddress(addr(it)), it) }
    }

    @Test
    fun `None refuses every exempt case`() {
        listOf("http://localhost:9999/", "http://127.0.0.1:9999/", "http://[::1]:9999/", "http://127.0.0.2/", "http://[::ffff:127.0.0.1]/").forEach {
            assertNotEquals("allowed", hop(strict, it), it)
        }
        listOf("127.0.0.1", "::1", "::ffff:127.0.0.1").forEach { assertNotNull(strict.checkAddress(addr(it)), it) }
    }

    @Test
    fun `Trusted is the same with and without the relaxation`() {
        val policy = OutboundUrlPolicy.trusted { allowCleartext = true }
        val with = UrlGuard(policy, GuardRelaxation.LoopbackForTests)
        val without = UrlGuard(policy)
        listOf("http://localhost:9999/", "http://10.0.0.5/", "http://0x7f.1/", "http://printer.local/").forEach {
            assertEquals(without.checkHop(url(it), null), with.checkHop(url(it), null), it)
        }
        assertEquals(trustedGuard().checkAddress(addr("10.0.0.5")), with.checkAddress(addr("10.0.0.5")))
    }

    @Test
    fun `the two values are distinct`() {
        assertNotSame(GuardRelaxation.None, GuardRelaxation.LoopbackForTests)
        assertNotEquals(GuardRelaxation.None.allowLoopback, GuardRelaxation.LoopbackForTests.allowLoopback)
    }
}
