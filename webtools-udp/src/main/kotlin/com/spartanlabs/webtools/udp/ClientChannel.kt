package com.spartanlabs.webtools.udp

import java.net.InetSocketAddress

/**
 * The narrow seam a [UDPConnection] needs onto the server's shared socket and
 * handler registry: send a datagram, bind a message handler, deregister it.
 *
 * Implemented by [HandshakeCoordinator]. Exists so [UDPConnection] has exactly one
 * mockable collaborator and can be unit-tested with no real socket.
 */
internal interface ClientChannel {
    /**
     * Sends [bytes] as one datagram to [to] over the shared socket.
     * @param bytes the datagram payload
     * @param to the client endpoint to address
     * @return [Result.success] if the datagram was sent, or the failure that prevented it
     */
    fun send(bytes: ByteArray, to: InetSocketAddress): Result<Unit>

    /**
     * Registers [onMessage] as the handler for datagrams whose source is [peer].
     * No-op if [peer] is not a currently-registered client.
     * @param peer the client endpoint whose datagrams [onMessage] should receive
     * @param onMessage the handler, invoked on the server's dispatch executor
     */
    fun bind(peer: InetSocketAddress, onMessage: (String) -> Unit)

    /**
     * Registers [onMessage] as the raw-bytes handler for datagrams whose source is
     * [peer]: it receives an exact-length, undecoded, untrimmed copy of each
     * datagram body. No-op if [peer] is not a currently-registered client.
     *
     * Mutually exclusive with [bind]: binding a bytes handler clears any text
     * handler for [peer], and vice versa.
     * @param peer the client endpoint whose datagrams [onMessage] should receive
     * @param onMessage the handler, invoked on the server's dispatch executor
     */
    fun bindBytes(peer: InetSocketAddress, onMessage: (ByteArray) -> Unit)

    /**
     * Fully removes the registration for [peer]: clears any bound handler and
     * drops the entry from the registry entirely, so it is no longer addressed
     * by broadcast/target-all operations and cannot be rebound via [bind]
     * afterward. No-op if [peer] is not (or is no longer) currently registered.
     * @param peer the client endpoint to deregister
     */
    fun deregister(peer: InetSocketAddress)

    /**
     * Arms (or re-arms) an idle-aware background keepalive toward [peer]: every
     * ~[intervalMillis] of output silence one `KA` datagram is sent, until
     * [cancelKeepAlive], the registration is removed, or the server stops. Last
     * call wins - a repeat call replaces the schedule.
     * @param peer the client endpoint to keep alive
     * @param intervalMillis output-idle time before a keepalive is sent; must be > 0
     * @return [Result.success] once armed, or the failure that prevented it
     */
    fun scheduleKeepAlive(peer: InetSocketAddress, intervalMillis: Long): Result<Unit>

    /**
     * Cancels the scheduled keepalive for [peer]. Idempotent - a no-op if none is
     * armed.
     * @param peer the client endpoint whose keepalive to cancel
     * @return [Result.success] once cancelled, or the failure that prevented it
     */
    fun cancelKeepAlive(peer: InetSocketAddress): Result<Unit>

    /**
     * Arms (or re-arms) an opt-in link-quality probe toward [peer]: every
     * [intervalMillis] one `PING <seq>` is sent, and each matching `PONG` updates a
     * per-connection smoothed RTT / jitter / loss estimate readable via
     * [linkQualityOf]. Last call wins - a repeat call replaces the schedule. Runs
     * until [cancelProbe], the registration is removed, or the server stops.
     * @param peer the client endpoint to probe
     * @param intervalMillis probe period; must be >=
     * [TransportWireFormat.MIN_PROBE_INTERVAL_MILLIS] (250 ms)
     * @return [Result.success] once armed; [Result.failure] with an
     * [IllegalArgumentException] if [intervalMillis] is below the floor, an
     * [IllegalStateException] if [peer] is not registered, or the failure that
     * prevented arming the schedule
     */
    fun scheduleProbe(peer: InetSocketAddress, intervalMillis: Long): Result<Unit>

    /**
     * Cancels the link-quality probe for [peer]. Idempotent - a no-op if none is
     * armed.
     * @param peer the client endpoint whose probe to cancel
     * @return [Result.success] once cancelled, or the failure that prevented it
     */
    fun cancelProbe(peer: InetSocketAddress): Result<Unit>

    /**
     * The latest link-quality snapshot for [peer], or `null` if no probe is
     * running or none has resolved yet.
     * @param peer the client endpoint whose link quality to read
     * @return the current [LinkQuality], or `null`
     */
    fun linkQualityOf(peer: InetSocketAddress): LinkQuality?

    /**
     * Offers [bytes] to [peer]'s reliable-ordered channel, creating the engine and
     * arming its retransmit tick on first use.
     * @param peer the client endpoint to send to
     * @param bytes the application payload to send reliably
     * @return success once accepted for reliable delivery (buffered and sequenced -
     * not necessarily already on the wire); failure with
     * [ReliableMessageTooLargeException] if [bytes] exceeds the cap,
     * [ReliableWindowFullException] if the in-flight window is full, or
     * [IllegalStateException] if [peer] is no longer registered
     */
    fun sendReliable(peer: InetSocketAddress, bytes: ByteArray): Result<Unit>

    /**
     * Binds [onMessage] as [peer]'s reliable-ordered inbound handler. Independent
     * of [bind]/[bindBytes] - binding one never disturbs the other.
     * @param peer the client endpoint whose reliable datagrams [onMessage] should receive
     * @param onMessage the handler, invoked on the server's dispatch executor
     * @return [Result.success] once bound; [Result.failure] with an
     * [IllegalStateException] if [peer] is not registered
     */
    fun bindReliable(peer: InetSocketAddress, onMessage: (ByteArray) -> Unit): Result<Unit>

    /** The configured reliable message-size cap, for the channel handle's pre-check KDoc/tests. */
    val reliableMaxMessageBytes: Int
}
