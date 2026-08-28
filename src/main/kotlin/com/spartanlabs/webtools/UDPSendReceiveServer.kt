package com.spartanlabs.webtools

import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException

/**
 * A simple bidirectional UDP socket pair: one socket for sending to a fixed
 * target address/port, and a separate socket bound to a local port for
 * asynchronously receiving incoming datagrams on a background thread.
 *
 * Every fallible operation returns a [Result]; the only exception is [close],
 * which must return `Unit` to satisfy [AutoCloseable] and therefore logs its
 * failures instead. Use [shutDown] when you want the outcome as a value.
 *
 * @param targetAddress the address messages sent via [send] are delivered to
 * @param sendPort the port on [targetAddress] that messages sent via [send] are delivered to
 * @param listenPort the local port this server binds to receive incoming datagrams
 */
class UDPSendReceiveServer(
    private val targetAddress: InetAddress,
    private val sendPort: Int,
    private val listenPort: Int
) : AutoCloseable {

    /** Socket used exclusively for outgoing datagrams. */
    private val sendSocket = DatagramSocket()

    /** Socket bound to [listenPort], used exclusively for incoming datagrams. */
    private val listenSocket = DatagramSocket(listenPort)

    /** Guard flag for the receive loop running on [listenerThread]. */
    @Volatile
    private var listening = false

    /** Background thread that services [listenSocket] once [startListening] is called. */
    private var listenerThread: Thread? = null

    /**
     * Sends raw bytes to [targetAddress]:[sendPort].
     * @param byteArray the payload to send
     * @return [Result.success] if the datagram was handed to the socket, or
     * [Result.failure] holding the [java.io.IOException] that prevented it
     */
    fun send(byteArray: ByteArray): Result<Unit> {
        log.trace("Sending {} byte(s) to {}:{}", byteArray.size, targetAddress, sendPort)
        return runCatching { sendSocket.send(DatagramPacket(byteArray, byteArray.size, targetAddress, sendPort)) }
            .onFailure { cause ->
                log.error("Failed to send {} byte(s) to {}:{}", byteArray.size, targetAddress, sendPort, cause)
            }
    }

    /**
     * Encodes [message] as UTF-8 and sends it to [targetAddress]:[sendPort].
     * @param message the text to send
     * @return the outcome of the underlying [send] call
     */
    fun send(message: String): Result<Unit> = send(message.toByteArray(Charsets.UTF_8))

    /**
     * Starts a background thread that blocks on [listenSocket]'s receive, decoding
     * each incoming datagram as a UTF-8 string and passing it to [onMessage] along
     * with the sender's address.
     *
     * @param onMessage invoked on the listener thread for every datagram received
     * @return [Result.success] once the listener thread is running, or
     * [Result.failure] if the thread could not be started
     */
    fun startListening(onMessage: (message: String, senderAddress: InetAddress) -> Unit): Result<Unit> {
        log.info("Starting to listen on port {}", listenPort)
        listening = true
        return runCatching {
            listenerThread = Thread { receiveLoop(onMessage) }.apply {
                isDaemon = true
                start()
            }
        }.onFailure { cause ->
            listening = false
            log.error("Could not start a listener thread for port {}", listenPort, cause)
        }
    }

    /**
     * Body of the listener thread: receives datagrams until [listening] is cleared
     * or [listenSocket] is closed.
     * @param onMessage invoked with the decoded text and sender of each datagram
     */
    private fun receiveLoop(onMessage: (message: String, senderAddress: InetAddress) -> Unit) {
        val buffer = ByteArray(RECEIVE_BUFFER_BYTES)
        while (listening) {
            val handled = runCatching {
                // Create the datagram packet that is going to contain the received message
                val packet = DatagramPacket(buffer, buffer.size)
                // Read the message and store it in the datagram packet we just created
                listenSocket.receive(packet)
                // Convert the packet into a String
                val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                log.debug("Received message from {}: {}", packet.address, message)
                // Take action based on the received message
                onMessage(message, packet.address)
            }
            handled.onFailure { cause ->
                if (cause is SocketException) {
                    log.debug("Listen socket on port {} was closed, stopping listener", listenPort)
                    listening = false // socket was closed - stop listening
                } else {
                    log.warn("Failed to handle incoming datagram: {}", cause.message, cause)
                }
            }
        }
    }

    /**
     * Signals the background listener loop to stop and closes [listenSocket],
     * interrupting any in-progress blocking `receive()` call.
     * @return [Result.success] if the listen socket was released, or the failure that prevented it
     */
    fun stopListening(): Result<Unit> {
        log.info("Stopping listener on port {}", listenPort)
        listening = false
        return runCatching { listenSocket.close() }
            .onFailure { cause -> log.error("Could not close the listen socket on port {}", listenPort, cause) }
    }

    /**
     * Stops listening, closes both the send and listen sockets, and waits (up to
     * [LISTENER_JOIN_TIMEOUT_MILLIS]) for the listener thread to terminate.
     *
     * Both sockets are released even if one of the steps fails, so a partial
     * failure never leaks a bound port.
     *
     * @return [Result.success] if every step succeeded, or the first failure encountered
     */
    fun shutDown(): Result<Unit> {
        log.debug("Closing UDPSendReceiveServer for port {}", listenPort)
        val listenerStopped = stopListening()
        val sendSocketClosed = runCatching { sendSocket.close() }
            .onFailure { cause -> log.error("Could not close the send socket for port {}", sendPort, cause) }
        val listenerJoined = runCatching { listenerThread?.join(LISTENER_JOIN_TIMEOUT_MILLIS) }
            .map { }
            .onFailure { cause ->
                if (cause is InterruptedException) Thread.currentThread().interrupt()
                log.warn("Interrupted while waiting for the listener thread on port {} to stop", listenPort)
            }
        return listenerStopped.flatMap { sendSocketClosed }.flatMap { listenerJoined }
    }

    /**
     * [AutoCloseable] entry point, so this server can be used with `use { }`.
     * Delegates to [shutDown] and discards the outcome - the interface fixes the
     * return type as `Unit`, so failures are only logged. Call [shutDown] directly
     * if you need to react to them.
     */
    override fun close() {
        shutDown()
    }

    companion object {
        /** Shared slf4j logger for all [UDPSendReceiveServer] instances. */
        private val log = LoggerFactory.getLogger(UDPSendReceiveServer::class.java)

        /** Size of the reusable buffer incoming datagrams are read into. */
        private const val RECEIVE_BUFFER_BYTES = 1024

        /** How long [shutDown] waits for the listener thread to notice it should stop. */
        private const val LISTENER_JOIN_TIMEOUT_MILLIS = 1000L
    }
}
