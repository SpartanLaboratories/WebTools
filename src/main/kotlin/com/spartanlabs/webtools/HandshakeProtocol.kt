package com.spartanlabs.webtools

/**
 * The pure, socket-free rules of the [MultiConnectionUDPServer] handshake: how an
 * `Iam` line is parsed and validated, the single-token `REGISTERED` reply, and the
 * `KA` keepalive token.
 *
 * Everything here is a deterministic function of its arguments with no I/O and no
 * state, so it can be tested exhaustively without binding a socket.
 *
 * Note: an application layered on top of this protocol that legitimately sends the
 * exact two-byte payload [KEEPALIVE_TOKEN] as a message will have it silently
 * swallowed by the server - application protocols control their own payloads.
 */
internal object HandshakeProtocol {
    /** The verb that opens a client handshake: `Iam <name>`. */
    const val VERB = "Iam"

    /** The entire server handshake reply: a single token, no arguments. */
    const val REGISTERED_REPLY = "REGISTERED"

    /** The token a client sends on an idle interval to keep its NAT mapping warm. */
    const val KEEPALIVE_TOKEN = "KA"

    /** Index of the client-supplied name within a whitespace-split handshake line. */
    private const val NAME_INDEX = 1

    /** Fewest tokens a valid handshake can carry: the verb plus the name. */
    private const val MIN_TOKENS = 2

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
     * True if [tokens] opens a handshake (verb match only; validity is
     * [parseHandshake]'s job).
     * @param tokens the datagram text split on spaces
     * @return true if [tokens] begins with the handshake verb
     */
    fun isHandshake(tokens: List<String>): Boolean = tokens.firstOrNull() == VERB

    /**
     * True if [text] is a bare keepalive datagram (to be dropped, never dispatched).
     * @param text the trimmed datagram text
     * @return true if [text] is exactly the bare keepalive token
     */
    fun isKeepAlive(text: String): Boolean = text == KEEPALIVE_TOKEN
}
