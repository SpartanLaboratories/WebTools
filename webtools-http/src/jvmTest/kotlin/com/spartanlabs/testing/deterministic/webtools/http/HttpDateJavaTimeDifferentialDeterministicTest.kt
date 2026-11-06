package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.HttpDateOracle
import com.spartanlabs.testing.support.webtools.http.HttpDateOracle.visible
import com.spartanlabs.webtools.http.HttpDate
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Level 4a - the `HttpDate` versus `java.time` differential: fixed seed 7, `WEBTOOLS_DIFFERENTIAL_INPUTS`
 * raising the count with the coverage floors scaled along. The oracle referees calendar arithmetic and year windows; it is never
 * asked to referee a policy, so the parser's three liberties are normalised out of part 2's comparison rather than excepted in it.
 * The parser is called unwrapped: a throw is a failure. Part 1 - every date the oracle prints for a random second in
 * `[−62_167_219_200, 253_402_300_799]` (IMF-fixdate, asctime; RFC 850 within a random reference's window) is parsed to that
 * instant and the oracle's strict parse agrees. Part 2 - field-random texts (valid and invalid days, months, years biased to the
 * edges, hours, minutes, seconds with second 60 at 10 %, a weekday right or wrong, random ASCII case, optional SP/HTAB padding):
 * both accept and agree, or both reject, after the oracle's input is normalised (weekday stripped, padding trimmed, second 60
 * replaced by 59 and a second added to its result). Part 3 - one edit of a printed valid text (delete, insert `x`, replace by a
 * non-letter, or by `z` or `é` where the original is not a letter, truncate) is rejected by both. Part 4 - the coverage floors.
 */
class HttpDateJavaTimeDifferentialDeterministicTest {

    private val seed = 7
    private val defaultIterations = 200_000
    private val iterations: Int = System.getenv("WEBTOOLS_DIFFERENTIAL_INPUTS")?.toIntOrNull() ?: defaultIterations
    private val scale: Double = iterations.toDouble() / defaultIterations

    private val shortDays = HttpDateOracle.SHORT_DAYS.values.toList()
    private val longDays = HttpDateOracle.LONG_DAYS.values.toList()
    private val months = HttpDateOracle.MONTHS.values.toList()

    private fun mine(text: String, reference: Long): Result<Long> = HttpDate.parse(text, reference)

    //region part 1
    private fun checkAccepted(text: String, expectedMillis: Long, oracle: DateTimeFormatter, reference: Long, label: String) {
        val parsed = mine(text, reference).getOrElse { fail("$label: HttpDate rejected ${visible(text)}: ${it.message}") }
        assertEquals(expectedMillis, parsed, "$label: HttpDate's instant for ${visible(text)}")
        assertEquals(expectedMillis, HttpDateOracle.parseStrict(oracle, text), "$label: the oracle's own parse of ${visible(text)}")
    }

    private class Rfc850Case(val text: String, val epochSecond: Long, val reference: Long, val formatter: DateTimeFormatter)

