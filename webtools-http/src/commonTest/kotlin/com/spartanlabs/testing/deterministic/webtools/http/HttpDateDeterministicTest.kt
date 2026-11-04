package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.HttpDate
import com.spartanlabs.webtools.http.HttpDateParseException
import com.spartanlabs.webtools.http.HttpDateProblem
import com.spartanlabs.webtools.http.RetryAfter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Level 4a - the HTTP-date table over `HttpDate.parse(text, reference)`: the three RFC 9110 examples, the epoch, the year
 * boundaries `0000` and `9999`, leap days, leap seconds read linearly, the three liberties (ASCII case, SP/HTAB padding, a
 * disagreeing weekday), the two-digit-year rule at two references and at the `Long` extremes, and every rejection as `Format`,
 * `Range` or `Empty` with a message that never echoes the input. A sweep over U+0000..U+FFFF in `"1" + c + "2"` through
 * `RetryAfter` accepts only an ASCII digit.
 */
class HttpDateDeterministicTest {

    private val now = 946_681_200_000L            // 1999-12-31T23:00:00Z
    private val ref26 = 1_793_707_200_000L        // 2026-11-03T12:00:00Z
    private val rfcExample = 784_111_777_000L     // 1994-11-06T08:49:37Z

    private fun parse(text: String, reference: Long = ref26): Long = HttpDate.parse(text, reference).getOrThrow()

    private fun problemOf(text: String, reference: Long = ref26): HttpDateProblem {
        val result = HttpDate.parse(text, reference)
        val exception = assertIs<HttpDateParseException>(result.exceptionOrNull(), "expected a failure for '$text'")
        assertEquals(exception.problem.message, exception.message)
        if (text.isNotEmpty()) assertFalse(text in exception.message!!, "the message echoes the input '$text'")
        return exception.problem
    }

    @Test
    fun `the three RFC 9110 example forms give one instant`() {
        assertEquals(rfcExample, parse("Sun, 06 Nov 1994 08:49:37 GMT"))
        assertEquals(rfcExample, parse("Sunday, 06-Nov-94 08:49:37 GMT"))
        assertEquals(rfcExample, parse("Sun Nov  6 08:49:37 1994"))
        assertEquals(rfcExample, parse("Sunday, 06-Nov-94 08:49:37 GMT", now))
    }

    @Test
    fun `known instants and the year boundaries`() {
        assertEquals(0L, parse("Thu, 01 Jan 1970 00:00:00 GMT"))
        assertEquals(1_445_412_480_000L, parse("Wed, 21 Oct 2015 07:28:00 GMT"))
        assertEquals(-62_167_219_200_000L, parse("Sat, 01 Jan 0000 00:00:00 GMT"))
        assertEquals(253_402_300_799_000L, parse("Fri, 31 Dec 9999 23:59:59 GMT"))
    }

    @Test
    fun `leap days are accepted in leap years and refused otherwise`() {
        assertEquals(951_782_400_000L, parse("Tue, 29 Feb 2000 00:00:00 GMT"))
        assertEquals(1_709_164_800_000L, parse("Thu, 29 Feb 2024 00:00:00 GMT"))
        assertEquals(HttpDateProblem.Range, problemOf("Thu, 29 Feb 1900 00:00:00 GMT"))
    }

    @Test
    fun `second 60 is read linearly as the next minute`() {
        assertEquals(parse("Sat, 01 Jan 2000 00:00:00 GMT"), parse("Fri, 31 Dec 1999 23:59:60 GMT"))
        assertEquals(parse("Sun, 06 Nov 1994 08:50:00 GMT"), parse("Sun, 06 Nov 1994 08:49:60 GMT"))
    }

