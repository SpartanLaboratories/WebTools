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
 * Level 4a - every `UrlCorpus.reject` entry fails with the expected `UrlProblem`, the failure is an
 * `IllegalArgumentException`, and its message is the kind's constant. The crash list is rejected too.
 */
class UrlRejectDeterministicTest {

    @Test
    fun `every rejected corpus entry fails with its kind and constant message`() {
        UrlCorpus.reject.forEach { case ->
            val result = Url.parse(case.input)
            assertTrue(result.isFailure, "${UrlCorpus.visible(case.input)} was accepted as ${result.getOrNull()}")
            val exception = result.exceptionOrNull()
            assertIs<IllegalArgumentException>(exception)
            assertIs<UrlParseException>(exception)
            assertEquals(case.problem, exception.problem, "kind of ${UrlCorpus.visible(case.input)}")
            assertEquals(case.problem!!.message, exception.message)
        }
    }

    @Test
    fun `every known HttpUrl crash input is rejected as a bad host`() {
        UrlCorpus.crash.forEach { input ->
            val exception = Url.parse(input).exceptionOrNull()
            assertIs<UrlParseException>(exception, "${UrlCorpus.visible(input)} was accepted")
            assertEquals(UrlProblem.Host, exception.problem, UrlCorpus.visible(input))
        }
    }

    @Test
    fun `the reject list covers every kind but DotSegment`() {
        val kinds = UrlCorpus.reject.map { it.problem }.toSet()
        assertEquals(UrlProblem.entries.toSet() - UrlProblem.DotSegment, kinds)
    }
}
