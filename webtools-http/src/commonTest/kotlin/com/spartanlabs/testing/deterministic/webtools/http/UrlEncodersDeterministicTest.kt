package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.UrlParseException
import com.spartanlabs.webtools.http.UrlProblem
import com.spartanlabs.webtools.http.encodePathSegment
import com.spartanlabs.webtools.http.encodeQueryComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Level 4a - the `internal` encoders over all 128 ASCII characters (the raw sets of plan §6.5),
 * non-ASCII as upper-case UTF-8, `.` and `..` failing with `DotSegment`, `%2e` becoming `%252e`, `~`
 * becoming `%7E` in a query, and an unpaired surrogate failing with `NotUnicode`.
 */
class UrlEncodersDeterministicTest {

    private val pathRaw: Set<Char> = (('A'..'Z') + ('a'..'z') + ('0'..'9') + "!\$&'()*+,-.:;=@[]_~".toList()).toSet()
    private val queryRaw: Set<Char> = (('A'..'Z') + ('a'..'z') + ('0'..'9') + "*-._".toList()).toSet()

    private fun hex(c: Char): String = "%" + c.code.toString(16).uppercase().padStart(2, '0')

    @Test
    fun `encodePathSegment leaves exactly its raw set alone over ASCII`() {
        (0..127).map { it.toChar() }.forEach { c ->
            val text = "a${c}b"
            val expected = if (c == '.') null else "a" + (if (c in pathRaw) c.toString() else hex(c)) + "b"
            // "a.b" is not a dot segment; only "." and ".." are.
            assertEquals(expected ?: "a.b", encodePathSegment(text).getOrThrow(), "character ${c.code}")
        }
        assertEquals("%2F", encodePathSegment("/").getOrThrow())
        assertEquals("%3F", encodePathSegment("?").getOrThrow())
        assertEquals("%23", encodePathSegment("#").getOrThrow())
        assertEquals("%20", encodePathSegment(" ").getOrThrow())
        assertEquals("+", encodePathSegment("+").getOrThrow())
    }

    @Test
    fun `encodeQueryComponent leaves exactly its raw set alone over ASCII`() {
        (0..127).map { it.toChar() }.forEach { c ->
            val expected = if (c in queryRaw) c.toString() else hex(c)
            assertEquals(expected, encodeQueryComponent(c.toString()).getOrThrow(), "character ${c.code}")
        }
        assertEquals("%20", encodeQueryComponent(" ").getOrThrow())
        assertEquals("%2B", encodeQueryComponent("+").getOrThrow())
        assertEquals("%25", encodeQueryComponent("%").getOrThrow())
        assertEquals("%7E", encodeQueryComponent("~").getOrThrow())
    }

    @Test
    fun `non-ASCII is UTF-8 in upper-case hex`() {
        assertEquals("%C3%A9", encodePathSegment("é").getOrThrow())
        assertEquals("%E6%97%A5%E6%9C%AC%E8%AA%9E", encodePathSegment("日本語").getOrThrow())
        assertEquals("%F0%9F%98%80", encodePathSegment("😀").getOrThrow())
        assertEquals("%C3%A9", encodeQueryComponent("é").getOrThrow())
        assertEquals("%F0%9F%98%80", encodeQueryComponent("😀").getOrThrow())
    }

    @Test
    fun `dot segments fail and an encoded dot is harmless`() {
        listOf(".", "..").forEach {
            val exception = encodePathSegment(it).exceptionOrNull()
            assertIs<UrlParseException>(exception, it)
            assertEquals(UrlProblem.DotSegment, exception.problem, it)
            assertEquals("A path segment must not be \".\" or \"..\".", exception.message)
        }
        assertEquals("%252e", encodePathSegment("%2e").getOrThrow())
        assertEquals("...", encodePathSegment("...").getOrThrow())
        assertEquals("a..", encodePathSegment("a..").getOrThrow())
    }

    @Test
    fun `an unpaired surrogate fails with NotUnicode`() {
        listOf("\uD800", "\uDC00", "a\uD800b", "\uD83D", "\uDE00\uD83D").forEach { text ->
            listOf(encodePathSegment(text), encodeQueryComponent(text)).forEach { result ->
                val exception = result.exceptionOrNull()
                assertIs<UrlParseException>(exception)
                assertEquals(UrlProblem.NotUnicode, exception.problem)
            }
        }
    }
}
