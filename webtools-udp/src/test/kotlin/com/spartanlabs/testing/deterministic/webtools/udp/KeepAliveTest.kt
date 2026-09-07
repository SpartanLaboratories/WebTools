package com.spartanlabs.testing.deterministic.webtools.udp

import com.spartanlabs.webtools.udp.KeepAlive
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Level 4a - the pure input->output rules of the opt-in scheduled keepalive. Mirrors LivenessTest.
@Tag("deterministic")
class KeepAliveTest {

    private val ms = 1_000_000L

    @Test
    fun `isDue is always true when the interval is zero or negative regardless of the gap`() {
        listOf(0L, -1L, Long.MIN_VALUE).forEach { interval ->
            assertTrue(KeepAlive.isDue(10_000L * ms, 0L, interval))
            assertTrue(KeepAlive.isDue(0L, 10_000L * ms, interval))
        }
    }

    @Test
    fun `isDue boundary is inclusive at exactly the interval`() {
        val now = 1_000_000_000_000L
        assertTrue(KeepAlive.isDue(now - 300 * ms, now, 300L), "exactly the interval is due")
        assertFalse(KeepAlive.isDue(now - 299 * ms, now, 300L), "just under is not")
        assertTrue(KeepAlive.isDue(now - 5_000 * ms, now, 300L), "well over is")
    }

    @Test
    fun `isDue treats a now before lastOutboundAt as not due`() {
        val now = 1_000_000_000_000L
        assertFalse(KeepAlive.isDue(now + 50 * ms, now, 300L))
    }

    @Test
    fun `isDue does not overflow for a Long MAX_VALUE interval`() {
        assertFalse(KeepAlive.isDue(0L, Long.MAX_VALUE, Long.MAX_VALUE))
    }

    @Test
    fun `pollIntervalMillis clamps low, clamps high, and is interval over four in the mid band`() {
        assertEquals(250L, KeepAlive.pollIntervalMillis(0L))
        assertEquals(250L, KeepAlive.pollIntervalMillis(400L))
        assertEquals(1_000L, KeepAlive.pollIntervalMillis(4_000L))
        assertEquals(5_000L, KeepAlive.pollIntervalMillis(20_000L))
        assertEquals(5_000L, KeepAlive.pollIntervalMillis(1_000_000L))
        assertEquals(5_000L, KeepAlive.pollIntervalMillis(Long.MAX_VALUE))
    }

    @Test
    fun `pollIntervalMillis never returns outside 250 to 5000`() {
        listOf(-5L, 0L, 1L, 999L, 1_000L, 20_000L, 100_000L, Long.MAX_VALUE).forEach {
            val poll = KeepAlive.pollIntervalMillis(it)
            assertTrue(poll in 250L..5_000L, "poll for $it was $poll")
        }
    }
}
