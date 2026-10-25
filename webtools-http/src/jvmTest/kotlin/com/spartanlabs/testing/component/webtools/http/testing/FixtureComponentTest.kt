package com.spartanlabs.testing.component.webtools.http.testing

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.Fixture
import com.spartanlabs.webtools.http.testing.FixtureError
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Level 2 - `Fixture` in isolation: the status check, the copied body, `bodyText()`, equality and hash,
 * `toResponse`, `toBytes` with and without comments, `parse` with the default and a custom origin, the
 * round trip and `toString()`.
 */
class FixtureComponentTest {

    private val headers = HttpHeaders { add("Content-Type", "application/json"); add("ETag", "\"v1\"") }
    private val body = "{\"a\":1}".encodeToByteArray()

    @Test
    fun `a status outside 100 to 599 is refused`() {
        assertFailsWith<IllegalArgumentException> { Fixture(99, headers, body) }
        assertFailsWith<IllegalArgumentException> { Fixture(600, headers, body) }
        assertEquals(100, Fixture(100, headers, body).status)
        assertEquals(599, Fixture(599, headers, body).status)
    }

    @Test
    fun `the body is copied in and body() is a fresh copy each call`() {
        val source = byteArrayOf(1, 2, 3)
        val fixture = Fixture(200, headers, source)
        source[0] = 9
        val first = fixture.body()
        val second = fixture.body()
        assertContentEquals(byteArrayOf(1, 2, 3), first)
        assertNotSame(first, second)
        first[1] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), fixture.body())
        assertEquals(3, fixture.bodySize)
    }

    @Test
    fun `bodyText replaces malformed bytes`() {
        val fixture = Fixture(200, headers, byteArrayOf(0x61, 0xFF.toByte(), 0x62))
        assertEquals("a�b", fixture.bodyText())   // U+FFFD REPLACEMENT CHARACTER
    }

    @Test
    fun `equality and hash are on status headers and body bytes`() {
        val a = Fixture(200, headers, body)
        val same = Fixture(200, HttpHeaders { add("Content-Type", "application/json"); add("ETag", "\"v1\"") }, body.copyOf())
        assertEquals(a, same)
        assertEquals(a.hashCode(), same.hashCode())
        assertNotEquals(a, Fixture(201, headers, body))
        assertNotEquals(a, Fixture(200, HttpHeaders(), body))
        assertNotEquals(a, Fixture(200, headers, byteArrayOf()))
        assertFalse(a.equals("Fixture"))
    }

    @Test
    fun `toResponse carries status headers body and the given url with no redirects`() {
        val url = Url.parse("https://example.com/x?y=1").getOrThrow()
        val response = Fixture(304, headers, body).toResponse(url)
        assertEquals(304, response.status)
        assertEquals(headers, response.headers)
        assertContentEquals(body, response.body())
        assertEquals(url, response.url)
        assertTrue(response.redirectChain.isEmpty())
    }

    @Test
    fun `toBytes writes the canonical file with and without comments`() {
        val fixture = Fixture(200, headers, body)
        val expected = "HTTP/1.1 200 OK\nContent-Type: application/json\nETag: \"v1\"\n\n{\"a\":1}"
        assertEquals(expected, fixture.toBytes().decodeToString())
        assertEquals("# one\n# two\n$expected", fixture.toBytes(listOf("one", "two")).decodeToString())
    }

    @Test
    fun `a comment holding CR or LF is refused`() {
        val fixture = Fixture(200, headers, body)
        assertFailsWith<IllegalArgumentException> { fixture.toBytes(listOf("a\nb")) }
        assertFailsWith<IllegalArgumentException> { fixture.toBytes(listOf("a\rb")) }
    }

    @Test
    fun `parse names the default and a custom origin`() {
        val bad = "nope".encodeToByteArray()
        assertEquals("<fixture>, line 1: expected a status line (HTTP/1.1 200 OK)", assertFailsWith<FixtureError> { Fixture.parse(bad) }.message)
        assertEquals("sample/x.http, line 1: expected a status line (HTTP/1.1 200 OK)", assertFailsWith<FixtureError> { Fixture.parse(bad, "sample/x.http") }.message)
    }

    @Test
    fun `parse of toBytes equals the fixture`() {
        val fixture = Fixture(404, headers, body)
        assertEquals(fixture, Fixture.parse(fixture.toBytes()))
        assertEquals(fixture, Fixture.parse(fixture.toBytes(listOf("request: GET https://example.com/"))))
    }

    @Test
    fun `toString shows the status the header names and the body size and no value`() {
        val fixture = Fixture(200, HttpHeaders { add("X-Api-Key", "SECRET-VALUE"); addSensitive("X-Token", "ALSO-SECRET") }, "SECRET-BODY".encodeToByteArray())
        assertEquals("Fixture(status=200, headers=HttpHeaders(X-Api-Key, X-Token*), bodySize=11)", fixture.toString())
        assertFalse("SECRET" in fixture.toString())
    }
}
