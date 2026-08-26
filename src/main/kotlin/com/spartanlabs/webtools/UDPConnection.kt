package com.spartanlabs.webtools

import org.slf4j.LoggerFactory
import java.net.InetAddress

/**
 * Represents a single dedicated, named UDP connection to a client, backed by a
 * [UDPSendReceiveServer] bound to its own send/receive port pair.
 * @property name a human-readable identifier for this connection, typically
 * supplied by the client during the handshake
 * @property address the address of the remote peer
 * @property sendPort the local port this connection sends messages to on the peer
 * @property receivePort the local port this connection listens on for incoming messages
 */
internal class UDPConnection(val name:String, val address: InetAddress, val sendPort: Int, val receivePort: Int) {
    /** The underlying socket pair used to actually send and receive datagrams. */
    private val server = UDPSendReceiveServer(address, sendPort, receivePort)

    init {
        log.debug("Created UDPConnection '{}' for {} (send={}, receive={})", name, address, sendPort, receivePort)
    }

    /**
     * Begins listening for incoming messages on this connection's receive port.
     * @param onMessage callback invoked with the decoded text of each message received
     */
    fun actuate(onMessage: (message: String) -> Unit) {
        log.info("Actuating connection '{}'", name)
        server.startListening { message, senderAddress ->
            log.trace("Connection '{}' received message from {}: {}", name, senderAddress, message)
            onMessage(message)
        }
    }

    /**
     * Stops listening and releases the underlying sockets for this connection.
     * Once called, this connection can no longer send or receive messages.
     */
    fun terminate() {
        log.info("Terminating connection '{}'", name)
        server.close()
    }

    /**
     * Sends a message to the remote peer on this connection's [sendPort].
     * @param message the text to send
     */
    fun push(message: String) {
        log.trace("Connection '{}' pushing message: {}", name, message)
        server.send(message)
    }

    companion object {
        /** Shared slf4j logger for all [UDPConnection] instances. */
        private val log = LoggerFactory.getLogger(UDPConnection::class.java)
    }
}