package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.minute
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.WindowLedger
import com.spartanlabs.webtools.http.reservedUnits
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Level 4a - `reservedUnits`: the table (`ceil`, at least 1, the epsilon keeping `0.2 * 50` at 10 and `0.3 * 10` at 3), the seeded
 * grid properties (at least 1, at most the limit for a share at most 1, non-decreasing in the share), and `capacityFor` for one,
 * two and three lanes.
 */
class ShareRoundingDeterministicTest {

    @Test
    fun `the rounding table`() {
        assertEquals(1, reservedUnits(0.2, 3))
        assertEquals(2, reservedUnits(0.2, 7))
        assertEquals(10, reservedUnits(0.2, 50))
        assertEquals(12, reservedUnits(0.2, 60))
        assertEquals(1, reservedUnits(0.01, 10))
        assertEquals(10, reservedUnits(1.0, 10))
        assertEquals(3, reservedUnits(0.5, 5))
        assertEquals(1, reservedUnits(0.2, 1))
        assertEquals(7, reservedUnits(0.07, 100))
        assertEquals(3, reservedUnits(0.3, 10))
    }

    @Test
    fun `the grid properties`() {
        val random = Random(7)
        repeat(20_000) {
            val limit = random.nextInt(1, 5_001)
            val share = random.nextDouble(Double.MIN_VALUE, 1.0).coerceAtMost(1.0)
            val units = reservedUnits(share, limit)
            assertTrue(units >= 1, "share $share limit $limit gave $units")
            assertTrue(units <= limit, "share $share limit $limit gave $units")
            val larger = (share + random.nextDouble(0.0, 1.0 - share)).coerceAtMost(1.0)
            assertTrue(reservedUnits(larger, limit) >= units, "share $larger below $share at limit $limit")
        }
        for (limit in 1..200) {
            assertEquals(limit, reservedUnits(1.0, limit), "a full share reserves the whole window of $limit")
            assertEquals(1, reservedUnits(1e-12, limit), "a tiny share reserves one unit of $limit")
        }
    }

    @Test
    fun `capacityFor one, two and three lanes`() {
        val a = Lane("a")
        val b = Lane("b")
        val c = Lane("c")
        val one = WindowLedger(minute(10), 0L, mapOf(Lane.Interactive to 2))
        assertEquals(10, one.capacityFor(Lane.Interactive))
        assertEquals(8, one.capacityFor(Lane.Tracker))
        val two = WindowLedger(minute(10), 0L, mapOf(a to 2, b to 3))
        assertEquals(7, two.capacityFor(a))
        assertEquals(8, two.capacityFor(b))
        assertEquals(5, two.capacityFor(c))
        val three = WindowLedger(minute(10), 0L, mapOf(a to 2, b to 3, c to 4))
        assertEquals(3, three.capacityFor(a))
        assertEquals(4, three.capacityFor(b))
        assertEquals(5, three.capacityFor(c))
        assertEquals(1, three.capacityFor(Lane.Tracker))
        assertEquals(10, WindowLedger(minute(10), 0L, emptyMap()).capacityFor(Lane.Tracker))
    }
}
