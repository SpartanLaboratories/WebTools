package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpSyntax
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 4a - the ASCII-only fold (`get("Key")` with U+212A does not find `key`; `ı` does not find `i`;
 * `CONTENT-TYPE` finds `Content-Type`; the empty lookup is `null`), `trimOws`, and `isCredentialName`
 * in any case.
 */
class HttpSyntaxDeterministicTest {

    @Test
    fun `lookup folds ASCII letters only`() {
        val headers = HttpHeaders { add("key", "1"); add("i", "2"); add("Content-Type", "3") }
        assertNull(headers["\u212Aey"])   // \u212A is KELVIN SIGN, which Kotlin ignoreCase would fold to k
        assertNull(headers["ı"])
        assertNull(headers["İ"])
        assertEquals("3", headers["CONTENT-TYPE"])
        assertEquals("3", headers["content-type"])
        assertEquals("1", headers["KEY"])
        assertNull(headers[""])
        assertTrue(HttpSyntax.equalsName("Content-Type", "CONTENT-TYPE"))
        assertFalse(HttpSyntax.equalsName("key", "\u212Aey"))
        assertFalse(HttpSyntax.equalsName("a", "ab"))
    }

    @Test
    fun `trimOws removes SP and HTAB at the ends and nothing else`() {
        assertEquals("v", HttpSyntax.trimOws(" \t v\t "))
        assertEquals("a b", HttpSyntax.trimOws("a b"))
        assertEquals("", HttpSyntax.trimOws("  \t"))
        assertEquals("\u00A0v\u00A0", HttpSyntax.trimOws("\u00A0v\u00A0"))   // U+00A0 (NBSP) is not OWS, so it stays
        assertEquals("\nv\n", HttpSyntax.trimOws("\nv\n"))
    }

    @Test
    fun `isCredentialName matches the four names in any case`() {
        listOf("Authorization", "AUTHORIZATION", "proxy-authorization", "Cookie", "SET-COOKIE", "set-cookie").forEach {
            assertTrue(HttpSyntax.isCredentialName(it), it)
        }
        listOf("Cookie2", "X-Authorization", "Authorization2", "", "\u212Aookie").forEach {   // \u212A is KELVIN SIGN
            assertFalse(HttpSyntax.isCredentialName(it), it)
        }
    }

    @Test
    fun `asciiLower folds A to Z only`() {
        assertEquals('a', HttpSyntax.asciiLower('A'))
        assertEquals('z', HttpSyntax.asciiLower('Z'))
        assertEquals('a', HttpSyntax.asciiLower('a'))
        assertEquals('1', HttpSyntax.asciiLower('1'))
        assertEquals('\u212A', HttpSyntax.asciiLower('\u212A'))   // KELVIN SIGN is not an ASCII letter
        assertEquals('É', HttpSyntax.asciiLower('É'))
    }
}
