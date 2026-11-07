package com.spartanlabs.testing.deterministic.webtools.feeds

import com.spartanlabs.webtools.feeds.FeedValidators
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Level 4a - the `FeedValidators` rules as tables: the ETag table and the `Last-Modified` table, each input to the value
 * held or `null`.
 */
class FeedValidatorsDeterministicTest {

    @Test
    fun `the ETag table`() {
        val rows: List<Pair<String?, String?>> = listOf(
            "\"abc\"" to "\"abc\"",
            "W/\"abc\"" to "W/\"abc\"",
            "\"a\", \"b\"" to "\"a\", \"b\"",
            " \t\"abc\"\t " to "\"abc\"",
            "" to null,
            "   " to null,
            "\t" to null,
            "\"" + "a".repeat(254) + "\"" to "\"" + "a".repeat(254) + "\"",
            "\"" + "a".repeat(255) + "\"" to null,
            "\"a\tb\"" to null,
            "\"a\u007Fb\"" to null,
            "\"a\u0080b\"" to null,
            "\"中\"" to null,
            "*" to null,
            " * " to null,
            "\"*\"" to "\"*\"",
            null to null,
        )
        for ((input, expected) in rows) assertEquals(expected, FeedValidators(input, null).etag, "'$input'")
        assertEquals(256, ("\"" + "a".repeat(254) + "\"").length)
    }

    @Test
    fun `the Last-Modified table`() {
        val fixdate = "Fri, 06 Nov 2026 04:25:27 GMT"
        val singleDigitDay = "Fri, 6 Nov 2026 04:25:27 GMT"
        assertEquals(28, singleDigitDay.length)
        val rows: List<Pair<String?, String?>> = listOf(
            fixdate to fixdate,
            singleDigitDay to singleDigitDay,
            " $fixdate\t" to fixdate,
            "a".repeat(64) to "a".repeat(64),
            "a".repeat(65) to null,
            "" to null,
            "Fri, 06 Nov 2026 04:25:27 GMTé" to null,
            "Fri,\n06 Nov 2026" to null,
            "*" to "*",
            null to null,
        )
        for ((input, expected) in rows) assertEquals(expected, FeedValidators(null, input).lastModified, "'$input'")
    }
}
