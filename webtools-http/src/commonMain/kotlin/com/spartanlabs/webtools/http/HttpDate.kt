package com.spartanlabs.webtools.http

/** The kinds of failure the HTTP-date and `Retry-After` parsers report, each with its constant message. */
internal enum class HttpDateProblem(val message: String) {
    Empty("The value is empty."),
    Format("The value is neither a whole number of seconds nor an HTTP-date."),
    Range("The HTTP-date has a field outside its range or a day its month does not have."),
}

/** The failure `HttpDate.parse` and `RetryAfter.parse` return; its [problem] is what tests assert on. A message never carries input. */
internal class HttpDateParseException(val problem: HttpDateProblem) : IllegalArgumentException(problem.message)

internal fun <T> httpDateFailure(problem: HttpDateProblem): Result<T> = Result.failure(HttpDateParseException(problem))

/**
 * The HTTP-date parser of RFC 9110 §5.6.7 in common Kotlin: IMF-fixdate, the obsolete RFC 850 form and asctime, with three
 * liberties (names and `GMT` in any ASCII case, SP/HTAB trimmed around the value, a weekday that disagrees with the date
 * ignored). Pure; no `java.*`, no regex.
 */
internal object HttpDate {
    private val SHORT_DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val LONG_DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private class Hms(val h: Int, val m: Int, val s: Int)

    /**
     * Parses [text] as an HTTP-date.
     *
     * @param text the value; surrounding SP and HTAB are ignored.
     * @param referenceEpochMillis the instant a two-digit RFC 850 year is read against (any `Long` is accepted).
     * @return the instant in epoch milliseconds, or a [HttpDateParseException] (`Empty`, `Format`, `Range`).
     */
    fun parse(text: String, referenceEpochMillis: Long): Result<Long> {
        val s = HttpSyntax.trimOws(text)
        if (s.isEmpty()) return httpDateFailure(HttpDateProblem.Empty)
        // Dispatch needs no backtracking: the leading run of ASCII letters and the character after it name the form.
        var letters = 0
        while (letters < s.length && isAsciiLetter(s[letters])) letters++
        val after = if (letters < s.length) s[letters] else '\u0000'
        return when {
            letters == 3 && after == ',' -> imfFixdate(s)
            letters in 6..9 && after == ',' -> rfc850(s, letters, referenceEpochMillis)
            letters == 3 && after == ' ' -> asctime(s)
            else -> httpDateFailure(HttpDateProblem.Format)
        }
    }

    // Day, DD Mon YYYY HH:MM:SS GMT — 29 characters. The weekday only has to be a day name.
    // `shape` is a short-circuit chain that reads a separator only behind the length check; the field readers bounds-check themselves.
    private fun imfFixdate(s: String): Result<Long> {
        val shape = s.length == 29 && dayNameIndex(s, 0, SHORT_DAYS) >= 0 && s[4] == ' ' && s[7] == ' ' && s[11] == ' ' && s[16] == ' ' &&
            s[25] == ' ' && matches(s, 26, "GMT")
        val day = digits(s, 5, 2)
        val month = month(s, 8)
        val year = digits(s, 12, 4)
        val time = time(s, 17)
        if (!shape || day < 0 || month < 0 || year < 0 || time == null) return httpDateFailure(HttpDateProblem.Format)
        return finish(year.toLong(), month, day, time)
    }

    // Weekday, DD-Mon-YY HH:MM:SS GMT — the name is L letters, then 24 more characters.
    private fun rfc850(s: String, nameLength: Int, reference: Long): Result<Long> {
        val l = nameLength
        val shape = s.length == l + 24 && LONG_DAYS.any { it.length == l && matches(s, 0, it) } && s[l + 1] == ' ' && s[l + 4] == '-' && s[l + 8] == '-' &&
            s[l + 11] == ' ' && s[l + 20] == ' ' && matches(s, l + 21, "GMT")
        val day = digits(s, l + 2, 2)
        val month = month(s, l + 5)
        val yy = digits(s, l + 9, 2)
        val time = time(s, l + 12)
        if (!shape || day < 0 || month < 0 || yy < 0 || time == null) return httpDateFailure(HttpDateProblem.Format)
        return finish(resolveTwoDigitYear(yy, reference), month, day, time)
    }

