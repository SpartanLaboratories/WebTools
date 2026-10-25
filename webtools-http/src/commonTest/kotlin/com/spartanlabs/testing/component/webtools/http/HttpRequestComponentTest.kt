package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.RequestBody
import com.spartanlabs.webtools.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 2 - `HttpRequest`: factory defaults; bodies on the methods that permit one; `GET`, `HEAD`,
 * `TRACE` with a body, `CONNECT`, a non-sendable value and a `Content-Type` beside a body content type
 * refused with their messages; the first broken rule reported; identity equality; `toString()`.
 */
class HttpRequestComponentTest {

    private val url = Url.parse("https://h/p?api_key=SECRET").getOrThrow()
    private val body = RequestBody(byteArrayOf(1, 2), "text/plain")

    @Test
    fun `the factory defaults to empty headers and no body`() {
        val request = HttpRequest(HttpMethod.Get, url)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals(url, request.url)
        assertTrue(request.headers.isEmpty())
        assertNull(request.body)
    }

    @Test
    fun `POST PUT PATCH DELETE and OPTIONS accept a body and may omit it`() {
        listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete, HttpMethod.Options).forEach { method ->
            assertEquals(body, HttpRequest(method, url) { this.body = this@HttpRequestComponentTest.body }.body, method.name)
            assertNull(HttpRequest(method, url).body, method.name)
        }
    }

    @Test
    fun `GET HEAD and TRACE refuse a body, naming the method`() {
        listOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.of("TRACE").getOrThrow()).forEach { method ->
            val exception = assertFailsWith<IllegalArgumentException> { HttpRequest(method, url) { this.body = this@HttpRequestComponentTest.body } }
            assertEquals("A ${method.name} request must not have a body", exception.message)
        }
    }

    @Test
    fun `CONNECT is refused`() {
        val exception = assertFailsWith<IllegalArgumentException> { HttpRequest(HttpMethod.of("CONNECT").getOrThrow(), url) }
        assertEquals("A CONNECT request is not supported", exception.message)
    }

    @Test
    fun `a non-sendable value is refused naming the header and never the value`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            HttpRequest(HttpMethod.Get, url) { headers = HttpHeaders { add("X-Note", "café SECRET") } }
        }
        assertEquals("Value of header 'X-Note' must be visible ASCII, space or tab to be sent", exception.message)
        assertFalse(exception.message!!.contains("SECRET"))
        HttpRequest(HttpMethod.Get, url) { headers = HttpHeaders { add("X-Note", "a\tb c~") } }
    }

    @Test
    fun `a Content-Type header beside a body content type is refused, in any case`() {
        listOf("Content-Type", "content-type", "CONTENT-TYPE").forEach { name ->
            val exception = assertFailsWith<IllegalArgumentException> {
                HttpRequest(HttpMethod.Post, url) { headers = HttpHeaders { add(name, "text/plain") }; this.body = this@HttpRequestComponentTest.body }
            }
            assertEquals("A request with a body content type must not also set a Content-Type header", exception.message, name)
        }
        HttpRequest(HttpMethod.Post, url) { headers = HttpHeaders { add("Content-Type", "text/plain") }; this.body = RequestBody(byteArrayOf(1), null) }
        HttpRequest(HttpMethod.Post, url) { this.body = this@HttpRequestComponentTest.body }
    }

    @Test
    fun `a request breaking two rules reports the first in order`() {
        val connectAndValue = assertFailsWith<IllegalArgumentException> {
            HttpRequest(HttpMethod.of("CONNECT").getOrThrow(), url) { headers = HttpHeaders { add("X", "é") } }
        }
        assertEquals("A CONNECT request is not supported", connectAndValue.message)
        val valueAndBody = assertFailsWith<IllegalArgumentException> {
            HttpRequest(HttpMethod.Get, url) { headers = HttpHeaders { add("X", "é") }; this.body = this@HttpRequestComponentTest.body }
        }
        assertEquals("Value of header 'X' must be visible ASCII, space or tab to be sent", valueAndBody.message)
        val bodyAndContentType = assertFailsWith<IllegalArgumentException> {
            HttpRequest(HttpMethod.Get, url) { headers = HttpHeaders { add("Content-Type", "a/b") }; this.body = this@HttpRequestComponentTest.body }
        }
        assertEquals("A GET request must not have a body", bodyAndContentType.message)
    }

    @Test
    fun `equality is identity`() {
        val a = HttpRequest(HttpMethod.Get, url)
        val b = HttpRequest(HttpMethod.Get, url)
        assertNotEquals(a, b)
        assertEquals(a, a)
    }

    @Test
    fun `toString shows the redacted URL and the header names, never a value or the body`() {
        val request = HttpRequest(HttpMethod.Post, url) {
            headers = HttpHeaders { add("Accept", "application/json"); addSensitive("X-Api-Key", "SECRET-KEY") }
            this.body = RequestBody("SECRET-BODY".encodeToByteArray(), "text/plain")
        }
        assertEquals(
            "HttpRequest(method=POST, url=https://h/p?..., headers=HttpHeaders(Accept, X-Api-Key*), body=RequestBody(size=11, hasContentType=true))",
            request.toString(),
        )
        assertFalse(request.toString().contains("SECRET"))
        assertEquals("HttpRequest(method=GET, url=https://h/p?..., headers=HttpHeaders(), body=none)", HttpRequest(HttpMethod.Get, url).toString())
    }
}
