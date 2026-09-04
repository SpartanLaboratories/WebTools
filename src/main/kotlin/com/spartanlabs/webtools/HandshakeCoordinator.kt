package com.spartanlabs.webtools

import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * The heart of [MultiConnectionUDPServer], with the socket pulled out behind
 * injected collaborators so it can be tested with no real I/O. It is three things
 * at once:
 *
 * - the **handshake state machine** - a first `Iam` from a new origin registers a
 *   [Connection] and replies with the single token `REGISTERED`; a retransmit from
 *   a known origin just re-sends `REGISTERED`;
 * - the **inbound-datagram router** - [accept] classifies every datagram as a
 *   handshake, a keepalive (dropped), or application data (handed to the dispatch
 *   executor for the bound handler);
 * - the [ClientChannel] implementation the connections it mints delegate to for
 *   sending, binding, and unbinding.
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
     * @param text the trimmed datagram text
     * @return [Result.success] if handled or harmlessly ignored, or [Result.failure]
     * if a recognised handshake was malformed or its reply could not be delivered
     */
    fun accept(origin: InetSocketAddress, text: String): Result<Unit> = when {
        HandshakeProtocol.isKeepAlive(text) ->
            Result.success(Unit).also { log.trace("Keepalive from {}", origin) }

        HandshakeProtocol.isHandshake(text.split(' ')) -> handleHandshake(origin, text.split(' '))

        else -> deliverData(origin, text)
    }

    private fun handleHandshake(origin: InetSocketAddress, tokens: List<String>): Result<Unit> =
        HandshakeProtocol.parseHandshake(tokens).flatMap { name ->
            val extra = HandshakeProtocol.extraTokenCount(tokens)
            if (extra > 0) log.debug("Ignoring {} extra handshake token(s)", extra)

            registrations.findByOrigin(origin)?.let {
                // Retransmitted Iam from a known origin - repeat the token it already earned.
                log.info("Repeating handshake reply for already-registered origin {}", origin)
                send(REGISTERED_BYTES, origin)
            } ?: run {
                val connection = newConnection(name, origin, this)
                registrations.add(Registration(connection))
                log.info("Registered connection '{}' for {}", name, origin)
                send(REGISTERED_BYTES, origin).map { onRegistered(connection) }
            }
        }

    private fun deliverData(origin: InetSocketAddress, text: String): Result<Unit> {
        val registration = registrations.findByOrigin(origin)
            ?: return Result.success(Unit).also { log.debug("Dropped datagram from unregistered {}", origin) }
        val handler = registration.onMessage
            ?: return Result.success(Unit).also { log.debug("No handler bound for {}, dropping", origin) }
        // Hand delivery to the single-threaded executor so a slow handler never stalls the
        // listener thread (and therefore the handshake). The inner runCatching keeps a
        // throwing handler from killing the dispatch thread.
        return runCatching {
            dispatch { runCatching { handler(text) }.onFailure { log.warn("Handler for {} threw", origin, it) } }
        }
    }

    // --- ClientChannel ---

    override fun send(bytes: ByteArray, to: InetSocketAddress): Result<Unit> = sender(bytes, to)

    override fun bind(peer: InetSocketAddress, onMessage: (String) -> Unit) {
        registrations.findByOrigin(peer)?.onMessage = onMessage
    }

    override fun unbind(peer: InetSocketAddress) {
        registrations.findByOrigin(peer)?.onMessage = null
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
     * Sends a message to every registered client's endpoint, stopping at the first failure.
     * @param message the text to send to every client
     * @return [Result.success] if the message reached every client, or the first failure
     */
    fun broadcast(message: String): Result<Unit> {
        val bytes = message.toByteArray(Charsets.UTF_8)
        return registrations.snapshot().fold(Result.success(Unit)) { sent, registration ->
            sent.flatMap { send(bytes, registration.connection.peer) }
        }
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
