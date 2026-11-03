package com.spartanlabs.testing.component.webtools.http.testing

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.testing.Fixture
import com.spartanlabs.webtools.http.testing.toMockResponse
import mockwebserver3.MockResponse
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Level 2 - the fixture-to-`MockResponse` framing: a `200` with a body writes the headers in order then `Content-Length` from the bytes (a
 * fixture's own `Content-Length: 3` over six bytes becomes `6`); an empty `200` writes `Content-Length: 0`; `204` and `205` carry no
 * `Content-Length` and no body; a `304` carries no body and keeps the declared length, none when absent; a `HEAD` response carries no body
 * and the declared or derived length, a `Transfer-Encoding` fixture included; a `Transfer-Encoding` fixture to `GET` is chunked; a UTF-8 value
 * round-trips; `Content-Encoding`, `Connection`, `Keep-Alive` and duplicate `Set-Cookie` are written as given; a `1xx` fixture and a
 * `204`/`205` fixture with a body are refused; the status line has three parts even for an uncommon status; the builder's default
 * `Content-Length: 0` never survives.
 */
class ToMockResponseComponentTest {

    private fun fixture(status: Int, body: String = "", configure: HttpHeaders.Builder.() -> Unit = {}): Fixture =
        Fixture(status, HttpHeaders(configure), body.encodeToByteArray())

    private fun pairs(response: MockResponse): List<Pair<String, String>> =
        (0 until response.headers.size).map { response.headers.name(it) to response.headers.value(it) }

    private fun bodyBytes(response: MockResponse): ByteArray? = response.body?.let { Buffer().also(it::writeTo).readByteArray() }

    @Test
    fun `a 200 with a body writes the headers in order then Content-Length from the bytes`() {
        val response = fixture(200, "sixsix") { add("Content-Type", "text/plain"); add("Content-Length", "3"); add("X-A", "1") }.toMockResponse()
        assertEquals("HTTP/1.1 200 OK", response.status)
        assertEquals(listOf("Content-Type" to "text/plain", "X-A" to "1", "Content-Length" to "6"), pairs(response))
        assertContentEquals("sixsix".encodeToByteArray(), bodyBytes(response))
    }

    @Test
    fun `an empty 200 writes Content-Length 0`() {
        val response = fixture(200).toMockResponse()
        assertEquals(listOf("Content-Length" to "0"), pairs(response))
        assertEquals(0, bodyBytes(response)!!.size)
    }

    @Test
    fun `204 and 205 carry no Content-Length and no body`() {
        listOf(204, 205).forEach { status ->
            val response = fixture(status) { add("X-A", "1") }.toMockResponse()
            assertEquals(listOf("X-A" to "1"), pairs(response), "$status")
            assertNull(response.body, "$status")
        }
    }

    @Test
    fun `a 304 carries no body and keeps the declared length only`() {
        val declared = fixture(304) { add("ETag", "\"v1\""); add("Content-Length", "5000") }.toMockResponse()
        assertEquals(listOf("ETag" to "\"v1\"", "Content-Length" to "5000"), pairs(declared))
        assertNull(declared.body)
        val undeclared = fixture(304) { add("ETag", "\"v1\"") }.toMockResponse()
        assertEquals(listOf("ETag" to "\"v1\""), pairs(undeclared))
        assertNull(undeclared.body)
    }

    @Test
    fun `a HEAD response carries no body and the declared or derived length`() {
        val declared = fixture(200, "body") { add("Content-Length", "5000") }.toMockResponse(HttpMethod.Head)
        assertEquals(listOf("Content-Length" to "5000"), pairs(declared))
        assertNull(declared.body)
        val derived = fixture(200, "body") { add("X-A", "1") }.toMockResponse(HttpMethod.Head)
        assertEquals(listOf("X-A" to "1", "Content-Length" to "4"), pairs(derived))
        assertNull(derived.body)
        val chunkedFixture = fixture(200, "body") { add("Transfer-Encoding", "chunked") }.toMockResponse(HttpMethod.Head)
        assertEquals(listOf("Content-Length" to "4"), pairs(chunkedFixture))
        assertNull(chunkedFixture.body)
    }

    @Test
    fun `a Transfer-Encoding fixture to GET is chunked with no Content-Length`() {
        val response = fixture(200, "body") { add("Transfer-Encoding", "chunked"); add("X-A", "1") }.toMockResponse()
        // Lowercased because chunkedBody writes its header in its own spelling, `Transfer-encoding`; every other test compares names as given.
        val names = pairs(response).map { it.first.lowercase() }
        assertEquals(listOf("x-a", "transfer-encoding"), names)
        assertEquals("chunked", response.headers["Transfer-Encoding"])
        assertContentEquals("4\r\nbody\r\n0\r\n".encodeToByteArray(), bodyBytes(response))
    }

    @Test
    fun `a UTF-8 value round-trips and the other headers are written as given`() {
        val response = fixture(200, "x") {
            add("X-Note", "café"); add("Content-Encoding", "gzip"); add("Connection", "close"); add("Keep-Alive", "timeout=5")
            add("Set-Cookie", "a=1"); add("Set-Cookie", "b=2")
        }.toMockResponse()
        assertEquals(
            listOf("X-Note" to "café", "Content-Encoding" to "gzip", "Connection" to "close", "Keep-Alive" to "timeout=5", "Set-Cookie" to "a=1", "Set-Cookie" to "b=2", "Content-Length" to "1"),
            pairs(response),
        )
    }

    @Test
    fun `a 1xx fixture and a 204 or 205 fixture with a body are refused`() {
        assertFailsWith<IllegalArgumentException> { fixture(100).toMockResponse() }
        assertFailsWith<IllegalArgumentException> { fixture(101).toMockResponse() }
        assertFailsWith<IllegalArgumentException> { fixture(204, "x").toMockResponse() }
        assertFailsWith<IllegalArgumentException> { fixture(205, "x").toMockResponse() }
    }

    @Test
    fun `the status line has three parts even for an uncommon status`() {
        assertEquals("HTTP/1.1 404 Not Found", fixture(404).toMockResponse().status)
        val teapot = fixture(418).toMockResponse()
        assertEquals("HTTP/1.1 418 ", teapot.status)
        assertEquals(418, teapot.code)
        assertEquals("", teapot.message)
    }
}
