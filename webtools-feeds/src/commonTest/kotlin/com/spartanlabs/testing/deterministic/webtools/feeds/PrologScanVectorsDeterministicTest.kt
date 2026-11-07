package com.spartanlabs.testing.deterministic.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.PrologVector
import com.spartanlabs.testing.support.webtools.feeds.PrologVectors
import com.spartanlabs.webtools.feeds.PrologScan
import com.spartanlabs.webtools.feeds.PrologScanResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Level 4a - the prolog-scan catalogue of 135 rows: the N rows are `NoDoctype` but for N17 (`Empty`) and N18, N20, N21
 * (`NotMarkup`); the D rows `Doctype`; the H rows `HtmlDocument`; the M rows `IllFormed`; the U rows their `Undecodable` reason;
 * the E rows their outcome; ids are unique.
 */
class PrologScanVectorsDeterministicTest {

    private fun check(rows: List<PrologVector>) {
        val wrong = rows.mapNotNull { row ->
            val actual = PrologScan.scan(row.bytes, row.contentType)
            if (actual == row.expected) null else "${row.id}: expected ${row.expected}, got $actual"
        }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    @Test
    fun `the N rows give NoDoctype, except N17 Empty and N18, N20, N21 NotMarkup`() {
        assertEquals(32, PrologVectors.normal.size)
        val exceptions = mapOf("N17" to PrologScanResult.Empty, "N18" to PrologScanResult.NotMarkup, "N20" to PrologScanResult.NotMarkup, "N21" to PrologScanResult.NotMarkup)
        for (row in PrologVectors.normal) assertEquals(exceptions[row.id] ?: PrologScanResult.NoDoctype, row.expected, row.id)
        check(PrologVectors.normal)
    }

    @Test
    fun `the D rows give Doctype`() {
        assertEquals(40, PrologVectors.doctype.size)
        assertTrue(PrologVectors.doctype.all { it.expected == PrologScanResult.Doctype })
        check(PrologVectors.doctype)
    }

    @Test
    fun `the H rows give HtmlDocument`() {
        assertEquals(12, PrologVectors.html.size)
        assertTrue(PrologVectors.html.all { it.expected == PrologScanResult.HtmlDocument })
        check(PrologVectors.html)
    }

    @Test
    fun `the M rows give IllFormed`() {
        assertEquals(10, PrologVectors.malformed.size)
        assertTrue(PrologVectors.malformed.all { it.expected == PrologScanResult.IllFormed })
        check(PrologVectors.malformed)
    }

    @Test
    fun `the U rows give the listed Undecodable reason`() {
        assertEquals(31, PrologVectors.undecodable.size)
        assertTrue(PrologVectors.undecodable.all { it.expected is PrologScanResult.Undecodable })
        check(PrologVectors.undecodable)
    }

    @Test
    fun `the E rows give the listed outcome`() {
        assertEquals(10, PrologVectors.extra.size)
        check(PrologVectors.extra)
    }

    @Test
    fun `ids are unique and the catalogue holds 135 rows`() {
        assertEquals(135, PrologVectors.all.size)
        assertEquals(135, PrologVectors.all.map { it.id }.toSet().size)
    }
}
