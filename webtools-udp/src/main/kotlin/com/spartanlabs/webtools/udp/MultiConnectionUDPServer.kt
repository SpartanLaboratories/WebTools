package com.spartanlabs.webtools.udp

import org.slf4j.LoggerFactory
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A UDP server that multiplexes **all** traffic for any number of clients over a
 * single well-known "common" socket ([COMMON_LISTEN_PORT]).
 *
 * A client registers by sending `Iam <name>` to the common port from a socket it
 * keeps open. The server replies - from that same socket, addressed straight back
 * to the datagram's source address and port - with the single token `REGISTERED`.
 * From then on the client sends and receives everything (application data,
 * broadcasts, keepalives) over that one socket to port [COMMON_LISTEN_PORT], and
 * the server addresses every datagram back to the client's observed post-NAT
 * source. Because the server only ever transmits on a 5-tuple the client already
 * opened, the data path traverses NAT and the server binds no per-client ports.
 *
 * A client sends the token `KA` on an idle interval (~20 s recommended) to keep
 * its NAT mapping warm; the server consumes inbound `KA` without dispatching it.
 *
 * This class is abstract because it does not itself decide what to do once a
 * client has finished the handshake - subclasses implement [onClientConnect]
 * (e.g. call [Connection.actuate], track the connection, notify the application).
 *
 * The handshake rules live in [HandshakeProtocol] (pure) and [HandshakeCoordinator]
 * (the state machine + inbound router); this class binds them to a real socket.
 *
 * ### Binary application payloads
 * Post-handshake application data may be raw bytes: [Connection.push] takes a
 * `ByteArray`, [Connection.actuateBytes] / [startBytes] register a raw-bytes
 * inbound handler, and [pushToAll] has a `ByteArray` overload. Delivery is an
 * exact-length copy of the datagram body - no UTF-8 decode, no `.trim()`. The
 * `Iam`/`KA` classifier still runs on the trimmed UTF-8 view of *every* inbound
 * datagram, so a binary payload that decodes/trims to a control token is
 * intercepted and never delivered; lead every binary application datagram with a
 * byte that cannot start `Iam`/`KA` and is not ASCII whitespace (e.g. `0x00`, or
 * any byte `>= 0x80`). An inbound datagram over 65507 bytes is delivered
 * truncated, not rejected; on send ([Connection.push] / [pushToAll]), a payload
 * over the OS datagram limit fails the `Result` (cause logged) rather than being
 * sent. Keep frames under the path MTU (~1200 bytes) for real-network use.
 *
 * The receive buffer size is a constructor parameter [receiveBufferBytes],
 * defaulting to [DEFAULT_RECEIVE_BUFFER_BYTES] (65507) so that by default no
 * well-formed datagram is ever truncated on receive. A datagram larger than the
 * configured size is delivered truncated, not rejected; when a datagram fills a
 * sub-maximum buffer a WARN is logged. The "over 65507 bytes is delivered
 * truncated" wording above stays accurate for the default configuration.
 *
 * ### Construction side effects
 * Instantiating a subclass **binds the OS UDP port [COMMON_LISTEN_PORT]** and
 * starts a daemon listener thread plus a single daemon dispatch thread.
 * Construction throws [java.net.SocketException] (typically
 * [java.net.BindException]) if that port is already in use, so only one instance
 * can exist per JVM/host at a time. Call [stop] to release the port; the instance
 * is single-use afterwards. Construction throws [IllegalArgumentException] if
 * [receiveBufferBytes] is outside [MIN_RECEIVE_BUFFER_BYTES]..[MAX_UDP_PAYLOAD_BYTES].
 *
 * @param receiveBufferBytes size of the datagram receive buffer,
 * [MIN_RECEIVE_BUFFER_BYTES]..[MAX_UDP_PAYLOAD_BYTES]; defaults to
 * [DEFAULT_RECEIVE_BUFFER_BYTES] (65507) so no well-formed datagram is truncated
 *
 * ### Concurrency
 * One long-lived daemon listener thread only *demultiplexes*: `receive()` ->
 * classify (`Iam` / `KA` / data) -> run the socket-free handshake state machine
 * inline, drop the keepalive, or hand the payload to the dispatch executor. The
 * dispatch executor is a **single** daemon thread (`mcups-dispatch`) that invokes
 * [Connection] message handlers, so per-client message order is preserved and a
 * slow handler cannot stall the listener or the handshake. Accepted trade-off:
 * a slow handler delays delivery to *other* clients - handlers must return
 * promptly. The common socket is received on only by the listener thread but sent
 * on from any thread (the JDK permits a concurrent send during a receive); the
 * registration list is copy-on-write.
 *
 * See the sequence diagram in `docs/issue-1-tier-2-plan.md` §2.1 for the canonical
 * end-to-end flow.
 */
