package com.spartanlabs.webtools.udp

/**
 * The pure, socket-free rules of the [MultiConnectionUDPServer] handshake: how an
 * `Iam` line is parsed and validated, and the `REGISTERED <version>` reply.
 *
 * Everything here is a deterministic function of its arguments with no I/O and no
 * state, so it can be tested exhaustively without binding a socket.
 *
 * The handshake stays plain UTF-8 text; every **post-`REGISTERED`** datagram is
 * framed with a 1-byte [DatagramType] tag ([TransportWireFormat]) and is
 * classified off byte 0, never off a token match, so an application payload can
 * no longer collide with a control token.
 *
 * The public subset of the handshake tokens is published as [HandshakeWireFormat];
 * this object additionally owns the server-only inbound parsing ([parseHandshake],
 * [extraTokenCount], [isHandshake]) that a client never needs.
 */
internal object HandshakeProtocol {
    /** The verb that opens a client handshake: `Iam <name>`. */
    const val VERB = HandshakeWireFormat.HANDSHAKE_VERB

    /** The verb of the server handshake-accepted reply (`REGISTERED <version>`). */
    const val REGISTERED_REPLY = HandshakeWireFormat.REGISTERED_REPLY

    /** The verb of the server's handshake-refused reply: `REFUSED <reason>`. */
    const val REFUSED_REPLY = HandshakeWireFormat.REFUSED_REPLY

    /** The exact accepted-reply datagram this build sends: `REGISTERED 2` as UTF-8 bytes. */
    val REGISTERED_DATAGRAM: ByteArray = HandshakeWireFormat.registeredMessage().toByteArray(Charsets.UTF_8)

    /** Index of the client-supplied name within a whitespace-split handshake line. */
    private const val NAME_INDEX = 1

    /** Index of the optional client-supplied opaque credential token. */
    private const val CREDENTIAL_INDEX = 2

    /** Fewest tokens a valid handshake can carry: the verb plus the name. */
    private const val MIN_TOKENS = 2

    /**
     * Parses the whitespace-split text of an `Iam` datagram into its name and
     * optional opaque credential.
     *
     * The caller is expected to have already matched [VERB]. `tokens[2]`, if
     * present, is the opaque credential handed verbatim to
     * [MultiConnectionUDPServer.admit]; it is absent -> `""`. Any token past the
     * credential slot is ignored (see [extraTokenCount]).
     *
     * @param tokens the handshake line split on spaces
     * @return the parsed [Handshake] (name + credential, `""` if none), or
     * [Result.failure] holding an [IllegalArgumentException] if [tokens] is not a
     * well-formed `Iam <name>` line
     */
    fun parseHandshake(tokens: List<String>): Result<Handshake> = runCatching {
        require(tokens.firstOrNull() == VERB) { "Not an $VERB message: $tokens" }
        require(tokens.size >= MIN_TOKENS) { "Expected '$VERB <name>' but got ${tokens.size} token(s)" }
        Handshake(tokens[NAME_INDEX], tokens.getOrElse(CREDENTIAL_INDEX) { "" })
    }

    /**
     * How many tokens past the optional credential slot a handshake line carries -
     * all of which are ignored. Zero for a clean `Iam <name>` or
     * `Iam <name> <credential>`; never negative.
     * @param tokens the handshake line split on spaces
     * @return the count of ignored trailing tokens, `>= 0`
     */
    fun extraTokenCount(tokens: List<String>): Int =
        (tokens.size - (CREDENTIAL_INDEX + 1)).coerceAtLeast(0)

    /**
     * True if [tokens] opens a handshake (verb match only; validity is
     * [parseHandshake]'s job).
     * @param tokens the datagram text split on spaces
     * @return true if [tokens] begins with the handshake verb
     */
    fun isHandshake(tokens: List<String>): Boolean = tokens.firstOrNull() == VERB
}

/**
 * The parsed content of a client `Iam` handshake line.
 * @property name the client's chosen name (`tokens[1]`)
 * @property credential the opaque credential token (`tokens[2]`), or the empty
 * string when the client sent a bare `Iam <name>`; never interpreted by the library
 */
internal data class Handshake(val name: String, val credential: String)
