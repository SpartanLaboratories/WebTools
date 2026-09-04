package com.spartanlabs.webtools

import java.net.InetAddress

/**
 * A single dedicated, named connection to one remote client.
 *
 * [UDPConnection] is the production implementation, backed by a real socket pair.
 * The interface exists so [MultiConnectionUDPServer]'s registration and handshake
 * logic can be exercised against a socket-free fake.
 *
 * @property name a human-readable identifier for this connection, typically
 * supplied by the client during the handshake
 * @property address the address of the remote peer, as observed on its handshake datagram
 * @property sendPort the local port this connection sends messages to on the peer
 * @property receivePort the local port this connection listens on for incoming messages
 */
interface Connection {
    val name: String
    val address: InetAddress
    val sendPort: Int
    val receivePort: Int

    /**
     * Begins listening for incoming messages on this connection's receive port.
     * @param onMessage callback invoked with the decoded text of each message received;
     * it runs on this connection's background receive thread, not the caller's
     * @return [Result.success] once the connection is listening, or the failure that prevented it
     */
    fun actuate(onMessage: (message: String) -> Unit): Result<Unit>

    /**
     * Stops listening and releases the underlying resources for this connection.
     * @return [Result.success] if the resources were released, or the failure that prevented it
     */
    fun terminate(): Result<Unit>

    /**
     * Sends a message to the remote peer on this connection's [sendPort].
     * @param message the text to send
     * @return [Result.success] if the message was sent, or the failure that prevented it
     */
    fun push(message: String): Result<Unit>
}
