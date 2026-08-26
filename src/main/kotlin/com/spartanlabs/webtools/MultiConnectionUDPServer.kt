package com.spartanlabs.webtools

import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException

/**
 * A UDP server that accepts handshakes from any number of clients on a single
 * well-known "common" port pair, and hands each accepted client off to its own
 * dedicated [UDPConnection] on a private port pair.
 *
 * A client registers itself by sending an `Iam <name> <address>` message to the
 * common listen port (9998). The server replies with a `TXRXON <sendPort> <receivePort>`
 * message on the common send port (9999) telling the client which dedicated ports
 * to use for further communication.
 */
class MultiConnectionUDPServer {
    /** Guard flag for the common listener loop. Currently always `true` for the lifetime of the instance. */
    private val listening = true
    /** Background thread that services [commonListenSocket]. */
    private var commonListenerThread: Thread? = null
    /** Socket that receives handshake (`Iam ...`) messages from clients. */
    private val commonListenSocket = DatagramSocket(9998)
    /** Port that handshake replies (`TXRXON ...`) are sent back to on the client. */
    private val commonSendPort = 9999
    /** Socket used to send handshake replies. */
    private val commonSendSocket = DatagramSocket()

    /**
     * Starts the common listener thread, which handles incoming `Iam` handshake
     * messages by registering a new [UDPConnection] and replying with the
     * dedicated ports the client should use.
     */
    init{
        log.info("Starting common listener thread on port {}", commonListenSocket.localPort)
        commonListenerThread = Thread {
            val buffer = ByteArray(1024)
            while (listening) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    commonListenSocket.receive(packet)
                    log.debug("The server has received a message on the common listen port")
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    log.trace("The message is {}", text)
                    val message = text.split(' ')
                    when(message[0]) {
                        "Iam" -> {
                            log.info("Normal Communication Detected: {}", message[0])
                            val address = InetAddress.getByName(message[2].removePrefix("/"))
                            log.debug("Address: {}", address)
                            val connection = addConnection(message[1], address)
                            val declarationResponse = "$address TXRXON ${connection.sendPort} ${connection.receivePort}"
                            pushToAddress(declarationResponse, address)
                        }
                    }
                } catch (e: SocketException) {
                    log.debug("Common listen socket was closed, stopping listener")
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

    /** All clients that have completed the `Iam` handshake, in registration order. */
    private val connections = arrayListOf<UDPConnection>()

    /**
     * Registers a new [UDPConnection] for a client that has just completed the
     * `Iam` handshake, allocating it a fresh, unused pair of dedicated ports
     * below [commonSendPort].
     * @param name the client-supplied name from its `Iam` message
     * @param address the client's address
     * @return the newly created and registered connection
     */
    private fun addConnection(name:String, address: InetAddress):UDPConnection{
        val portOffset = connections.size + 2
        log.info("Adding a new connection '{}' for address {}", name, address)
        val connection = UDPConnection(name, address, commonSendPort-portOffset, commonSendPort-portOffset-1)
        connections.add(connection)
        return connection
    }

    /**
     * Starts listening on every currently-registered connection's dedicated port pair.
     * @param onClientMessage callback invoked with the raw message body whenever any
     * connection receives a datagram
     */
    fun start(onClientMessage:(String)->Unit) {
        log.info("Starting {} connection(s)", connections.size)
        connections.forEach{ connection -> connection.actuate(onClientMessage)}
    }

    /**
     * Sends a raw message to a single address on the common send port.
     * Used to deliver the `TXRXON` handshake reply.
     * @param message the text to send
     * @param address the destination address
     */
    private fun pushToAddress(message:String, address: InetAddress)  {
        log.trace("Pushing message to {}: {}", address, message)
        message.toByteArray(charset = Charsets.UTF_8).let { packet ->
            commonSendSocket.send(DatagramPacket(packet, packet.size, address, commonSendPort))
        }
    }

    /**
     * Broadcasts a message to every registered connection's address on the common send port.
     * @param message the text to send to all clients
     */
    fun pushToAll(message:String) {
        log.info("Pushing message to all {} connection(s)", connections.size)
        connections.forEach { connection -> pushToAddress(message, connection.address) }
    }

    /**
     * Shuts the server down: terminates every registered [UDPConnection] (releasing
     * their dedicated ports), then stops the common listener thread and releases the
     * common listen port so the server can no longer accept new handshakes.
     *
     * Once called, this instance should be discarded - there is no corresponding
     * "restart" operation.
     */
    fun stop() {
        log.info("Stopping server: terminating {} connection(s)", connections.size)
        connections.forEach { connection -> connection.terminate() }
        // Give the listener thread a chance to exit on its own before we force it via socket closure below.
        commonListenerThread?.join(1000)
        log.info("Closing common listen socket on port {}", commonListenSocket.localPort)
        commonListenSocket.close()
    }

    companion object {
        /** Shared slf4j logger for all [MultiConnectionUDPServer] instances. */
        private val log = LoggerFactory.getLogger(MultiConnectionUDPServer::class.java)
    }
}