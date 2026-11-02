package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.HopExchanges
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.RequestBody
import com.spartanlabs.webtools.http.hopRequest
import okhttp3.Request
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Level 2 - the hop request: the URL is the `Url` text with query and port, the method name goes as is, the caller's headers go in
 * order with duplicates, `User-Agent` is the factory's value once, `Range` adds `Accept-Encoding: identity`, a body's content type is a
 * plain header while the OkHttp body's `MediaType` stays `null`, a body-required method without a body gets an empty one, two calls carry
 * different `HopExchanges` and a `newBuilder()` copy shares the tag.
 */
class HopRequestComponentTest {

    private val agent = "webtools/1.0"

    private fun build(method: HttpMethod = HttpMethod.Get, urlText: String = "https://h.test/p", headers: HttpHeaders = HttpHeaders(), body: RequestBody? = null): Request =
        hopRequest(method, url(urlText), headers, body, agent).getOrThrow()

    @Test
    fun `the URL is the Url text with query and port kept`() {
        assertEquals("https://h.test:8443/p/q?x=1&y=%20", build(urlText = "https://h.test:8443/p/q?x=1&y=%20").url.toString())
    }

    @Test
    fun `the method name goes as is`() {
        assertEquals("PURGE", build(method = HttpMethod.of("PURGE").getOrThrow()).method)
        assertEquals("HEAD", build(method = HttpMethod.Head).method)
    }

    @Test
    fun `the caller's headers go in order with duplicates then the User-Agent once`() {
        val request = build(headers = HttpHeaders { add("X-A", "1"); add("X-B", "2"); add("X-A", "3") })
        val pairs = (0 until request.headers.size).map { request.headers.name(it) to request.headers.value(it) }
        assertEquals(listOf("X-A" to "1", "X-B" to "2", "X-A" to "3", "User-Agent" to agent), pairs)
        assertEquals(listOf(agent), request.headers.values("User-Agent"))
    }

    @Test
    fun `Range adds Accept-Encoding identity and its absence adds none`() {
        assertEquals(listOf("identity"), build(headers = HttpHeaders { add("range", "bytes=0-9") }).headers.values("Accept-Encoding"))
        assertEquals(emptyList(), build().headers.values("Accept-Encoding"))
    }

    @Test
    fun `a body content type is a plain header and the OkHttp body has no MediaType`() {
        val request = build(method = HttpMethod.Post, body = RequestBody("{}".encodeToByteArray(), "application/json"))
        assertEquals("application/json", request.header("Content-Type"))
        val body = assertNotNull(request.body)
        assertNull(body.contentType())
        assertEquals(2L, body.contentLength())
    }

    @Test
    fun `a body without a content type adds no Content-Type header`() {
        val request = build(method = HttpMethod.Post, body = RequestBody(byteArrayOf(1), null))
        assertNull(request.header("Content-Type"))
        assertEquals(1L, request.body!!.contentLength())
    }

    @Test
    fun `a body-required method without a body gets an empty body and the others get null`() {
        listOf("POST", "PUT", "PATCH", "PROPPATCH", "REPORT", "QUERY").forEach { name ->
            val body = assertNotNull(build(method = HttpMethod.of(name).getOrThrow()).body, name)
            assertEquals(0L, body.contentLength(), name)
            assertNull(body.contentType(), name)
        }
        listOf("GET", "HEAD", "DELETE", "OPTIONS").forEach { name ->
            assertNull(build(method = HttpMethod.of(name).getOrThrow()).body, name)
        }
    }

    @Test
    fun `two calls carry different HopExchanges and a copy shares the tag`() {
        val first = build()
        val second = build()
        val tag = assertNotNull(first.tag(HopExchanges::class.java))
        assertNotSame(tag, assertNotNull(second.tag(HopExchanges::class.java)))
        assertSame(tag, first.newBuilder().build().tag(HopExchanges::class.java))
    }
}
