package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.DAY_MS
import com.spartanlabs.testing.support.webtools.http.MINUTE_MS
import com.spartanlabs.testing.support.webtools.http.ledgerOf
import com.spartanlabs.testing.support.webtools.http.minute
import com.spartanlabs.testing.support.webtools.http.nowAt
import com.spartanlabs.webtools.http.LedgerNow
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.WindowLedger
import com.spartanlabs.webtools.http.ceilToMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Level 4a - the window arithmetic: `indexAt`, `startOf` and the elapsed fraction around a boundary; a period that does not divide
 * a day; a pre-epoch wall time; the `Long` extremes for `LedgerNow` and `ceilToMillis`; `nextCritical` with and without a guard;
 * `chargeIndices` (the guard's two-cell charge) at every edge, and the two bands never meeting at the largest allowed guard.
 */
class WindowArithmeticDeterministicTest {

    private fun window(spec: RateWindow = minute(10), guardMillis: Long = 0L): WindowLedger = WindowLedger(spec, guardMillis, emptyMap())

    @Test
    fun `indexAt and startOf around a boundary`() {
        val w = window()
        assertEquals(0L, w.indexAt(MINUTE_MS - 1))
        assertEquals(1L, w.indexAt(MINUTE_MS))
        assertEquals(1L, w.indexAt(MINUTE_MS + 1))
        assertEquals(MINUTE_MS, w.startOf(1))
        assertEquals(0L, w.startOf(0))
        assertEquals(2 * MINUTE_MS, w.startOf(w.indexAt(2 * MINUTE_MS + 59_999)))
    }

    @Test
    fun `the elapsed fraction around a boundary`() {
        val ledger = ledgerOf(minute(10))
        fun fraction(wall: Long): Double = ledger.utilisation(nowAt(wall), 0).windows[0].elapsedFraction
        assertEquals(59_999.0 / 60_000.0, fraction(MINUTE_MS - 1))
        assertEquals(0.0, fraction(MINUTE_MS))
        assertEquals(1.0 / 60_000.0, fraction(MINUTE_MS + 1))
        assertEquals(MINUTE_MS, ledger.utilisation(nowAt(MINUTE_MS + 1), 0).windows[0].windowStartEpochMillis)
        assertEquals(2 * MINUTE_MS, ledger.utilisation(nowAt(MINUTE_MS + 1), 0).windows[0].resetsAtEpochMillis)
    }

    @Test
    fun `a period that does not divide a day`() {
        val w = window(RateWindow("seven", 10, 7.seconds))
        assertEquals(12_342L, w.indexAt(DAY_MS))
        assertEquals(86_394_000L, w.startOf(w.indexAt(DAY_MS)))
        assertEquals(86_401_000L, w.nextCritical(DAY_MS))
        assertEquals(12_343L, w.indexAt(86_401_000L))
    }

    @Test
    fun `a pre-epoch wall time floors towards negative infinity`() {
        val w = window()
        assertEquals(-1L, w.indexAt(-1))
        assertEquals(-1L, w.indexAt(-MINUTE_MS))
        assertEquals(-2L, w.indexAt(-MINUTE_MS - 1))
        assertEquals(-MINUTE_MS, w.startOf(-1))
        assertEquals(listOf(-1L), w.chargeIndices(-1))
        assertEquals(0L, w.nextCritical(-1))
    }

    @Test
    fun `the Long extremes for LedgerNow and ceilToMillis`() {
        val w = window()
        assertEquals(Long.MAX_VALUE / MINUTE_MS, w.indexAt(Long.MAX_VALUE))
        assertEquals(Long.MIN_VALUE.floorDiv(MINUTE_MS), w.indexAt(Long.MIN_VALUE))
        assertEquals(Long.MAX_VALUE, LedgerNow(Long.MAX_VALUE, Duration.INFINITE).wallMillis)
        assertEquals(Duration.INFINITE, LedgerNow(Long.MIN_VALUE, Duration.INFINITE).elapsed)
        assertEquals(Long.MAX_VALUE, Duration.INFINITE.ceilToMillis())
        assertEquals(1L, 1.nanoseconds.ceilToMillis())
        assertEquals(1L, 1.milliseconds.ceilToMillis())
        assertEquals(1L, 999.microseconds.ceilToMillis())
        assertEquals(2L, 1500.microseconds.ceilToMillis())
        assertEquals(0L, Duration.ZERO.ceilToMillis())
        assertEquals(30_000L, 30.seconds.ceilToMillis())
    }

    @Test
    fun `nextCritical with and without a guard`() {
        val plain = window()
        assertEquals(MINUTE_MS, plain.nextCritical(0))
        assertEquals(MINUTE_MS, plain.nextCritical(10_000))
        assertEquals(2 * MINUTE_MS, plain.nextCritical(MINUTE_MS))
        val guarded = window(guardMillis = 1_000L)
        assertEquals(1_000L, guarded.nextCritical(0))
        assertEquals(1_000L, guarded.nextCritical(500))
        assertEquals(MINUTE_MS, guarded.nextCritical(1_000))
        assertEquals(MINUTE_MS, guarded.nextCritical(59_500))
        assertEquals(MINUTE_MS + 1_000L, guarded.nextCritical(MINUTE_MS))
    }

    @Test
    fun `chargeIndices - the guard's two-cell charge`() {
        val guarded = window(guardMillis = 1_000L)
        assertEquals(listOf(1L, 0L), guarded.chargeIndices(MINUTE_MS + 500))
        assertEquals(listOf(1L), guarded.chargeIndices(MINUTE_MS + 1_000))
        assertEquals(listOf(1L), guarded.chargeIndices(2 * MINUTE_MS - 1_001))
        assertEquals(listOf(1L, 2L), guarded.chargeIndices(2 * MINUTE_MS - 1_000))
        assertEquals(listOf(1L, 2L), guarded.chargeIndices(2 * MINUTE_MS - 1))
        assertEquals(listOf(1L, 0L), guarded.chargeIndices(MINUTE_MS))
        assertEquals(listOf(1L), guarded.chargeIndices(MINUTE_MS + 30_000))
        val plain = window()
        for (wall in listOf(MINUTE_MS, MINUTE_MS + 1, MINUTE_MS + 500, MINUTE_MS + 30_000, 2 * MINUTE_MS - 1_000, 2 * MINUTE_MS - 1)) {
            assertEquals(listOf(1L), plain.chargeIndices(wall), "wall $wall")
        }
    }

    @Test
    fun `the two bands never meet at the largest allowed guard`() {
        val quarter = window(guardMillis = MINUTE_MS / 4)
        for (wall in MINUTE_MS until 2 * MINUTE_MS step 250) {
            val indices = quarter.chargeIndices(wall)
            assertTrue(indices.size <= 2, "wall $wall charges $indices")
            assertTrue(1L in indices, "wall $wall charges $indices")
        }
        assertEquals(listOf(1L, 0L), quarter.chargeIndices(MINUTE_MS + 14_999))
        assertEquals(listOf(1L), quarter.chargeIndices(MINUTE_MS + 15_000))
        assertEquals(listOf(1L), quarter.chargeIndices(MINUTE_MS + 44_999))
        assertEquals(listOf(1L, 2L), quarter.chargeIndices(MINUTE_MS + 45_000))
    }
}
