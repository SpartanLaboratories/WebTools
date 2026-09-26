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
    fun `tag values are the six exact bytes`() {
        assertEquals(0x80.toByte(), DatagramType.KEEPALIVE.tag)
        assertEquals(0x81.toByte(), DatagramType.PROBE_PING.tag)
        assertEquals(0x82.toByte(), DatagramType.PROBE_PONG.tag)
        assertEquals(0x90.toByte(), DatagramType.UNRELIABLE.tag)
        assertEquals(0xA0.toByte(), DatagramType.RELIABLE_DATA.tag)
        assertEquals(0xA1.toByte(), DatagramType.RELIABLE_ACK.tag)
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
                0xA0 -> DatagramType.RELIABLE_DATA
                0xA1 -> DatagramType.RELIABLE_ACK
                else -> null
            }
            assertEquals(expected, DatagramType.ofTagByte(b), "byte 0x${v.toString(16)}")
        }
    }

    @Test
    fun `the reserved ranges are all null`() {
        for (v in 0x00..0x7F) assertNull(DatagramType.ofTagByte(v.toByte()), "0x${v.toString(16)}")
        for (v in 0x83..0x8F) assertNull(DatagramType.ofTagByte(v.toByte()), "0x${v.toString(16)}")
        for (v in 0x91..0x9F) assertNull(DatagramType.ofTagByte(v.toByte()), "0x${v.toString(16)}")
        for (v in 0xA2..0xFF) assertNull(DatagramType.ofTagByte(v.toByte()), "0x${v.toString(16)}")
    }

    @Test
    fun `the Stage-2 reliable tags are now live, promoted from reserved`() {
        assertEquals(DatagramType.RELIABLE_DATA, DatagramType.ofTagByte(0xA0.toByte()))
        assertEquals(DatagramType.RELIABLE_ACK, DatagramType.ofTagByte(0xA1.toByte()))
    }

    @Test
    fun `null in yields null out`() {
        assertNull(DatagramType.ofTagByte(null))
    }
}
