package com.spartanlabs.webtools

import org.slf4j.LoggerFactory
import java.net.InetAddress

/**
 * The production [Connection]: a single dedicated, named UDP connection to a
 * client, backed by a [UDPSendReceiveServer] bound to its own send/receive port pair.
 *
 * Every fallible operation returns a [Result], so a connection that cannot be
 * actuated, pushed to, or terminated reports why rather than throwing.
 *
 * @property name a human-readable identifier for this connection, typically
 * supplied by the client during the handshake
 * @property address the address of the remote peer
 * @property sendPort the local port this connection sends messages to on the peer
 * @property receivePort the local port this connection listens on for incoming messages
 */
class UDPConnection(
    override val name: String,
    override val address: InetAddress,
    override val sendPort: Int,
    override val receivePort: Int,
) : Connection {
    /** The underlying socket pair used to actually send and receive datagrams. */
    private val server = UDPSendReceiveServer(address, sendPort, receivePort)

    init {
        log.debug("Created UDPConnection '{}' for {} (send={}, receive={})", name, address, sendPort, receivePort)
    }

    /**
     * Begins listening for incoming messages on this connection's receive port.
     * @param onMessage callback invoked with the decoded text of each message received
     * @return [Result.success] once the connection is listening, or the failure that prevented it
     */
    override fun actuate(onMessage: (message: String) -> Unit): Result<Unit> {
        log.info("Actuating connection '{}'", name)
        return server.startListening { message, senderAddress ->
            log.trace("Connection '{}' received message from {}: {}", name, senderAddress, message)
            onMessage(message)
        }.onFailure { cause -> log.error("Could not actuate connection '{}'", name, cause) }
    }

    /**
     * Stops listening and releases the underlying sockets for this connection.
     * Once this succeeds, this connection can no longer send or receive messages.
     * @return [Result.success] if the sockets were released, or the failure that prevented it
     */
    override fun terminate(): Result<Unit> {
        log.info("Terminating connection '{}'", name)
        return server.shutDown()
            .onFailure { cause -> log.error("Could not cleanly terminate connection '{}'", name, cause) }
    }

    /**
     * Sends a message to the remote peer on this connection's [sendPort].
     * @param message the text to send
     * @return [Result.success] if the datagram was sent, or the failure that prevented it
     */
    override fun push(message: String): Result<Unit> {
        log.trace("Connection '{}' pushing message: {}", name, message)
        return server.send(message)
            .onFailure { cause -> log.error("Connection '{}' could not push a message", name, cause) }
    }

    companion object {
        /** Shared slf4j logger for all [UDPConnection] instances. */
        private val log = LoggerFactory.getLogger(UDPConnection::class.java)
    }
}
