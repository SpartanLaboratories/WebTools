package com.spartanlabs.webtools.udp

/**
 * The reliable in-flight window is full - [inFlight] messages are already
 * awaiting acknowledgement. Never thrown; only ever the payload of a
 * [Result.failure] from `UdpChannel.send` on a reliable channel - the sibling
 * of [HandshakeRefusedException] in that respect. This is backpressure, not an
 * error: retry after the next retransmit tick, coalesce the message with a
 * later one, or drop it.
 * @property inFlight how many messages were unacked when the send was rejected
 */
class ReliableWindowFullException(val inFlight: Int) : ReliableSendFailure(
    "The reliable in-flight window is full ($inFlight message(s) unacked); retry, coalesce, or drop",
) {
    // A Throwable pays for its stack trace at construction, not at throw. This exception is used
    // purely as a Result.failure control-flow value - a sender looping at frame rate against a
    // full window would otherwise construct one every frame - and its trace would only ever name
    // UdpChannel.send, never anything diagnostic. The standard no-op override for that case.
    override fun fillInStackTrace(): Throwable = this
}
