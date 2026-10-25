package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.testing.support.webtools.http.UrlCorpus
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlParseException
import com.spartanlabs.webtools.http.UrlProblem
import com.spartanlabs.webtools.http.encodePathSegment
import com.spartanlabs.webtools.http.encodeQueryComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Level 4c - a canary placed in every position of a URL that can fail (scheme, user-info, host, port,
 * path, query, fragment) and for every parse failure kind that can hold text (`Empty` cannot; `DotSegment`
 * is the encoders' and is covered separately): no failure message and no `toString()` of a failure
 * contains it, and every message is one of the constants.
 */
class UrlParseSecrecyNonFunctionalTest {

    private val canary = UrlCorpus.CANARY
    private val constants: Set<String> = UrlProblem.entries.map { it.message }.toSet()

    private val inputs: Map<String, UrlProblem> = mapOf(
        "$canary://h/" to UrlProblem.Scheme,
        "$canary" to UrlProblem.Scheme,
        "http://$canary@h/" to UrlProblem.UserInfo,
        "http://u:$canary@h/" to UrlProblem.UserInfo,
        "http://$canary!/" to UrlProblem.Host,
        "http://[$canary]/" to UrlProblem.Host,
        "http://$canary%41/" to UrlProblem.HostEncoding,
        "http://${canary}é/" to UrlProblem.HostEncoding,
        "http://xn--$canary/" to UrlProblem.HostIdn,
        "http://h:$canary/" to UrlProblem.Port,
        "http://h/$canary\\x" to UrlProblem.Backslash,
        "http://h/$canary x" to UrlProblem.WhitespaceOrControl,
        "http://h/$canary\u0000" to UrlProblem.WhitespaceOrControl,
        "http://h/$canary\uD800" to UrlProblem.NotUnicode,
        "http://h/?$canary\\" to UrlProblem.Backslash,
        "http://h/?q=$canary\u0000" to UrlProblem.WhitespaceOrControl,
        "http://h/?q=$canary\uDC00" to UrlProblem.NotUnicode,
        "http://h/#$canary\\" to UrlProblem.Backslash,
        "http://h/#$canary\tx" to UrlProblem.WhitespaceOrControl,
        "http://h/#$canary\uD800" to UrlProblem.NotUnicode,
    )

    @Test
    fun `no parse failure echoes the canary and every message is a constant`() {
        inputs.forEach { (input, problem) ->
            val exception = Url.parse(input).exceptionOrNull()
            assertIs<UrlParseException>(exception, "${UrlCorpus.visible(input)} was accepted")
            assertEquals(problem, exception.problem, UrlCorpus.visible(input))
            assertFalse(exception.message!!.contains(canary), UrlCorpus.visible(input))
            assertFalse(exception.toString().contains(canary), UrlCorpus.visible(input))
            assertTrue(exception.message in constants, exception.message)
        }
    }

    @Test
    fun `every parse failure kind but Empty and DotSegment is exercised`() {
        assertEquals(UrlProblem.entries.toSet() - UrlProblem.Empty - UrlProblem.DotSegment, inputs.values.toSet())
    }

    @Test
    fun `the encoders never echo the canary`() {
        listOf(encodePathSegment("$canary\uD800"), encodeQueryComponent("$canary\uDC00")).forEach { result ->
            val exception = result.exceptionOrNull()
            assertIs<UrlParseException>(exception)
            assertEquals(UrlProblem.NotUnicode, exception.problem)
            assertFalse(exception.toString().contains(canary))
        }
        val dot = encodePathSegment("..").exceptionOrNull()
        assertIs<UrlParseException>(dot)
        assertTrue(dot.message in constants)
    }
}
