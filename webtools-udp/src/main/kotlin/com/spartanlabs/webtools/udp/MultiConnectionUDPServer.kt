package com.spartanlabs.webtools.udp

import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * A UDP server that multiplexes **all** traffic for any number of clients over a
 * single well-known "common" socket ([COMMON_LISTEN_PORT]).
 *
 * A client registers by sending `Iam <name>` (optionally `Iam <name> <credential>`
 * with an opaque trailing token) to the common port from a socket it keeps open.
 * The server screens every new handshake through [admit] and either replies -
 * from that same socket, addressed straight back to the datagram's source address
 * and port - with the single token `REGISTERED`, or refuses it with
 * `REFUSED <reason>` (registering nothing, firing no [onClientConnect]).
 * From then on the client sends and receives everything (application data,
 * broadcasts, keepalives) over that one socket to port [COMMON_LISTEN_PORT], and
 * the server addresses every datagram back to the client's observed post-NAT
 * source. Because the server only ever transmits on a 5-tuple the client already
 * opened, the data path traverses NAT and the server binds no per-client ports.
 *
 * A client sends the token `KA` on an idle interval (~20 s recommended) to keep
 * its NAT mapping warm; the server consumes inbound `KA` without dispatching it,
 * but - when idle detection is enabled (see below) - an inbound `KA` also
 * refreshes that client's per-connection liveness timestamp.
 *
 * This class is abstract because it does not itself decide what to do once a
 * client has finished the handshake - subclasses implement [onClientConnect]
 * (e.g. call [Connection.actuate], track the connection, notify the application).
 *
 * The handshake rules live in [HandshakeProtocol] (pure) and [HandshakeCoordinator]
 * (the state machine + inbound router); this class binds them to a real socket.
 *
 * ### Handshake screening & credentials
 * Override [admit] to accept or refuse a handshake before it is registered. The
 * default admits every well-formed `Iam` - identical to pre-`1.4.0` behaviour.
 * [admit] returns [Admission.Admitted] or [Admission.Refused]`(reason)`; on a
 * refusal the server sends `REFUSED <reason>` back to the `Iam` origin, mints no
 * [Connection], adds no registration, and does not call [onClientConnect] - the
 * client surfaces it as a typed [HandshakeRefusedException]. This removes the old
 * "reply `REGISTERED`, then silently drop" workaround for capacity/auth refusals.
 * A client may carry an opaque, whitespace-free `<credential>` token in its `Iam`
 * line (`Iam <name> <credential>`); it is passed to [admit] verbatim, never
 * interpreted by the library, and rides in cleartext. [admit] runs **inline on
 * the common listener thread**, so it must return promptly.
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
 * [receiveBufferBytes] is outside [MIN_RECEIVE_BUFFER_BYTES]..[MAX_UDP_PAYLOAD_BYTES],
 * or if [idleTimeoutMillis] is negative. A positive [idleTimeoutMillis] also
 * starts the `mcups-liveness` daemon thread.
 *
 * @param receiveBufferBytes size of the datagram receive buffer,
 * [MIN_RECEIVE_BUFFER_BYTES]..[MAX_UDP_PAYLOAD_BYTES]; defaults to
 * [DEFAULT_RECEIVE_BUFFER_BYTES] (65507) so no well-formed datagram is truncated
 * @param idleTimeoutMillis opt-in idle-connection threshold in milliseconds; `0`
 * (the default, [DISABLED_IDLE_TIMEOUT]) disables idle detection entirely - no
 * extra thread, no per-datagram work. Must be `>= 0`; a negative value throws
 * [IllegalArgumentException]; `0` disables detection.
 *
 * ### Connection liveness
 * With a positive [idleTimeoutMillis], every registered connection carries the
 * monotonic time of its last inbound datagram (application data or `KA`), and a
 * dedicated `mcups-liveness` daemon thread periodically reports any connection
 * idle beyond the threshold via [onClientDisconnect] with
 * [DisconnectReason.TIMEOUT]. This is **notify-only**: the registration stays in
 * place and addressable by [pushToAll] until the application calls
 * [Connection.terminate] itself. A same-name supersede
 * ([DisconnectReason.SUPERSEDED]) and an application [Connection.terminate]
 * ([DisconnectReason.TERMINATED]) also flow through [onClientDisconnect].
 * [stop] teardown does not.
 *
 * ### Concurrency
 * One long-lived daemon listener thread only *demultiplexes*: `receive()` ->
 * classify (`Iam` / `KA` / data) -> run the socket-free handshake state machine
 * inline, drop the keepalive, or hand the payload to the dispatch executor. The
 * [admit] screen runs inline on this listener thread too (like [onClientConnect]),
 * so a slow [admit] stalls demultiplexing for every client. A
 * third daemon thread - `mcups-liveness`, a [ScheduledExecutorService] - exists
 * **only when [idleTimeoutMillis] > 0**; it runs the idle-connection sweep and
 * never runs application code (the [onClientDisconnect] hook is handed to
 * `mcups-dispatch`). The
 * dispatch executor is a **single** daemon thread (`mcups-dispatch`) that invokes
 * [Connection] message handlers, so per-client message order is preserved and a
 * slow handler cannot stall the listener or the handshake. Accepted trade-off:
 * a slow handler delays delivery to *other* clients - handlers must return
 * promptly. The common socket is received on only by the listener thread but sent
 * on from any thread (the JDK permits a concurrent send during a receive); the
 * registration list is copy-on-write.
 *
 * See the sequence diagram in `docs/issue-1-tier-2-plan.md` §2.1 for the canonical
 * end-to-end flow, and `docs/issue-10-connection-liveness-plan.md` §2.8 for the
 * idle-sweep sequence.
 */