    @Test
    fun `the three liberties - ASCII case, padding and a disagreeing weekday`() {
        assertEquals(rfcExample, parse("sun, 06 nov 1994 08:49:37 gmt"))
        assertEquals(rfcExample, parse("SUN, 06 NOV 1994 08:49:37 GMT"))
        assertEquals(rfcExample, parse("sunday, 06-nov-94 08:49:37 gmt"))
        assertEquals(rfcExample, parse("SUN NOV  6 08:49:37 1994"))
        assertEquals(rfcExample, parse("\t Sun, 06 Nov 1994 08:49:37 GMT \t"))
        assertEquals(rfcExample, parse("  Sun Nov  6 08:49:37 1994  "))
        assertEquals(rfcExample, parse("Mon, 06 Nov 1994 08:49:37 GMT"))
        assertEquals(rfcExample, parse("Monday, 06-Nov-94 08:49:37 GMT"))
        assertEquals(rfcExample, parse("Mon Nov  6 08:49:37 1994"))
    }

    @Test
    fun `two-digit years resolve to the greatest year not after the reference year plus 50`() {
        assertEquals(parse("Fri, 06 Nov 2076 08:49:37 GMT"), parse("Friday, 06-Nov-76 08:49:37 GMT", ref26))
        assertEquals(parse("Sun, 06 Nov 1977 08:49:37 GMT"), parse("Sunday, 06-Nov-77 08:49:37 GMT", ref26))
        assertEquals(parse("Mon, 06 Nov 2000 08:49:37 GMT"), parse("Monday, 06-Nov-00 08:49:37 GMT", ref26))
        assertEquals(parse("Fri, 06 Nov 2026 08:49:37 GMT"), parse("Friday, 06-Nov-26 08:49:37 GMT", ref26))
        assertEquals(parse("Sat, 06 Nov 1999 08:49:37 GMT"), parse("Saturday, 06-Nov-99 08:49:37 GMT", ref26))
        assertEquals(parse("Sat, 06 Nov 2049 08:49:37 GMT"), parse("Saturday, 06-Nov-49 08:49:37 GMT", now))
        assertEquals(parse("Mon, 06 Nov 1950 08:49:37 GMT"), parse("Monday, 06-Nov-50 08:49:37 GMT", now))
        // The year-granular edge: 2076-12-31 is more than 50 years after the reference instant, but the rule compares years.
        assertEquals(parse("Thu, 31 Dec 2076 23:59:59 GMT"), parse("Thursday, 31-Dec-76 23:59:59 GMT", ref26))
        assertEquals(parse("Tue, 29 Feb 2000 00:00:00 GMT"), parse("Tuesday, 29-Feb-00 00:00:00 GMT", ref26))
        assertEquals(parse("Tue, 29 Feb 2000 00:00:00 GMT"), parse("Tuesday, 29-Feb-00 00:00:00 GMT", now))
        assertEquals(2076L, HttpDate.resolveTwoDigitYear(76, ref26))
        assertEquals(1977L, HttpDate.resolveTwoDigitYear(77, ref26))
        assertEquals(2049L, HttpDate.resolveTwoDigitYear(49, now))
        assertEquals(1950L, HttpDate.resolveTwoDigitYear(50, now))
    }

    @Test
    fun `the Long extremes as a reference give years 0094 and 9994 with no overflow`() {
        assertEquals(94L, HttpDate.resolveTwoDigitYear(94, Long.MIN_VALUE))
        assertEquals(9994L, HttpDate.resolveTwoDigitYear(94, Long.MAX_VALUE))
        assertEquals(parse("Sun, 06 Nov 0094 08:49:37 GMT"), parse("Sunday, 06-Nov-94 08:49:37 GMT", Long.MIN_VALUE))
        assertEquals(parse("Sun, 06 Nov 9994 08:49:37 GMT"), parse("Sunday, 06-Nov-94 08:49:37 GMT", Long.MAX_VALUE))
        assertEquals(1L, HttpDate.resolveTwoDigitYear(1, Long.MIN_VALUE))
        assertEquals(9999L, HttpDate.resolveTwoDigitYear(99, Long.MAX_VALUE))
    }

