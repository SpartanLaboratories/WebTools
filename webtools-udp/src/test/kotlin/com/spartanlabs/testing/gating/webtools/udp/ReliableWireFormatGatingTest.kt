package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.webtools.udp.DatagramType
import com.spartanlabs.webtools.udp.ReliableWireFormat
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// Level 1 - a fast, socket-free smoke over the reliable data/ack datagram codec. The exhaustive
// boundary/round-trip coverage lives in the Level 4a deterministic suite (ReliableWireFormatTest).
@Tag("gating")
class ReliableWireFormatGatingTest {

    @Test
    fun `reliableDataDatagram leads with the 0xA0 tag`() {
        val framed = ReliableWireFormat.reliableDataDatagram(seq = 1, ack = 0, ackBitfield = 0, payload = byteArrayOf(9))
        assertEquals(DatagramType.RELIABLE_DATA.tag, framed[0])
    }

    @Test
    fun `reliableAckDatagram leads with the 0xA1 tag and is exactly 8 bytes`() {
        val framed = ReliableWireFormat.reliableAckDatagram(ack = 0, ackBitfield = 0)
        assertEquals(DatagramType.RELIABLE_ACK.tag, framed[0])
        assertEquals(8, framed.size)
    }

    @Test
    fun `reliable data header and payload round-trip`() {
        val payload = byteArrayOf(1, 2, 3)
        val framed = ReliableWireFormat.reliableDataDatagram(seq = 7, ack = 3, ackBitfield = 0b101, payload = payload)

        val header = assertNotNull(ReliableWireFormat.reliableDataHeaderOf(framed))
        assertEquals(7, header.seq)
        assertEquals(3, header.ack)
        assertEquals(0b101, header.ackBitfield)
        assertEquals(payload.toList(), ReliableWireFormat.reliablePayloadOf(framed)!!.toList())
    }

    @Test
    fun `reliableChannelOf reads byte 1, or null when absent`() {
        val framed = ReliableWireFormat.reliableDataDatagram(
            seq = 0, ack = 0, ackBitfield = 0, payload = byteArrayOf(1), channel = 0x05,
        )
        assertEquals(0x05.toByte(), ReliableWireFormat.reliableChannelOf(framed))
        assertNull(ReliableWireFormat.reliableChannelOf(byteArrayOf(0xA0.toByte())))
    }
}
