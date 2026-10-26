package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.trustedGuard
import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.RedirectAdmission
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlGuard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * Level 2 - `UrlGuard.admitRedirect`: an admitted target carries the parsed `Url`; a downgrade is refused; a parse failure
 * maps `UserInfo`, `Scheme` and `Port` to their reasons and every other problem to `HostForm`; a parseable target is then
 * judged by `checkHop`; a `Trusted` guard applies its own rules; no refusal's `toString()` holds the target text.
 */
class RedirectAdmissionComponentTest {

    private val untrusted = untrustedGuard()
    private val previous = url("https://example.com/a")
    private val canary = "secret-canary-7f3a"

    private fun refused(guard: UrlGuard, target: String): RefusalReason =
        assertIs<RedirectAdmission.Refused>(guard.admitRedirect(target, previous), target).refusal.reason

    @Test
    fun `an admitted target carries the parsed URL`() {
        val admitted = assertIs<RedirectAdmission.Admitted>(untrusted.admitRedirect("https://example.com/b", previous))
        assertEquals(Url.parse("https://example.com/b").getOrThrow(), admitted.url)
    }

    @Test
    fun `a downgrade is refused`() {
        assertEquals(RefusalReason.InsecureRedirect, refused(untrusted, "http://example.com/b"))
    }

    @Test
    fun `a parse failure maps by problem`() {
        assertEquals(RefusalReason.UserInfo, refused(untrusted, "http://u@example.com/"))
        assertEquals(RefusalReason.Scheme, refused(untrusted, "ftp://x/"))
        assertEquals(RefusalReason.Scheme, refused(untrusted, "//x"))
        assertEquals(RefusalReason.Port, refused(untrusted, "http://x:99999/"))
        assertEquals(RefusalReason.Port, refused(untrusted, "http://x:0/"))
        listOf("", "http://a\\b/", "http://a b/", "http://a..b/", "http://xn--n3h.net/", "http://münchen.de/", "http://[1:2:3:4:5:6:7::8]/").forEach {
            assertEquals(RefusalReason.HostForm, refused(untrusted, it), it)
        }
    }

    @Test
    fun `a parseable but denied target is judged by the hop rules`() {
        assertEquals(RefusalReason.HostForm, refused(untrusted, "http://127.1/"))
        val denied = assertIs<RedirectAdmission.Refused>(untrusted.admitRedirect("http://169.254.169.254/", previous))
        assertEquals(RefusalReason.DeniedAddress, denied.refusal.reason)
        assertEquals("link-local", denied.refusal.rangeClass)
        assertEquals(RefusalReason.ReservedName, refused(untrusted, "http://localhost/"))
        assertEquals(RefusalReason.Port, refused(untrusted, "http://example.com:6379/"))
    }

    @Test
    fun `a Trusted guard applies its own rules`() {
        assertEquals(RefusalReason.Scheme, refused(trustedGuard(), "http://x/"))
        assertIs<RedirectAdmission.Admitted>(trustedGuard().admitRedirect("https://x/", previous))
        assertEquals(RefusalReason.HostNotPinned, refused(trustedGuard { pinnedHosts = setOf("example.com") }, "https://other.test/"))
    }

    @Test
    fun `no refusal prints the target text`() {
        listOf("http://$canary:6379/", "http://u@$canary/", "ftp://$canary/", "http://$canary:0/", "http://[$canary]/").forEach { target ->
            val refusal = assertIs<RedirectAdmission.Refused>(untrusted.admitRedirect(target, previous), target)
            assertFalse(refusal.refusal.toString().contains(canary, ignoreCase = true), target)
            assertFalse(refusal.toString().contains(canary, ignoreCase = true), target)
        }
    }
}
