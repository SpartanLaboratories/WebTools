package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpSyntax
import com.spartanlabs.webtools.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Level 4a - header values: the container rule (the research table, with the stored trimmed values),
 * a sweep over U+0000..U+FFFF in `"a" + c + "b"` for the container rule (CR, LF, NUL refused) and for
 * the sendable rule (iff `c == '\t'` or `c in ' '..'~'`), and the `sanitizeReceivedValue` rows.
 */
class HttpHeaderValueDeterministicTest {

    private val url = Url.parse("http://h/").getOrThrow()

    private fun stored(value: String): String? = try {
        HttpHeaders { add("X", value) }["X"]
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun sendable(value: String): Boolean = try {
        HttpRequest(HttpMethod.Get, url) { headers = HttpHeaders { add("X", value) } }
        true
    } catch (e: IllegalArgumentException) {
        false
    }

    @Test
    fun `the container rows are stored trimmed of SP and HTAB`() {
        listOf("", "v", "a b", "a\tb", "café", "日本語", "a\u007Fb", "a\u0001b", "\u212A", " v").forEach {   // \u212A is KELVIN SIGN
            assertEquals(HttpSyntax.trimOws(it), stored(it), it)
        }
        assertEquals("v", stored(" v "))
        assertEquals("v", stored("\t v \t"))
        assertEquals("", stored("   "))
        listOf("a\nb", "a\rb", "a\u0000b", "v\r\n", "\nv").forEach { assertEquals(null, stored(it), it) }
        assertEquals("Value of header 'X' must not contain CR, LF or NUL", assertFailsWith<IllegalArgumentException> { HttpHeaders { add("X", "a\nb") } }.message)
    }

    @Test
    fun `the sweep for the container rule and the sendable rule`() {
        for (code in 0..0xFFFF) {
            val c = code.toChar()
            val value = "a" + c + "b"
            val label = "character U+${code.toString(16).padStart(4, '0')}"
            val containerAccepts = c != '\r' && c != '\n' && c != '\u0000'
            assertEquals(containerAccepts, stored(value) != null, label)
            assertEquals(containerAccepts, HttpSyntax.isFieldValue(value), label)
            val sendableExpected = c == '\t' || c in ' '..'~'
            assertEquals(sendableExpected, HttpSyntax.isSendableValue(value), label)
            if (containerAccepts) assertEquals(sendableExpected, sendable(value), label)
        }
    }

    @Test
    fun `sanitizeReceivedValue replaces CR LF and NUL with SP, then trims`() {
        assertEquals("a  b", HttpSyntax.sanitizeReceivedValue("a\r\nb"))
        assertEquals("a b", HttpSyntax.sanitizeReceivedValue("a\u0000b"))
        assertEquals("v", HttpSyntax.sanitizeReceivedValue(" v "))
        assertEquals("v", HttpSyntax.sanitizeReceivedValue("\nv\r"))
        assertEquals("", HttpSyntax.sanitizeReceivedValue("\r\n"))
        assertEquals("café", HttpSyntax.sanitizeReceivedValue("café"))
    }
}
