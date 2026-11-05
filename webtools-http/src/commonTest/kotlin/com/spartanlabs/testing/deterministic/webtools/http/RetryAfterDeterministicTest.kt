package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.HttpDateParseException
import com.spartanlabs.webtools.http.HttpDateProblem
import com.spartanlabs.webtools.http.RetryAfter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Level 4a - the `Retry-After` table over both `RetryAfter.parse` overloads: `delay-seconds` with leading zeros, padding and the
 * saturation cap; dates ahead, equal and behind the reference in all three forms; a leap second; the server-date overload
 * measuring against the response's `Date` rather than the local clock; every rejection as `Empty` or `Format`; a success is
 * never negative.
 */
class RetryAfterDeterministicTest {

    private val now = 946_681_200_000L                  // 1999-12-31T23:00:00Z
    private val serverDate = 946_681_800_000L           // 1999-12-31T23:10:00Z
    private val cap = 253_402_300_799L.seconds

    private fun both(value: String, reference: Long = now): Duration {
        val local = RetryAfter.parse(value, reference).getOrThrow()
        val server = RetryAfter.parse(value, 0L, reference).getOrThrow()
        assertEquals(local, server, "the two overloads disagree on '$value'")
        assertFalse(local.isNegative(), "a negative delay for '$value'")
        return local
    }

    private fun problemOf(value: String): HttpDateProblem {
        val local = assertIs<HttpDateParseException>(RetryAfter.parse(value, now).exceptionOrNull(), "expected a failure for '$value'")
        val server = assertIs<HttpDateParseException>(RetryAfter.parse(value, now, serverDate).exceptionOrNull(), "expected a failure for '$value'")
        assertEquals(local.problem, server.problem)
        assertEquals(local.problem.message, local.message)
        if (value.isNotEmpty()) assertFalse(value in local.message!!, "the message echoes '$value'")
        return local.problem
    }

    @Test
    fun `delay-seconds with leading zeros and padding`() {
        assertEquals(120.seconds, both("120"))
        assertEquals(Duration.ZERO, both("0"))
        assertEquals(Duration.ZERO, both("000"))
        assertEquals(7.seconds, both("007"))
        assertEquals(120.seconds, both("\t120\t"))
        assertEquals(120.seconds, both(" 120 "))
    }

    @Test
    fun `delay-seconds saturates at the cap`() {
        assertEquals(cap, both("253402300799"))
        assertEquals(cap, both("253402300800"))
        assertEquals(cap, both("99999999999999999999999"))
        assertEquals(cap, both("9223372036854775807"))
        assertEquals(cap, both("9223372036854775808"))
        assertEquals(253_402_300_798L.seconds, both("253402300798"))
    }

    @Test
    fun `a date is the delay from the reference, never negative`() {
        assertEquals(3_600.seconds, both("Sat, 01 Jan 2000 00:00:00 GMT"))
        assertEquals(3_599.seconds, both("Fri, 31 Dec 1999 23:59:59 GMT"))
        assertEquals(1.seconds, both("Fri, 31 Dec 1999 23:00:01 GMT"))
        assertEquals(Duration.ZERO, both("Fri, 31 Dec 1999 23:00:00 GMT"))
        assertEquals(Duration.ZERO, both("Fri, 31 Dec 1999 22:00:00 GMT"))
        assertEquals(Duration.ZERO, both("Sun, 06 Nov 1994 08:49:37 GMT"))
    }

    @Test
    fun `the three forms give the same delay`() {
        assertEquals(3_600.seconds, both("Sat, 01 Jan 2000 00:00:00 GMT"))
        assertEquals(3_600.seconds, both("Saturday, 01-Jan-00 00:00:00 GMT"))
        assertEquals(3_600.seconds, both("Sat Jan  1 00:00:00 2000"))
        assertEquals(3_600.seconds, both("Fri, 31 Dec 1999 23:59:60 GMT"))
    }

    @Test
    fun `the last expressible date at the epoch is the cap`() {
        assertEquals(cap, both("Fri, 31 Dec 9999 23:59:59 GMT", reference = 0L))
        assertEquals(cap, RetryAfter.parse("Fri, 31 Dec 9999 23:59:59 GMT", Long.MIN_VALUE).getOrThrow())
    }

    @Test
    fun `the server-date overload measures against the response's Date and not the local clock`() {
        assertEquals(3_000.seconds, RetryAfter.parse("Sat, 01 Jan 2000 00:00:00 GMT", now, serverDate).getOrThrow())
        assertEquals(3_600.seconds, RetryAfter.parse("Sat, 01 Jan 2000 00:00:00 GMT", now).getOrThrow())
        assertEquals(Duration.ZERO, RetryAfter.parse("Fri, 31 Dec 1999 23:05:00 GMT", now, serverDate).getOrThrow())
        assertEquals(120.seconds, RetryAfter.parse("120", now, serverDate).getOrThrow())
        assertEquals(252_455_618_999L.seconds, RetryAfter.parse("Fri, 31 Dec 9999 23:59:59 GMT", now, serverDate).getOrThrow())
    }

    @Test
    fun `an empty value is Empty`() {
        assertEquals(HttpDateProblem.Empty, problemOf(""))
        assertEquals(HttpDateProblem.Empty, problemOf("  "))
        assertEquals(HttpDateProblem.Empty, problemOf("\t"))
    }

    @Test
    fun `everything else that is not a number or a date is Format`() {
        listOf(
            "-1", "+1", "1.5", "1e3", "12 s", "12s", "0x10", "120, 60", "120\n", "\n120", "٣", "１２",
            "Sun, 06 Nov 1994 08:49:37 UTC", "tomorrow", "Sun, 06 Nov 1994 08:49:37 GMT, Sun, 06 Nov 1994 08:49:37 GMT",
            "99999999999999999999999x", "1 2", "٣٠", "１２０", "Infinity", "NaN",
        ).forEach { assertEquals(HttpDateProblem.Format, problemOf(it), it) }
        assertEquals(HttpDateProblem.Range, problemOf("Sun, 31 Feb 1994 08:49:37 GMT"))
    }
}
