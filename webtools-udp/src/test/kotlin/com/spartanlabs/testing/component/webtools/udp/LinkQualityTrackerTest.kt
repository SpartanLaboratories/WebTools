package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.webtools.udp.LinkQualityTracker
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 2 - the per-endpoint link-quality accumulator in isolation, driven with a
// controllable nanoClock so RTT and loss aging are deterministic. No socket, no timer.
@Tag("component")
class LinkQualityTrackerTest {

    private var now = 1_000_000_000_000L
    private val clock = { now }
    private val ms = 1_000_000L

    private fun tracker(window: Int = 8) = LinkQualityTracker(nanoClock = clock, windowSize = window)

    private fun advance(millis: Long) {
        now += millis * ms
    }

    @Test
    fun `snapshot is null before the first completeProbe`() {
        val t = tracker()
        assertNull(t.snapshot())
        t.beginProbe()
        assertNull(t.snapshot(), "still null with only an in-flight probe")
    }

    @Test
    fun `the first delivered sample seeds SRTT and RTTVAR per RFC 6298`() {
        val t = tracker()
        val seq = t.beginProbe()
        advance(40)
        t.completeProbe(seq)

        val snap = assertNotNull(t.snapshot())
        assertEquals(40.0, snap.rttMillis, 0.001)
        assertEquals(20.0, snap.rttVarianceMillis, 0.001)
        assertEquals(0.0, snap.packetLossRatio, 0.001)
        assertEquals(1L, snap.probesSent)
        assertEquals(1L, snap.probesDelivered)
    }

    @Test
    fun `a second sample moves SRTT toward it but stays nearer the first`() {
        val t = tracker()
        val s1 = t.beginProbe()
        advance(40)
        t.completeProbe(s1)
        val s2 = t.beginProbe()
        advance(80)
        t.completeProbe(s2)

        val rtt = assertNotNull(t.snapshot()).rttMillis
        assertTrue(rtt in 40.0..80.0, "was $rtt")
        assertTrue(rtt < 60.0, "alpha = 1/8 keeps it near the first sample, was $rtt")
    }

    @Test
    fun `completeProbe for an unknown or already-delivered seq is a no-op`() {
        val t = tracker()
        val seq = t.beginProbe()
        advance(30)
        t.completeProbe(seq)
        t.completeProbe(seq)        // duplicate
        t.completeProbe(9_999L)     // unknown

        assertEquals(1L, assertNotNull(t.snapshot()).probesDelivered)
    }

    @Test
    fun `sweep moves in-flight probes past the horizon to LOST and the loss ratio reflects it`() {
        val t = tracker()
        t.probeIntervalMillis = 300L

        val delivered = t.beginProbe()
        advance(20)
        t.completeProbe(delivered)

        val lost1 = t.beginProbe()
        val lost2 = t.beginProbe()
        advance(3 * 300 + 1)
        t.sweep()

        val snap = assertNotNull(t.snapshot())
        // 2 lost, 1 delivered -> 2/3
        assertEquals(2.0 / 3.0, snap.packetLossRatio, 0.001)
        assertEquals(3L, snap.probesSent)
        assertEquals(1L, snap.probesDelivered)
        // referencing the seqs keeps them meaningful in the assertion story
        assertTrue(lost1 != lost2 && delivered != lost1)
    }

    @Test
    fun `only the last windowSize probes inform the loss ratio`() {
        val t = tracker(window = 4)
        t.probeIntervalMillis = 100L

        // 4 probes that will all be swept LOST, then evicted
        repeat(4) { t.beginProbe() }
        advance(3 * 100 + 1)
        t.sweep()

        // 4 fresh probes, all delivered
        repeat(4) {
            val s = t.beginProbe()
            advance(10)
            t.completeProbe(s)
        }

        val snap = assertNotNull(t.snapshot())
        assertEquals(0.0, snap.packetLossRatio, 0.001, "the LOST probes were evicted from the ring")
        assertEquals(8L, snap.probesSent)
        assertEquals(4L, snap.probesDelivered)
    }
}
