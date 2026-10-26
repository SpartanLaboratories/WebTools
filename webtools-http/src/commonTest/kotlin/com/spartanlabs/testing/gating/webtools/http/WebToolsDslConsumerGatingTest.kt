package com.spartanlabs.testing.gating.webtools.http

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RequestBody
import com.spartanlabs.webtools.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Level 1 - the permanent consumer of the builder DSL, compiled in `commonTest` **without any opt-in** and using only public
 * API. Each nested block sets its own receiver; an outer builder is reached through its label (`this@untrusted`,
 * `this@HttpRequest`, ...). What the `WebToolsDsl` marker forbids, an implicit reach from a nested block into an outer
 * builder, is a compile error, which a passing test cannot show; a throwaway canary file showed it once when the marker
 * was added (plan.md §9.2, P12).
 */
class WebToolsDslConsumerGatingTest {

    private val url: Url = Url.parse("https://api.example.com/").getOrThrow()

    @Test
    fun `each block sets its own receiver`() {
        val policy = OutboundUrlPolicy.untrusted { allowedPorts = setOf(443); settings { maxRedirects = 1 } }
        assertEquals(setOf(443), policy.allowedPorts)
        assertEquals(1, policy.settings.maxRedirects)
        val request = HttpRequest(HttpMethod.Post, url) {
            headers = HttpHeaders { add("Accept", "text/plain") }
            body = RequestBody(byteArrayOf(1, 2), null)
        }
        assertEquals(listOf("text/plain"), request.headers.getAll("Accept"))
        assertEquals(2, request.body?.size)
    }

    @Test
    fun `an outer policy builder is reached through its label`() {
        val untrusted = OutboundUrlPolicy.untrusted { settings { maxRedirects = 1; this@untrusted.allowedPorts = null } }
        assertNull(untrusted.allowedPorts)
        assertEquals(1, untrusted.settings.maxRedirects)
        val trusted = OutboundUrlPolicy.trusted { settings { maxRedirects = 2; this@trusted.allowCleartext = true } }
        assertEquals(true, trusted.allowCleartext)
        assertEquals(2, trusted.settings.maxRedirects)
    }

    @Test
    fun `an outer request or response builder is reached through its label`() {
        val request = HttpRequest(HttpMethod.Post, url) {
            headers = HttpHeaders { add("Accept", "text/plain"); this@HttpRequest.body = RequestBody(byteArrayOf(1, 2), null) }
        }
        assertEquals(2, request.body?.size)
        assertEquals(listOf("text/plain"), request.headers.getAll("Accept"))
        val response = HttpResponse(200, url) {
            headers = HttpHeaders { add("X-Trace", "t"); this@HttpResponse.body(byteArrayOf(3)) }
        }
        assertEquals(1, response.bodySize)
        assertEquals(listOf("t"), response.headers.getAll("X-Trace"))
    }
}
