package com.spartanlabs.webtools.http

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Reads a `Retry-After` header value: a number of seconds (`delay-seconds`) or an HTTP-date in any of RFC 9110's three forms.
 *
 * **Stable Core.** The grammar is RFC 9110's with three liberties: day and month names and `GMT` in any ASCII case; SP and HTAB
 * around the value trimmed; a weekday that disagrees with the date ignored. Other zones (`UTC`, `+0000`), unpadded days, a
 * four-digit RFC 850 year, signs, fractions, units and non-ASCII digits are rejected. A number saturates at 253,402,300,799
 * seconds (the instant 9999-12-31T23:59:59Z, the largest delay a date can express), so a huge value is a very long wait and
 * `now + delay` cannot overflow. A date in the past is [Duration.ZERO]; the result is never negative. The pause a value causes
 * is capped elsewhere, by `maxPause`. A failure's message is constant and never echoes the value.
 */
public object RetryAfter {
    private const val CAP_SECONDS: Long = 253_402_300_799L
    private const val CAP_MILLIS: Long = CAP_SECONDS * 1_000L

    /**
     * Reads [value] against the local clock: a date's delay is the date minus [nowEpochMillis].
     *
     * @param value the header value.
     * @param nowEpochMillis the local clock, in epoch milliseconds.
     * @return the delay, or an `IllegalArgumentException` whose constant message says whether the value was empty, malformed or
     *   out of range; it never echoes the value.
     */
    public fun parse(value: String, nowEpochMillis: Long): Result<Duration> = parse(value, nowEpochMillis, nowEpochMillis)

    /**
     * Reads [value] against the server's own clock: a date's delay is the date minus [serverDateEpochMillis] (the response's
     * `Date`), so a skewed local clock cancels out of a pair of server readings.
     *
     * @param value the header value.
     * @param nowEpochMillis the local clock, in epoch milliseconds; not read by this overload (the server's `Date` replaces it),
     *   accepted so both overloads take the same leading arguments.
     * @param serverDateEpochMillis the response's `Date`, in epoch milliseconds.
     * @return the delay, or an `IllegalArgumentException` for text that is not a `Retry-After` value.
     */
    @Suppress("UNUSED_PARAMETER")
    public fun parse(value: String, nowEpochMillis: Long, serverDateEpochMillis: Long): Result<Duration> {
        val text = HttpSyntax.trimOws(value)
        if (text.isEmpty()) return httpDateFailure(HttpDateProblem.Empty)
        // Every date form starts with a letter, so a leading ASCII digit selects delay-seconds without backtracking.
        if (text[0] in '0'..'9') return seconds(text)
        return HttpDate.parse(text, serverDateEpochMillis).map { dateMillis -> delayUntil(dateMillis, serverDateEpochMillis) }
    }

    // delay-seconds: every character is validated even after the cap is reached.
    private fun seconds(text: String): Result<Duration> {
        var acc = 0L
        for (c in text) {
            if (c !in '0'..'9') return httpDateFailure(HttpDateProblem.Format)
            acc = minOf(acc * 10 + (c - '0'), CAP_SECONDS)
        }
        return Result.success(acc.seconds)
    }

    private fun delayUntil(dateMillis: Long, referenceMillis: Long): Duration {
        if (dateMillis <= referenceMillis) return Duration.ZERO
        val d = dateMillis - referenceMillis                     // a wrap to negative means the true difference exceeds Long
        return (if (d < 0) CAP_MILLIS else minOf(d, CAP_MILLIS)).milliseconds
    }
}
