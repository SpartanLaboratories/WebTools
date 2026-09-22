package com.spartanlabs.webtools.udp

/**
 * Which delivery guarantee a [UdpChannel] provides. Each entry has its own
 * sequence space and its own inbound handler (see [Connection.channel] /
 * [UdpChannel.actuateBytes]).
 */
enum class DeliveryMode {
    /**
     * Fire-and-forget: no acks, no ordering guarantee beyond arrival order, the
     * `0x90` frame. Use for newest-wins state (position snapshots, etc.) where a
     * dropped or reordered message is harmless because a fresher one is already
     * on the way.
     */
    UNRELIABLE,

    /**
     * Acked, retransmitted, and delivered exactly once, in send order - the
     * `0xA0`/`0xA1` frames. **Head-of-line blocking is inherent**: a missing
     * message stalls delivery of every message sent after it on this channel,
     * to this peer, until it is recovered. Use for discrete events (chat,
     * ability casts, inventory changes) and never for bulk or latency-critical
     * data - use [UNRELIABLE] for that.
     */
    RELIABLE_ORDERED,
}
