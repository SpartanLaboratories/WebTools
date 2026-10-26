package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.trustedGuard
import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.RefusalReason
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Level 4a - the reason that wins when two rules apply (the order `UrlGuard.checkHop`'s KDoc lists: scheme, user-info, host
 * form, IP literal, reserved name, port, pin, downgrade; the URL before the headers). User-info has no row: a `Url` never
 * carries any.
 */
class GuardReasonOrderDeterministicTest {

    private val untrusted = untrustedGuard()

    @Test
    fun `Untrusted - a denied literal wins over the port`() {
        assertEquals(RefusalReason.DeniedAddress, untrusted.checkHop(url("http://127.0.0.1:6379/"), null)?.reason)
    }

    @Test
    fun `Untrusted - a reserved name wins over the port`() {
        assertEquals(RefusalReason.ReservedName, untrusted.checkHop(url("http://localhost:6379/"), null)?.reason)
    }

    @Test
    fun `Untrusted - the host form wins over the port`() {
        assertEquals(RefusalReason.HostForm, untrusted.checkHop(url("http://127.1:6379/"), null)?.reason)
    }

    @Test
    fun `Untrusted - a name with a bad port is Port`() {
        assertEquals(RefusalReason.Port, untrusted.checkHop(url("http://example.com:6379/"), null)?.reason)
    }

    @Test
    fun `Untrusted - the port wins over the downgrade`() {
        assertEquals(RefusalReason.Port, untrusted.checkHop(url("http://example.com:6379/"), url("https://example.com/"))?.reason)
    }

    @Test
    fun `Untrusted - a denied literal wins over the downgrade`() {
        assertEquals(RefusalReason.DeniedAddress, untrusted.checkHop(url("http://127.0.0.1/"), url("https://example.com/"))?.reason)
    }

    @Test
    fun `Untrusted - a clean downgrade is InsecureRedirect`() {
        assertEquals(RefusalReason.InsecureRedirect, untrusted.checkHop(url("http://example.com/"), url("https://example.com/"))?.reason)
    }

    @Test
    fun `Untrusted - the URL is judged before the headers`() {
        val headers = HttpHeaders { add("Authorization", "Bearer x") }
        val denied = HttpRequest(HttpMethod.Get, url("http://127.0.0.1:6379/")) { this.headers = headers }
        assertEquals(RefusalReason.DeniedAddress, untrusted.checkRequest(denied)?.reason)
        val sensitive = HttpRequest(HttpMethod.Get, url("http://example.com/")) { this.headers = headers }
        assertEquals(RefusalReason.SensitiveHeader, untrusted.checkRequest(sensitive)?.reason)
    }

    @Test
    fun `Trusted - the scheme wins over the downgrade`() {
        assertEquals(RefusalReason.Scheme, trustedGuard().checkHop(url("http://x.test/"), url("https://x.test/"))?.reason)
    }

    @Test
    fun `Trusted - the pin wins over the downgrade`() {
        val guard = trustedGuard { pinnedHosts = setOf("api.example.com"); allowCleartext = true }
        assertEquals(RefusalReason.HostNotPinned, guard.checkHop(url("http://other.test/"), url("https://api.example.com/"))?.reason)
    }

    @Test
    fun `Trusted - a pinned cleartext downgrade is InsecureRedirect`() {
        val guard = trustedGuard { pinnedHosts = setOf("api.example.com"); allowCleartext = true }
        assertEquals(RefusalReason.InsecureRedirect, guard.checkHop(url("http://api.example.com/"), url("https://api.example.com/"))?.reason)
    }
}
