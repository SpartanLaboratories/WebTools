package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.webtools.udp.KeepAlive
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Level 1 - a fast, socket-free smoke over the pure keepalive rules. The exhaustive
// input->output matrix lives in the Level 4a deterministic suite (KeepAliveTest).
@Tag("gating")
class KeepAliveGatingTest {

    private val ms = 1_000_000L

    @Test
    fun `isDue is true once the gap reaches the interval and false while fresh`() {
        val now = 1_000_000_000_000L
        assertTrue(KeepAlive.isDue(now - 300 * ms, now, 300L), "at the interval")
        assertFalse(KeepAlive.isDue(now - 100 * ms, now, 300L), "fresh")
    }

    @Test
    fun `isDue is always true for a non-positive interval`() {
        val now = 1_000_000_000_000L
        assertTrue(KeepAlive.isDue(now, now, 0L))
        assertTrue(KeepAlive.isDue(now, now, -5L))
    }

    @Test
    fun `pollIntervalMillis stays within 250 to 5000 across a spread of inputs`() {
        listOf(-1L, 0L, 1L, 800L, 1_200L, 20_000L, 100_000L, Long.MAX_VALUE).forEach {
            val poll = KeepAlive.pollIntervalMillis(it)
            assertTrue(poll in 250L..5_000L, "poll for $it was $poll")
        }
    }
}
