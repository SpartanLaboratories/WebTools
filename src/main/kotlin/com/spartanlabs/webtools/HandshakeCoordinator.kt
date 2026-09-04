package com.spartanlabs.webtools

import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * The handshake state machine of [MultiConnectionUDPServer], with the socket
 * pulled out behind three injected collaborators so it can be tested without any
 * real I/O:
 *
 * - [newConnection] mints a dedicated [Connection] for a freshly seen client;
 * - [reply] delivers a datagram body back to a client's handshake origin;
 * - [onRegistered] notifies the owner that a new client has completed the handshake.
 *
 * @param newConnection builds the dedicated connection for a new client, given its
 * name, handshake origin, and the port pair this coordinator allocated for it
 * @param reply sends [message] back to a handshake [origin]; its [Result] is
 * propagated out of [handle]
 * @param onRegistered invoked exactly once per newly registered client, after its
 * reply has been sent - never for a retransmitted handshake
 */
internal class HandshakeCoordinator(
    private val newConnection: (name: String, origin: InetSocketAddress, ports: HandshakeProtocol.PortPair) -> Connection,
    private val reply: (message: String, origin: InetSocketAddress) -> Result<Unit>,
    private val onRegistered: (Connection) -> Unit,
) {
    private val registrations = Registrations()

    /** How many clients have completed the handshake. */
    val size: Int get() = registrations.size

    /**
     * A stable snapshot of every completed handshake, oldest first.
     * @return an immutable copy, safe to iterate while new handshakes arrive
     */
    fun snapshot(): List<Registration> = registrations.snapshot()

    /**
     * Handles one already-split datagram received from [origin]. A message whose
     * verb is not [HandshakeProtocol.VERB] is ignored (returns success without
     * side effects).
     *
     * A well-formed `Iam` from a new origin allocates the next dedicated port
     * pair, registers a connection, sends the `TXRXON` reply, and - only if the
     * reply succeeded - calls [onRegistered]. A well-formed `Iam` from an origin
     * that is already registered re-sends its existing reply and does nothing
     * else, so a client retransmit never burns a second port pair.
     *
     * @param origin the address and source port the datagram was received from -
     * where every reply for this client is sent
     * @param tokens the whitespace-split datagram text
     * @return [Result.success] if the message was handled or harmlessly ignored,
     * or [Result.failure] if a recognised handshake was malformed or its reply
     * could not be delivered
     */
    fun handle(origin: InetSocketAddress, tokens: List<String>): Result<Unit> =
        when (tokens.firstOrNull()) {
            HandshakeProtocol.VERB -> HandshakeProtocol.parseHandshake(tokens).flatMap { name ->
                val extra = HandshakeProtocol.extraTokenCount(tokens)
                if (extra > 0) log.debug("Ignoring {} extra handshake token(s)", extra)

                registrations.findByOrigin(origin)?.let { existing ->
                    // Retransmitted Iam from a known origin - repeat the reply it already earned.
                    log.info("Repeating handshake reply for already-registered origin {}", origin)
                    reply(replyBodyFor(existing.connection), origin)
                } ?: run {
                    val ports = HandshakeProtocol.portPairFor(registrations.size)
                    val connection = newConnection(name, origin, ports)
                    registrations.add(Registration(connection, origin))
                    log.info("Registered connection '{}' for {} on ports {}", name, origin, ports)
                    reply(replyBodyFor(connection), origin).map { onRegistered(connection) }
                }
            }

            else -> Result.success(Unit).also { log.trace("Ignoring unrecognised message: {}", tokens) }
        }

    /**
     * Actuates every registered connection with [onMessage], stopping at the first
     * failure.
     * @param onMessage the callback each connection invokes for every datagram it receives
     * @return [Result.success] if every connection was actuated, or the first failure
     */
    fun actuateAll(onMessage: (message: String) -> Unit): Result<Unit> =
        registrations.snapshot().fold(Result.success(Unit)) { actuated, registration ->
            actuated.flatMap { registration.connection.actuate(onMessage) }
        }

    /**
     * Sends a message to every registered client's handshake origin through the
     * injected reply sink, stopping at the first failure.
     * @param message the text to send to every client
     * @return [Result.success] if the message reached every client, or the first failure
     */
    fun broadcast(message: String): Result<Unit> =
        registrations.snapshot().fold(Result.success(Unit)) { sent, registration ->
            sent.flatMap { reply(message, registration.origin) }
        }

    /**
     * Terminates every registered connection, releasing its ports. Every connection
     * is terminated even if an earlier one failed; only the reported [Result]
     * short-circuits to the first failure.
     * @return [Result.success] if every connection terminated cleanly, or the first failure
     */
    fun terminateAll(): Result<Unit> =
        registrations.snapshot().fold(Result.success(Unit)) { terminated, registration ->
            registration.connection.terminate().let { outcome -> terminated.flatMap { outcome } }
        }

    private fun replyBodyFor(connection: Connection): String =
        HandshakeProtocol.txrxonReply(connection.sendPort, connection.receivePort)

    private companion object {
        private val log = LoggerFactory.getLogger(HandshakeCoordinator::class.java)
    }
}
