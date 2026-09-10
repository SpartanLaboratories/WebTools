package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.webtools.udp.Rtt
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Level 1 - a fast, socket-free smoke over the pure link-quality rules. The exhaustive
// input->output matrix lives in the Level 4a deterministic suite (RttTest).
@Tag("gating")
class RttGatingTest {

    private val ms = 1_000_000L

    @Test
    fun `smoothedRttMillis moves toward the sample`() {
        val next = Rtt.smoothedRttMillis(40.0, 80.0)
        assertTrue(next in 40.0..80.0, "was $next")
        assertTrue(next < 60.0, "alpha = 1/8 keeps it near the previous value, was $next")
    }

    @Test
    fun `lossRatio is zero when nothing has resolved`() {
        assertEquals(0.0, Rtt.lossRatio(0, 0))
    }

    @Test
    fun `isProbeLost is true past the horizon, false within it, false for a non-positive interval`() {
        val now = 1_000_000_000_000L
        assertTrue(Rtt.isProbeLost(now - 3 * 300 * ms, now, 300L), "at 3 intervals")
        assertFalse(Rtt.isProbeLost(now - 2 * 300 * ms, now, 300L), "within the horizon")
        assertFalse(Rtt.isProbeLost(now - 10 * 300 * ms, now, 0L), "disabled for interval <= 0")
    }
}
