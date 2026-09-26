package com.spartanlabs.webtools.udp

/**
 * The reliable-ordered channel's wire header codec: the `0xA0` reliable-data
 * frame and the `0xA1` standalone-ack frame (design doc §6.2). A separate
 * `internal` object rather than an extension of the public
 * [TransportWireFormat] — keeps Stage 3's dispatch-wiring diff clean, and
 * mirrors the `Rtt.kt`-beside-`LinkQualityTracker.kt` precedent of a pure
 * wire/math helper living next to the stateful type that consumes it.
 *
 * ### Layout
 *
 * `0xA0` reliable data — 10-byte header + payload:
 * ```
 *  byte 0        : tag (0xA0)
 *  byte 1        : channel id
 *  bytes 2-3     : seq        (uint16, big-endian)
 *  bytes 4-5     : ack        (uint16, big-endian)
 *  bytes 6-9     : ack bitfield (uint32, big-endian; bit n set => (ack - n - 1) also received)
 *  bytes 10..    : payload
 * ```
 *
 * `0xA1` standalone ack — exactly 8 bytes, no seq, no payload:
 * ```
 *  byte 0        : tag (0xA1)
 *  byte 1        : channel id
 *  bytes 2-3     : ack        (uint16, big-endian)
 *  bytes 4-7     : ack bitfield (uint32, big-endian)
 * ```
 *
 * `seq`/`ack` are carried as `Int` holding a masked `0..65535` value — the
 * 16-bit sequence space RFC 1982 arithmetic (see [SerialSequence]) operates
 * over. Big-endian encode/decode mirrors [TransportWireFormat]'s own
 * probe-sequence codec exactly, at 2-/4-byte widths instead of 8. Stateless.
 *
 * A receiver drops either frame's `channel id` byte with a WARN when it is
 * non-zero (Issue #40) — see [DEFAULT_RELIABLE_CHANNEL].
 */
internal object ReliableWireFormat {
    /**
     * The reliable channel id this build emits, always `0x00`. A receiver drops a datagram
     * whose channel byte is non-zero, with a WARN; see `docs/webtools-udp-protocol.md` for the
     * full channel-byte contract.
     */
    const val DEFAULT_RELIABLE_CHANNEL: Byte = 0x00

    /** The decoded header of an inbound `0xA0` reliable-data datagram. */
    data class ReliableDataHeader(val channel: Byte, val seq: Int, val ack: Int, val ackBitfield: Int)

    /** The decoded header of an inbound `0xA1` standalone-ack datagram. */
    data class ReliableAckHeader(val channel: Byte, val ack: Int, val ackBitfield: Int)

    // Field widths, in bytes.
    private const val CHANNEL_BYTES = 1
    private const val SEQ_BYTES = 2
    private const val ACK_BYTES = 2
    private const val ACK_BITFIELD_BYTES = 4

    /** `[0xA0][channel][seq:2][ack:2][bits:4]` — the fixed reliable-data prefix, before the payload. */
    private const val RELIABLE_DATA_HEADER_BYTES = 1 + CHANNEL_BYTES + SEQ_BYTES + ACK_BYTES + ACK_BITFIELD_BYTES

    /** `[0xA1][channel][ack:2][bits:4]` — the fixed, exact size of a standalone ack. */
    private const val RELIABLE_ACK_BYTES = 1 + CHANNEL_BYTES + ACK_BYTES + ACK_BITFIELD_BYTES

    /**
     * The reliable-data datagram: `[0xA0][channel][seq:2][ack:2][bits:4][payload…]`.
     * @param seq this datagram's sequence number, masked to `0..65535`
     * @param ack the highest sequence received at all from the peer (gaps allowed), masked to `0..65535`
     * @param ackBitfield bit `n` set means `(ack - n - 1)` was also received
     * @param payload the application bytes to carry, placed on the wire verbatim
     * @param channel the reliable channel id; this stage only ever emits [DEFAULT_RELIABLE_CHANNEL]
     * @return the framed datagram, `payload.size + 10` bytes
     */
    fun reliableDataDatagram(
        seq: Int,
        ack: Int,
        ackBitfield: Int,
        payload: ByteArray,
        channel: Byte = DEFAULT_RELIABLE_CHANNEL,
    ): ByteArray {
        val out = ByteArray(RELIABLE_DATA_HEADER_BYTES + payload.size)
        out[0] = DatagramType.RELIABLE_DATA.tag
        out[1] = channel
        writeUShort(out, 2, seq)
        writeUShort(out, 4, ack)
        writeUInt(out, 6, ackBitfield)
        payload.copyInto(out, RELIABLE_DATA_HEADER_BYTES)
        return out
    }

