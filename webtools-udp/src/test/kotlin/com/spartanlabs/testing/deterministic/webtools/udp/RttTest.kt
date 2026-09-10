package com.spartanlabs.testing.deterministic.webtools.udp

import com.spartanlabs.webtools.udp.Rtt
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Level 4a - the pure input->output rules of the opt-in link-quality probe. Mirrors
// LivenessTest / KeepAliveTest.
@Tag("deterministic")
class RttTest {

    private val ms = 1_000_000L
    private val tol = 1e-9

    @Test
    fun `smoothedRttMillis applies the RFC 6298 one-eighth weighting`() {
        // 40 + (1/8)(120 - 40) = 50
        assertEquals(50.0, Rtt.smoothedRttMillis(40.0, 120.0), tol)
        // moving down: 80 + (1/8)(0 - 80) = 70
        assertEquals(70.0, Rtt.smoothedRttMillis(80.0, 0.0), tol)
    }

    @Test
    fun `smoothedRttMillis is a fixed point when the sample equals the estimate`() {
        assertEquals(42.0, Rtt.smoothedRttMillis(42.0, 42.0), tol)
    }

    @Test
    fun `smoothedRttMillis converges monotonically toward a constant sample`() {
        var srtt = 0.0
        var previous = -1.0
        repeat(50) {
            srtt = Rtt.smoothedRttMillis(srtt, 100.0)
            assertTrue(srtt in previous..100.0, "monotone non-decreasing and bounded, was $srtt")
            previous = srtt
        }
        assertTrue(srtt > 99.0, "converged near the sample, was $srtt")
    }

    @Test
    fun `smoothedRttVarianceMillis applies the RFC 6298 one-quarter weighting and never goes negative`() {
        // 0.75*10 + 0.25*|50 - 90| = 7.5 + 10 = 17.5
        assertEquals(17.5, Rtt.smoothedRttVarianceMillis(10.0, 50.0, 90.0), tol)
        // shrinks when the sample equals SRTT: 0.75*20 + 0 = 15
        assertEquals(15.0, Rtt.smoothedRttVarianceMillis(20.0, 40.0, 40.0), tol)
        assertTrue(Rtt.smoothedRttVarianceMillis(0.0, 10.0, 10.0) >= 0.0)
    }

    @Test
    fun `lossRatio maps counts to a clamped fraction`() {
        assertEquals(0.75, Rtt.lossRatio(3, 1), tol)
        assertEquals(0.0, Rtt.lossRatio(0, 0), tol)
        assertEquals(1.0, Rtt.lossRatio(5, 0), tol)
        assertEquals(0.0, Rtt.lossRatio(-3, 1), tol)
        assertTrue(Rtt.lossRatio(Int.MAX_VALUE, Int.MAX_VALUE) in 0.0..1.0)
    }

    @Test
    fun `isProbeLost boundary is inclusive at the loss horizon`() {
        val now = 1_000_000_000_000L
        val horizon = Rtt.LOSS_HORIZON_INTERVALS * 300L
        assertTrue(Rtt.isProbeLost(now - horizon * ms, now, 300L), "exactly at the horizon")
        assertFalse(Rtt.isProbeLost(now - (horizon - 1) * ms, now, 300L), "just under")
    }

    @Test
    fun `isProbeLost is false for a non-positive interval, a now before sentAt, and does not overflow`() {
        val now = 1_000_000_000_000L
        assertFalse(Rtt.isProbeLost(now - 10_000 * ms, now, 0L))
        assertFalse(Rtt.isProbeLost(now - 10_000 * ms, now, -5L))
        assertFalse(Rtt.isProbeLost(now + 50 * ms, now, 300L))
        assertFalse(Rtt.isProbeLost(0L, Long.MAX_VALUE, Long.MAX_VALUE))
    }
}
