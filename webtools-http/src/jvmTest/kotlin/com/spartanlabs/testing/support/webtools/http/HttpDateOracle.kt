package com.spartanlabs.testing.support.webtools.http

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * The `java.time` oracle of the HTTP-date differential: one strict, locale-free formatter per RFC 9110 form, used both as printer
 * and as strict parser. Every name table is explicit (`Map<Long, String>`), the year is `appendValue(YEAR, 4)` (never `yyyy` or
 * `uuuu`), the asctime day is `padNext(2).appendValue(DAY_OF_MONTH)` and the RFC 850 year is `appendValueReduced(YEAR, 2, 2, base)`
 * with `base = clamp(referenceYear, 50, 9949) - 49` (always at least 1). The `…Body` variants omit the weekday, so a text whose
 * weekday the parser under test ignores can be refereed without the oracle's cross-check. The oracle referees calendar arithmetic
 * and year windows only; it is never asked to referee a policy.
 */
internal object HttpDateOracle {
    val SHORT_DAYS: Map<Long, String> = mapOf(1L to "Mon", 2L to "Tue", 3L to "Wed", 4L to "Thu", 5L to "Fri", 6L to "Sat", 7L to "Sun")
    val LONG_DAYS: Map<Long, String> = mapOf(1L to "Monday", 2L to "Tuesday", 3L to "Wednesday", 4L to "Thursday", 5L to "Friday", 6L to "Saturday", 7L to "Sunday")
    val MONTHS: Map<Long, String> = mapOf(
        1L to "Jan", 2L to "Feb", 3L to "Mar", 4L to "Apr", 5L to "May", 6L to "Jun", 7L to "Jul", 8L to "Aug", 9L to "Sep", 10L to "Oct", 11L to "Nov", 12L to "Dec",
    )

    /** The first second of year 0 (0000-01-01T00:00:00Z). */
    const val MIN_SECOND: Long = -62_167_219_200L

    /** The last second of year 9999 (9999-12-31T23:59:59Z). */
    const val MAX_SECOND: Long = 253_402_300_799L

    private fun DateTimeFormatterBuilder.time(): DateTimeFormatterBuilder =
        appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(':').appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(':').appendValue(ChronoField.SECOND_OF_MINUTE, 2)

    private fun DateTimeFormatterBuilder.strict(): DateTimeFormatter =
        toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT).withZone(ZoneOffset.UTC)

    private fun start(): DateTimeFormatterBuilder = DateTimeFormatterBuilder().parseCaseInsensitive()

    private fun DateTimeFormatterBuilder.imfBody(): DateTimeFormatterBuilder =
        appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral(' ').appendText(ChronoField.MONTH_OF_YEAR, MONTHS).appendLiteral(' ')
            .appendValue(ChronoField.YEAR, 4).appendLiteral(' ').time().appendLiteral(" GMT")

    private fun DateTimeFormatterBuilder.rfc850Body(base: Int): DateTimeFormatterBuilder =
        appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral('-').appendText(ChronoField.MONTH_OF_YEAR, MONTHS).appendLiteral('-')
            .appendValueReduced(ChronoField.YEAR, 2, 2, base).appendLiteral(' ').time().appendLiteral(" GMT")

    private fun DateTimeFormatterBuilder.asctimeBody(): DateTimeFormatterBuilder =
        appendText(ChronoField.MONTH_OF_YEAR, MONTHS).appendLiteral(' ').padNext(2).appendValue(ChronoField.DAY_OF_MONTH).appendLiteral(' ')
            .time().appendLiteral(' ').appendValue(ChronoField.YEAR, 4)

    /** `Day, DD Mon YYYY HH:MM:SS GMT`. */
    val imfFixdate: DateTimeFormatter = start().appendText(ChronoField.DAY_OF_WEEK, SHORT_DAYS).appendLiteral(", ").imfBody().strict()

    /** `DD Mon YYYY HH:MM:SS GMT` (no weekday). */
    val imfFixdateBody: DateTimeFormatter = start().imfBody().strict()

    /** `Dow Mon D HH:MM:SS YYYY`, the day space-padded to two characters. */
    val asctime: DateTimeFormatter = start().appendText(ChronoField.DAY_OF_WEEK, SHORT_DAYS).appendLiteral(' ').asctimeBody().strict()

    /** `Mon D HH:MM:SS YYYY` (no weekday). */
    val asctimeBody: DateTimeFormatter = start().asctimeBody().strict()

    /** `Weekday, DD-Mon-YY HH:MM:SS GMT`, the year reduced into `[base, base + 99]`. */
    fun rfc850(base: Int): DateTimeFormatter = start().appendText(ChronoField.DAY_OF_WEEK, LONG_DAYS).appendLiteral(", ").rfc850Body(base).strict()

    /** `DD-Mon-YY HH:MM:SS GMT` (no weekday). */
    fun rfc850Body(base: Int): DateTimeFormatter = start().rfc850Body(base).strict()

    /** The proleptic ISO year of [epochMillis], as `java.time` reads it. */
    fun referenceYear(epochMillis: Long): Int = Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC).year

    /** The RFC 850 base year for a reference year: the window is `[referenceYear - 49, referenceYear + 50]` after the clamp, so the base is at least 1. */
    fun base(referenceYear: Int): Int = referenceYear.coerceIn(50, 9949) - 49

    /** Prints the instant [epochSecond] with [formatter]. */
    fun print(formatter: DateTimeFormatter, epochSecond: Long): String = LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC).format(formatter)

    /** Parses [text] strictly with [formatter]: the instant in epoch milliseconds, or `null` when the oracle rejects the text. */
    fun parseStrict(formatter: DateTimeFormatter, text: String): Long? = try {
        LocalDateTime.parse(text, formatter).toEpochSecond(ZoneOffset.UTC) * 1_000L
    } catch (e: DateTimeParseException) {
        null
    }

    /** [text] with every character outside printable ASCII shown as `\uXXXX`, for failure messages. */
    fun visible(text: String): String = buildString {
        text.forEach { c -> if (c in ' '..'~') append(c) else append("\\u").append(c.code.toString(16).uppercase().padStart(4, '0')) }
    }
}