    // Dow Mon D HH:MM:SS YYYY — 24 characters; the day is `06` or ` 6`.
    private fun asctime(s: String): Result<Long> {
        val shape = s.length == 24 && dayNameIndex(s, 0, SHORT_DAYS) >= 0 && s[3] == ' ' && s[7] == ' ' && s[10] == ' ' && s[19] == ' '
        val month = month(s, 4)
        val day = if (s.length > 9 && s[8] == ' ') digits(s, 9, 1) else digits(s, 8, 2)   // `Nov  6` (SP, one digit) or `Nov 06`; bounds-safe for a short text
        val year = digits(s, 20, 4)
        val time = time(s, 11)
        if (!shape || day < 0 || month < 0 || year < 0 || time == null) return httpDateFailure(HttpDateProblem.Format)
        return finish(year.toLong(), month, day, time)
    }

    private fun finish(year: Long, month: Int, day: Int, t: Hms): Result<Long> {
        if (day < 1 || day > daysInMonth(year, month) || t.h > 23 || t.m > 59 || t.s > 60) return httpDateFailure(HttpDateProblem.Range)
        // Second 60 is read linearly: hh:mm:60 lands on the next minute's :00, so a leap second never shortens a wait.
        val seconds = daysFromCivil(year, month, day) * 86_400L + t.h * 3_600L + t.m * 60L + t.s
        return Result.success(seconds * 1_000L)
    }

    private fun isAsciiLetter(c: Char): Boolean = c in 'A'..'Z' || c in 'a'..'z'

    // ASCII-only, allocation-free match of [name] at [from]; false when the text is too short.
    private fun matches(s: String, from: Int, name: String): Boolean =
        from >= 0 && from + name.length <= s.length && name.indices.all { HttpSyntax.asciiLower(s[from + it]) == HttpSyntax.asciiLower(name[it]) }

    private fun dayNameIndex(s: String, from: Int, names: List<String>): Int = names.indexOfFirst { matches(s, from, it) }

    // The month number 1..12 at [from], or -1.
    private fun month(s: String, from: Int): Int = MONTHS.indexOfFirst { matches(s, from, it) }.let { if (it < 0) -1 else it + 1 }

    // Exactly [n] ASCII digits at [from] as a number, or -1. Digits are compared as a range, never through a character-class predicate.
    private fun digits(s: String, from: Int, n: Int): Int {
        if (from < 0 || from + n > s.length) return -1
        var value = 0
        for (i in from until from + n) {
            val c = s[i]
            if (c !in '0'..'9') return -1
            value = value * 10 + (c - '0')
        }
        return value
    }

    // HH:MM:SS at [at], two digits each; null when the shape is wrong (the ranges are checked by finish).
    private fun time(s: String, at: Int): Hms? {
        if (at < 0 || at + 8 > s.length || s[at + 2] != ':' || s[at + 5] != ':') return null
        val h = digits(s, at, 2)
        val m = digits(s, at + 3, 2)
        val sec = digits(s, at + 6, 2)
        return if (h < 0 || m < 0 || sec < 0) null else Hms(h, m, sec)
    }

    /** Whether [y] is a leap year of the proleptic Gregorian calendar. */
    fun isLeap(y: Long): Boolean = (y % 4 == 0L && y % 100 != 0L) || y % 400 == 0L

    /** The days of month [m] (1..12) in year [y]. */
    fun daysInMonth(y: Long, m: Int): Int = when (m) { 2 -> if (isLeap(y)) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }

    /** Hinnant's `days_from_civil`: days since 1970-01-01 of the proleptic Gregorian date; no validation. */
    fun daysFromCivil(year: Long, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = y.floorDiv(400L)                                 // floor, so a negative year needs no branch
        val yoe = y - era * 400                                    // [0, 399]
        val doy = (153 * ((month + 9) % 12) + 2) / 5 + day - 1     // [0, 365]; March is month 0
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy            // [0, 146096]
        return era * 146_097 + doe - 719_468
    }

    /** Hinnant's `civil_from_days`, the year only. */
    fun yearFromDays(days: Long): Long {
        val z = days + 719_468
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        return yoe + era * 400 + (if (mp >= 10) 1 else 0)
    }

    /** RFC 9110 §5.6.7's two-digit-year rule against [referenceEpochMillis]: the greatest year with these last two digits not after `referenceYear + 50`. */
    fun resolveTwoDigitYear(yy: Int, referenceEpochMillis: Long): Long {
        // The clamp keeps every result in 1..9999 for any reference, Long.MIN_VALUE and Long.MAX_VALUE included.
        val referenceYear = yearFromDays(referenceEpochMillis.floorDiv(86_400_000L)).coerceIn(50L, 9949L)
        val limit = referenceYear + 50
        return limit - (limit - yy).mod(100L)
    }
}
