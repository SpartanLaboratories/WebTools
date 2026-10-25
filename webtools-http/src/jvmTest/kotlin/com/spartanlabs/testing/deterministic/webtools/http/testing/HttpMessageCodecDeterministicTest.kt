package com.spartanlabs.testing.deterministic.webtools.http.testing

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.testing.Fixture
import com.spartanlabs.webtools.http.testing.FixtureError
import com.spartanlabs.webtools.http.testing.HttpMessageCodec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 4a - the `.http` codec as input-to-output mappings: byte-exact round trips, CRLF heads, comments,
 * the status-line forms, every refusal of the plan's table with its line number, the header-after-status
 * rule, the reason table, and that no error message echoes a value.
 */
class HttpMessageCodecDeterministicTest {

    private val canary = "SECRET-CANARY-7f3a"

    private fun bytes(text: String): ByteArray = text.encodeToByteArray()

    private fun refusal(text: String, origin: String = "o"): String =
        assertFailsWith<FixtureError> { Fixture.parse(bytes(text), origin) }.message!!

    private fun refusal(raw: ByteArray): String = assertFailsWith<FixtureError> { Fixture.parse(raw, "o") }.message!!

    @Test
    fun `round trips are byte-exact for awkward bodies`() {
        val headers = HttpHeaders { add("Content-Type", "application/octet-stream") }
        listOf(
            byteArrayOf(0, 1, 0, 2),
            bytes("\n\n"),
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()),
            bytes("line\r\nline\r\n"),
            byteArrayOf(),
            bytes("\n"),
        ).forEach { body ->
            val fixture = Fixture(200, headers, body)
            val again = Fixture.parse(fixture.toBytes())
            assertContentEquals(body, again.body())
            assertEquals(fixture, again)
            assertContentEquals(fixture.toBytes(), again.toBytes())
        }
    }

    @Test
    fun `a CRLF head parses and the body starts after the blank line`() {
        val fixture = Fixture.parse(bytes("HTTP/1.1 200 OK\r\nA: 1\r\nB: 2\r\n\r\nbody\r\n"))
        assertEquals(200, fixture.status)
        assertEquals(HttpHeaders { add("A", "1"); add("B", "2") }, fixture.headers)
        assertEquals("body\r\n", fixture.bodyText())
    }

    @Test
    fun `comments are ignored on read and written by toBytes`() {
        val none = Fixture.parse(bytes("HTTP/1.1 200 OK\n\nx"))
        val several = Fixture.parse(bytes("# a\n# b\n#c\nHTTP/1.1 200 OK\n\nx"))
        assertEquals(none, several)
        assertEquals("# p\n# q\nHTTP/1.1 200 OK\n\nx", none.toBytes(listOf("p", "q")).decodeToString())
    }

    @Test
    fun `every documented status-line form parses to its code`() {
        assertEquals(200, Fixture.parse(bytes("HTTP/1.1 200 OK\n")).status)
        assertEquals(404, Fixture.parse(bytes("HTTP/2 404\n")).status)
        assertEquals(304, Fixture.parse(bytes("HTTP/1.1 304")).status)
        assertEquals(500, Fixture.parse(bytes("HTTP/1.0 500 Internal Server Error\n\n")).status)
        assertEquals(0, Fixture.parse(bytes("HTTP/1.1 204 No Content\n")).bodySize)
    }

    @Test
    fun `every refusal of the table names the origin and the line`() {
        assertEquals("o: the file is empty", refusal(""))
        assertEquals("o, line 2: no status line", refusal("# only a comment\n"))
        assertEquals("o, line 1: expected a status line (HTTP/1.1 200 OK)", refusal("FOO 200 OK\n"))
        assertEquals("o, line 1: expected a status line (HTTP/1.1 200 OK)", refusal("HTTP/x 200 OK\n"))
        assertEquals("o, line 1: expected a status line (HTTP/1.1 200 OK)", refusal("HTTP/1.1"))
        assertEquals("o, line 1: the status code is not three digits", refusal("HTTP/1.1 20 OK\n"))
        assertEquals("o, line 1: the status code is not three digits", refusal("HTTP/1.1 2000\n"))
        assertEquals("o, line 1: the status code is not three digits", refusal("HTTP/1.1 20x\n"))
        assertEquals("o, line 1: the status code is outside 100..599", refusal("HTTP/1.1 099\n"))
        assertEquals("o, line 1: the status code is outside 100..599", refusal("HTTP/1.1 600\n"))
        assertEquals("o, line 3: a folded header line is not supported", refusal("HTTP/1.1 200\nA: b\n c\n"))
        assertEquals("o, line 3: a folded header line is not supported", refusal("HTTP/1.1 200\nA: b\n\tc\n"))
        assertEquals("o, line 2: not a header line (expected Name: value)", refusal("HTTP/1.1 200\nBogus\n"))
        assertEquals("o, line 2: the header name is not an RFC 9110 token (first offending character at index 3)", refusal("HTTP/1.1 200\nBad Header: x\n"))
        assertEquals("o, line 2: the header name is not an RFC 9110 token (first offending character at index 0)", refusal("HTTP/1.1 200\n: x\n"))
        assertEquals("o, line 2: a header value must not contain NUL", refusal("HTTP/1.1 200\nA: b\u0000c\n"))
        assertEquals("o, line 2: the line is not valid UTF-8", refusal(bytes("HTTP/1.1 200\nA: ") + byteArrayOf(0xFF.toByte()) + bytes("\n")))
        assertEquals("o, line 1: a carriage return must be followed by a line feed", refusal("HTTP/1.1 200\rX\n"))
        assertEquals("o, line 2: a carriage return must be followed by a line feed", refusal("HTTP/1.1 200\nA: b\r"))
        assertEquals("o, line 1: a blank line before the status line", refusal("\nHTTP/1.1 200\n"))
        assertEquals("o, line 2: a blank line before the status line", refusal("# c\n\nHTTP/1.1 200\n"))
        assertEquals("sample/a.http, line 1: expected a status line (HTTP/1.1 200 OK)", refusal("x", "sample/a.http"))
    }

    @Test
    fun `a hash line after the status line is a header`() {
        val fixture = Fixture.parse(bytes("HTTP/1.1 200 OK\n#name: v\n\n"))
        assertEquals(HttpHeaders { add("#name", "v") }, fixture.headers)
    }

    @Test
    fun `the reason table holds every common status and none for others`() {
        val expected = mapOf(
            100 to "Continue", 101 to "Switching Protocols", 200 to "OK", 201 to "Created", 202 to "Accepted",
            204 to "No Content", 206 to "Partial Content", 301 to "Moved Permanently", 302 to "Found",
            303 to "See Other", 304 to "Not Modified", 307 to "Temporary Redirect", 308 to "Permanent Redirect",
            400 to "Bad Request", 401 to "Unauthorized", 403 to "Forbidden", 404 to "Not Found",
            405 to "Method Not Allowed", 408 to "Request Timeout", 409 to "Conflict", 410 to "Gone",
            412 to "Precondition Failed", 413 to "Content Too Large", 414 to "URI Too Long",
            415 to "Unsupported Media Type", 416 to "Range Not Satisfiable", 422 to "Unprocessable Content",
            429 to "Too Many Requests", 500 to "Internal Server Error", 501 to "Not Implemented",
            502 to "Bad Gateway", 503 to "Service Unavailable", 504 to "Gateway Timeout",
        )
        expected.forEach { (status, reason) -> assertEquals(reason, HttpMessageCodec.reasonPhrase(status), "$status") }
        assertNull(HttpMessageCodec.reasonPhrase(299))
        assertNull(HttpMessageCodec.reasonPhrase(418))
        assertEquals("HTTP/1.1 299\n\n", Fixture(299, HttpHeaders(), byteArrayOf()).toBytes().decodeToString())
        assertEquals("HTTP/1.1 413 Content Too Large\n\n", Fixture(413, HttpHeaders(), byteArrayOf()).toBytes().decodeToString())
    }

    @Test
    fun `error messages never echo a header value a body or an invalid line`() {
        val messages = listOf(
            refusal("HTTP/1.1 200\nBad Header: $canary\n\n$canary"),
            refusal("HTTP/1.1 200\n$canary\n"),
            refusal("$canary\n"),
            refusal("HTTP/1.1 200\nA: $canary\u0000\n"),
            refusal("HTTP/1.1 200\nA: $canary\r\n$canary"),
            refusal("HTTP/1.1 999 $canary\n"),
            refusal(bytes("HTTP/1.1 200\nA: $canary") + byteArrayOf(0xC0.toByte()) + bytes("\n")),
        )
        messages.forEach { message ->
            assertFalse(canary in message, message)
            assertTrue(message.startsWith("o, line "), message)
        }
    }
}
