package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.webtools.udp.SerialSequence
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Level 1 - a fast, socket-free smoke over RFC 1982 serial-number arithmetic. The exhaustive
// wraparound-boundary coverage lives in the Level 4a deterministic suite (SerialSequenceTest).
@Tag("gating")
class SerialSequenceGatingTest {

    @Test
    fun `compare is zero for equal values and signed for ordered values`() {
        assertEquals(0, SerialSequence.compare(5, 5))
        assertTrue(SerialSequence.compare(5, 10) < 0)
        assertTrue(SerialSequence.compare(10, 5) > 0)
    }

    @Test
    fun `add wraps at the 65536 boundary`() {
        assertEquals(0, SerialSequence.add(65_535, 1))
        assertEquals(65_535, SerialSequence.add(0, -1))
    }

    @Test
    fun `inWindow accepts the base and rejects a seq far ahead`() {
        assertTrue(SerialSequence.inWindow(10, base = 10, windowSize = 256))
        assertFalse(SerialSequence.inWindow(10_000, base = 10, windowSize = 256))
    }
}
