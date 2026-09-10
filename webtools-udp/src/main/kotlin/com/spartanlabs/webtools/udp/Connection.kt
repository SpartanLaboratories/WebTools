package com.spartanlabs.webtools.udp

import java.net.InetSocketAddress

/**
 * A single named, logical connection to one remote client of a
 * [MultiConnectionUDPServer].
 *
 * [UDPConnection] is the production implementation: a socket-free handle over the
 * server's single shared UDP socket. The interface exists so the server's
 * registration and handshake logic can be exercised against a socket-free fake.
 *
 * [UDPConnection] is still socket-free and owns no thread of its own; an opt-in
 * scheduled keepalive ([startKeepAlive]) borrows the server's shared
 * `mcups-keepalive` thread rather than starting one per connection, and an opt-in
 * link-quality probe ([startProbe]) likewise borrows the server's shared
 * `mcups-probe` thread and updates a per-connection estimator.
 *
 * @property name a human-readable identifier for this connection, typically
 * supplied by the client during the handshake
 * @property peer the client's post-NAT endpoint, learned from its `Iam` datagram;
 * every datagram to this client is addressed here so it rides the NAT mapping the
 * handshake opened
 */
interface Connection {
    val name: String

    val peer: InetSocketAddress

    /**
     * Registers [onMessage] as the handler for datagrams from this client. No
     * socket is bound - the server already owns the one shared socket; this only
     * routes inbound datagrams whose source matches [peer] to [onMessage].
     * Calling this on a connection that has been [terminate]d is a silent no-op
     * (see [terminate]). The handler receives the trimmed UTF-8 text of each
     * datagram; for a binary protocol use [actuateBytes] instead.
     * @param onMessage callback invoked with the decoded text of each message received;
     * it runs on the server's single-threaded dispatch executor, not the caller's
     * thread, so it must return promptly - a slow handler delays delivery to other clients
     * @return [Result.success] once the handler is registered, or the failure that prevented it
     */
    fun actuate(onMessage: (message: String) -> Unit): Result<Unit>

    /**
     * Registers [onMessage] as the raw-bytes handler for datagrams from this
     * client: it is handed an exact-length copy of each inbound datagram body -
     * no UTF-8 decode, no `.trim()`.
     *
     * Mutually exclusive with [actuate] - the last call wins; binding a bytes
     * handler clears any text handler and vice versa. A consumer that wants both
     * views decodes inside the bytes handler.
     *
     * Handshake (`Iam`) and keepalive (`KA`) datagrams are filtered upstream and
     * never delivered here. Note the classifier runs on the trimmed UTF-8 view of
     * every datagram, so a binary payload that decodes/trims to a control token is
     * still intercepted - lead binary application datagrams with a byte that
     * cannot start `Iam`/`KA` and is not ASCII whitespace (e.g. `0x00` or any byte
     * `>= 0x80`).
     *
     * The delivered copy is at most 65507 bytes (the maximum UDP payload over
     * IPv4); an inbound datagram larger than that is delivered truncated to that
     * length, not rejected.
     *
     * The default implementation is a lossy UTF-8 round-trip over [actuate];
     * [UDPConnection] overrides it to deliver the datagram bytes verbatim.
     * @param onMessage callback invoked with an exact-length copy of each datagram;
     * it runs on the server's single-threaded dispatch executor, so it must return promptly
     * @return [Result.success] once the handler is registered, or the failure that prevented it
     */
    fun actuateBytes(onMessage: (bytes: ByteArray) -> Unit): Result<Unit> =
        actuate { onMessage(it.toByteArray(Charsets.UTF_8)) }

    /**
     * Fully deregisters this connection: its message handler is cleared and its
     * registration is removed from the server entirely, so it is no longer
     * addressed by [MultiConnectionUDPServer.pushToAll] and any subsequent
     * datagram from [peer] is dropped as unregistered. This is final - calling
     * [actuate] again afterward is a silent no-op, since there is no longer a
     * registration to bind against; the client must complete a fresh `Iam`
     * handshake to be registered again.
     *
     * Call this whenever a connection becomes stale from the application's point
     * of view - e.g. an application-level refusal (over capacity, banned, etc.)
     * discovered only after the WebTools-level handshake already completed.
     * @return [Result.success] once deregistered, or the failure that prevented it
     */
    fun terminate(): Result<Unit>

    /**
     * Sends [message] as the body of one datagram to [peer] over the server's
     * shared socket. This is the abstract send primitive; the default
     * [push]`(ByteArray)` delegates to it via a UTF-8 decode unless an implementor
     * overrides that overload to send raw bytes.
     *
     * This is how a subclass sends to one specific client over the shared channel -
     * key connections by name or [peer] and call [push] on the one you want;
     * [MultiConnectionUDPServer.pushToAll] is the broadcast-to-everyone path, not
     * the only per-client path.
     * @param message the text to send
     * @return [Result.success] if the message was sent, or the failure that prevented it
     */
    fun push(message: String): Result<Unit>

