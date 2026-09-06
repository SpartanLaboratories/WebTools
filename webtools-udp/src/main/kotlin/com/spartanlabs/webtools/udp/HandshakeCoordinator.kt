package com.spartanlabs.webtools.udp

import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * The heart of [MultiConnectionUDPServer], with the socket pulled out behind
 * injected collaborators so it can be tested with no real I/O. It is three things
 * at once:
 *
 * - the **handshake state machine** - a first `Iam` from a new origin registers a
 *   [Connection] and replies with the single token `REGISTERED`; a retransmit from
 *   a known origin just re-sends `REGISTERED`; a first `Iam` from a name that is
 *   already registered under a different, now-stale origin (e.g. after a NAT
 *   rebind) supersedes it - the stale registration is terminated and removed
 *   before the new one is added;
 * - the **inbound-datagram router** - [accept] classifies every datagram as a
 *   handshake, a keepalive (dropped), or application data (handed to the dispatch
 *   executor for the bound handler);
 * - the [ClientChannel] implementation the connections it mints delegate to for
 *   sending, binding, and deregistering.
 *
 * @param newConnection builds the connection for a new client, given its name,
 * handshake origin, and the [ClientChannel] it should delegate to (always `this`)
 * @param sender sends raw bytes to a client endpoint; its [Result] is propagated
 * @param onRegistered invoked exactly once per newly registered client, after its
 * `REGISTERED` reply has been sent - never for a retransmitted handshake
 * @param dispatch hands a block to the server's single-threaded dispatch executor
 */
