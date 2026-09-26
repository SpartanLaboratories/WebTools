package com.spartanlabs.testing.deterministic.webtools.udp

import com.spartanlabs.webtools.udp.DatagramType
import com.spartanlabs.webtools.udp.TransportWireFormat
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Level 4a - exhaustive input -> output mapping for every pure function in TransportWireFormat.
@Tag("deterministic")
class TransportWireFormatTest {

    // --- constants ---

    @Test
    fun `framed protocol version and default channel`() {
        assertEquals(2, TransportWireFormat.FRAMED_PROTOCOL_VERSION)
        assertEquals(0x00.toByte(), TransportWireFormat.DEFAULT_UNRELIABLE_CHANNEL)
        assertEquals(20_000L, TransportWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS)
        assertEquals(1_000L, TransportWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS)
    }

    // --- keepalive ---

    @Test
    fun `keepaliveDatagram is exactly the single tag byte`() {
        assertContentEquals(byteArrayOf(0x80.toByte()), TransportWireFormat.keepaliveDatagram())
    }

    // --- probe ---

    @Test
    fun `probe datagrams lead with the right tag`() {
        assertEquals(DatagramType.PROBE_PING.tag, TransportWireFormat.probePingDatagram(1)[0])
        assertEquals(DatagramType.PROBE_PONG.tag, TransportWireFormat.probePongDatagram(1)[0])
    }

    @Test
    fun `probe sequence is 8-byte big-endian`() {
        assertContentEquals(
            byteArrayOf(0x81.toByte(), 0, 0, 0, 0, 0, 0, 0, 1),
            TransportWireFormat.probePingDatagram(1),
        )
        assertContentEquals(
            byteArrayOf(0x82.toByte(), 0, 0, 0, 0, 0, 0, 0, 1),
            TransportWireFormat.probePongDatagram(1),
        )
    }

    @Test
    fun `probeSequenceOf round-trips every representative sequence`() {
        for (s in listOf(0L, 1L, 255L, 1L shl 32, Long.MAX_VALUE)) {
            assertEquals(s, TransportWireFormat.probeSequenceOf(TransportWireFormat.probePingDatagram(s)))
            assertEquals(s, TransportWireFormat.probeSequenceOf(TransportWireFormat.probePongDatagram(s)))
        }
    }

    @Test
    fun `probeSequenceOf rejects a wrong-length body`() {
        assertNull(TransportWireFormat.probeSequenceOf(byteArrayOf(0x81.toByte(), 0, 0, 0, 0, 0, 0, 1)))
        assertNull(TransportWireFormat.probeSequenceOf(byteArrayOf(0x81.toByte())))
        assertNull(TransportWireFormat.probeSequenceOf(ByteArray(0)))
    }

    // --- unreliable frame ---

    @Test
    fun `unreliableDatagram frames with tag, channel, then payload verbatim`() {
        val framed = TransportWireFormat.unreliableDatagram("hi".toByteArray())
        assertContentEquals(byteArrayOf(0x90.toByte(), 0x00, 'h'.code.toByte(), 'i'.code.toByte()), framed)
        assertEquals(0x00.toByte(), TransportWireFormat.unreliableChannelOf(framed))
        assertContentEquals("hi".toByteArray(), TransportWireFormat.unreliablePayloadOf(framed))
    }

    @Test
    fun `unreliable frame round-trips empty, single-byte, and 64 KB payloads`() {
        for (payload in listOf(ByteArray(0), byteArrayOf(0x7F), ByteArray(64 * 1024) { it.toByte() })) {
            val framed = TransportWireFormat.unreliableDatagram(payload)
            assertContentEquals(payload, TransportWireFormat.unreliablePayloadOf(framed))
            assertEquals(payload.size + 2, framed.size)
        }
    }

    @Test
    fun `unreliableDatagram carries a non-zero channel byte`() {
        val framed = TransportWireFormat.unreliableDatagram(byteArrayOf(1, 2), channel = 0x7.toByte())
        assertEquals(0x7.toByte(), TransportWireFormat.unreliableChannelOf(framed))
        assertContentEquals(byteArrayOf(1, 2), TransportWireFormat.unreliablePayloadOf(framed))
    }

    @Test
    fun `unreliableChannelOf reads a 0xFF channel byte without sign-extension`() {
        val framed = TransportWireFormat.unreliableDatagram(byteArrayOf(1), channel = 0xFF.toByte())
        assertEquals(0xFF.toByte(), TransportWireFormat.unreliableChannelOf(framed))
    }

    @Test
    fun `unreliable accessors reject a datagram shorter than the 2-byte prefix`() {
        assertNull(TransportWireFormat.unreliablePayloadOf(byteArrayOf(0x90.toByte())))
        assertNull(TransportWireFormat.unreliablePayloadOf(ByteArray(0)))
        assertNull(TransportWireFormat.unreliableChannelOf(byteArrayOf(0x90.toByte())))
    }
}
