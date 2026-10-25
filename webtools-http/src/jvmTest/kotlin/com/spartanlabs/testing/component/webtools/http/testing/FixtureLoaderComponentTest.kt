package com.spartanlabs.testing.component.webtools.http.testing

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.testing.FixtureError
import com.spartanlabs.webtools.http.testing.FixtureLoader
import com.spartanlabs.webtools.http.testing.contentTypeFor
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 2 - `FixtureLoader` against the sample resources under `fixtures/sample/`: the `.http` files, the
 * body-only files and their content types, the name rules, the missing and malformed resource errors, a
 * custom root and a class loader that finds nothing.
 */
class FixtureLoaderComponentTest {

    private val loader = FixtureLoader()

    @Test
    fun `loads a dot-http fixture with its comment ignored`() {
        val fixture = loader.load("sample/player-1.http")
        assertEquals(200, fixture.status)
        assertEquals(
            HttpHeaders {
                add("Content-Type", "application/json"); add("ETag", "\"v1\""); add("X-Note", "café")   // U+00E9 LATIN SMALL LETTER E WITH ACUTE
                add("X-RateLimit-Remaining-Minute", "59")
            },
            fixture.headers,
        )
        assertEquals("{\"account_id\":1,\"name\":\"Ünïcode\"}\n", fixture.bodyText())   // U+00DC, U+00EF
    }

    @Test
    fun `loads a 304 without a body`() {
        val fixture = loader.load("sample/not-modified.http")
        assertEquals(304, fixture.status)
        assertEquals(HttpHeaders { add("ETag", "\"v1\"") }, fixture.headers)
        assertEquals(0, fixture.bodySize)
    }

    @Test
    fun `a body-only resource is a 200 with a content type by extension and the file's exact bytes`() {
        val json = loader.load("sample/matches.json")
        assertEquals(200, json.status)
        assertEquals(HttpHeaders { add("Content-Type", "application/json") }, json.headers)
        assertContentEquals("[{\"match_id\":1}]".encodeToByteArray(), json.body())

        val rss = loader.load("sample/feed.rss")
        assertEquals(200, rss.status)
        assertEquals("application/rss+xml", rss.headers["Content-Type"])
        assertContentEquals("<rss version=\"2.0\"></rss>".encodeToByteArray(), rss.body())

        val txt = loader.load("sample/notes.txt")
        assertEquals(200, txt.status)
        assertEquals("text/plain; charset=utf-8", txt.headers["Content-Type"])
        assertContentEquals("plain text".encodeToByteArray(), txt.body())
    }

    @Test
    fun `contentTypeFor maps every row and falls back to octet-stream`() {
        assertEquals("application/json", contentTypeFor("json"))
        assertEquals("application/xml", contentTypeFor("xml"))
        assertEquals("application/rss+xml", contentTypeFor("rss"))
        assertEquals("application/atom+xml", contentTypeFor("atom"))
        assertEquals("text/plain; charset=utf-8", contentTypeFor("txt"))
        assertEquals("text/html; charset=utf-8", contentTypeFor("html"))
        assertEquals("text/html; charset=utf-8", contentTypeFor("htm"))
        assertEquals("application/json", contentTypeFor("JSON"))
        assertEquals("application/octet-stream", contentTypeFor("bin"))
        assertEquals("application/octet-stream", contentTypeFor(""))
    }

    @Test
    fun `an absolute name a backslash or an empty dot or dot-dot segment is refused`() {
        listOf("/sample/notes.txt", "sample\\notes.txt", "", "sample//notes.txt", "./notes.txt", "sample/../notes.txt", "sample/notes.txt/").forEach { name ->
            assertFailsWith<IllegalArgumentException>(name) { loader.load(name) }
        }
    }

    @Test
    fun `a missing resource is a FixtureError naming the resource`() {
        val failure = assertFailsWith<FixtureError> { loader.load("sample/absent.http") }
        assertEquals("fixture resource not found: fixtures/sample/absent.http", failure.message)
    }

    @Test
    fun `a malformed file is a FixtureError naming the resource and the line`() {
        val failure = assertFailsWith<FixtureError> { loader.load("sample/broken.http") }
        assertEquals("fixtures/sample/broken.http, line 2: the header name is not an RFC 9110 token (first offending character at index 3)", failure.message)
    }

    @Test
    fun `a custom root is used`() {
        val fixture = FixtureLoader(root = "fixtures/sample").load("notes.txt")
        assertEquals("plain text", fixture.bodyText())
        assertEquals("fixtures/sample", FixtureLoader(root = "fixtures/sample").root)
        assertFailsWith<IllegalArgumentException> { FixtureLoader(root = " ") }
        assertFailsWith<IllegalArgumentException> { FixtureLoader(root = "/fixtures") }
    }

    @Test
    fun `a class loader that finds nothing yields a not-found error`() {
        val empty = object : ClassLoader(null) {
            override fun getResourceAsStream(name: String): InputStream? = null
        }
        assertNull(empty.getResourceAsStream("fixtures/sample/notes.txt"))
        val failure = assertFailsWith<FixtureError> { FixtureLoader(classLoader = empty).load("sample/notes.txt") }
        assertTrue(failure.message!!.startsWith("fixture resource not found: "))
    }
}
