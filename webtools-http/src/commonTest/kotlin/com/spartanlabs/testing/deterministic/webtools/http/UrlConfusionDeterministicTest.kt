package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.UrlCorpus
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

/**
 * Level 4a - the confusion strings of plan §9.1 (host-spoofing, slash, scheme and `xn--` forms) give
 * their expected canonical text or kind.
 */
class UrlConfusionDeterministicTest {

    @Test
    fun `every confusion string gives its expected result`() {
        UrlCorpus.confusion.forEach { case ->
            val result = Url.parse(case.input)
            if (case.canonical != null) {
                val url = result.getOrElse { fail("${UrlCorpus.visible(case.input)} was rejected: ${it.message}") }
                assertEquals(case.canonical, url.toString(), "canonical text of ${UrlCorpus.visible(case.input)}")
            } else {
                val exception = result.exceptionOrNull()
                assertIs<UrlParseException>(exception, "${UrlCorpus.visible(case.input)} was accepted as ${result.getOrNull()}")
                assertEquals(case.problem, exception.problem, "kind of ${UrlCorpus.visible(case.input)}")
            }
        }
    }
}
