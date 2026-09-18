package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.webtools.udp.DatagramType
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Level 1 - a fast, socket-free smoke over the four live tags and ofTagByte. The exhaustive
// input->output mapping over every byte 0x00-0xFF lives in the Level 4a deterministic suite.
@Tag("gating")
class DatagramTypeGatingTest {

    @Test
    fun `the four live tags are the exact literal bytes`() {
        assertEquals(0x80.toByte(), DatagramType.KEEPALIVE.tag)
        assertEquals(0x81.toByte(), DatagramType.PROBE_PING.tag)
        assertEquals(0x82.toByte(), DatagramType.PROBE_PONG.tag)
        assertEquals(0x90.toByte(), DatagramType.UNRELIABLE.tag)
    }

    @Test
    fun `ofTagByte resolves a live tag to its entry`() {
        assertEquals(DatagramType.KEEPALIVE, DatagramType.ofTagByte(0x80.toByte()))
        assertEquals(DatagramType.UNRELIABLE, DatagramType.ofTagByte(0x90.toByte()))
    }

    @Test
    fun `ofTagByte is null for null, an unframed byte, and a Stage-2 reserved tag`() {
        assertNull(DatagramType.ofTagByte(null))
        assertNull(DatagramType.ofTagByte(0x00.toByte()))
        assertNull(DatagramType.ofTagByte(0xA0.toByte()))
    }
}