abstract class MultiConnectionUDPServer @JvmOverloads protected constructor(
    private val receiveBufferBytes: Int = DEFAULT_RECEIVE_BUFFER_BYTES,
) {
    init {
        require(receiveBufferBytes in MIN_RECEIVE_BUFFER_BYTES..MAX_UDP_PAYLOAD_BYTES) {
            "receiveBufferBytes must be $MIN_RECEIVE_BUFFER_BYTES..$MAX_UDP_PAYLOAD_BYTES, was $receiveBufferBytes"
        }
    }

    /** Guard flag for the common listener loop, cleared by [stop]. */
    @Volatile
    private var listening = true

    /** Background thread that services [commonChannel]. */
    private var commonListenerThread: Thread? = null

    /** The single socket every client's traffic is multiplexed over. */
    private val commonChannel = CommonChannel(COMMON_LISTEN_PORT)

    /**
     * Single daemon thread that runs [Connection] message handlers, off the
     * listener thread. Single-threaded so per-client message ordering holds.
     */
    private val dispatchExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "mcups-dispatch").apply { isDaemon = true } }

    /**
     * The handshake state machine + inbound router, wired to this server's real
     * socket and a real [UDPConnection] factory.
     */
    private val coordinator = HandshakeCoordinator(
        newConnection = { name, peer, channel -> UDPConnection(name, peer, channel) },
        sender = commonChannel::send,
        onRegistered = ::onClientConnect,
        dispatch = { block -> dispatchExecutor.execute(block) },
    )

    /**
     * Starts the common listener thread, which demultiplexes every inbound
     * datagram through [HandshakeCoordinator.accept].
     */
    init {
        log.info("Starting common listener thread on port {}", commonChannel.localPort)
        commonListenerThread = Thread { receiveLoop() }.apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Body of the common listener thread: receives and routes datagrams until
     * [listening] is cleared or the common socket is closed. Each iteration's
     * failure is logged and skipped so one malformed datagram cannot kill the server.
     */
    private fun receiveLoop() {
        val buffer = ByteArray(receiveBufferBytes)
        while (listening) {
            commonChannel.receive(buffer)
                .flatMap { inbound -> coordinator.accept(inbound.origin, inbound.bytes, inbound.text) }
                .onFailure { cause ->
                    if (cause is SocketException) {
                        log.debug("Common listen socket was closed, stopping listener")
                        listening = false
                    } else {
                        log.warn("Failed to handle incoming datagram: {}", cause.message, cause)
                    }
                }
        }
    }

    /**
     * Called once a client has completed the `Iam` handshake and its [Connection]
     * has been registered. Subclasses decide what to do with the newly connected
     * client here - for example calling [Connection.actuate] to register a message
     * handler, or storing a reference to it.
     *
     * Invoked on the common listener thread, so implementations should return
     * quickly and hand off any lengthy work to another thread.
     * @param connection the connection that was just registered
     */
    abstract fun onClientConnect(connection: Connection)

    /**
     * Binds [onClientMessage] as the message handler on every currently-registered
     * connection. No socket is started - the one shared socket is already running.
     * @param onClientMessage callback invoked with the raw message body whenever any
     * connection receives an application datagram; it runs on the single-threaded
     * dispatch executor, not the caller's thread, so it must return quickly
     * @return [Result.success] if every connection was actuated, or the first failure
     */
    fun start(onClientMessage: (String) -> Unit): Result<Unit> {
        log.info("Actuating {} connection(s)", coordinator.size)
        return coordinator.actuateAll(onClientMessage)
    }

    /**
     * Binds [onClientMessage] as the raw-bytes handler on every currently-registered
     * connection: each receives an exact-length, undecoded, untrimmed copy of every
     * application datagram (at most 65507 bytes - a larger inbound datagram is
     * truncated, not rejected). Mutually exclusive with [start] per connection (last
     * call wins). No socket is started - the one shared socket is already running.
     * @param onClientMessage callback invoked with the raw datagram body; it runs on
     * the single-threaded dispatch executor, not the caller's thread, so it must return quickly
     * @return [Result.success] if every connection was actuated, or the first failure
     */
    fun startBytes(onClientMessage: (ByteArray) -> Unit): Result<Unit> {
        log.info("Actuating (bytes) {} connection(s)", coordinator.size)
        return coordinator.actuateAllBytes(onClientMessage)
    }

    /**
     * Broadcasts a message to every registered client's endpoint over the common socket.
     * "Registered" here means *currently* registered - a connection that has been
     * `terminate()`d, or superseded by a same-name reconnect from a new origin, is
     * pruned and no longer addressed.
     * @param message the text to send to all clients
     * @return [Result.success] if the message reached every client, or the first failure
     */
    fun pushToAll(message: String): Result<Unit> {
        log.info("Pushing message to all {} connection(s)", coordinator.size)
        return coordinator.broadcast(message)
    }

    /**
     * Broadcasts [bytes] verbatim to every registered client's endpoint over the
     * common socket - no encoding, no trim. Raw-bytes mirror of [pushToAll]`(String)`.
     * A payload above the OS datagram limit fails the [Result] (cause logged)
     * rather than being sent.
     * @param bytes the raw payload to send to all clients
     * @return [Result.success] if the datagram reached every client, or the first failure
     */
    fun pushToAll(bytes: ByteArray): Result<Unit> {
        log.info("Pushing datagram to all {} connection(s)", coordinator.size)
        return coordinator.broadcast(bytes)
    }

    /**
     * Shuts the server down: terminates (fully deregisters) every registered
     * [Connection], leaving `Registrations` empty, then stops the common listener
     * thread, releases the common socket, and shuts the dispatch executor.
     *
     * Every step runs even if an earlier one failed, so a partial failure never
     * leaks a bound port. Once called, this instance should be discarded.
     *
     * @return [Result.success] if every step succeeded, or the first failure encountered
     */
    fun stop(): Result<Unit> {
        log.info("Stopping server: terminating {} connection(s)", coordinator.size)
        val connectionsTerminated = coordinator.terminateAll()
        listening = false
        val listenerJoined = runCatching { commonListenerThread?.join(LISTENER_JOIN_TIMEOUT_MILLIS) }
            .map { }
            .onFailure { cause ->
                if (cause is InterruptedException) Thread.currentThread().interrupt()
                log.warn("Interrupted while waiting for the common listener thread to stop")
            }
        log.info("Closing common socket on port {}", commonChannel.localPort)
        val socketClosed = commonChannel.closeResult()
        val executorStopped = runCatching { dispatchExecutor.shutdownNow() }.map { }
            .onFailure { log.warn("Could not cleanly shut the dispatch executor", it) }
        return connectionsTerminated.flatMap { listenerJoined }.flatMap { socketClosed }.flatMap { executorStopped }
    }

    companion object {
        /** Shared slf4j logger for all [MultiConnectionUDPServer] instances. */
        private val log = LoggerFactory.getLogger(MultiConnectionUDPServer::class.java)

        /**
         * Well-known port clients send their `Iam` handshake to, and over which
         * every client's traffic (data, broadcasts, keepalives) is multiplexed.
         */
        const val COMMON_LISTEN_PORT = 9998

        /**
         * Maximum UDP payload over IPv4 (65535 - 8 UDP - 20 IP): the largest a
         * `receiveBufferBytes` may be, and the size at which no datagram is ever
         * truncated on receive.
         */
        const val MAX_UDP_PAYLOAD_BYTES = 65507

        /**
         * Smallest permitted `receiveBufferBytes`. Comfortably holds every
         * handshake / keepalive token and a small application payload with
         * headroom; a buffer below this is almost certainly a misconfiguration.
         */
        const val MIN_RECEIVE_BUFFER_BYTES = 512

        /**
         * Default `receiveBufferBytes` - equal to [MAX_UDP_PAYLOAD_BYTES], so by
         * default no well-formed datagram is ever truncated on receive.
         */
        const val DEFAULT_RECEIVE_BUFFER_BYTES = MAX_UDP_PAYLOAD_BYTES

        /** How long [stop] waits for the common listener thread to notice it should stop. */
        private const val LISTENER_JOIN_TIMEOUT_MILLIS = 1000L
    }
}
