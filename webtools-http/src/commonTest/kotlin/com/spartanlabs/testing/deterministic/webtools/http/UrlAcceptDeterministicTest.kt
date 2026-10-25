package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.UrlCorpus
import com.spartanlabs.webtools.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Level 4a - every `UrlCorpus.accept` entry parses to its canonical text, and a sample of entries
 * exposes the expected `scheme`, `host`, `port`, `encodedPath` and `encodedQuery`.
 */
class UrlAcceptDeterministicTest {

    private fun parse(input: String): Url =
        Url.parse(input).getOrElse { fail("${UrlCorpus.visible(input)} was rejected: ${it.message}") }

    @Test
    fun `every accepted corpus entry prints its canonical text`() {
        UrlCorpus.accept.forEach { case ->
            assertEquals(case.canonical, parse(case.input).toString(), "canonical text of ${UrlCorpus.visible(case.input)}")
        }
    }

    @Test
    fun `the members of a sample of accepted entries`() {
        parse("https://[2001:db8::1]:8443/a").let {
            assertEquals("https", it.scheme)
            assertEquals("2001:db8::1", it.host)
            assertEquals(8443, it.port)
            assertEquals("/a", it.encodedPath)
            assertNull(it.encodedQuery)
        }
        parse("HTTP://EXAMPLE.COM:80/a/./b/../c?x=1#frag").let {
            assertEquals("http", it.scheme)
            assertEquals("example.com", it.host)
            assertEquals(80, it.port)
            assertEquals("/a/c", it.encodedPath)
            assertEquals("x=1", it.encodedQuery)
        }
        parse("https://example.com/?").let {
            assertEquals(443, it.port)
            assertEquals("/", it.encodedPath)
            assertEquals("", it.encodedQuery)
        }
        parse("http://[::ffff:127.0.0.1]/").let {
            assertEquals("127.0.0.1", it.host)
            assertEquals(80, it.port)
        }
        parse("https://h:80/").let {
            assertEquals(80, it.port)
            assertEquals("https://h:80/", it.toString())
        }
        parse("https://example.com/päth/日本?q=ü").let {
            assertEquals("/p%C3%A4th/%E6%97%A5%E6%9C%AC", it.encodedPath)
            assertEquals("q=%C3%BC", it.encodedQuery)
        }
    }
}
