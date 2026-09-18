package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.webtools.udp.TransportWireFormat
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Level 1 - a fast, socket-free smoke over TransportWireFormat's framing helpers. The
// exhaustive input->output mapping lives in the Level 4a deterministic suite.
@Tag("gating")
class TransportWireFormatGatingTest {

    @Test
    fun `the wire-protocol major this build speaks is 2`() {
        assertEquals(2, TransportWireFormat.FRAMED_PROTOCOL_VERSION)
    }

    @Test
    fun `keepaliveDatagram is exactly the single 0x80 tag byte`() {
        assertContentEquals(byteArrayOf(0x80.toByte()), TransportWireFormat.keepaliveDatagram())
    }

    @Test
    fun `probePingDatagram round-trips through probeSequenceOf`() {
        assertEquals(7L, TransportWireFormat.probeSequenceOf(TransportWireFormat.probePingDatagram(7L)))
    }

    @Test
    fun `unreliableDatagram round-trips its payload and channel, and leads with 0x90`() {
        val framed = TransportWireFormat.unreliableDatagram("hi".toByteArray())

        assertEquals(0x90.toByte(), framed[0])
        assertContentEquals("hi".toByteArray(), TransportWireFormat.unreliablePayloadOf(framed))
        assertEquals(TransportWireFormat.DEFAULT_UNRELIABLE_CHANNEL, TransportWireFormat.unreliableChannelOf(framed))
    }

    @Test
    fun `unreliablePayloadOf rejects a datagram shorter than the 2-byte prefix`() {
        assertNull(TransportWireFormat.unreliablePayloadOf(byteArrayOf(0x90.toByte())))
    }
}
