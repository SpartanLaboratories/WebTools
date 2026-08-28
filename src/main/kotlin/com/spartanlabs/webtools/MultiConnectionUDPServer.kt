package com.spartanlabs.webtools

import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A UDP server that accepts handshakes from any number of clients on a single
 * well-known "common" port pair, and hands each accepted client off to its own
 * dedicated [UDPConnection] on a private port pair.
 *
 * A client registers itself by sending an `Iam <name> <address>` message to the
 * common listen port ([COMMON_LISTEN_PORT]). The server replies with a
 * `TXRXON <sendPort> <receivePort>` message on the common send port
 * ([COMMON_SEND_PORT]) telling the client which dedicated ports to use for
 * further communication.
 *
 * This class is abstract because it does not itself decide what to do once a client
 * has finished the handshake - subclasses must implement [onClientConnect] to react
 * to newly registered connections (e.g. by calling [UDPConnection.actuate] on them,
 * tracking them, notifying other parts of the application, etc.).
 */
abstract class MultiConnectionUDPServer {
    /** Guard flag for the common listener loop, cleared by [stop]. */
    @Volatile
    private var listening = true

    /** Background thread that services [commonListenSocket]. */
    private var commonListenerThread: Thread? = null

    /** Socket that receives handshake (`Iam ...`) messages from clients. */
    private val commonListenSocket = DatagramSocket(COMMON_LISTEN_PORT)

    /** Socket used to send handshake replies. */
    private val commonSendSocket = DatagramSocket()

    /**
     * All clients that have completed the `Iam` handshake, in registration order.
     * Copy-on-write because it is appended to from the listener thread while
     * [start], [pushToAll] and [stop] read it from caller threads.
     */
    private val connections = CopyOnWriteArrayList<UDPConnection>()

