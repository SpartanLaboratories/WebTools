package com.spartanlabs.webtools.udp

/**
 * The framed `webtools-udp` `2.x` transport wire format: the 1-byte
 * [DatagramType] prefix, the control-datagram encodings ([keepaliveDatagram],
 * [probePingDatagram], [probePongDatagram]), and the unreliable application-data
 * frame ([unreliableDatagram]). The handshake bootstrap stays plain UTF-8 text —
 * see [HandshakeWireFormat].
 *
 * ### The datagram-type tag (byte 0)
 *
 * | Byte 0        | Entry                     | Rest of datagram |
 * |---------------|---------------------------|------------------|
 * | `0x80`        | [DatagramType.KEEPALIVE]   | *(empty)* |
 * | `0x81`        | [DatagramType.PROBE_PING]  | 8-byte big-endian `uint64` probe sequence |
 * | `0x82`        | [DatagramType.PROBE_PONG]  | 8-byte big-endian `uint64` probe sequence (echoed) |
 * | `0x90`        | [DatagramType.UNRELIABLE]  | `[channel:1]` (`2.0`: always `0x00`) + payload verbatim — channel-byte contract: see [DEFAULT_UNRELIABLE_CHANNEL] |
 *
 * ### Boundary note
 * This is a wire break: there is **no** `1.x` ↔ `2.x` post-handshake interop.
 * Both ends must be on `webtools-udp` `2.0.0`+. A cross-major peer is caught at
 * the handshake — the server's `REGISTERED` reply carries
 * [FRAMED_PROTOCOL_VERSION] (`REGISTERED 2`) and a mismatch fails
 * `MultiConnectionUDPClient.handshake` cleanly with an
 * [IncompatibleProtocolException].
 *
 * Every other byte 0 — including the reliable-channel `0xA0`/`0xA1` frames — is listed in
 * [DatagramType]'s own value-space table; the complete wire reference is
 * `docs/webtools-udp-protocol.md`.
 */
object TransportWireFormat {
    /** The wire-protocol major this build speaks; the token in the `REGISTERED 2` reply. */
    const val FRAMED_PROTOCOL_VERSION = 2

    /**
     * The unreliable channel id this build emits, always `0x00`. A receiver drops a datagram
     * whose channel byte is non-zero, with a WARN; see `docs/webtools-udp-protocol.md` for the
     * full channel-byte contract.
     */
    const val DEFAULT_UNRELIABLE_CHANNEL: Byte = 0x00

    /**
     * The recommended output-idle interval, in milliseconds, between keepalive
     * datagrams on a NAT'd path (~20 s). Moved here from `HandshakeWireFormat`.
     */
    const val DEFAULT_KEEPALIVE_INTERVAL_MILLIS = 20_000L

    /**
     * The default link-quality probe interval, in milliseconds (1 s). Moved here
     * from `HandshakeWireFormat`.
     */
    const val DEFAULT_PROBE_INTERVAL_MILLIS = 1_000L

    // 250 is the edge of the range the pre-Issue-#34 poll-divided clamp could honour: below
    // it that clamp already forced a 250 ms cadence. See docs/issue-34-probe-cadence-architecture.md §1.2.
    /**
     * The minimum accepted link-quality probe interval, in milliseconds (250 ms).
     * [Connection.startProbe] and [MultiConnectionUDPClient.startProbe] fail with
     * an [IllegalArgumentException] for an `intervalMillis` below this floor - the
     * value is rejected, never silently rounded up - and send exactly one probe per
     * requested interval at or above it.
     */
    const val MIN_PROBE_INTERVAL_MILLIS = 250L

    /** The fixed size of the big-endian `uint64` probe sequence carried by an `0x81`/`0x82` datagram. */
    private const val PROBE_SEQ_BYTES = 8

    /** `[0x81|0x82]` tag + [PROBE_SEQ_BYTES] sequence. */
    private const val PROBE_DATAGRAM_BYTES = 1 + PROBE_SEQ_BYTES

    /** `[0x90]` tag + `[channel:1]` — the fixed unreliable-frame prefix, before the payload. */
    private const val UNRELIABLE_PREFIX_BYTES = 2

    // --- control ---