internal class HandshakeCoordinator(
    private val newConnection: (name: String, peer: InetSocketAddress, channel: ClientChannel) -> Connection,
    private val sender: (bytes: ByteArray, to: InetSocketAddress) -> Result<Unit>,
    private val onRegistered: (Connection) -> Unit,
    private val dispatch: (block: () -> Unit) -> Unit,
) : ClientChannel {
    private val registrations = Registrations()

    /** How many clients have completed the handshake. */
    val size: Int get() = registrations.size

    /**
     * A stable snapshot of every completed handshake, oldest first.
     * @return an immutable copy, safe to iterate while new handshakes arrive
     */
    fun snapshot(): List<Registration> = registrations.snapshot()

    /**
     * The single entry point the listener loop calls for every inbound datagram.
     * Classifies before acting: a bare `KA` keepalive is dropped (success, no
     * dispatch); an `Iam` runs the handshake state machine inline; anything else
     * is application data routed to the bound handler.
     *
     * @param origin the datagram's post-NAT source - where any reply is addressed
     * @param bytes the exact-length datagram body, handed verbatim to a bound
     * bytes handler; classification still runs off [text]
     * @param text the trimmed datagram text
     * @return [Result.success] if handled or harmlessly ignored, or [Result.failure]
     * if a recognised handshake was malformed or its reply could not be delivered
     */
    fun accept(origin: InetSocketAddress, bytes: ByteArray, text: String): Result<Unit> = when {
        HandshakeProtocol.isKeepAlive(text) ->
            Result.success(Unit).also { log.trace("Keepalive from {}", origin) }

        HandshakeProtocol.isHandshake(text.split(' ')) -> handleHandshake(origin, text.split(' '))

        else -> deliverData(origin, bytes, text)
    }

    /**
     * Two-argument [accept] overload retained for callers that only have the
     * datagram text - delegates with `bytes` derived from a UTF-8 encode of [text].
     */
    fun accept(origin: InetSocketAddress, text: String): Result<Unit> =
        accept(origin, text.toByteArray(Charsets.UTF_8), text)

    private fun handleHandshake(origin: InetSocketAddress, tokens: List<String>): Result<Unit> =
        HandshakeProtocol.parseHandshake(tokens).flatMap { name ->
            val extra = HandshakeProtocol.extraTokenCount(tokens)
            if (extra > 0) log.debug("Ignoring {} extra handshake token(s)", extra)

            registrations.findByOrigin(origin)?.let {
                // Retransmitted Iam from a known origin - repeat the token it already earned.
                log.info("Repeating handshake reply for already-registered origin {}", origin)
                send(REGISTERED_BYTES, origin)
            } ?: run {
                // A same-name registration under a different origin is a stale entry (e.g. a NAT
                // rebind), not a distinct client - supersede it directly against Registrations,
                // rather than relying solely on stale.connection.terminate(), so pruning stays
                // correct even for fakes whose terminate() doesn't reach back into this registry.
                registrations.findByName(name)?.let { stale ->
                    log.info("Superseding stale registration for '{}': {} -> {}", name, stale.origin, origin)
                    registrations.removeByOrigin(stale.origin)
                    stale.connection.terminate()
                        .onFailure { log.warn("Failed to terminate superseded connection '{}'", name, it) }
                }
                val connection = newConnection(name, origin, this)
                registrations.add(Registration(connection))
                log.info("Registered connection '{}' for {}", name, origin)
                send(REGISTERED_BYTES, origin).map { onRegistered(connection) }
            }
        }

    private fun deliverData(origin: InetSocketAddress, bytes: ByteArray, text: String): Result<Unit> {
        val registration = registrations.findByOrigin(origin)
            ?: return Result.success(Unit).also { log.debug("Dropped datagram from unregistered {}", origin) }
        // A bytes handler, if bound, wins over a text handler - the two are mutually
        // exclusive in practice (bind/bindBytes null the other) but check bytes first.
        val bytesHandler = registration.onBytes
        val textHandler = registration.onMessage
        // Hand delivery to the single-threaded executor so a slow handler never stalls the
        // listener thread (and therefore the handshake). The inner runCatching keeps a
        // throwing handler from killing the dispatch thread.
        return when {
            bytesHandler != null -> runCatching {
                dispatch { runCatching { bytesHandler(bytes) }.onFailure { log.warn("Handler for {} threw", origin, it) } }
            }

            textHandler != null -> runCatching {
                dispatch { runCatching { textHandler(text) }.onFailure { log.warn("Handler for {} threw", origin, it) } }
            }

            else -> Result.success(Unit).also { log.debug("No handler bound for {}, dropping", origin) }
        }
    }

    // --- ClientChannel ---

    override fun send(bytes: ByteArray, to: InetSocketAddress): Result<Unit> = sender(bytes, to)

    override fun bind(peer: InetSocketAddress, onMessage: (String) -> Unit) {
        registrations.findByOrigin(peer)?.let {
            it.onMessage = onMessage
            it.onBytes = null
        }
    }

    override fun bindBytes(peer: InetSocketAddress, onMessage: (ByteArray) -> Unit) {
        registrations.findByOrigin(peer)?.let {
            it.onBytes = onMessage
            it.onMessage = null
        }
    }

    override fun deregister(peer: InetSocketAddress) {
        if (registrations.removeByOrigin(peer)) {
            log.info("Deregistered connection for {}", peer)
        }
    }

    /**
     * Binds [onMessage] as the handler on every registered connection, stopping at
     * the first failure. No socket is started.
     * @param onMessage the callback each connection invokes for every datagram it receives
     * @return [Result.success] if every connection was actuated, or the first failure
     */
    fun actuateAll(onMessage: (message: String) -> Unit): Result<Unit> =
        registrations.snapshot().fold(Result.success(Unit)) { actuated, registration ->
            actuated.flatMap { registration.connection.actuate(onMessage) }
        }

    /**
     * Binds [onMessage] as the raw-bytes handler on every registered connection,
     * stopping at the first failure. Mirror of [actuateAll] for the binary path.
     * @param onMessage the callback each connection invokes with an exact-length
     * copy of every datagram it receives
     * @return [Result.success] if every connection was actuated, or the first failure
     */
    fun actuateAllBytes(onMessage: (ByteArray) -> Unit): Result<Unit> =
        registrations.snapshot().fold(Result.success(Unit)) { actuated, registration ->
            actuated.flatMap { registration.connection.actuateBytes(onMessage) }
        }

    /**
     * Sends a message to every registered client's endpoint, stopping at the first failure.
     * @param message the text to send to every client
     * @return [Result.success] if the message reached every client, or the first failure
     */
    fun broadcast(message: String): Result<Unit> = broadcast(message.toByteArray(Charsets.UTF_8))

    /**
     * Sends [bytes] verbatim to every registered client's endpoint, stopping at
     * the first failure. Raw-bytes mirror of [broadcast]`(String)`. A payload
     * above the OS datagram limit fails the [Result] for that client (cause
     * logged) rather than being sent.
     * @param bytes the raw payload to send to every client
     * @return [Result.success] if the datagram reached every client, or the first failure
     */
    fun broadcast(bytes: ByteArray): Result<Unit> =
        registrations.snapshot().fold(Result.success(Unit)) { sent, registration ->
            sent.flatMap { send(bytes, registration.connection.peer) }
        }

    /**
     * Terminates every registered connection. Every connection is terminated even
     * if an earlier one failed; only the reported [Result] short-circuits to the
     * first failure.
     * @return [Result.success] if every connection terminated cleanly, or the first failure
     */
    fun terminateAll(): Result<Unit> =
        registrations.snapshot().fold(Result.success(Unit)) { terminated, registration ->
            registration.connection.terminate().let { outcome -> terminated.flatMap { outcome } }
        }

    private companion object {
        private val log = LoggerFactory.getLogger(HandshakeCoordinator::class.java)
        private val REGISTERED_BYTES = HandshakeProtocol.REGISTERED_REPLY.toByteArray(Charsets.UTF_8)
    }
}
