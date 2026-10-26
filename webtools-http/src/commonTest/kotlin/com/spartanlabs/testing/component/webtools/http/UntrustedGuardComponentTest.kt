package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.addr
import com.spartanlabs.testing.support.webtools.http.cidr
import com.spartanlabs.testing.support.webtools.http.outcome
import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.GuardRelaxation
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.UrlGuard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Level 2 - the `Untrusted` rules of plan §6.5 through `UrlGuard`, each as a test: the port rule and its two settings;
 * denied and allowed literals with their classes; lenient host forms; reserved names at a label boundary; the policy
 * suffixes; `extraDeniedRanges` on a literal and never on a name; the downgrade rule; `checkRequest`'s sensitive-header
 * rule after the URL; `checkAddress` on the table, an extra block and unusual lengths; the default relaxation.
 */
class UntrustedGuardComponentTest {

    private val default = untrustedGuard()

    private fun hop(guard: UrlGuard, text: String): String = guard.checkHop(url(text), null).outcome()

    private fun denied(guard: UrlGuard, text: String): String? {
        val refusal = assertNotNull(guard.checkHop(url(text), null), "$text allowed")
        assertEquals(RefusalReason.DeniedAddress, refusal.reason, text)
        return refusal.rangeClass
    }

    @Test
    fun `the default ports are allowed and others are Port`() {
        listOf(80, 443, 8080, 8443).forEach { assertEquals("allowed", hop(default, "http://example.com:$it/"), "$it") }
        listOf(6379, 22).forEach { assertEquals("Port", hop(default, "http://example.com:$it/"), "$it") }
    }

    @Test
    fun `allowedPorts null allows any port and a custom set replaces the default`() {
        assertEquals("allowed", hop(untrustedGuard { allowedPorts = null }, "http://example.com:6379/"))
        val nine = untrustedGuard { allowedPorts = setOf(9000) }
        assertEquals("Port", hop(nine, "https://example.com/"))
        assertEquals("allowed", hop(nine, "http://example.com:9000/"))
    }

    @Test
    fun `a denied literal is DeniedAddress with its class and a public literal is allowed`() {
        assertEquals("private-use", denied(default, "http://10.0.0.5/"))
        assertEquals("link-local", denied(default, "http://169.254.169.254/"))
        assertEquals("not-global-unicast", denied(default, "http://[::1]/"))
        assertEquals("not-global-unicast", denied(default, "http://[fd00::1]/"))
        assertEquals("loopback", denied(default, "http://[64:ff9b::7f00:1]/"))
        assertEquals("6to4", denied(default, "http://[2002:7f00:1::]/"))
        assertEquals("allowed", hop(default, "http://8.8.8.8/"))
        assertEquals("allowed", hop(default, "http://[2606:4700:4700::1111]/"))
    }

    @Test
    fun `a lenient host form is HostForm`() {
        listOf("http://127.1/", "http://2130706433/", "http://0x7f.1/", "http://127.0.0.1./").forEach { assertEquals("HostForm", hop(default, it), it) }
    }

    @Test
    fun `a reserved name is ReservedName and the boundary is a label`() {
        listOf("localhost", "foo.localhost", "printer.local", "intranet", "x.internal", "router.home.arpa", "host.localdomain").forEach {
            assertEquals("ReservedName", hop(default, "http://$it/"), it)
        }
        listOf("example.com", "localhost.example.com", "notlocal.example").forEach { assertEquals("allowed", hop(default, "http://$it/"), it) }
    }

    @Test
    fun `a policy suffix refuses the name and its subdomains only`() {
        val guard = untrustedGuard { extraDeniedHostSuffixes = setOf("corp.example") }
        listOf("corp.example", "a.corp.example", "a.corp.example.").forEach { assertEquals("ReservedName", hop(guard, "http://$it/"), it) }
        assertEquals("allowed", hop(guard, "http://mycorp.example/"))
    }

    @Test
    fun `an extra range denies a literal with its own class and never a name`() {
        val guard = untrustedGuard { extraDeniedRanges = setOf(cidr("203.0.114.0/24")) }
        assertEquals("extra-denied-range", denied(guard, "http://203.0.114.7/"))
        val everything = untrustedGuard { extraDeniedRanges = setOf(cidr("0.0.0.0/0"), cidr("::/0")) }
        assertEquals("allowed", hop(everything, "http://example.com/"))
    }

    @Test
    fun `the downgrade rule`() {
        val previous = url("https://example.com/a")
        assertEquals("InsecureRedirect", default.checkHop(url("http://example.com/b"), previous).outcome())
        assertNull(untrustedGuard { settings { allowHttpsToHttp = true } }.checkHop(url("http://example.com/b"), previous))
        assertNull(default.checkHop(url("http://example.com/b"), url("http://example.com/a")))
        assertNull(default.checkHop(url("https://example.com/b"), previous))
        assertNull(default.checkHop(url("http://example.com/b"), null))
    }

    @Test
    fun `checkRequest refuses a sensitive header after the URL`() {
        fun request(text: String, configure: HttpHeaders.Builder.() -> Unit) =
            HttpRequest(HttpMethod.Get, url(text)) { headers = HttpHeaders(configure) }
        listOf("Authorization", "authorization", "Proxy-Authorization", "Cookie", "Set-Cookie").forEach { name ->
            assertEquals("SensitiveHeader", default.checkRequest(request("http://example.com/") { add(name, "v") }).outcome(), name)
        }
        assertEquals("SensitiveHeader", default.checkRequest(request("http://example.com/") { addSensitive("X-Api-Key", "k") }).outcome())
        assertNull(default.checkRequest(request("http://example.com/") { add("X-Trace", "t") }))
        val refusal = default.checkRequest(request("http://10.0.0.5/") { add("Authorization", "Bearer SECRET-CANARY-7f3a") })
        assertEquals(RefusalReason.DeniedAddress, refusal?.reason)
        assertFalse(refusal.toString().contains("SECRET-CANARY-7f3a"))
        assertFalse(default.checkRequest(request("http://example.com/") { add("Cookie", "SECRET-CANARY-7f3a") }).toString().contains("SECRET-CANARY-7f3a"))
    }

    @Test
    fun `checkAddress judges the table an extra block and unusual lengths`() {
        assertEquals("private-use", default.checkAddress(addr("10.0.0.1"))?.rangeClass)
        assertEquals("loopback", default.checkAddress(addr("::ffff:127.0.0.1"))?.rangeClass)
        assertEquals("not-global-unicast", default.checkAddress(addr("fd00::1"))?.rangeClass)
        assertNull(default.checkAddress(addr("8.8.8.8")))
        val guard = untrustedGuard { extraDeniedRanges = setOf(cidr("203.0.114.0/24")) }
        assertEquals("extra-denied-range", guard.checkAddress(addr("203.0.114.7"))?.rangeClass)
        assertEquals("extra-denied-range", guard.checkAddress(addr("::ffff:203.0.114.7"))?.rangeClass)
        assertEquals("extra-denied-range", guard.checkAddress(addr("64:ff9b::cb00:7207"))?.rangeClass)
        listOf(ByteArray(0), ByteArray(5)).forEach { bytes ->
            val refusal = assertNotNull(default.checkAddress(bytes))
            assertEquals(RefusalReason.DeniedAddress, refusal.reason)
            assertEquals("unusual-length", refusal.rangeClass)
        }
    }

    @Test
    fun `a guard built without a relaxation has None`() {
        assertSame(GuardRelaxation.None, UrlGuard(OutboundUrlPolicy.untrusted()).relaxation)
    }
}
