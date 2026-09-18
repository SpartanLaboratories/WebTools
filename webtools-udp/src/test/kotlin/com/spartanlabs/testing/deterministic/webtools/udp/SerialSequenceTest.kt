package com.spartanlabs.testing.deterministic.webtools.udp

import com.spartanlabs.webtools.udp.SerialSequence
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Level 4a - exhaustive input -> output mapping for RFC 1982 serial-number arithmetic over the
// 16-bit sequence space (design doc D4). The headline case is correctness across the
// 65535 -> 0 wraparound boundary, where naive integer comparison would get the order backwards.
@Tag("deterministic")
class SerialSequenceTest {

    // --- wrap / add ---

    @Test
    fun `wrap normalizes any integer into 0 to 65535`() {
        assertEquals(0, SerialSequence.wrap(0))
        assertEquals(65_535, SerialSequence.wrap(65_535))
        assertEquals(0, SerialSequence.wrap(65_536))
        assertEquals(65_535, SerialSequence.wrap(-1))
        assertEquals(0, SerialSequence.wrap(-65_536))
    }

    @Test
    fun `add wraps forward and backward across the 65536 boundary`() {
        assertEquals(0, SerialSequence.add(65_535, 1))
        assertEquals(65_535, SerialSequence.add(0, -1))
        assertEquals(5, SerialSequence.add(65_535, 6))
        assertEquals(1, SerialSequence.add(3, -2))
    }

    // --- compare / lessThan: the RFC 1982 uint16 wraparound headline case ---

    @Test
    fun `compare treats 65535 as preceding 0 - the wraparound boundary`() {
        assertTrue(SerialSequence.compare(65_535, 0) < 0, "65535 precedes the wrapped-to 0")
        assertTrue(SerialSequence.compare(0, 65_535) > 0, "0 follows the wrapped-from 65535")
        assertTrue(SerialSequence.lessThan(65_535, 0))
        assertFalse(SerialSequence.lessThan(0, 65_535))
    }

    @Test
    fun `compare is a strict, non-wrapping order for two nearby non-wrapping values`() {
        assertTrue(SerialSequence.compare(10, 20) < 0)
        assertTrue(SerialSequence.compare(20, 10) > 0)
        assertEquals(0, SerialSequence.compare(20, 20))
        assertTrue(SerialSequence.lessThan(10, 20))
        assertFalse(SerialSequence.lessThan(20, 10))
    }

    @Test
    fun `compare orders correctly at every point around the wraparound boundary`() {
        // 65533, 65534, 65535, 0, 1, 2 - each strictly precedes the next.
        val sequence = listOf(65_533, 65_534, 65_535, 0, 1, 2)
        for (i in 0 until sequence.size - 1) {
            assertTrue(
                SerialSequence.lessThan(sequence[i], sequence[i + 1]),
                "${sequence[i]} should precede ${sequence[i + 1]}",
            )
        }
    }

    @Test
    fun `compare is antisymmetric for every representative pair away from the antipodal distance`() {
        val samples = listOf(0, 1, 100, 32_767, 32_769, 65_000, 65_535)
        for (a in samples) for (b in samples) {
            if (a == b) continue
            val delta = SerialSequence.wrap(a - b)
            if (delta == 32_768) continue // the antipodal case - documented separately below
            val ab = SerialSequence.compare(a, b)
            val ba = SerialSequence.compare(b, a)
            assertTrue(
                (ab < 0 && ba > 0) || (ab > 0 && ba < 0),
                "compare($a, $b)=$ab and compare($b, $a)=$ba must have opposite signs",
            )
        }
    }

    // --- antipodal case: RFC 1982-undefined, resolved deterministically but arbitrarily ---

    @Test
    fun `the antipodal distance (half the sequence space) resolves deterministically, not by throwing`() {
        // a and b are exactly 32768 apart - RFC 1982 leaves this case's ordering formally
        // undefined. This module never exercises it in practice (every window used is far
        // smaller), so the only contract locked here is "does not throw, is a total function".
        val a = 0
        val b = 32_768
        val result = SerialSequence.compare(a, b)
        // Calling it again must be the same answer - deterministic, not merely non-throwing.
        assertEquals(result, SerialSequence.compare(a, b))
    }

    // --- inWindow ---

    @Test
    fun `inWindow accepts the base and the last in-range offset, rejects the first out-of-range offset`() {
        assertTrue(SerialSequence.inWindow(base = 100, seq = 100, windowSize = 10))
        assertTrue(SerialSequence.inWindow(base = 100, seq = 109, windowSize = 10))
        assertFalse(SerialSequence.inWindow(base = 100, seq = 110, windowSize = 10))
    }

    @Test
    fun `inWindow rejects a seq behind the base`() {
        assertFalse(SerialSequence.inWindow(base = 100, seq = 99, windowSize = 10))
    }

    @Test
    fun `inWindow wraps correctly across the 65536 boundary`() {
        assertTrue(SerialSequence.inWindow(base = 65_530, seq = 65_535, windowSize = 10))
        assertTrue(SerialSequence.inWindow(base = 65_530, seq = 3, windowSize = 10))
        assertFalse(SerialSequence.inWindow(base = 65_530, seq = 65_529, windowSize = 10))
    }

    @Test
    fun `inWindow never matches for a non-positive windowSize`() {
        assertFalse(SerialSequence.inWindow(base = 100, seq = 100, windowSize = 0))
        assertFalse(SerialSequence.inWindow(base = 100, seq = 100, windowSize = -5))
    }
}
