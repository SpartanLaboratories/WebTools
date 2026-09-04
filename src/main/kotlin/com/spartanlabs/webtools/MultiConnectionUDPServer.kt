package com.spartanlabs.webtools

import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException

/**
 * A UDP server that accepts handshakes from any number of clients on a single
 * well-known "common" port, and hands each accepted client off to its own
 * dedicated [Connection] on a private port pair.
 *
 * A client registers itself by sending an `Iam <name>` message to the common
 * listen port ([COMMON_LISTEN_PORT]). The server replies - from that same socket,
 * addressed straight back to the datagram's source address and port - with a
 * `TXRXON <sendPort> <receivePort>` message telling the client which dedicated
 * ports to use for further communication. Because the reply targets the UDP
 * source rather than anything the client puts in its payload, the handshake
 * completes even when the client is behind NAT.
 *
 * This class is abstract because it does not itself decide what to do once a client
 * has finished the handshake - subclasses must implement [onClientConnect] to react
 * to newly registered connections (e.g. by calling [Connection.actuate] on them,
 * tracking them, notifying other parts of the application, etc.).
 *
 * The handshake rules themselves live in [HandshakeProtocol] (pure) and
 * [HandshakeCoordinator] (the state machine); this class only binds them to a
 * real socket and a real [UDPConnection] factory.
 *
 * ### Known limitation
 * The per-client dedicated [UDPConnection] still binds a fixed port pair and the
 * server may transmit on it before the client has opened a matching NAT binding,
 * so the *data* path is not yet NAT-traversable - only the handshake is. Tracked
 * as SpartanLaboratories/WebTools#1 (Tier 2).
 */
abstract class MultiConnectionUDPServer {
    /** Guard flag for the common listener loop, cleared by [stop]. */
    @Volatile
    private var listening = true

    /** Background thread that services [commonSocket]. */
    private var commonListenerThread: Thread? = null

    /**
     * Socket bound to [COMMON_LISTEN_PORT]. It both receives `Iam` handshakes and
     * sends every common-channel datagram back out (`TXRXON` replies and [pushToAll]
     * broadcasts), so those datagrams traverse the exact NAT binding the client's
     * handshake just opened.
     */
    private val commonSocket = DatagramSocket(COMMON_LISTEN_PORT)

    /**
     * The handshake state machine, wired to this server's real socket (for replies)
     * and a real [UDPConnection] factory (for accepted clients).
     */
    private val coordinator = HandshakeCoordinator(
        newConnection = { name, origin, ports ->
            UDPConnection(name, origin.address, ports.sendPort, ports.receivePort)
        },
        reply = ::replyToOrigin,
        onRegistered = ::onClientConnect,
    )

