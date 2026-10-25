package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.RedirectHop
import com.spartanlabs.webtools.http.Url
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Level 2 - `HttpResponse`: statuses 99 and 600 refused, 100 and 599 accepted; defaults; the body
 * copied when built and `body()` a fresh copy; `redirectChain` a copy; `bodySize`; identity equality;
 * `toString()` with status, redacted URL, header names, body size, redirect count and no value or body.
 */
class HttpResponseComponentTest {

    private val url = Url.parse("https://h/p?token=SECRET").getOrThrow()

    @Test
    fun `the status must be in 100 to 599`() {
        assertEquals("Status code must be in 100..599", assertFailsWith<IllegalArgumentException> { HttpResponse(99, url) }.message)
        assertFailsWith<IllegalArgumentException> { HttpResponse(600, url) }
        assertEquals(100, HttpResponse(100, url).status)
        assertEquals(599, HttpResponse(599, url).status)
    }

    @Test
    fun `the factory defaults to empty headers, no redirects and an empty body`() {
        val response = HttpResponse(204, url)
        assertTrue(response.headers.isEmpty())
        assertTrue(response.redirectChain.isEmpty())
        assertEquals(0, response.bodySize)
        assertEquals(0, response.body().size)
        assertEquals(url, response.url)
    }

    @Test
    fun `the body is copied when built and read back as a fresh copy`() {
        val source = byteArrayOf(1, 2, 3)
        val response = HttpResponse(200, url) { body(source) }
        source[0] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), response.body())
        response.body()[1] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), response.body())
        assertEquals(3, response.bodySize)
    }

    @Test
    fun `the redirect chain is a copy`() {
        val hops = mutableListOf(RedirectHop(Url.parse("http://a/").getOrThrow(), 301))
        val response = HttpResponse(200, url) { redirectChain = hops }
        hops.add(RedirectHop(Url.parse("http://b/").getOrThrow(), 302))
        assertEquals(1, response.redirectChain.size)
    }

    @Test
    fun `equality is identity`() {
        val a = HttpResponse(200, url)
        val b = HttpResponse(200, url)
        assertNotEquals(a, b)
        assertEquals(a, a)
    }

    @Test
    fun `toString shows the structure and never a value or the body`() {
        val response = HttpResponse(200, url) {
            headers = HttpHeaders { add("X-Trace", "SECRET-TRACE"); add("Set-Cookie", "SECRET-COOKIE") }
            redirectChain = listOf(RedirectHop(Url.parse("http://a/?SECRET-Q").getOrThrow(), 301))
            body("SECRET-BODY".encodeToByteArray())
        }
        assertEquals("HttpResponse(status=200, url=https://h/p?..., headers=HttpHeaders(X-Trace, Set-Cookie*), bodySize=11, redirects=1)", response.toString())
        assertFalse(response.toString().contains("SECRET"))
    }
}
