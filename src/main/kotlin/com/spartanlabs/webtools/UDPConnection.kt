package com.spartanlabs.webtools

import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * The production [Connection]: a socket-free handle to one multiplexed client of a
 * [MultiConnectionUDPServer].
 *
 * It owns no socket and no thread of its own. [push] and [keepAlive] address
 * [peer] over the server's single shared socket; [actuate] / [terminate] register
 * and unregister this connection's message handler in the server's registry. Every
 * fallible operation returns a [Result].
 *
 * Instances are minted only by [MultiConnectionUDPServer]'s internal factory -
 * the constructor is `internal`. Standalone bidirectional-socket users take
 * [UDPSendReceiveServer] directly.
 *
 * @property name a human-readable identifier for this connection, typically
 * supplied by the client during the handshake
 * @property peer the client's post-NAT endpoint, learned from its `Iam` datagram;
 * every datagram to this client is addressed here
 */
class UDPConnection internal constructor(
    override val name: String,
    override val peer: InetSocketAddress,
    private val channel: ClientChannel,
) : Connection {

    init {
        log.debug("Created UDPConnection '{}' for {}", name, peer)
    }

    override fun actuate(onMessage: (message: String) -> Unit): Result<Unit> =
        runCatching { channel.bind(peer, onMessage) }
            .onFailure { log.error("Could not actuate connection '{}'", name, it) }

    override fun terminate(): Result<Unit> =
        runCatching { channel.unbind(peer) }
            .onFailure { log.error("Could not terminate connection '{}'", name, it) }

    override fun push(message: String): Result<Unit> =
        channel.send(message.toByteArray(Charsets.UTF_8), peer)
            .onFailure { log.error("Connection '{}' could not push a message", name, it) }

    override fun keepAlive(): Result<Unit> =
        channel.send(KEEPALIVE_BYTES, peer)
            .onFailure { log.error("Connection '{}' could not send a keepalive", name, it) }

    private companion object {
        private val log = LoggerFactory.getLogger(UDPConnection::class.java)
        private val KEEPALIVE_BYTES = HandshakeProtocol.KEEPALIVE_TOKEN.toByteArray(Charsets.UTF_8)
    }
}
