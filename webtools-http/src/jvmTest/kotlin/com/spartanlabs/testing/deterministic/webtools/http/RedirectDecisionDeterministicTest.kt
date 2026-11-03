package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.trustedGuard
import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.DeliverReason
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.RedirectOutcome
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlGuard
import com.spartanlabs.webtools.http.decideRedirect
import com.spartanlabs.webtools.http.foreignScheme
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Level 4a - the redirect decision table against the real `HttpUrl.resolve` on `https://example.com/dir/page?q=1`: the ten follow cases,
 * the method and status deliveries, the `Location` value rules, the resolution shapes (under a `Trusted` guard with cleartext allowed, so
 * only resolution is judged), the foreign schemes, the unresolvable and throwing targets, the `Url.parse` refusals, the policy refusals under
 * `Untrusted` and the pin and downgrade rules under `Trusted`, the cap before admission, and the `foreignScheme` grammar.
 */
class RedirectDecisionDeterministicTest {

    private val current = url("https://example.com/dir/page?q=1")
    private val untrusted = untrustedGuard()
    private val trustedCleartext = trustedGuard { allowCleartext = true }
    private val realResolve: (String) -> String? = { current.toString().toHttpUrl().resolve(it)?.toString() }
    private val followed = listOf(301, 302, 303, 307, 308)

    private fun decide(
        location: String?, method: HttpMethod = HttpMethod.Get, status: Int = 302, followedSoFar: Int = 0, cap: Int = 3,
        guard: UrlGuard = untrusted, from: Url = current, resolve: (String) -> String? = realResolve,
    ): RedirectOutcome = decideRedirect(method, status, listOfNotNull(location), followedSoFar, from, cap, guard, resolve)

    private fun decideAll(locations: List<String>, method: HttpMethod = HttpMethod.Get, status: Int = 302): RedirectOutcome =
        decideRedirect(method, status, locations, 0, current, 3, untrusted, realResolve)

    private fun RedirectOutcome.followedTo(): String = assertIs<RedirectOutcome.Follow>(this).next.toString()
    private fun RedirectOutcome.refusedFor(): RefusalReason = assertIs<RedirectOutcome.Refused>(this).refusal.reason
    private fun RedirectOutcome.deliveredFor(): DeliverReason = assertIs<RedirectOutcome.Deliver>(this).why

    @Test
    fun `group 1 - GET and HEAD follow the five statuses`() {
        listOf(HttpMethod.Get, HttpMethod.Head).forEach { method ->
            followed.forEach { status -> assertEquals("https://example.com/x", decide("/x", method, status).followedTo(), "$method $status") }
        }
    }

    @Test
    fun `group 2 - any other method delivers the 3xx`() {
        val methods = listOf("POST", "PUT", "PATCH", "DELETE", "OPTIONS", "PURGE").map { HttpMethod.of(it).getOrThrow() }
        methods.forEach { method ->
            followed.forEach { status -> assertEquals(DeliverReason.Method, decide("/x", method, status).deliveredFor(), "$method $status") }
        }
    }

    @Test
    fun `group 3 - a status outside the five delivers`() {
        listOf(300, 304, 305, 306, 309, 399, 200, 404).forEach { status ->
            assertEquals(DeliverReason.Status, decide("/x", status = status).deliveredFor(), "$status")
        }
    }

    @Test
    fun `group 4 - the Location values`() {
        assertEquals(DeliverReason.NoTarget, decide(null).deliveredFor())
        assertEquals(DeliverReason.NoTarget, decide("").deliveredFor())
        assertEquals(DeliverReason.NoTarget, decide("   ").deliveredFor())
        assertEquals(DeliverReason.NoTarget, decide("\t").deliveredFor())
        assertEquals(DeliverReason.NoTarget, decideAll(listOf("", " ")).deliveredFor())
        assertEquals(DeliverReason.AmbiguousTarget, decideAll(listOf("/a", "/b")).deliveredFor())
        assertEquals(DeliverReason.AmbiguousTarget, decideAll(listOf("", "/x")).deliveredFor())
        assertEquals("https://example.com/x", decideAll(listOf("/x", "/x")).followedTo())
        assertEquals("https://example.com/x", decideAll(listOf(" /x ", "/x\r\n")).followedTo())
    }

    @Test
    fun `group 5 - resolution shapes print as the table says`() {
        val expected = mapOf(
            "x" to "https://example.com/dir/x",
            "../x" to "https://example.com/x",
            "?y=2" to "https://example.com/dir/page?y=2",
            "?" to "https://example.com/dir/page?",
            "//other.example/x" to "https://other.example/x",
            "///x" to "https://x/",
            "\\\\host\\x" to "https://host/x",
            "/\\host" to "https://host/",
            "HTTP://H.EXAMPLE/X" to "http://h.example/X",
            "%2e%2e/x" to "https://example.com/x",
            "https:x" to "https://example.com/dir/x",
            "#frag" to current.toString(),
            "https://example.com:443/dir/page?q=1" to current.toString(),
        )
        // Cleartext and the downgrade allowed, so only resolution is judged (HTTP://H.EXAMPLE/X is an https-to-http hop).
        val resolutionOnly = trustedGuard { allowCleartext = true; settings { allowHttpsToHttp = true } }
        expected.forEach { (location, printed) -> assertEquals(printed, decide(location, guard = resolutionOnly).followedTo(), location) }
    }

