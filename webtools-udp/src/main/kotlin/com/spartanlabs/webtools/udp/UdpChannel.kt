package com.spartanlabs.webtools.udp

/**
 * A delivery-mode-scoped view of one connection: send under this mode's
 * guarantees, and bind the handler for traffic that arrives under it.
 * Stateless and cheap - hold it, or fetch it per call; either is correct.
 *
 * Reachable as `connection.channel(mode)` on the server side and
 * `client.channel(mode)` on the client side. `channel(...)` itself is an
 * accessor, not a fallible operation - it always returns a handle; the
 * [Result] lives on [send] / [actuate] / [actuateBytes].
 *
 * **Stable Core.** Full semver guarantee — breaking changes only in a major.
 */
interface UdpChannel {
    /** The delivery guarantee this handle provides. */
    val mode: DeliveryMode

    /**
     * Sends [bytes] under [mode]'s guarantees.
     *
     * For [DeliveryMode.RELIABLE_ORDERED], `Result.success` means **accepted
     * for reliable delivery** - buffered, sequenced, and retransmitted until
     * acked - not "already on the wire".
     * @param bytes the raw application payload
     * @return [Result.success] once accepted, or the typed failure that prevented it
     */
    fun send(bytes: ByteArray): Result<Unit>

    /** UTF-8 convenience over [send]`(ByteArray)`. */
    fun send(message: String): Result<Unit> = send(message.toByteArray(Charsets.UTF_8))

    /**
     * Binds [onMessage] as the raw-bytes handler for inbound traffic on this
     * channel: an exact-length, undecoded, untrimmed copy of each message.
     * Per-mode: binding a reliable handler does not disturb the unreliable
     * one, and vice versa.
     * @param onMessage callback invoked with an exact-length copy of each message
     * @return [Result.success] once the handler is registered, or the failure that prevented it
     */
    fun actuateBytes(onMessage: (bytes: ByteArray) -> Unit): Result<Unit>

    /**
     * Binds [onMessage] as the **text** handler for inbound traffic on this
     * channel: each message decoded as UTF-8 and `.trim()`ed, matching
     * [Connection.actuate]'s long-standing semantics exactly. For a binary or
     * whitespace-significant payload use [actuateBytes].
     * @param onMessage callback invoked with the decoded, trimmed text of each message
     * @return [Result.success] once the handler is registered, or the failure that prevented it
     */
    fun actuate(onMessage: (message: String) -> Unit): Result<Unit> =
        actuateBytes { onMessage(String(it, Charsets.UTF_8).trim()) }

    companion object {
        /** Default maximum reliable application payload, in bytes (design D5). */
        const val DEFAULT_MAX_RELIABLE_MESSAGE_BYTES = 1024

        /** Hard ceiling a consumer may raise the cap to, in bytes (design D5). */
        const val MAX_RELIABLE_MESSAGE_BYTES = 8192
    }
}

/**
 * [UdpChannel] over [DeliveryMode.UNRELIABLE] for one [Connection], implemented
 * as a thin façade over its long-standing [Connection.push] / [Connection.actuateBytes]
 * primitives - this is what [Connection.channel]'s default body returns.
 *
 * Suppressed at the class level: both members this delegates to are deprecated
 * (see the `2.0` deprecation ladder), and this is the one place in the module
 * that *must* keep calling them, since the channel is defined as a façade over
 * them.
 */
@Suppress("DEPRECATION")
internal class UnreliableConnectionChannel(private val connection: Connection) : UdpChannel {
    override val mode = DeliveryMode.UNRELIABLE

    override fun send(bytes: ByteArray): Result<Unit> = connection.push(bytes)

    override fun actuateBytes(onMessage: (bytes: ByteArray) -> Unit): Result<Unit> =
        connection.actuateBytes(onMessage)
}

/**
 * A [UdpChannel] whose operations always fail with [UnsupportedOperationException]
 * carrying [reason] - mirrors [Connection.startKeepAlive]'s default. Backs
 * [Connection.channel]`(RELIABLE_ORDERED)` for every [Connection] implementation
 * except the production [UDPConnection].
 */
internal class UnsupportedUdpChannel(override val mode: DeliveryMode, private val reason: String) : UdpChannel {
    override fun send(bytes: ByteArray): Result<Unit> = Result.failure(UnsupportedOperationException(reason))

    override fun actuateBytes(onMessage: (bytes: ByteArray) -> Unit): Result<Unit> =
        Result.failure(UnsupportedOperationException(reason))
}