abstract class MultiConnectionUDPServer @JvmOverloads protected constructor(
    private val receiveBufferBytes: Int = DEFAULT_RECEIVE_BUFFER_BYTES,
    private val idleTimeoutMillis: Long = DISABLED_IDLE_TIMEOUT,
) {
    init {
        require(receiveBufferBytes in MIN_RECEIVE_BUFFER_BYTES..MAX_UDP_PAYLOAD_BYTES) {
            "receiveBufferBytes must be $MIN_RECEIVE_BUFFER_BYTES..$MAX_UDP_PAYLOAD_BYTES, was $receiveBufferBytes"
        }
        require(idleTimeoutMillis >= 0L) {
            "idleTimeoutMillis must be >= 0 (0 disables idle detection), was $idleTimeoutMillis"
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
        admit = ::admit,
        dispatch = { block -> dispatchExecutor.execute(block) },
        onDisconnect = { connection, reason ->
            dispatchExecutor.execute {
                runCatching { onClientDisconnect(connection, reason) }
                    .onFailure { log.warn("onClientDisconnect threw for '{}'", connection.name, it) }
            }
        },
        idleTimeoutMillis = idleTimeoutMillis,
    )

    /**
     * Optional idle-connection sweep executor - a single `mcups-liveness` daemon
     * thread, created only when [idleTimeoutMillis] > 0.
     */
    private val livenessExecutor: ScheduledExecutorService? =
        if (idleTimeoutMillis > 0)
            Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "mcups-liveness").apply { isDaemon = true }
            }
        else null

    init {
        livenessExecutor?.let { exec ->
            val interval = Liveness.sweepIntervalMillis(idleTimeoutMillis)
            // scheduleWithFixedDelay (not AtFixedRate): a delay between runs prevents sweep
            // pile-up if one sweep is stalled by a GC or scheduler pause.
            exec.scheduleWithFixedDelay(
                {
                    // runCatching is load-bearing: a ScheduledExecutorService silently cancels a
                    // repeating task forever the first time it throws, which would disable idle
                    // detection permanently - so every sweep must swallow its own failure.
                    runCatching { coordinator.sweepIdleConnections() }
                        .onFailure { log.warn("Liveness sweep failed; detection continues", it) }
                },
                interval, interval, TimeUnit.MILLISECONDS,
            )
        }
    }

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
     * Screens an incoming `Iam` handshake before it is accepted. The default
     * admits every well-formed handshake - today's behaviour. Override to validate
     * [credential] (an opaque token the client supplied, empty if it sent none) or
     * to refuse for application reasons (over capacity, banned): return
     * [Admission.Refused] and the client receives `REFUSED <reason>` as a typed
     * [HandshakeRefusedException], with nothing registered and no
     * [onClientConnect] call. This replaces the old "reply `REGISTERED` then
     * silently drop the client" pattern for a connect-time refusal.
     *
     * Runs **inline on the common listener thread** (like [onClientConnect]), so it
     * must return promptly - a blocking credential lookup stalls inbound handling
     * for every client. Do fast, local checks here; hand a slow verification to
     * another thread and gate on its cached result.
     *
     * Called only for a first `Iam` from an unknown origin - not for a retransmit
     * from an already-registered origin. Called for a same-name reconnect from a
     * **new** origin *before* the stale registration is superseded, so refusing it
     * leaves the existing connection intact.
     *
     * A thrown exception is caught, logged, and the handshake is dropped (no
     * reply, no registration); the client sees a timeout and may retry.
     *
     * @param name the client's chosen name
     * @param peer the client's observed post-NAT origin
     * @param credential the opaque token from `Iam <name> <credential>`, verbatim;
     * the empty string if the client sent `Iam <name>`. A custom implementation
     * must tolerate arbitrary/garbage credential strings (a legacy client may put
     * an unrelated trailing token there).
     * @return [Admission.Admitted] to accept, [Admission.Refused] to reject
     */
    open fun admit(name: String, peer: InetSocketAddress, credential: String): Admission =
        Admission.Admitted

    /**
     * Called when a registered client connection stops being addressable - see
     * [DisconnectReason]. Runs on the single-threaded dispatch executor
     * (`mcups-dispatch`), not the caller's thread, so it must return promptly.
     *
     * The default implementation does nothing; override to react (e.g. start a
     * reconnect grace window on [DisconnectReason.TIMEOUT], then keep the entities
     * alive and rebind on a resume token).
     *
     * Notify-only: [DisconnectReason.TIMEOUT] does **not** remove the registration
     * or call [Connection.terminate] - the connection stays addressable by
     * [pushToAll] until the application decides. A subsequent [Connection.terminate]
     * then produces a second call here with [DisconnectReason.TERMINATED].
     *
     * Not called during [stop]; a full server shutdown is not a per-connection event.
     *
     * @param connection the connection that stopped being addressable
     * @param reason why
     */
    open fun onClientDisconnect(connection: Connection, reason: DisconnectReason) {}

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
     * thread, releases the common socket, shuts the `mcups-liveness` executor (if
     * one was started), and shuts the dispatch executor.
     *
     * Fires no [onClientDisconnect] callbacks: [stop] calls the coordinator's
     * `stopNotifying()` before `terminateAll()`, so a full server shutdown is not
     * reported as a per-connection event.
     *
     * Every step runs even if an earlier one failed, so a partial failure never
     * leaks a bound port. Once called, this instance should be discarded.
     *
     * @return [Result.success] if every step succeeded, or the first failure encountered
     */
    fun stop(): Result<Unit> {
        log.info("Stopping server: terminating {} connection(s)", coordinator.size)
        coordinator.stopNotifying()
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
        val livenessStopped = runCatching { livenessExecutor?.shutdownNow(); Unit }
            .onFailure { log.warn("Could not cleanly shut the liveness executor", it) }
        val executorStopped = runCatching { dispatchExecutor.shutdownNow() }.map { }
            .onFailure { log.warn("Could not cleanly shut the dispatch executor", it) }
        return connectionsTerminated.flatMap { listenerJoined }.flatMap { socketClosed }
            .flatMap { livenessStopped }.flatMap { executorStopped }
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

        /**
         * Default [idleTimeoutMillis]: `0`, i.e. idle-connection detection is off.
         * When enabling it, a value around 3x the ~20 s `KA` cadence (~60_000) is
         * a sane starting point.
         */
        const val DISABLED_IDLE_TIMEOUT = 0L

        /** How long [stop] waits for the common listener thread to notice it should stop. */
        private const val LISTENER_JOIN_TIMEOUT_MILLIS = 1000L
    }
}
