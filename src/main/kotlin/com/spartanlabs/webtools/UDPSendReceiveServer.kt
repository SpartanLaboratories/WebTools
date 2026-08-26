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
     */
    fun send(byteArray: ByteArray) {
        log.trace("Sending {} byte(s) to {}:{}", byteArray.size, targetAddress, sendPort)
        sendSocket.send(DatagramPacket(byteArray, byteArray.size, targetAddress, sendPort))
    }

    /**
     * Encodes [message] as UTF-8 and sends it to [targetAddress]:[sendPort].
     * @param message the text to send
     */
    fun send(message  : String) = send(message.toByteArray())

    /**
     * Starts a background thread that blocks on [listenSocket]'s receive,
     * decoding each incoming datagram as a UTF-8 string and passing it to
     * [onMessage] along with the sender's address.
     */
    fun startListening(onMessage: (message: String, senderAddress: InetAddress) -> Unit) {
        log.info("Starting to listen on port {}", listenPort)
        listening = true
        listenerThread = Thread {
            val buffer = ByteArray(1024)
            while (listening) {
                try {
                    // Create the datagram packet that is going to contain the received message
                    val packet = DatagramPacket(buffer, buffer.size)
                    // Read the message and store it in the datagram packet we just created
                    listenSocket.receive(packet)
                    // Convert the packet into a String
                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    log.debug("Received message from {}: {}", packet.address, message)
                    // Take action based on the received message
                    onMessage(message, packet.address)
                } catch (e: SocketException) {
                    log.debug("Listen socket on port {} was closed, stopping listener", listenPort)
                    break // socket was closed - stop listening
                } catch (e: Exception) {
                    log.warn("Failed to handle incoming datagram: {}", e.message, e)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Signals the background listener loop to stop and closes [listenSocket],
     * interrupting any in-progress blocking `receive()` call.
     */
    fun stopListening() {
        log.info("Stopping listener on port {}", listenPort)
        listening = false
        listenSocket.close()
    }

    /**
     * Stops listening, closes both the send and listen sockets, and waits (up to
     * one second) for the listener thread to terminate.
     */
    override fun close() {
        log.debug("Closing UDPSendReceiveServer for port {}", listenPort)
        stopListening()
        sendSocket.close()
        listenerThread?.join(1000)
    }

    companion object {
        /** Shared slf4j logger for all [UDPSendReceiveServer] instances. */
        private val log = LoggerFactory.getLogger(UDPSendReceiveServer::class.java)
    }
}