    private fun rfc850Case(random: Random): Rfc850Case {
        val referenceYear = random.nextInt(0, 10_000)
        val base = HttpDateOracle.base(referenceYear)
        val year = base + random.nextInt(100)
        val dayOfYear = random.nextInt(1, if (LocalDate.ofYearDay(year, 1).isLeapYear) 367 else 366)
        val epochSecond = LocalDate.ofYearDay(year, dayOfYear).atStartOfDay().toEpochSecond(ZoneOffset.UTC) + random.nextInt(86_400)
        val reference = LocalDate.of(referenceYear, 1, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1_000L + random.nextLong(0L, 365L * 86_400_000L)
        val formatter = HttpDateOracle.rfc850(base)
        return Rfc850Case(HttpDateOracle.print(formatter, epochSecond), epochSecond, reference, formatter)
    }

    @Test
    fun `part 1 - every date the oracle prints is parsed to its instant and the oracle agrees`() {
        val random = Random(seed)
        repeat(iterations) { i ->
            val second = random.nextLong(HttpDateOracle.MIN_SECOND, HttpDateOracle.MAX_SECOND + 1)
            checkAccepted(HttpDateOracle.print(HttpDateOracle.imfFixdate, second), second * 1_000L, HttpDateOracle.imfFixdate, 0L, "seed $seed #$i imf")
            checkAccepted(HttpDateOracle.print(HttpDateOracle.asctime, second), second * 1_000L, HttpDateOracle.asctime, 0L, "seed $seed #$i asctime")
            val rfc = rfc850Case(random)
            checkAccepted(rfc.text, rfc.epochSecond * 1_000L, rfc.formatter, rfc.reference, "seed $seed #$i rfc850 (reference ${rfc.reference})")
        }
    }
    //endregion

    //region part 2
    private class FormCounts {
        var accepted = 0
        var rejected = 0
        var leapSecond = 0
        val total: Int get() = accepted + rejected
        override fun toString(): String = "accepted=$accepted rejected=$rejected leapSecond=$leapSecond"
    }

    private fun randomCase(random: Random, text: String): String =
        text.map { c -> if (c.isLetter() && random.nextBoolean()) (if (c.isUpperCase()) c.lowercaseChar() else c.uppercaseChar()) else c }.joinToString("")

    private fun pad(random: Random, text: String): String {
        val pads = listOf("", " ", "\t", " \t", "  ")
        return pads[random.nextInt(pads.size)] + text + pads[random.nextInt(pads.size)]
    }

    private fun randomYear(random: Random): Int {
        val biased = listOf(1900, 2000, 2100, 0, 9999, 2024, 1999)
        return if (random.nextInt(3) == 0) biased[random.nextInt(biased.size)] else random.nextInt(0, 10_000)
    }

    private fun randomDay(random: Random): Int = if (random.nextInt(3) == 0) random.nextInt(28, 32) else random.nextInt(0, 33)

    private fun two(n: Int): String = n.toString().padStart(2, '0')

    @Test
    fun `part 2 - random fields are accepted by both and agree, or rejected by both`() {
        val random = Random(seed + 1)
        val counts = listOf(FormCounts(), FormCounts(), FormCounts())
        repeat(iterations) { i ->
            val day = randomDay(random)
            val month = random.nextInt(1, 13)
            val year = randomYear(random)
            val hour = random.nextInt(0, 26)
            val minute = random.nextInt(0, 62)
            val second = if (random.nextInt(10) == 0) 60 else random.nextInt(0, 63)
            val weekday = random.nextInt(7)
            val time = "${two(hour)}:${two(minute)}:${two(second)}"
            val oracleTime = "${two(hour)}:${two(minute)}:${two(if (second == 60) 59 else second)}"
            val form = i % 3
            val reference: Long
            val oracleFormatter: DateTimeFormatter
            val body: String
            val oracleBody: String
            val prefix: String
            when (form) {
                0 -> {
                    prefix = "${shortDays[weekday]}, "
                    body = "${two(day)} ${months[month - 1]} ${year.toString().padStart(4, '0')} $time GMT"
                    oracleBody = "${two(day)} ${months[month - 1]} ${year.toString().padStart(4, '0')} $oracleTime GMT"
                    reference = 0L
                    oracleFormatter = HttpDateOracle.imfFixdateBody
                }
                1 -> {
                    val referenceYear = random.nextInt(0, 10_000)
                    reference = LocalDate.of(referenceYear, 1, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1_000L + random.nextLong(0L, 365L * 86_400_000L)
                    oracleFormatter = HttpDateOracle.rfc850Body(HttpDateOracle.base(referenceYear))
                    val yy = two(year % 100)
                    prefix = "${longDays[weekday]}, "
                    body = "${two(day)}-${months[month - 1]}-$yy $time GMT"
                    oracleBody = "${two(day)}-${months[month - 1]}-$yy $oracleTime GMT"
                }
                else -> {
                    prefix = "${shortDays[weekday]} "
                    val dayText = if (day < 10 && random.nextBoolean()) " $day" else two(day)
                    body = "${months[month - 1]} $dayText $time ${year.toString().padStart(4, '0')}"
                    oracleBody = "${months[month - 1]} $dayText $oracleTime ${year.toString().padStart(4, '0')}"
                    reference = 0L
                    oracleFormatter = HttpDateOracle.asctimeBody
                }
            }
            val text = pad(random, randomCase(random, prefix + body))
            val label = "seed $seed #$i form $form"
            val theirs = HttpDateOracle.parseStrict(oracleFormatter, oracleBody)?.let { it + (if (second == 60) 1_000L else 0L) }
            val ours = mine(text, reference)
            if (theirs == null) {
                assertTrue(ours.isFailure, "$label: HttpDate accepted ${visible(text)} as ${ours.getOrNull()}, the oracle rejected ${visible(oracleBody)}")
                counts[form].rejected++
            } else {
                val parsed = ours.getOrElse { fail("$label: HttpDate rejected ${visible(text)} (${it.message}), the oracle accepted ${visible(oracleBody)} as $theirs") }
                assertEquals(theirs, parsed, "$label: instant of ${visible(text)}")
                counts[form].accepted++
                if (second == 60) counts[form].leapSecond++
            }
        }
        val summary = "imf={${counts[0]}} rfc850={${counts[1]}} asctime={${counts[2]}}"
        println("HttpDateJavaTimeDifferentialDeterministicTest part 2 seed=$seed iterations=$iterations $summary")
        // Part 4 - the coverage floors: accepted and rejected above 20 % per form, leap-second texts above 5 % of the IMF texts.
        counts.forEachIndexed { form, c ->
            assertTrue(c.accepted >= c.total * 0.2, "form $form: fewer than 20% accepted: $summary")
            assertTrue(c.rejected >= c.total * 0.2, "form $form: fewer than 20% rejected: $summary")
        }
        assertTrue(counts[0].leapSecond >= counts[0].total * 0.05, "fewer than 5% leap-second IMF texts: $summary")
    }
    //endregion

    //region part 3
    // What each replacement probes: HTAB (whitespace the grammar allows only around the value) and NUL are controls; `~` is a printable non-letter; `é` is a
    // non-ASCII letter a lenient `isLetter` would accept; `٠`, `٣` (Arabic-Indic) and `１` (fullwidth) are non-ASCII digits a lenient `isDigit` would accept;
    // `z` is the one ASCII letter.
    private val replacements: List<Char> = listOf('\t', '~', 'é', '٠', '٣', '\u0000', '１', 'z')

    @Test
    fun `part 3 - one edit of a valid text is rejected by the parser and by the strict oracle`() {
        val random = Random(seed + 2)
        var attempted = 0
        var run = 0
        repeat(iterations) { i ->
            val second = random.nextLong(HttpDateOracle.MIN_SECOND, HttpDateOracle.MAX_SECOND + 1)
            val form = i % 3
            val text: String
            val oracle: DateTimeFormatter
            val reference: Long
            when (form) {
                0 -> { text = HttpDateOracle.print(HttpDateOracle.imfFixdate, second); oracle = HttpDateOracle.imfFixdate; reference = 0L }
                1 -> { val rfc = rfc850Case(random); text = rfc.text; oracle = rfc.formatter; reference = rfc.reference }
                else -> { text = HttpDateOracle.print(HttpDateOracle.asctime, second); oracle = HttpDateOracle.asctime; reference = 0L }
            }
            attempted++
            val position = random.nextInt(text.length)
            val mutant: String = when (random.nextInt(4)) {
                0 -> text.removeRange(position, position + 1)
                1 -> text.substring(0, position) + "x" + text.substring(position)
                2 -> {
                    val replacement = replacements[random.nextInt(replacements.size)]
                    // A letter in place of a letter could spell another valid name, so that edit is excluded (`z` and `é` are the set's two letters; the rest are non-letters).
                    if (replacement == text[position] || (replacement.isLetter() && text[position].isLetter())) return@repeat
                    text.substring(0, position) + replacement + text.substring(position + 1)
                }
                else -> text.substring(0, position)
            }
            run++
            val label = "seed $seed #$i form $form"
            assertTrue(mine(mutant, reference).isFailure, "$label: HttpDate accepted the mutant ${visible(mutant)} of ${visible(text)}")
            assertNull(HttpDateOracle.parseStrict(oracle, mutant), "$label: the oracle accepted the mutant ${visible(mutant)} of ${visible(text)}")
        }
        println("HttpDateJavaTimeDifferentialDeterministicTest part 3 seed=$seed attempted=$attempted run=$run")
        // Part 4 - the mutants run are above 90 % of those attempted.
        assertTrue(run >= attempted * 0.9, "fewer than 90% of the mutants ran: attempted=$attempted run=$run")
        assertEquals(iterations, attempted, "the mutant part did not run its iterations")
    }
    //endregion
}
