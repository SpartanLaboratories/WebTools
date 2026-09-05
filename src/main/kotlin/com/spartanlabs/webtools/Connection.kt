package com.spartanlabs.webtools

import java.net.InetSocketAddress

/**
 * A single named, logical connection to one remote client of a
 * [MultiConnectionUDPServer].
 *
 * [UDPConnection] is the production implementation: a socket-free handle over the
 * server's single shared UDP socket. The interface exists so the server's
 * registration and handshake logic can be exercised against a socket-free fake.
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
     * (see [terminate]).
     * @param onMessage callback invoked with the decoded text of each message received;
     * it runs on the server's single-threaded dispatch executor, not the caller's
     * thread, so it must return promptly - a slow handler delays delivery to other clients
     * @return [Result.success] once the handler is registered, or the failure that prevented it
     */
    fun actuate(onMessage: (message: String) -> Unit): Result<Unit>

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
     * Sends a message to [peer] over the server's shared socket.
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
     * Sends one minimal keepalive datagram to [peer] to keep its NAT mapping
     * warm. A one-shot: the caller schedules it (recommended ~20 s idle
     * cadence). Owns no timer or thread.
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
}
