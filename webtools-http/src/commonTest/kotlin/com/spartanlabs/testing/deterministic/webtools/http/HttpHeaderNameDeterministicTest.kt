package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpSyntax
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Level 4a - header names: a sweep over U+0000..U+FFFF (a one-character name is accepted by the
 * builder iff it is one of the 77 `tchar`s), the fixed rows of the research, the empty name, and the
 * messages.
 */
class HttpHeaderNameDeterministicTest {

    private val tchars: Set<Char> = (('A'..'Z') + ('a'..'z') + ('0'..'9') + "!#\$%&'*+-.^_`|~".toList()).toSet()

    private fun accepts(name: String): Boolean = try {
        HttpHeaders { add(name, "v") }
        true
    } catch (e: IllegalArgumentException) {
        false
    }

    @Test
    fun `a one-character name is accepted iff it is a tchar`() {
        assertEquals(77, tchars.size)
        var accepted = 0
        for (code in 0..0xFFFF) {
            val c = code.toChar()
            val expected = c in tchars
            assertEquals(expected, accepts(c.toString()), "character U+${code.toString(16).padStart(4, '0')}")
            assertEquals(expected, HttpSyntax.isTokenChar(c))
            if (expected) accepted++
        }
        assertEquals(77, accepted)
    }

    @Test
    fun `every token name is accepted and every non-token name is refused`() {
        listOf("X-Test", "a", "A_b-c.d~e", "123", "!#\$%&'*+-.^_`|~").forEach { assertTrue(accepts(it), it) }
        listOf(
            "", "a b", "a:b", ":path", "a(b", "a/b", "a\"b", "a,b", "a;b", "a<b", "a=b", "a>b", "a?b", "a@b", "a\\b", "a]b", "a[b",
            "a{b", "a}b", "é", "\u212Aey", "a\tb", "a\u007Fb", "a\u0000b", "a\r\nb", "Name ",   // \u212A is KELVIN SIGN
        ).forEach { assertTrue(!accepts(it), it) }
    }

    @Test
    fun `a non-token name is refused with the constant message holding the first offending index, an empty name with its own`() {
        assertEquals("Header name must not be empty", assertFailsWith<IllegalArgumentException> { HttpHeaders { add("", "v") } }.message)
        assertEquals("Header name is not an RFC 9110 token (first offending character at index 1)", assertFailsWith<IllegalArgumentException> { HttpHeaders { add("a b", "v") } }.message)
        assertEquals("Header name is not an RFC 9110 token (first offending character at index 0)", assertFailsWith<IllegalArgumentException> { HttpHeaders { add(":path", "v") } }.message)
        assertEquals("Header name is not an RFC 9110 token (first offending character at index 4)", assertFailsWith<IllegalArgumentException> { HttpHeaders { set("Name ", "v") } }.message)
        assertEquals(-1, HttpSyntax.firstNonTokenIndex("X-Test"))
        assertEquals(-1, HttpSyntax.firstNonTokenIndex(""))
        assertEquals(false, HttpSyntax.isToken(""))
    }
}
