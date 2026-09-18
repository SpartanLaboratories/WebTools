package com.spartanlabs.testing.deterministic.webtools.udp

import com.spartanlabs.webtools.udp.DatagramType
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Level 4a - exhaustive input -> output mapping for DatagramType.ofTagByte over every byte value.
@Tag("deterministic")
class DatagramTypeTest {

    @Test
    fun `tag values are the four exact bytes`() {
        assertEquals(0x80.toByte(), DatagramType.KEEPALIVE.tag)
        assertEquals(0x81.toByte(), DatagramType.PROBE_PING.tag)
        assertEquals(0x82.toByte(), DatagramType.PROBE_PONG.tag)
        assertEquals(0x90.toByte(), DatagramType.UNRELIABLE.tag)
    }

    @Test
    fun `every byte 0x00 to 0xFF maps to exactly the value-space table entry or null`() {
        for (v in 0..0xFF) {
            val b = v.toByte()
            val expected = when (v) {
                0x80 -> DatagramType.KEEPALIVE
                0x81 -> DatagramType.PROBE_PING
                0x82 -> DatagramType.PROBE_PONG
                0x90 -> DatagramType.UNRELIABLE
                else -> null
            }
            assertEquals(expected, DatagramType.ofTagByte(b), "byte 0x${v.toString(16)}")
        }
    }

    @Test
    fun `the reserved ranges are all null`() {
        for (v in 0x00..0x7F) assertNull(DatagramType.ofTagByte(v.toByte()), "0x${v.toString(16)}")
        for (v in 0x83..0x8F) assertNull(DatagramType.ofTagByte(v.toByte()), "0x${v.toString(16)}")
        for (v in 0x91..0xFF) assertNull(DatagramType.ofTagByte(v.toByte()), "0x${v.toString(16)}")
    }

    @Test
    fun `the Stage-2 reserved reliable tags are null in Stage 1`() {
        assertNull(DatagramType.ofTagByte(0xA0.toByte()))
        assertNull(DatagramType.ofTagByte(0xA1.toByte()))
    }

    @Test
    fun `null in yields null out`() {
        assertNull(DatagramType.ofTagByte(null))
    }
}
