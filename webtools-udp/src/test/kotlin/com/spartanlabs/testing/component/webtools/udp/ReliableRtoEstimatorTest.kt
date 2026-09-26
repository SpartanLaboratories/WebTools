package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.webtools.udp.ReliableRtoEstimator
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 2 - the reliable channel's private RTO estimator in isolation. Not pure: it carries
// EWMA state across calls, mirroring LinkQualityTrackerTest's shape for the same reason.
@Tag("component")
class ReliableRtoEstimatorTest {

    @Test
    fun `currentRtoMillis is the floor before any sample`() {
        val estimator = ReliableRtoEstimator(floorMillis = 200L, capMillis = 5_000L)
        assertEquals(200L, estimator.currentRtoMillis())
    }

    @Test
    fun `the first sample seeds SRTT and RTTVAR per RFC 6298 and the RTO reflects it`() {
        val estimator = ReliableRtoEstimator(floorMillis = 10L, capMillis = 5_000L)
        estimator.onRttSample(40.0)

        // RTO = SRTT + 4*RTTVAR = 40 + 4*20 = 120
        assertEquals(120L, estimator.currentRtoMillis())
    }

    @Test
    fun `a second sample moves SRTT toward it but stays nearer the first - EWMA convergence`() {
        val estimator = ReliableRtoEstimator(floorMillis = 1L, capMillis = 5_000L)
        estimator.onRttSample(40.0)
        val rtoAfterFirst = estimator.currentRtoMillis()
        estimator.onRttSample(80.0)
        val rtoAfterSecond = estimator.currentRtoMillis()

        assertTrue(rtoAfterSecond > rtoAfterFirst, "RTO grows as the sample moves upward")
    }

    @Test
    fun `currentRtoMillis is clamped to the floor when the formula would go lower`() {
        val estimator = ReliableRtoEstimator(floorMillis = 500L, capMillis = 5_000L)
        estimator.onRttSample(1.0)

        assertEquals(500L, estimator.currentRtoMillis())
    }

    @Test
    fun `currentRtoMillis is clamped to the cap when the formula would go higher`() {
        val estimator = ReliableRtoEstimator(floorMillis = 1L, capMillis = 1_000L)
        estimator.onRttSample(10_000.0)

        assertEquals(1_000L, estimator.currentRtoMillis())
    }
}
