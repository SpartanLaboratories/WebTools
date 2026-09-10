package com.spartanlabs.webtools.udp

/**
 * The pure, socket-free rules of the [MultiConnectionUDPServer] handshake: how an
 * `Iam` line is parsed and validated, the single-token `REGISTERED` reply, and the
 * `KA` keepalive token.
 *
 * Everything here is a deterministic function of its arguments with no I/O and no
 * state, so it can be tested exhaustively without binding a socket.
 *
 * Note: an application layered on top of this protocol that legitimately sends a
 * message which, after trimming ASCII whitespace, is exactly [KEEPALIVE_TOKEN] -
 * or whose UTF-8 decode begins with the [VERB] token followed by a space - will
 * have it swallowed by the classifier before it reaches an application handler. This applies equally to a
 * raw binary payload whose decoded/trimmed bytes collide with a control token;
 * application protocols control their own payloads (lead binary datagrams with a
 * byte `>= 0x80` or `0x00` to sidestep it).
 *
 * The public subset of these tokens (and the keepalive check) is published as
 * [HandshakeWireFormat]; this object additionally owns the server-only inbound
 * parsing ([parseHandshake], [extraTokenCount], [isHandshake]) that a client
 * never needs.
 */
internal object HandshakeProtocol {
    /** The verb that opens a client handshake: `Iam <name>`. */
    const val VERB = HandshakeWireFormat.HANDSHAKE_VERB

    /** The entire server handshake reply: a single token, no arguments. */
    const val REGISTERED_REPLY = HandshakeWireFormat.REGISTERED_REPLY

    /** The verb of the server's handshake-refused reply: `REFUSED <reason>`. */
    const val REFUSED_REPLY = HandshakeWireFormat.REFUSED_REPLY

    /** The token a client sends on an idle interval to keep its NAT mapping warm. */
    const val KEEPALIVE_TOKEN = HandshakeWireFormat.KEEPALIVE_TOKEN

    /** The recommended output-idle interval between keepalive datagrams (~20 s). */
    const val DEFAULT_KEEPALIVE_INTERVAL_MILLIS = HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS

    /** The verb that opens a transport-level round-trip probe: `PING <token>`. */
    const val PROBE_REQUEST_VERB = HandshakeWireFormat.PROBE_REQUEST_VERB

    /** The verb of the probe echo: `PONG <token>`. */
    const val PROBE_REPLY_VERB = HandshakeWireFormat.PROBE_REPLY_VERB

    /** The default probe interval between `PING` datagrams (1 s). */
    const val DEFAULT_PROBE_INTERVAL_MILLIS = HandshakeWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS

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

    /**
     * True if [text] is a bare keepalive datagram (to be dropped, never dispatched).
     * @param text the trimmed datagram text
     * @return true if [text] is exactly the bare keepalive token
     */
    fun isKeepAlive(text: String): Boolean = HandshakeWireFormat.isKeepAlive(text)

    /**
     * True if [text] is a probe request (bare `PING` or `PING <token>`).
     * @param text the trimmed datagram text
     * @return true if [text] is a `PING` datagram
     */
    fun isProbeRequest(text: String): Boolean = HandshakeWireFormat.isProbeRequest(text)

    /**
     * True if [text] is a probe echo (bare `PONG` or `PONG <token>`).
     * @param text the trimmed datagram text
     * @return true if [text] is a `PONG` datagram
     */
    fun isProbeReply(text: String): Boolean = HandshakeWireFormat.isProbeReply(text)
}

/**
 * The parsed content of a client `Iam` handshake line.
 * @property name the client's chosen name (`tokens[1]`)
 * @property credential the opaque credential token (`tokens[2]`), or the empty
 * string when the client sent a bare `Iam <name>`; never interpreted by the library
 */
internal data class Handshake(val name: String, val credential: String)