    /**
     * Starts the common listener thread, which handles incoming `Iam` handshake
     * messages by registering a new [UDPConnection] and replying with the
     * dedicated ports the client should use.
     */
    init {
        log.info("Starting common listener thread on port {}", commonListenSocket.localPort)
        commonListenerThread = Thread { handshakeLoop() }.apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Body of the common listener thread: accepts handshakes until [listening] is
     * cleared or [commonListenSocket] is closed. Each iteration's failure is
     * logged and skipped so one malformed datagram cannot kill the server.
     */
    private fun handshakeLoop() {
        val buffer = ByteArray(RECEIVE_BUFFER_BYTES)
        while (listening) {
            runCatching {
                val packet = DatagramPacket(buffer, buffer.size)
                commonListenSocket.receive(packet)
                log.debug("The server has received a message on the common listen port")
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                log.trace("The message is {}", text)
                text.split(' ')
            }.flatMap { message -> handleHandshake(message) }
                .onFailure { cause ->
                    if (cause is SocketException) {
                        log.debug("Common listen socket was closed, stopping listener")
                        listening = false // socket was closed - stop listening
                    } else {
                        log.warn("Failed to handle incoming datagram: {}", cause.message, cause)
                    }
                }
        }
    }

    /**
     * Handles one already-split message from the common listen port. Messages
     * whose verb is not recognised are ignored rather than treated as failures.
     *
     * @param message the whitespace-split datagram text
     * @return [Result.success] if the message was handled (or harmlessly ignored),
     * or [Result.failure] if a recognised message was malformed or its reply could
     * not be delivered
     */
    private fun handleHandshake(message: List<String>): Result<Unit> =
        when (message.firstOrNull()) {
            HANDSHAKE_VERB -> runCatching {
                log.info("Normal Communication Detected: {}", HANDSHAKE_VERB)
                require(message.size > HANDSHAKE_ADDRESS_INDEX) {
                    "Expected '$HANDSHAKE_VERB <name> <address>' but got ${message.size} token(s)"
                }
                InetAddress.getByName(message[HANDSHAKE_ADDRESS_INDEX].removePrefix("/")) to
                    message[HANDSHAKE_NAME_INDEX]
            }.flatMap { (address, name) ->
                log.debug("Address: {}", address)
                val connection = addConnection(name, address)
                pushToAddress("$address TXRXON ${connection.sendPort} ${connection.receivePort}", address)
                    .map {
                        log.debug("Notifying subclass of new connection '{}'", connection.name)
                        onClientConnect(connection)
                    }
            }

            else -> Result.success(Unit).also { log.trace("Ignoring unrecognised message: {}", message) }
        }

    /**
     * Registers a new [UDPConnection] for a client that has just completed the
     * `Iam` handshake, allocating it a fresh, unused pair of dedicated ports
     * below [COMMON_SEND_PORT].
     * @param name the client-supplied name from its `Iam` message
     * @param address the client's address
     * @return the newly created and registered connection
     */
    private fun addConnection(name: String, address: InetAddress): UDPConnection {
        val portOffset = connections.size + 2
        log.info("Adding a new connection '{}' for address {}", name, address)
        return UDPConnection(name, address, COMMON_SEND_PORT - portOffset, COMMON_SEND_PORT - portOffset - 1)
            .also { connection -> connections.add(connection) }
    }

    /**
     * Called once a client has completed the `Iam` handshake and its dedicated
     * [UDPConnection] has been registered and told which ports to use. Subclasses
     * decide what to do with the newly connected client here - for example calling
     * [UDPConnection.actuate] to start listening on it, or storing a reference to it.
     *
     * Invoked on the common listener thread, so implementations should return quickly
     * and hand off any lengthy work to another thread.
     * @param connection the connection that was just registered
     */
    abstract fun onClientConnect(connection: UDPConnection)

    /**
     * Starts listening on every currently-registered connection's dedicated port pair.
     * @param onClientMessage callback invoked with the raw message body whenever any
     * connection receives a datagram
     * @return [Result.success] if every connection was actuated, or the first failure encountered
     */
    fun start(onClientMessage: (String) -> Unit): Result<Unit> {
        log.info("Starting {} connection(s)", connections.size)
        return connections.fold(Result.success(Unit)) { started, connection ->
            started.flatMap { connection.actuate(onClientMessage) }
        }
    }

    /**
     * Sends a raw message to a single address on the common send port.
     * Used to deliver the `TXRXON` handshake reply.
     * @param message the text to send
     * @param address the destination address
     * @return [Result.success] if the datagram was sent, or the failure that prevented it
     */
    private fun pushToAddress(message: String, address: InetAddress): Result<Unit> {
        log.trace("Pushing message to {}: {}", address, message)
        return runCatching {
            message.toByteArray(Charsets.UTF_8).let { payload ->
                commonSendSocket.send(DatagramPacket(payload, payload.size, address, COMMON_SEND_PORT))
            }
        }.onFailure { cause -> log.error("Could not push a message to {}", address, cause) }
    }

    /**
     * Broadcasts a message to every registered connection's address on the common send port.
     * @param message the text to send to all clients
     * @return [Result.success] if the message reached every address, or the first failure encountered
     */
    fun pushToAll(message: String): Result<Unit> {
        log.info("Pushing message to all {} connection(s)", connections.size)
        return connections.fold(Result.success(Unit)) { pushed, connection ->
            pushed.flatMap { pushToAddress(message, connection.address) }
        }
    }

    /**
     * Shuts the server down: terminates every registered [UDPConnection] (releasing
     * their dedicated ports), then stops the common listener thread and releases the
     * common listen port so the server can no longer accept new handshakes.
     *
     * Every step runs even if an earlier one failed, so a partial failure never
     * leaks a bound port. Once called, this instance should be discarded - there is
     * no corresponding "restart" operation.
     *
     * @return [Result.success] if every step succeeded, or the first failure encountered
     */
    fun stop(): Result<Unit> {
        log.info("Stopping server: terminating {} connection(s)", connections.size)
        val connectionsTerminated = connections.fold(Result.success(Unit)) { terminated, connection ->
            // Each connection is terminated regardless of its predecessors' outcome; only
            // the reported Result short-circuits, not the release of the ports.
            connection.terminate().let { outcome -> terminated.flatMap { outcome } }
        }
        listening = false
        // Give the listener thread a chance to exit on its own before we force it via socket closure below.
        val listenerJoined = runCatching { commonListenerThread?.join(LISTENER_JOIN_TIMEOUT_MILLIS) }
            .map { }
            .onFailure { cause ->
                if (cause is InterruptedException) Thread.currentThread().interrupt()
                log.warn("Interrupted while waiting for the common listener thread to stop")
            }
        log.info("Closing common listen socket on port {}", commonListenSocket.localPort)
        val socketsClosed = runCatching {
            commonListenSocket.close()
            commonSendSocket.close()
        }.onFailure { cause -> log.error("Could not close the common sockets", cause) }
        return connectionsTerminated.flatMap { listenerJoined }.flatMap { socketsClosed }
    }

    companion object {
        /** Shared slf4j logger for all [MultiConnectionUDPServer] instances. */
        private val log = LoggerFactory.getLogger(MultiConnectionUDPServer::class.java)

        /** Well-known port clients send their `Iam` handshake to. */
        const val COMMON_LISTEN_PORT = 9998

        /** Well-known port handshake replies (`TXRXON ...`) are sent back to on the client. */
        const val COMMON_SEND_PORT = 9999

        /** The verb that opens a client handshake. */
        private const val HANDSHAKE_VERB = "Iam"

        /** Index of the client-supplied name within a split handshake message. */
        private const val HANDSHAKE_NAME_INDEX = 1

        /** Index of the client-supplied address within a split handshake message. */
        private const val HANDSHAKE_ADDRESS_INDEX = 2

        /** Size of the reusable buffer incoming datagrams are read into. */
        private const val RECEIVE_BUFFER_BYTES = 1024

        /** How long [stop] waits for the common listener thread to notice it should stop. */
        private const val LISTENER_JOIN_TIMEOUT_MILLIS = 1000L
    }
}
