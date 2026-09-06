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
}
