package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.testing.support.webtools.http.UrlCorpus
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.RequestBody
import com.spartanlabs.webtools.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Level 4c - a canary in a header value, in a name position (a swapped argument), in a content type, in
 * a method string and in a URL query: no `IllegalArgumentException` message from `HttpHeaders.Builder`,
 * `HttpRequest`, `RequestBody`, `HttpMethod.of` or `HttpResponse` contains it; a name is echoed only
 * after it passed the token rule.
 */
class HeaderMessageSecrecyNonFunctionalTest {

    private val canary = UrlCorpus.CANARY
    private val url = Url.parse("https://h/p").getOrThrow()

    private fun messageOf(block: () -> Unit): String = assertFailsWith<IllegalArgumentException>(block = block).message!!

    @Test
    fun `a canary in a header value is never echoed`() {
        val container = messageOf { HttpHeaders { add("X-Note", "$canary\n") } }
        assertEquals("Value of header 'X-Note' must not contain CR, LF or NUL", container)
        val sendable = messageOf { HttpRequest(HttpMethod.Get, url) { headers = HttpHeaders { add("X-Note", "${canary}é") } } }
        assertEquals("Value of header 'X-Note' must be visible ASCII, space or tab to be sent", sendable)
        assertFalse(container.contains(canary) || sendable.contains(canary))
    }

    @Test
    fun `a canary in a name position is echoed only after it passed the token rule`() {
        val swapped = messageOf { HttpHeaders { add("$canary x", "X-Note") } }
        assertEquals("Header name is not an RFC 9110 token (first offending character at index ${canary.length})", swapped)
        assertFalse(swapped.contains(canary))
        // The canary is itself a token: once it passes the name rule it may be echoed, as the plan allows.
        val tokenName = messageOf { HttpHeaders { add(canary, "bad\n") } }
        assertEquals("Value of header '$canary' must not contain CR, LF or NUL", tokenName)
    }

    @Test
    fun `a canary in a content type, a method or a URL query is never echoed`() {
        val contentType = messageOf { RequestBody(ByteArray(0), "text/plain $canary") }
        assertEquals("Content type is not an RFC 9110 media type (first offending character at index 11)", contentType)
        val method = HttpMethod.of("$canary x").exceptionOrNull()
        assertIs<IllegalArgumentException>(method)
        assertEquals("Method is not an RFC 9110 token", method.message)
        val query = Url.parse("https://h/?$canary\\").exceptionOrNull()
        assertIs<IllegalArgumentException>(query)
        assertFalse(query.message!!.contains(canary))
        val status = messageOf { HttpResponse(600, Url.parse("https://h/?$canary").getOrThrow()) }
        assertEquals("Status code must be in 100..599", status)
        assertTrue(listOf(contentType, method.message!!, query.message!!, status).none { it.contains(canary) })
    }
}
