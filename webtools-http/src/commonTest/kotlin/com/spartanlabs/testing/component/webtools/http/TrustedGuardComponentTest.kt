package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.addr
import com.spartanlabs.testing.support.webtools.http.outcome
import com.spartanlabs.testing.support.webtools.http.trustedGuard
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.AnswerVerdict
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Level 2 - the `Trusted` rules of plan §6.5 through `UrlGuard`: the scheme rule and `allowCleartext`; no host, address or
 * port rule; pins by exact canonical equality (an IPv6 pin, a mapped literal matching its dotted pin); the downgrade rule
 * and `allowHttpsToHttp`; `checkRequest` never refuses a header; `checkAddress` is always `null`; `judgeAnswer` allows
 * every index.
 */
class TrustedGuardComponentTest {

    private val default = trustedGuard()

    @Test
    fun `https is allowed by default and http is Scheme`() {
        assertNull(default.checkHop(url("https://api.example.com/"), null))
        assertEquals("Scheme", default.checkHop(url("http://api.example.com/"), null).outcome())
    }

    @Test
    fun `allowCleartext allows http`() {
        assertNull(trustedGuard { allowCleartext = true }.checkHop(url("http://api.example.com/"), null))
    }

    @Test
    fun `there is no host address or port rule`() {
        listOf("https://10.0.0.5/", "https://localhost:6379/", "https://127.1/", "https://[::1]/", "https://metadata/").forEach {
            assertNull(default.checkHop(url(it), null), it)
        }
    }

    @Test
    fun `a pin matches by exact canonical equality`() {
        val guard = trustedGuard { pinnedHosts = setOf("api.example.com") }
        assertNull(guard.checkHop(url("https://api.example.com/"), null))
        assertNull(guard.checkHop(url("https://API.EXAMPLE.COM/"), null))
        listOf("https://api.example.com./", "https://evilapi.example.com/", "https://api.example.com.evil.test/", "https://other.test/").forEach {
            assertEquals("HostNotPinned", guard.checkHop(url(it), null).outcome(), it)
        }
    }

    @Test
    fun `an IPv6 pin and a dotted pin match their literal forms`() {
        assertNull(trustedGuard { pinnedHosts = setOf("[::1]") }.checkHop(url("https://[::1]/"), null))
        assertNull(trustedGuard { pinnedHosts = setOf("127.0.0.1") }.checkHop(url("https://[::ffff:127.0.0.1]/"), null))
    }

    @Test
    fun `the downgrade rule`() {
        val previous = url("https://api.example.com/")
        val hop = url("http://api.example.com/")
        assertEquals("InsecureRedirect", trustedGuard { allowCleartext = true }.checkHop(hop, previous).outcome())
        assertNull(trustedGuard { allowCleartext = true; settings { allowHttpsToHttp = true } }.checkHop(hop, previous))
        assertEquals("Scheme", default.checkHop(hop, previous).outcome())
    }

    @Test
    fun `checkRequest never refuses a header`() {
        val request = HttpRequest(HttpMethod.Get, url("https://api.example.com/")) {
            headers = HttpHeaders { add("Authorization", "Bearer x"); addSensitive("X-Api-Key", "k") }
        }
        assertNull(default.checkRequest(request))
    }

    @Test
    fun `checkAddress is null for every address`() {
        listOf("10.0.0.5", "127.0.0.1", "169.254.169.254", "::1", "fd00::1", "8.8.8.8").forEach { assertNull(default.checkAddress(addr(it)), it) }
        assertNull(default.checkAddress(ByteArray(5)))
    }

    @Test
    fun `judgeAnswer allows every index`() {
        val verdict = assertIs<AnswerVerdict.Allowed>(default.judgeAnswer(listOf(addr("10.0.0.1"), addr("127.0.0.1"))))
        assertEquals(listOf(0, 1), verdict.indices)
    }
}
