package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.webtools.udp.ReliableRetransmitBuffer
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 2 - the §3.7 per-tick retransmit cap: dueForRetransmit(limit) must mutate only the
// entries it actually returns, or the uncapped remainder would be marked retransmitted without
// ever reaching the wire (the bug this stage's implementer note calls out explicitly).
@Tag("component")
class ReliableRetransmitBufferLimitTest {

    private var now = 1_000_000_000_000L
    private val clock = { now }
    private val ms = 1_000_000L

    private fun buffer(window: Int = 256) = ReliableRetransmitBuffer(windowSize = window, nanoClock = clock)

    private fun advance(millis: Long) {
        now += millis * ms
    }

    @Test
    fun `dueForRetransmit returns at most limit entries and mutates only those`() {
        val b = buffer(window = 10)
        repeat(5) { b.offer(byteArrayOf(it.toByte()), initialRtoMillis = 100L) }
        advance(150) // all 5 entries are due

        val due = b.dueForRetransmit(now, rtoCapMillis = 5_000L, limit = 2)

        assertEquals(2, due.size, "only limit entries returned")

        // The 3 entries NOT returned must be untouched: still due immediately (lastSentAtNanos
        // was never bumped) and still at their original 100 ms RTO (never doubled).
        advance(1) // negligible - the untouched entries are still overdue
        val secondPass = b.dueForRetransmit(now, rtoCapMillis = 5_000L, limit = 100)
        assertEquals(3, secondPass.size, "the remainder from the first call comes back on the next call")

        // The 2 entries the first call DID return are now backed off (RTO doubled to 200 ms) and
        // freshly stamped, so they must NOT reappear in this second pass at only +1 ms.
        val secondPassSeqs = secondPass.map { it.seq }.toSet()
        val firstPassSeqs = due.map { it.seq }.toSet()
        assertTrue(firstPassSeqs.intersect(secondPassSeqs).isEmpty(), "mutated entries must not double-fire")
    }

    @Test
    fun `a limit of Int MAX_VALUE default returns every due entry - unbounded, unchanged behaviour`() {
        val b = buffer(window = 10)
        repeat(5) { b.offer(byteArrayOf(it.toByte()), initialRtoMillis = 100L) }
        advance(150)

        val due = b.dueForRetransmit(now, rtoCapMillis = 5_000L)

        assertEquals(5, due.size)
    }
}
