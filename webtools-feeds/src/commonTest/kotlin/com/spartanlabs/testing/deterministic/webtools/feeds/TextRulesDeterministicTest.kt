package com.spartanlabs.testing.deterministic.webtools.feeds

import com.spartanlabs.webtools.feeds.MAX_EPOCH_MILLIS
import com.spartanlabs.webtools.feeds.MIN_EPOCH_MILLIS
import com.spartanlabs.webtools.feeds.cleanText
import com.spartanlabs.webtools.feeds.enclosureLength
import com.spartanlabs.webtools.feeds.inSupportedYears
import com.spartanlabs.webtools.feeds.trimAscii
import com.spartanlabs.webtools.feeds.truncate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 4a - the text rules: `trimAscii` removes only TAB, LF, FF, CR and SPACE at the ends; `truncate` never leaves a high
 * surrogate at the cut; `cleanText` trims, treats blank as absent and cuts; `enclosureLength` keeps a positive value only;
 * `inSupportedYears` holds at the year 1 and year 9999 boundaries.
 */
class TextRulesDeterministicTest {

    @Test
    fun `trimAscii removes only TAB, LF, FF, CR and SPACE at the ends`() {
        assertEquals("x y", trimAscii(" \t\r\n\u000Cx y\u000C "))
        assertEquals(" x ", trimAscii(" x "))
        assertEquals("\u000Bx", trimAscii("\u000Bx"), "a vertical tab is not ASCII white space here")
        assertEquals("", trimAscii("   "))
        assertEquals("x", trimAscii("x"))
    }

    @Test
    fun `truncate never leaves a high surrogate at the cut`() {
        val s = "ab😀cd"
        assertEquals("ab", truncate(s, 2))
        assertEquals("ab", truncate(s, 3))
        assertEquals("ab😀", truncate(s, 4))
        assertEquals(s, truncate(s, 6))
        assertEquals(s, truncate(s, 7))
        assertEquals("", truncate("😀", 1))
    }

    @Test
    fun `cleanText trims, treats blank as absent and cuts`() {
        assertNull(cleanText(null, 10))
        assertNull(cleanText("", 10))
        assertNull(cleanText(" \t\n", 10))
        assertEquals("abc", cleanText("  abc  ", 10))
        assertEquals("ab", cleanText("  abcdef", 2))
        assertNull(cleanText("😀x", 1))
        assertEquals(" ", cleanText(" ", 10), "a no-break space is not blank to the text rule")
    }

    @Test
    fun `enclosureLength keeps a positive value and treats 0, negatives and absent as unknown`() {
        assertEquals(12_216_320L, enclosureLength(12_216_320L))
        assertEquals(Long.MAX_VALUE, enclosureLength(Long.MAX_VALUE))
        assertEquals(1L, enclosureLength(1L))
        assertNull(enclosureLength(0L))
        assertNull(enclosureLength(-5L))
        assertNull(enclosureLength(Long.MIN_VALUE))
    }

    @Test
    fun `inSupportedYears holds at the year 1 and year 9999 boundaries`() {
        assertEquals(-62_135_596_800_000L, MIN_EPOCH_MILLIS)
        assertEquals(253_402_300_799_999L, MAX_EPOCH_MILLIS)
        assertTrue(inSupportedYears(MIN_EPOCH_MILLIS))
        assertTrue(inSupportedYears(MAX_EPOCH_MILLIS))
        assertTrue(inSupportedYears(0L))
        assertFalse(inSupportedYears(MIN_EPOCH_MILLIS - 1))
        assertFalse(inSupportedYears(MAX_EPOCH_MILLIS + 1))
        assertFalse(inSupportedYears(Long.MIN_VALUE))
        assertFalse(inSupportedYears(Long.MAX_VALUE))
    }
}
