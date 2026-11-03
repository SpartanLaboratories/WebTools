package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.isValidUserAgent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Level 4a - the `isValidUserAgent` table: every character U+0020..U+007E inside a name is accepted, every other BMP code point at index 1 is
 * refused, a leading or trailing space is refused, one character is accepted.
 */
class UserAgentDeterministicTest {

    @Test
    fun `every visible ASCII character and the space are accepted inside a name`() {
        (0x20..0x7E).forEach { code -> assertTrue(isValidUserAgent("a${code.toChar()}b"), "U+%04X".format(code)) }
    }

    @Test
    fun `every other BMP code point at index 1 is refused`() {
        (0..0xFFFF).filter { it !in 0x20..0x7E }.forEach { code -> assertFalse(isValidUserAgent("a${code.toChar()}b"), "U+%04X".format(code)) }
    }

    @Test
    fun `a leading or trailing space is refused and one character is accepted`() {
        assertFalse(isValidUserAgent(" a"))
        assertFalse(isValidUserAgent("a "))
        assertFalse(isValidUserAgent(" "))
        assertFalse(isValidUserAgent(""))
        assertTrue(isValidUserAgent("a"))
        assertTrue(isValidUserAgent("~"))
    }
}
