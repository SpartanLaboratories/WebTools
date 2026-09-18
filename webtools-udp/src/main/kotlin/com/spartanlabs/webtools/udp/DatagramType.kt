package com.spartanlabs.webtools.udp

/**
 * The datagram-type tag carried as **byte 0 of every post-`REGISTERED` datagram**
 * on the framed `webtools-udp` `2.x` transport. The listener switches on this
 * byte before it ever looks at a payload, so control traffic and application
 * traffic no longer share the trimmed-UTF-8 namespace that the pre-`2.0`
 * classifier collided on.
 *
 * ### Value space (byte 0)
 * The tag space is partitioned by plane so later stages of the `2.x` series slot
 * in with no further wire break:
 *
 * | Byte 0        | Entry        | Plane      | Introduced |
 * |---------------|--------------|------------|------------|
 * | `0x00`–`0x7F` | *(none)*     | —          | not a valid tag — the text handshake bootstrap only (`Iam` / `REGISTERED` / `REFUSED`) |
 * | `0x80`        | [KEEPALIVE]  | control    | Stage 1    |
 * | `0x81`        | [PROBE_PING] | control    | Stage 1    |
 * | `0x82`        | [PROBE_PONG] | control    | Stage 1    |
 * | `0x83`–`0x8F` | *reserved*   | control    | future transport control (MTU probe, graceful close, …) |
 * | `0x90`        | [UNRELIABLE] | app data   | Stage 1    |
 * | `0x91`–`0x9F` | *reserved*   | app data   | future unreliable variants (unreliable-sequenced, newest-wins) |
 * | `0xA0`        | [RELIABLE_DATA] | app data | Stage 2 |
 * | `0xA1`        | [RELIABLE_ACK] | app data | Stage 2 |
 * | `0xA2`–`0xAF` | *reserved*   | app data   | reliable-channel control (SACK ranges, window updates, channel open/close) |
 * | `0xB0`–`0xFF` | *reserved*   | —          | unallocated |
 *
 * Every live tag is `>= 0x80`, so it cannot collide with the ASCII first byte of
 * `Iam` (`0x49`) or `REGISTERED` / `REFUSED` (`0x52`); post-handshake text is no
 * longer a wire concept — application text rides an [UNRELIABLE] frame like any
 * other bytes.
 *
 * A Stage-1 peer that receives a reserved tag drops it with a WARN: it is a
 * same-major peer running a later stage, which a Stage-1 ↔ Stage-1 session never
 * produces.
 *
 * @property tag the single byte this type occupies as byte 0 of a datagram
 */
enum class DatagramType(val tag: Byte) {
    /** `0x80` — a bare keepalive datagram (empty body). Replaces the text `KA` token. */
    KEEPALIVE(0x80.toByte()),

    /** `0x81` — a link-quality probe request; body is an 8-byte big-endian `uint64` sequence. */
    PROBE_PING(0x81.toByte()),

    /** `0x82` — a link-quality probe reply; body echoes the request's 8-byte big-endian sequence. */
    PROBE_PONG(0x82.toByte()),

    /** `0x90` — an unreliable application-data frame: `[0x90][channel:1][payload…]`. */
    UNRELIABLE(0x90.toByte()),

    /** `0xA0` — a reliable-ordered data frame: header (see `ReliableWireFormat`) + payload. */
    RELIABLE_DATA(0xA0.toByte()),

    /** `0xA1` — a standalone reliable ack (no seq, no payload). */
    RELIABLE_ACK(0xA1.toByte());

    companion object {
        private val byTag: Map<Byte, DatagramType> = entries.associateBy { it.tag }

        /**
         * The live [DatagramType] for [b], or `null` for a reserved, out-of-range,
         * or `null` byte (`0x00`–`0x7F`, `0x83`–`0x8F`, `0x91`–`0x9F`, `0xA2`–`0xFF`).
         * @param b byte 0 of a datagram, or `null` for an empty datagram
         * @return the matching type, or `null` when [b] is not a live tag
         */
        fun ofTagByte(b: Byte?): DatagramType? = b?.let { byTag[it] }
    }
}