    /**
     * The bare keepalive datagram: a single [DatagramType.KEEPALIVE] tag byte,
     * no body.
     * @return `[0x80]`
     */
    fun keepaliveDatagram(): ByteArray = byteArrayOf(DatagramType.KEEPALIVE.tag)

    /**
     * The probe-request datagram: `[0x81][seq: 8-byte big-endian uint64]`.
     * @param seq the prober's sequence number for this probe
     * @return the 9-byte `0x81` datagram
     */
    fun probePingDatagram(seq: Long): ByteArray = probeDatagram(DatagramType.PROBE_PING.tag, seq)

    /**
     * The probe-reply datagram: `[0x82][seq: 8-byte big-endian uint64]`, the
     * sequence copied verbatim from the request.
     * @param seq the sequence carried by the `0x81` request being answered
     * @return the 9-byte `0x82` datagram
     */
    fun probePongDatagram(seq: Long): ByteArray = probeDatagram(DatagramType.PROBE_PONG.tag, seq)

    private fun probeDatagram(tag: Byte, seq: Long): ByteArray {
        val out = ByteArray(PROBE_DATAGRAM_BYTES)
        out[0] = tag
        // Big-endian: most-significant byte first, so probePingDatagram(1) ends ...,0,0,0,0,0,0,0,1.
        for (i in 0 until PROBE_SEQ_BYTES) {
            out[1 + i] = (seq ushr (8 * (PROBE_SEQ_BYTES - 1 - i)) and 0xFF).toByte()
        }
        return out
    }

    /**
     * The probe sequence carried by an `0x81`/`0x82` [datagram], or `null` if the
     * datagram is not exactly a tag byte plus an 8-byte big-endian sequence.
     * @param datagram a received `0x81` or `0x82` datagram
     * @return the decoded `uint64` sequence as a [Long], or `null` if malformed
     */
    fun probeSequenceOf(datagram: ByteArray): Long? {
        if (datagram.size != PROBE_DATAGRAM_BYTES) return null
        var seq = 0L
        for (i in 0 until PROBE_SEQ_BYTES) {
            seq = (seq shl 8) or (datagram[1 + i].toLong() and 0xFF)
        }
        return seq
    }

    // --- unreliable application data ---

    /**
     * Frames [payload] as an unreliable application datagram:
     * `[0x90][channel][payload…]`. Framing is done **here, at the application-data
     * call site** — never in the shared send seam, which also carries unframed
     * control datagrams (keepalive, `PONG`, `REGISTERED`) that must not be
     * double-framed.
     * @param payload the application bytes to carry, placed on the wire verbatim
     * @param channel the unreliable channel id; this build only ever emits
     * [DEFAULT_UNRELIABLE_CHANNEL]
     * @return the framed datagram, `payload.size + 2` bytes
     */
    fun unreliableDatagram(payload: ByteArray, channel: Byte = DEFAULT_UNRELIABLE_CHANNEL): ByteArray {
        val out = ByteArray(UNRELIABLE_PREFIX_BYTES + payload.size)
        out[0] = DatagramType.UNRELIABLE.tag
        out[1] = channel
        payload.copyInto(out, UNRELIABLE_PREFIX_BYTES)
        return out
    }

    /**
     * The payload of an `0x90` [datagram] — bytes 2 onward — or `null` if the
     * datagram is shorter than the 2-byte `[0x90][channel]` prefix.
     * @param datagram a received `0x90` datagram
     * @return an exact-length copy of the payload, or `null` if malformed
     */
    fun unreliablePayloadOf(datagram: ByteArray): ByteArray? =
        if (datagram.size < UNRELIABLE_PREFIX_BYTES) null
        else datagram.copyOfRange(UNRELIABLE_PREFIX_BYTES, datagram.size)

    /**
     * The channel id byte of an `0x90` [datagram] (byte 1), or `null` if the
     * datagram is shorter than the 2-byte prefix.
     * @param datagram a received `0x90` datagram
     * @return the channel byte, or `null` if malformed
     */
    fun unreliableChannelOf(datagram: ByteArray): Byte? =
        if (datagram.size < UNRELIABLE_PREFIX_BYTES) null else datagram[1]
}
