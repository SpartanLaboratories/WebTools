package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.UrlCorpus
import com.spartanlabs.webtools.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Level 4a - printing and equality over the accepted corpus: `Url.parse(u.toString())` equals `u`;
 * `redacted()` equals `toString()` without a query and otherwise ends `?...` with the right length;
 * equality and hash are on the canonical text.
 */
class UrlPrintingDeterministicTest {

    private fun parse(input: String): Url =
        Url.parse(input).getOrElse { fail("${UrlCorpus.visible(input)} was rejected: ${it.message}") }

    private val accepted: List<Url> = (UrlCorpus.accept + UrlCorpus.confusion).filter { it.canonical != null }.map { parse(it.input) }

    @Test
    fun `the canonical text re-parses to an equal Url`() {
        accepted.forEach { url ->
            val again = parse(url.toString())
            assertEquals(url, again, url.toString())
            assertEquals(url.hashCode(), again.hashCode(), url.toString())
            assertEquals(url.toString(), again.toString())
        }
    }

    @Test
    fun `redacted hides the query and nothing else`() {
        accepted.forEach { url ->
            val text = url.toString()
            val query = url.encodedQuery
            if (query == null) {
                assertEquals(text, url.redacted(), text)
                assertTrue('?' !in url.redacted(), text)
            } else {
                assertTrue(url.redacted().endsWith("?..."), text)
                assertEquals(text.length - query.length + 3, url.redacted().length, text)
                assertEquals(text.substring(0, text.length - query.length - 1), url.redacted().removeSuffix("?..."), text)
            }
        }
        assertEquals("https://example.com/?...", parse("https://example.com/?").redacted())
        assertEquals("https://example.com/?...", parse("https://example.com/?api_key=abc").redacted())
    }

    @Test
    fun `equality and hash are on the canonical text`() {
        assertEquals(parse("http://h/b"), parse("HTTP://H:80/a/../b#x"))
        assertEquals(parse("http://h/b").hashCode(), parse("HTTP://H:80/a/../b#x").hashCode())
        assertNotEquals(parse("http://h/%2f"), parse("http://h/%2F"))
        assertNotEquals(parse("http://127.1/"), parse("http://127.0.0.1/"))
        assertNotEquals(parse("http://h/"), parse("http://h./"))
        assertNotEquals(parse("http://h/?"), parse("http://h/"))
        assertNotEquals<Any>(parse("http://h/"), "http://h/")
    }
}