    /**
     * Starts the common listener thread, which handles incoming `Iam` handshake
     * messages by registering a new [Connection] and replying with the dedicated
     * ports the client should use.
     */
    init {
        log.info("Starting common listener thread on port {}", commonSocket.localPort)
        commonListenerThread = Thread { handshakeLoop() }.apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Body of the common listener thread: accepts handshakes until [listening] is
     * cleared or [commonSocket] is closed. Each iteration's failure is logged and
     * skipped so one malformed datagram cannot kill the server.
     */
    private fun handshakeLoop() {
        val buffer = ByteArray(RECEIVE_BUFFER_BYTES)
        while (listening) {
            runCatching {
                val packet = DatagramPacket(buffer, buffer.size)
                commonSocket.receive(packet)
                // The reply has to go back to where the datagram actually came from, never
                // to an address in its payload - a NAT'd client only knows its private one.
                val origin = InetSocketAddress(packet.address, packet.port)
                log.debug("The server has received a message on the common listen port from {}", origin)
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                log.trace("The message is {}", text)
                origin to text.split(' ')
            }.flatMap { (origin, tokens) -> coordinator.handle(origin, tokens) }
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
     * Called once a client has completed the `Iam` handshake and its dedicated
     * [Connection] has been registered and told which ports to use. Subclasses
     * decide what to do with the newly connected client here - for example calling
     * [Connection.actuate] to start listening on it, or storing a reference to it.
     *
     * Invoked on the common listener thread, so implementations should return quickly
     * and hand off any lengthy work to another thread.
     * @param connection the connection that was just registered
     */
    abstract fun onClientConnect(connection: Connection)

    /**
     * Starts listening on every currently-registered connection's dedicated port pair.
     * @param onClientMessage callback invoked with the raw message body whenever any
     * connection receives a datagram
     * @return [Result.success] if every connection was actuated, or the first failure encountered
     */
    fun start(onClientMessage: (String) -> Unit): Result<Unit> {
        log.info("Starting {} connection(s)", coordinator.size)
        return coordinator.snapshot().fold(Result.success(Unit)) { started, registration ->
            started.flatMap { registration.connection.actuate(onClientMessage) }
        }
    }

    /**
     * Sends a raw message back through the common socket to [origin] - the exact
     * address and port a datagram was received from. Used for the `TXRXON`
     * handshake reply and for [pushToAll] broadcasts.
     *
     * Safe to call from any thread: [commonSocket] is only ever *received* on by
     * the single listener thread, and the JDK permits a concurrent
     * [DatagramSocket.send] while a [DatagramSocket.receive] is in progress.
     *
     * @param message the text to send
     * @param origin the destination address and port
     * @return [Result.success] if the datagram was sent, or the failure that prevented it
     */
    private fun replyToOrigin(message: String, origin: InetSocketAddress): Result<Unit> {
        log.trace("Replying to {}: {}", origin, message)
        return runCatching {
            message.toByteArray(Charsets.UTF_8).let { payload ->
                commonSocket.send(DatagramPacket(payload, payload.size, origin))
            }
        }.onFailure { cause -> log.error("Could not reply to {}", origin, cause) }
    }

    /**
     * Broadcasts a message to every registered connection's handshake origin over
     * the common channel.
     * @param message the text to send to all clients
     * @return [Result.success] if the message reached every client, or the first failure encountered
     */
    fun pushToAll(message: String): Result<Unit> {
        log.info("Pushing message to all {} connection(s)", coordinator.size)
        return coordinator.snapshot().fold(Result.success(Unit)) { pushed, registration ->
            pushed.flatMap { replyToOrigin(message, registration.origin) }
        }
    }

    /**
     * Shuts the server down: terminates every registered [Connection] (releasing
     * their dedicated ports), then stops the common listener thread and releases the
     * common socket so the server can no longer accept new handshakes.
     *
     * Every step runs even if an earlier one failed, so a partial failure never
     * leaks a bound port. Once called, this instance should be discarded - there is
     * no corresponding "restart" operation.
     *
     * @return [Result.success] if every step succeeded, or the first failure encountered
     */
    fun stop(): Result<Unit> {
        log.info("Stopping server: terminating {} connection(s)", coordinator.size)
        val connectionsTerminated = coordinator.snapshot().fold(Result.success(Unit)) { terminated, registration ->
            // Each connection is terminated regardless of its predecessors' outcome; only
            // the reported Result short-circuits, not the release of the ports.
            registration.connection.terminate().let { outcome -> terminated.flatMap { outcome } }
        }
        listening = false
        // Give the listener thread a chance to exit on its own before we force it via socket closure below.
        val listenerJoined = runCatching { commonListenerThread?.join(LISTENER_JOIN_TIMEOUT_MILLIS) }
            .map { }
            .onFailure { cause ->
                if (cause is InterruptedException) Thread.currentThread().interrupt()
                log.warn("Interrupted while waiting for the common listener thread to stop")
            }
        log.info("Closing common socket on port {}", commonSocket.localPort)
        val socketClosed = runCatching { commonSocket.close() }
            .onFailure { cause -> log.error("Could not close the common socket", cause) }
        return connectionsTerminated.flatMap { listenerJoined }.flatMap { socketClosed }
    }

    companion object {
        /** Shared slf4j logger for all [MultiConnectionUDPServer] instances. */
        private val log = LoggerFactory.getLogger(MultiConnectionUDPServer::class.java)

        /**
         * Well-known port clients send their `Iam` handshake to, and that the
         * server's `TXRXON` reply and [pushToAll] broadcasts are sent back out from.
         */
        const val COMMON_LISTEN_PORT = 9998

        /** Size of the reusable buffer incoming datagrams are read into. */
        private const val RECEIVE_BUFFER_BYTES = 1024

        /** How long [stop] waits for the common listener thread to notice it should stop. */
        private const val LISTENER_JOIN_TIMEOUT_MILLIS = 1000L
    }
}
