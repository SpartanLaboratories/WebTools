package com.spartanlabs.testing.deterministic.webtools.udp

import com.spartanlabs.webtools.udp.DatagramType
import com.spartanlabs.webtools.udp.ReliableWireFormat
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Level 4a - exhaustive input -> output mapping for every pure function in ReliableWireFormat.
// Mirrors TransportWireFormatTest's shape for the reliable-channel header codec.
@Tag("deterministic")
class ReliableWireFormatTest {

    // --- constants / tags ---

    @Test
    fun `reliable datagrams lead with the right tag and channel`() {
        val data = ReliableWireFormat.reliableDataDatagram(seq = 1, ack = 0, ackBitfield = 0, payload = ByteArray(0))
        val ack = ReliableWireFormat.reliableAckDatagram(ack = 0, ackBitfield = 0)
        assertEquals(DatagramType.RELIABLE_DATA.tag, data[0])
        assertEquals(ReliableWireFormat.DEFAULT_RELIABLE_CHANNEL, data[1])
        assertEquals(DatagramType.RELIABLE_ACK.tag, ack[0])
        assertEquals(ReliableWireFormat.DEFAULT_RELIABLE_CHANNEL, ack[1])
    }

    @Test
    fun `reliableDataDatagram carries a non-default channel byte`() {
        val framed = ReliableWireFormat.reliableDataDatagram(0, 0, 0, ByteArray(0), channel = 0x7.toByte())
        assertEquals(0x7.toByte(), framed[1])
    }

    // --- sizes ---

    @Test
    fun `reliable data overhead is exactly 10 bytes, ack-only is exactly 8`() {
        assertEquals(10, ReliableWireFormat.reliableDataDatagram(0, 0, 0, ByteArray(0)).size)
        assertEquals(13, ReliableWireFormat.reliableDataDatagram(0, 0, 0, byteArrayOf(1, 2, 3)).size)
        assertEquals(8, ReliableWireFormat.reliableAckDatagram(0, 0).size)
    }

    // --- big-endian byte order ---

    @Test
    fun `seq, ack, and the bitfield are encoded big-endian`() {
        val framed = ReliableWireFormat.reliableDataDatagram(seq = 1, ack = 2, ackBitfield = 1, payload = ByteArray(0))
        assertContentEquals(
            byteArrayOf(
                DatagramType.RELIABLE_DATA.tag, ReliableWireFormat.DEFAULT_RELIABLE_CHANNEL,
                0, 1, // seq = 1, big-endian uint16
                0, 2, // ack = 2, big-endian uint16
                0, 0, 0, 1, // ackBitfield = 1, big-endian uint32
            ),
            framed,
        )
    }

    @Test
    fun `an ack-only datagram is encoded big-endian with no seq field`() {
        val framed = ReliableWireFormat.reliableAckDatagram(ack = 258, ackBitfield = 0x01020304)
        assertContentEquals(
            byteArrayOf(
                DatagramType.RELIABLE_ACK.tag, ReliableWireFormat.DEFAULT_RELIABLE_CHANNEL,
                0x01, 0x02, // ack = 258, big-endian uint16
                0x01, 0x02, 0x03, 0x04, // ackBitfield, big-endian uint32
            ),
            framed,
        )
    }

    // --- round trips over the seq/ack boundary values ---

    @Test
    fun `reliable data header round-trips every representative seq and ack, including the uint16 boundaries`() {
        for (v in listOf(0, 1, 255, 256, 32_768, 65_534, 65_535)) {
            val framed = ReliableWireFormat.reliableDataDatagram(seq = v, ack = v, ackBitfield = -1, payload = byteArrayOf(9))
            val header = ReliableWireFormat.reliableDataHeaderOf(framed)!!
            assertEquals(v, header.seq, "seq round-trip for $v")
            assertEquals(v, header.ack, "ack round-trip for $v")
            assertEquals(-1, header.ackBitfield, "a full 32-bit bitfield round-trips including the sign bit")
        }
    }

    @Test
    fun `reliablePayloadOf round-trips empty, single-byte, and large payloads`() {
        for (payload in listOf(ByteArray(0), byteArrayOf(0x7F), ByteArray(8 * 1024) { it.toByte() })) {
            val framed = ReliableWireFormat.reliableDataDatagram(0, 0, 0, payload)
            assertContentEquals(payload, ReliableWireFormat.reliablePayloadOf(framed))
        }
    }

    @Test
    fun `reliableAckHeaderOf round-trips every representative ack and bitfield`() {
        for (v in listOf(0, 1, 32_768, 65_535)) {
            val framed = ReliableWireFormat.reliableAckDatagram(ack = v, ackBitfield = v)
            val header = ReliableWireFormat.reliableAckHeaderOf(framed)!!
            assertEquals(v, header.ack)
            assertEquals(v, header.ackBitfield)
        }
    }

    // --- truncation -> null, at every truncation length ---

    @Test
    fun `reliableDataHeaderOf and reliablePayloadOf are null for every length shorter than the 10-byte header`() {
        for (len in 0 until 10) {
            val truncated = ByteArray(len) { DatagramType.RELIABLE_DATA.tag }
            assertNull(ReliableWireFormat.reliableDataHeaderOf(truncated), "length $len")
            assertNull(ReliableWireFormat.reliablePayloadOf(truncated), "length $len")
        }
    }

    @Test
    fun `reliableAckHeaderOf is null for every length other than exactly 8`() {
        for (len in listOf(0, 1, 7, 9, 10, 20)) {
            val wrongLength = ByteArray(len) { DatagramType.RELIABLE_ACK.tag }
            assertNull(ReliableWireFormat.reliableAckHeaderOf(wrongLength), "length $len")
        }
    }
}
