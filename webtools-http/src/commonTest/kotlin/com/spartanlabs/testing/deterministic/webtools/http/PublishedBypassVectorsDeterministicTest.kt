package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.BypassVectors
import com.spartanlabs.testing.support.webtools.http.BypassVectors.Outcome
import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlGuard
import com.spartanlabs.webtools.http.UrlParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 4a - the published bypass vectors (`BypassVectors`), the one table, run by group. Stage 1: `Url.parse` fails with
 * exactly the vector's `UrlProblem`. Stage 2: the text parses, its host is the vector's host, and the default `Untrusted`
 * guard's `checkHop(url, null)` gives the vector's outcome (with the range class for `DeniedAddress`). The catalogue tags
 * `F1` (the host-form catalogue) and `F10` (the advisory vectors) each keep a floor, so a removed tag fails loudly.
 */
class PublishedBypassVectorsDeterministicTest {

    private val guard: UrlGuard = untrustedGuard()

    private fun group(prefix: String): List<BypassVectors.Vector> =
        BypassVectors.vectors.filter { it.id.startsWith(prefix) }.also { assertTrue(it.isNotEmpty(), "no vectors in group $prefix") }

    private fun check(vector: BypassVectors.Vector) {
        val problem = vector.problem
        if (problem != null) {
            val failure = assertNotNull(Url.parse(vector.text).exceptionOrNull(), "$vector: Url.parse accepted it")
            assertEquals(problem, assertIs<UrlParseException>(failure, "$vector").problem, "$vector")
            return
        }
        val url = Url.parse(vector.text).getOrElse { throw AssertionError("$vector: Url.parse refused it: ${it.message}") }
        assertEquals(vector.host, url.host, "$vector: host")
        val refusal = guard.checkHop(url, null)
        when (vector.outcome) {
            Outcome.Allowed -> assertNull(refusal, "$vector: refused as $refusal with class ${refusal?.rangeClass}")
            Outcome.HostForm -> assertEquals(RefusalReason.HostForm, refusal?.reason, "$vector")
            Outcome.ReservedName -> assertEquals(RefusalReason.ReservedName, refusal?.reason, "$vector")
            Outcome.Port -> assertEquals(RefusalReason.Port, refusal?.reason, "$vector")
            Outcome.DeniedAddress -> {
                assertEquals(RefusalReason.DeniedAddress, refusal?.reason, "$vector")
                assertEquals(vector.rangeClass, refusal?.rangeClass, "$vector: range class")
            }
            null -> throw AssertionError("$vector has neither a problem nor an outcome")
        }
    }

    @Test
    fun `stage 1 - the parser refuses every P vector with its problem`() {
        group("P").filter { it.problem != null }.forEach { check(it) }
    }

    @Test
    fun `stage 2 - the port rule refuses P20 and P12b is a denied address`() {
        group("P").filter { it.problem == null }.forEach { check(it) }
    }

    @Test
    fun `stage 2 - HostForm vectors`() = group("H").forEach { check(it) }

    @Test
    fun `stage 2 - DeniedAddress vectors with the most specific class`() = group("D").forEach { check(it) }

    @Test
    fun `stage 2 - the two design gaps closed by rows that are not registry rows`() = group("G").forEach { check(it) }

    @Test
    fun `stage 2 - ReservedName vectors`() = group("R").forEach { check(it) }

    @Test
    fun `stage 2 - names the resolver guard judges and the controls are allowed`() {
        group("N").forEach { check(it) }
        group("C").forEach { check(it) }
    }

    private fun tagged(tag: String): List<BypassVectors.Vector> =
        BypassVectors.vectors.filter { vector -> vector.parent?.split(',')?.any { it == tag } == true }

    @Test
    fun `the F1 host-form catalogue has at least 40 rows and all hold`() {
        val rows = tagged("F1")
        assertTrue(rows.size >= 40, "F1 rows: ${rows.size}")
        rows.forEach { check(it) }
    }

    @Test
    fun `the F10 advisory vectors number at least 25 and all hold`() {
        val rows = tagged("F10")
        assertTrue(rows.size >= 25, "F10 rows: ${rows.size}")
        rows.forEach { check(it) }
    }
}
