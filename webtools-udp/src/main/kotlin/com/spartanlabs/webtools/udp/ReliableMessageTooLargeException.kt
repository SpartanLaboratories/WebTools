package com.spartanlabs.webtools.udp

/**
 * A reliable message exceeded the configured cap. Never thrown; only ever the
 * payload of a [Result.failure] from `UdpChannel.send` on a reliable channel.
 * `2.0` does not fragment (`docs/webtools-udp-protocol.md`): split the message yourself, or
 * raise `reliableMaxMessageBytes` up to [UdpChannel.MAX_RELIABLE_MESSAGE_BYTES] at the
 * consumer's own IP-fragmentation risk. Unlike [ReliableWindowFullException], this keeps its
 * stack trace - it signals a programming or configuration mistake, where the trace is the
 * diagnostic.
 *
 * **Stable Core.** Full semver guarantee — breaking changes only in a major.
 * @property sizeBytes the size of the rejected message, in bytes
 * @property capBytes the configured cap that [sizeBytes] exceeded, in bytes
 */
class ReliableMessageTooLargeException(val sizeBytes: Int, val capBytes: Int) : ReliableSendFailure(
    "Reliable message of $sizeBytes byte(s) exceeds the $capBytes-byte cap; " +
        "v1 does not fragment - split it or raise reliableMaxMessageBytes (max " +
        "${UdpChannel.MAX_RELIABLE_MESSAGE_BYTES})",
)
