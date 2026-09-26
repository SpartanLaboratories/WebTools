package com.spartanlabs.webtools.udp

/**
 * The supertype of every failure a reliable [UdpChannel.send] can report.
 * Deliberately **open, not sealed**: a caller can `catch`/`is`-check one type
 * today, and a future reliable-send failure can join this hierarchy in a minor
 * version without breaking anyone's exhaustive `when`. `abstract` because
 * nothing should ever construct or throw the bare supertype - only
 * [ReliableWindowFullException] and [ReliableMessageTooLargeException] do,
 * and only ever as a [Result.failure] payload, never thrown.
 *
 * **Stable Core.** Full semver guarantee — breaking changes only in a major; the hierarchy
 * stays open rather than sealed, so a new subtype is additive, never breaking.
 */
abstract class ReliableSendFailure(message: String) : Exception(message)