    @Test
    fun `group 6 - a foreign scheme is Refused Scheme`() {
        listOf("ftp://h/x", "gopher://h/", "file:///etc/passwd", "javascript:alert(1)", "data:text/plain,hi", "mailto:a@b", "ws://h/x", "wss://h/x", "localhost:8080/x")
            .forEach { location -> assertEquals(RefusalReason.Scheme, decide(location).refusedFor(), location) }
    }

    @Test
    fun `group 7 - an unresolvable or throwing target is Refused HostForm`() {
        listOf("http://h:0/", "http://h:99999/", "http://[::1", "http://a b/", "////", "//[fe80::1%25eth0]/", "http://" + "a".repeat(300) + "/", "http:", "http://")
            .forEach { location -> assertEquals(RefusalReason.HostForm, decide(location).refusedFor(), location) }
        assertEquals(RefusalReason.HostForm, decide("//[1:2:3:4:5:6:7::8]/").refusedFor())
        listOf<(String) -> String?>(
            { throw ArrayIndexOutOfBoundsException("3") },
            { throw IllegalArgumentException("bad") },
            { throw IllegalStateException("odd") },
        ).forEach { throwing -> assertEquals(RefusalReason.HostForm, decide("/x", resolve = throwing).refusedFor()) }
        assertEquals(RefusalReason.Scheme, decide("ftp://h/x", resolve = { throw IllegalStateException("odd") }).refusedFor())
    }

    @Test
    fun `group 8 - a target Url parse refuses`() {
        listOf("http://user:pw@h/", "//u:p@h/", "http://example.com／@127.0.0.1/")
            .forEach { location -> assertEquals(RefusalReason.UserInfo, decide(location).refusedFor(), location) }
        listOf("http://xn--bcher-kva.example/", "http://bücher.example/")
            .forEach { location -> assertEquals(RefusalReason.HostForm, decide(location).refusedFor(), location) }
    }

    @Test
    fun `group 9 - the policy refuses under Untrusted and pins and downgrades under Trusted`() {
        assertEquals(RefusalReason.DeniedAddress, decide("http://127.0.0.1/").refusedFor())
        assertEquals(RefusalReason.HostForm, decide("//2130706433/").refusedFor())
        assertEquals(RefusalReason.HostForm, decide("//0x7f.1/").refusedFor())
        assertEquals(RefusalReason.DeniedAddress, decide("http://169.254.169.254/latest/").refusedFor())   // link-local; the cloud metadata address
        assertEquals(RefusalReason.DeniedAddress, decide("//[::ffff:127.0.0.1]/").refusedFor())
        assertEquals(RefusalReason.ReservedName, decide("http://localhost/").refusedFor())
        val pinned = trustedGuard { pinnedHosts = setOf("example.com") }
        assertEquals(RefusalReason.HostNotPinned, decide("https://other.example/x", guard = pinned).refusedFor())
        assertEquals("https://example.com/x", decide("/x", guard = pinned).followedTo())
        assertEquals(RefusalReason.InsecureRedirect, decide("http://example.com/x", guard = trustedCleartext).refusedFor())
        assertEquals(RefusalReason.InsecureRedirect, decide("http://example.com/x").refusedFor())
        val downgradeAllowed = trustedGuard { allowCleartext = true; settings { allowHttpsToHttp = true } }
        assertEquals("http://example.com/x", decide("http://example.com/x", guard = downgradeAllowed).followedTo())
        val plain = url("http://example.com/dir/page")
        assertEquals("https://example.com/x", decide("https://example.com/x", from = plain, guard = trustedCleartext).followedTo())
        assertEquals("https://example.com/x", decide("https://example.com/x", from = plain).followedTo())
    }

    @Test
    fun `group 10 - the cap comes before admission`() {
        assertIs<RedirectOutcome.TooMany>(decide("/x", followedSoFar = 3, cap = 3))
        assertEquals("https://example.com/x", decide("/x", followedSoFar = 2, cap = 3).followedTo())
        assertIs<RedirectOutcome.TooMany>(decide("/x", cap = 0))
        assertIs<RedirectOutcome.TooMany>(decide("ftp://h/", cap = 0))
        assertIs<RedirectOutcome.TooMany>(decide("http://127.0.0.1/", cap = 0))
        assertEquals(DeliverReason.NoTarget, decide(null, followedSoFar = 5, cap = 3).deliveredFor())
        assertEquals(DeliverReason.Status, decide("/x", status = 300, followedSoFar = 5, cap = 3).deliveredFor())
        assertEquals(DeliverReason.Method, decide("/x", method = HttpMethod.Post, followedSoFar = 5, cap = 3).deliveredFor())
    }

    @Test
    fun `foreignScheme follows the scheme grammar`() {
        listOf("a:", "a+b-c.d:", "ftp://h/x", "javascript:alert(1)", "localhost:8080/x").forEach { assertTrue(foreignScheme(it), it) }
        listOf("1a:", ":x", "a b:", "HTTP:", "Https:", "http://h/", "x", "/x", "//h/x", "").forEach { assertFalse(foreignScheme(it), it) }
    }
}