    /**
     * Sends [bytes] as one raw datagram to [peer] over the server's shared socket:
     * the payload is placed on the wire verbatim - no encoding, no trim.
     *
     * The default implementation round-trips through [push]`(String)` and is
     * therefore lossy for non-UTF-8 payloads; [UDPConnection] overrides it to send
     * the bytes unchanged.
     *
     * A payload larger than the OS datagram limit fails the [Result] (the cause is
     * logged) rather than being sent; for real-network use keep frames under the
     * path MTU (~1200 bytes) to avoid IP fragmentation.
     * @param bytes the raw datagram payload
     * @return [Result.success] if the datagram was sent, or the failure that prevented it
     */
    fun push(bytes: ByteArray): Result<Unit> = push(String(bytes, Charsets.UTF_8))

    /**
     * Sends one minimal keepalive datagram to [peer] to keep its NAT mapping
     * warm. A one-shot: the caller schedules it (recommended ~20 s idle
     * cadence). Owns no timer or thread. For a library-managed cadence use
     * [startKeepAlive].
     *
     * Direction caveat: this handle lives on the **server** side, so this is a
     * server->client datagram. It refreshes the mapping timer on
     * endpoint-independent-filtering (full-cone / restricted-cone) NATs and lets
     * the server verify its send path, but it does **not** reliably refresh
     * port-restricted or symmetric NATs. The authoritative keepalive must be sent
     * by the client on its own socket every ~20 s.
     * @return [Result.success] if the datagram was sent, or the failure that prevented it
     */
    fun keepAlive(): Result<Unit>

    /**
     * Starts an opt-in, idle-aware background keepalive for this connection: every
     * ~[intervalMillis] of output silence toward [peer] the server sends one `KA`
     * datagram, until [stopKeepAlive], [terminate], or server `stop()`.
     *
     * Server -> client keepalives refresh endpoint-independent (full-cone /
     * restricted-cone) NAT mappings and verify the send path; they do **not**
     * reliably refresh port-restricted or symmetric NATs - the authoritative
     * keepalive is still the client's own (see [keepAlive]). A convenience over
     * [keepAlive], which is unchanged and still owns no timer.
     *
     * The default implementation returns [Result.failure] - only the production
     * [UDPConnection] supports a scheduled keepalive.
     *
     * @param intervalMillis output-idle time before a keepalive is sent; must be > 0.
     * Defaults to [HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS] (20 s).
     * @return [Result.success] once armed, or the failure that prevented it
     */
    fun startKeepAlive(intervalMillis: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("This Connection does not support a scheduled keepalive"))

    /**
     * Starts the scheduled keepalive at the recommended interval
     * ([HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS], 20 s).
     * @return [Result.success] once armed, or the failure that prevented it
     * @see startKeepAlive
     */
    fun startKeepAlive(): Result<Unit> = startKeepAlive(HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS)

    /**
     * Stops the background keepalive started by [startKeepAlive]. Idempotent; a no-op
     * if none is running or the implementation does not support one. [terminate] and
     * server `stop()` also do this.
     * @return [Result.success] once cancelled
     */
    fun stopKeepAlive(): Result<Unit> = Result.success(Unit)

    /**
     * Starts an opt-in link-quality probe toward [peer]: every [intervalMillis] the
     * server sends one `PING`, and each `PONG` updates a smoothed RTT / jitter /
     * loss estimate readable via [linkQuality]. Runs until [stopProbe], [terminate],
     * or server `stop()`. Server -> client `PING` reaches the client whenever the
     * session is live (the client is actively holding its own mapping open); the
     * same cone-NAT caveat as [keepAlive] applies if the client has gone silent.
     *
     * The probe requires both ends on `1.6.0`+: a pre-`1.6.0` peer does not answer
     * `PING`, so `packetLossRatio` climbs toward `1.0` and a consumer that opted in
     * against such a peer should not have.
     *
     * The default returns [Result.failure] - only the production [UDPConnection]
     * supports a probe.
     * @param intervalMillis probe period; must be > 0.
     * @return [Result.success] once armed, or the failure that prevented it
     */
    fun startProbe(intervalMillis: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("This Connection does not support a link-quality probe"))

    /**
     * Starts the probe at the default interval
     * ([HandshakeWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS], 1 s).
     * @return [Result.success] once armed, or the failure that prevented it
     * @see startProbe
     */
    fun startProbe(): Result<Unit> = startProbe(HandshakeWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS)

    /**
     * Stops the probe started by [startProbe]. Idempotent; [terminate] and server
     * `stop()` also do this. The last [linkQuality] snapshot remains readable.
     * @return [Result.success] once cancelled
     */
    fun stopProbe(): Result<Unit> = Result.success(Unit)

    /**
     * The latest link-quality snapshot for this connection, or `null` if no probe
     * is running or none has resolved yet.
     * @return the current [LinkQuality], or `null`
     */
    fun linkQuality(): LinkQuality? = null
}