    /**
     * The standalone-ack datagram: `[0xA1][channel][ack:2][bits:4]`, no seq, no payload.
     * @param ack the highest sequence received at all from the peer (gaps allowed), masked to `0..65535`
     * @param ackBitfield bit `n` set means `(ack - n - 1)` was also received
     * @param channel the reliable channel id; this stage only ever emits [DEFAULT_RELIABLE_CHANNEL]
     * @return the 8-byte `0xA1` datagram
     */
    fun reliableAckDatagram(ack: Int, ackBitfield: Int, channel: Byte = DEFAULT_RELIABLE_CHANNEL): ByteArray {
        val out = ByteArray(RELIABLE_ACK_BYTES)
        out[0] = DatagramType.RELIABLE_ACK.tag
        out[1] = channel
        writeUShort(out, 2, ack)
        writeUInt(out, 4, ackBitfield)
        return out
    }

    /**
     * The decoded header of an `0xA0` [datagram], or `null` if it is shorter
     * than the 10-byte fixed header.
     * @param datagram a received `0xA0` datagram
     * @return the decoded [ReliableDataHeader], or `null` if malformed
     */
    fun reliableDataHeaderOf(datagram: ByteArray): ReliableDataHeader? {
        if (datagram.size < RELIABLE_DATA_HEADER_BYTES) return null
        return ReliableDataHeader(
            channel = datagram[1],
            seq = readUShort(datagram, 2),
            ack = readUShort(datagram, 4),
            ackBitfield = readUInt(datagram, 6),
        )
    }

    /**
     * The payload of an `0xA0` [datagram] — bytes 10 onward — or `null` if the
     * datagram is shorter than the 10-byte fixed header.
     * @param datagram a received `0xA0` datagram
     * @return an exact-length copy of the payload, or `null` if malformed
     */
    fun reliablePayloadOf(datagram: ByteArray): ByteArray? =
        if (datagram.size < RELIABLE_DATA_HEADER_BYTES) null
        else datagram.copyOfRange(RELIABLE_DATA_HEADER_BYTES, datagram.size)

    /**
     * The decoded header of an `0xA1` [datagram], or `null` unless it is
     * exactly 8 bytes.
     * @param datagram a received `0xA1` datagram
     * @return the decoded [ReliableAckHeader], or `null` if malformed
     */
    fun reliableAckHeaderOf(datagram: ByteArray): ReliableAckHeader? {
        if (datagram.size != RELIABLE_ACK_BYTES) return null
        return ReliableAckHeader(
            channel = datagram[1],
            ack = readUShort(datagram, 2),
            ackBitfield = readUInt(datagram, 4),
        )
    }

    /**
     * The channel id byte of an `0xA0`/`0xA1` [datagram] (byte 1), or `null` if the
     * datagram has no byte 1. Reads that byte alone - unlike [reliableDataHeaderOf] /
     * [reliableAckHeaderOf] it needs no complete header - so a receiver can screen the
     * channel of a truncated frame too. Mirrors [TransportWireFormat.unreliableChannelOf].
     * @param datagram a received `0xA0` or `0xA1` datagram
     * @return the channel byte, or `null` if the datagram is shorter than 2 bytes
     */
    fun reliableChannelOf(datagram: ByteArray): Byte? =
        if (datagram.size < 1 + CHANNEL_BYTES) null else datagram[1]

    // --- big-endian primitives, mirroring TransportWireFormat.probeDatagram/probeSequenceOf ---

    private fun writeUShort(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value ushr 8 and 0xFF).toByte()
        out[offset + 1] = (value and 0xFF).toByte()
    }

    private fun readUShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun writeUInt(out: ByteArray, offset: Int, value: Int) {
        for (i in 0 until ACK_BITFIELD_BYTES) {
            out[offset + i] = (value ushr (8 * (ACK_BITFIELD_BYTES - 1 - i)) and 0xFF).toByte()
        }
    }

    private fun readUInt(bytes: ByteArray, offset: Int): Int {
        var value = 0
        for (i in 0 until ACK_BITFIELD_BYTES) {
            value = (value shl 8) or (bytes[offset + i].toInt() and 0xFF)
        }
        return value
    }
}
