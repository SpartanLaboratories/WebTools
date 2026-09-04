package com.spartanlabs.webtools

/**
 * The pure, socket-free rules of the [MultiConnectionUDPServer] handshake: how an
 * `Iam` line is parsed and validated, how each connection's dedicated port pair
 * is allocated, and how the `TXRXON` reply body is rendered.
 *
 * Everything here is a deterministic function of its arguments with no I/O and no
 * state, so it can be tested exhaustively without binding a socket.
 */
internal object HandshakeProtocol {
    /** The verb that opens a client handshake: `Iam <name>`. */
    const val VERB = "Iam"

    /** The verb of the server's handshake reply: `TXRXON <sendPort> <receivePort>`. */
    const val REPLY_VERB = "TXRXON"

    /**
     * Ceiling below which each connection's dedicated `(send, receive)` port pair
     * is allocated. See [portPairFor].
     */
    const val DEDICATED_PORT_BASE = 9999

    /** Index of the client-supplied name within a whitespace-split handshake line. */
    private const val NAME_INDEX = 1

    /** Fewest tokens a valid handshake can carry: the verb plus the name. */
    private const val MIN_TOKENS = 2

    /** A dedicated `(send, receive)` port pair for one connection. */
    data class PortPair(val sendPort: Int, val receivePort: Int)

    /**
     * Extracts the client name from the whitespace-split text of an `Iam` datagram.
     *
     * The caller is expected to have already matched [VERB]; any token after the
     * name is a legacy client-claimed address and is ignored (see [extraTokenCount]).
     *
     * @param tokens the handshake line split on spaces
     * @return the client name, or [Result.failure] holding an [IllegalArgumentException]
     * if [tokens] is not a well-formed `Iam <name>` line
     */
    fun parseHandshake(tokens: List<String>): Result<String> = runCatching {
        require(tokens.firstOrNull() == VERB) { "Not an $VERB message: $tokens" }
        require(tokens.size >= MIN_TOKENS) { "Expected '$VERB <name>' but got ${tokens.size} token(s)" }
        tokens[NAME_INDEX]
    }

    /**
     * How many tokens past the client name a handshake line carries - all of which
     * are ignored. Zero for a clean `Iam <name>`; never negative.
     * @param tokens the handshake line split on spaces
     * @return the count of ignored trailing tokens, `>= 0`
     */
    fun extraTokenCount(tokens: List<String>): Int = (tokens.size - MIN_TOKENS).coerceAtLeast(0)

    /**
     * The dedicated port pair for the connection registered at zero-based [index].
     *
     * Registration *n* gets `DEDICATED_PORT_BASE - 2n - 2` for send and one below
     * it for receive. Stepping by two guarantees successive registrations never
     * share a port (the bug fixed in commit `ab53747`).
     *
     * @param index the zero-based registration order of the connection
     * @return its `(send, receive)` port pair
     * @throws IllegalArgumentException if [index] is negative
     */
    fun portPairFor(index: Int): PortPair {
        require(index >= 0) { "index must be >= 0, was $index" }
        val offset = index * 2 + 2
        return PortPair(DEDICATED_PORT_BASE - offset, DEDICATED_PORT_BASE - offset - 1)
    }

    /**
     * Renders the handshake reply body a client should receive.
     * @param ports the client's dedicated port pair
     * @return `TXRXON <sendPort> <receivePort>`
     */
    fun txrxonReply(ports: PortPair): String = "$REPLY_VERB ${ports.sendPort} ${ports.receivePort}"

    /**
     * Renders the reply body from a loose send/receive port.
     * @param sendPort the port the client should send to
     * @param receivePort the port the client should listen on
     * @return `TXRXON <sendPort> <receivePort>`
     */
    fun txrxonReply(sendPort: Int, receivePort: Int): String = txrxonReply(PortPair(sendPort, receivePort))
}
