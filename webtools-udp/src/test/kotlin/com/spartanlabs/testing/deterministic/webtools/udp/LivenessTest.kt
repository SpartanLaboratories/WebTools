package com.spartanlabs.testing.deterministic.webtools.udp

import com.spartanlabs.webtools.udp.Liveness
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

// Level 4a - the pure input->output rules of the idle-connection sweep.
@Tag("deterministic")
class LivenessTest {

    private val ms = 1_000_000L

    @Test
    fun `isOverdue is always false when the threshold is zero or negative`() {
        listOf(0L, -1L, Long.MIN_VALUE).forEach { threshold ->
            assertFalse(Liveness.isOverdue(0L, 10_000L * ms, threshold))
        }
    }

    @Test
    fun `isOverdue boundary is inclusive at exactly the threshold`() {
        val now = 1_000_000_000_000L
        assertTrue(Liveness.isOverdue(now - 300 * ms, now, 300L), "exactly the threshold is overdue")
        assertFalse(Liveness.isOverdue(now - 299 * ms, now, 300L), "just under is not")
        assertTrue(Liveness.isOverdue(now - 5_000 * ms, now, 300L), "well over is")
    }

    @Test
    fun `isOverdue treats a now before lastInboundAt as not overdue`() {
        val now = 1_000_000_000_000L
        assertFalse(Liveness.isOverdue(now + 50 * ms, now, 300L))
    }

    @Test
    fun `isOverdue does not overflow for a Long MAX_VALUE threshold`() {
        assertFalse(Liveness.isOverdue(0L, Long.MAX_VALUE, Long.MAX_VALUE))
    }

    @Test
    fun `sweepIntervalMillis clamps low, clamps high, and is threshold over four in the mid band`() {
        assertEquals(250L, Liveness.sweepIntervalMillis(0L))
        assertEquals(250L, Liveness.sweepIntervalMillis(400L))
        assertEquals(1_000L, Liveness.sweepIntervalMillis(4_000L))
        assertEquals(5_000L, Liveness.sweepIntervalMillis(1_000_000L))
        assertEquals(5_000L, Liveness.sweepIntervalMillis(Long.MAX_VALUE))
    }

    @Test
    fun `sweepIntervalMillis never returns outside 250 to 5000`() {
        listOf(-5L, 0L, 1L, 999L, 1_000L, 20_000L, 100_000L, Long.MAX_VALUE).forEach {
            val interval = Liveness.sweepIntervalMillis(it)
            assertTrue(interval in 250L..5_000L, "interval for $it was $interval")
        }
    }
}
