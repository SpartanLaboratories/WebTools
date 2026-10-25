package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.UrlCorpus
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlParseException
import com.spartanlabs.webtools.http.UrlProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Level 4a - the boundaries: host length 253/254 (with and without a trailing dot), label length
 * 63/64, ports 1, 65535, 0 and 65536, and the trim set (TAB, LF, FF, CR, SP trimmed at either end; VT,
 * NUL, NBSP rejected).
 */
class UrlBoundaryDeterministicTest {

    private fun accepts(input: String) {
        assertTrue(Url.parse(input).isSuccess, "${UrlCorpus.visible(input)} was rejected: ${Url.parse(input).exceptionOrNull()?.message}")
    }

    private fun rejects(input: String, problem: UrlProblem) {
        val exception = Url.parse(input).exceptionOrNull()
        assertIs<UrlParseException>(exception, "${UrlCorpus.visible(input)} was accepted")
        assertEquals(problem, exception.problem, UrlCorpus.visible(input))
    }

    @Test
    fun `host length 253 is accepted and 254 rejected, with and without a trailing dot`() {
        assertEquals(253, UrlCorpus.host253.length)
        assertEquals(254, UrlCorpus.host254.length)
        assertEquals(253, UrlCorpus.host253Dotted.length)
        assertEquals(254, UrlCorpus.host254Dotted.length)
        accepts("http://${UrlCorpus.host253}/")
        rejects("http://${UrlCorpus.host254}/", UrlProblem.Host)
        accepts("http://${UrlCorpus.host253Dotted}/")
        rejects("http://${UrlCorpus.host254Dotted}/", UrlProblem.Host)
    }

    @Test
    fun `label length 63 is accepted and 64 rejected`() {
        accepts("http://${UrlCorpus.label63}/")
        accepts("http://${UrlCorpus.label63}.com/")
        rejects("http://${UrlCorpus.label64}/", UrlProblem.Host)
        rejects("http://a.${UrlCorpus.label64}.com/", UrlProblem.Host)
    }

    @Test
    fun `ports 1 and 65535 are accepted, 0 and 65536 rejected`() {
        assertEquals(1, Url.parse("http://h:1/").getOrThrow().port)
        assertEquals(65535, Url.parse("http://h:65535/").getOrThrow().port)
        rejects("http://h:0/", UrlProblem.Port)
        rejects("http://h:65536/", UrlProblem.Port)
    }

    @Test
    fun `the trim set is TAB LF FF CR and SP, and VT NUL NBSP are rejected`() {
        listOf('\t', '\n', '\u000C', '\r', ' ').forEach { c ->
            assertEquals("http://h/", Url.parse("${c}http://h/").getOrThrow().toString(), "leading ${UrlCorpus.visible("$c")}")
            assertEquals("http://h/", Url.parse("http://h/$c").getOrThrow().toString(), "trailing ${UrlCorpus.visible("$c")}")
        }
        listOf('\u000B', '\u0000', '\u00A0').forEach { c ->   // VT, NUL and NBSP (U+00A0) are outside the trim set
            rejects("${c}http://h/", UrlProblem.WhitespaceOrControl)
            rejects("http://h/$c", UrlProblem.WhitespaceOrControl)
        }
    }
}