    @Test
    fun `every rejection is Format, Range or Empty`() {
        listOf(
            "Sun, 06 Nov 1994 08:49:37 UTC",
            "Sun, 06 Nov 1994 08:49:37 +0000",
            "Sun, 06 Nov 1994 08:49:37",
            "Sun, 06 Nov 1994 08:49:37 GMT x",
            "Sun, 6 Nov 1994 08:49:37 GMT",
            "Sun, 06 Nov 1994 08:49:37 GMT",
            "Sun,  06 Nov 1994 08:49:37 GMT",
            "Sun,\t06 Nov 1994 08:49:37 GMT",
            "Sun, 06 Sept 1994 08:49:37 GMT",
            "Sun, 06 November 1994 08:49:37 GMT",
            "Sun, 06 Nov 94 08:49:37 GMT",
            "Sun, 06 Nov 01994 08:49:37 GMT",
            "Sunday, 06-Nov-1994 08:49:37 GMT",
            "Sunday, 06-Nov-994 08:49:37 GMT",
            "Sunday, 06-Nov-4 08:49:37 GMT",
            "Sun, 06-Nov-94 08:49:37 GMT",
            "Sun Nov 6 08:49:37 1994",
            "Sun Nov  06 08:49:37 1994",
            "Sun Nov  6 08:49:37 1994 GMT",
            "Sun Nov  6 08:49:37 94",
            "Sun",
            "Sun,",
            "Xyz, 06 Nov 1994 08:49:37 GMT",
            "Sun, 06 Xyz 1994 08:49:37 GMT",
            "Sun, 06 Nov 1994 08:49:3x GMT",
            "Sun, 06 Nov 1994 08-49-37 GMT",
            "1994-11-06T08:49:37Z",
            "120",
        ).forEach { assertEquals(HttpDateProblem.Format, problemOf(it), it) }
        listOf(
            "Mon, 31 Feb 1994 08:49:37 GMT",
            "Sun, 00 Nov 1994 08:49:37 GMT",
            "Sun, 31 Nov 1994 08:49:37 GMT",
            "Sun, 06 Nov 1994 24:00:00 GMT",
            "Sun, 06 Nov 1994 08:60:00 GMT",
            "Sun, 06 Nov 1994 08:49:61 GMT",
            "Sunday, 31-Feb-94 08:49:37 GMT",
            "Sun Feb 30 08:49:37 1994",
        ).forEach { assertEquals(HttpDateProblem.Range, problemOf(it), it) }
        listOf("", "   ", "\t", " \t ").forEach { assertEquals(HttpDateProblem.Empty, problemOf(it), "'$it'") }
    }

    @Test
    fun `the calendar helpers`() {
        assertTrue(HttpDate.isLeap(2000))
        assertTrue(HttpDate.isLeap(2024))
        assertFalse(HttpDate.isLeap(1900))
        assertFalse(HttpDate.isLeap(2023))
        assertEquals(0L, HttpDate.daysFromCivil(1970, 1, 1))
        assertEquals(-719_528L, HttpDate.daysFromCivil(0, 1, 1))
        assertEquals(1970L, HttpDate.yearFromDays(0))
        assertEquals(1969L, HttpDate.yearFromDays(-1))
        assertEquals(0L, HttpDate.yearFromDays(-719_528L))
        assertEquals(9999L, HttpDate.yearFromDays(253_402_300_799L / 86_400L))
        for (year in listOf(0L, 1L, 1900L, 1999L, 2000L, 2024L, 2100L, 9999L)) {
            assertEquals(year, HttpDate.yearFromDays(HttpDate.daysFromCivil(year, 1, 1)), "year $year, 1 Jan")
            assertEquals(year, HttpDate.yearFromDays(HttpDate.daysFromCivil(year, 12, 31)), "year $year, 31 Dec")
        }
    }

    @Test
    fun `the sweep through RetryAfter accepts only an ASCII digit between two digits`() {
        for (code in 0..0xFFFF) {
            val c = code.toChar()
            val accepted = RetryAfter.parse("1" + c + "2", now).isSuccess
            assertEquals(c in '0'..'9', accepted, "character U+${code.toString(16).padStart(4, '0')}")
        }
    }
}
