package com.spartanlabs.webtools

/**
 * The public, socket-free subset of the [MultiConnectionUDPServer] wire
 * format a client needs: the verb that opens a handshake, the token that
 * confirms it, and the keepalive token (both directions) — without the
 * server's internal inbound-parsing rules ([HandshakeProtocol] stays internal
 * for those).
 */
object HandshakeWireFormat {
    /** The verb that opens a client handshake: `Iam <name>`. */
    const val HANDSHAKE_VERB = "Iam"

    /** The entire server handshake reply: a single token, no arguments. */
    const val REGISTERED_REPLY = "REGISTERED"

    /** The token either side sends on an idle interval to keep a NAT mapping warm. */
    const val KEEPALIVE_TOKEN = "KA"

    /**
     * Builds the `Iam <name>` datagram body a client sends to open a connection.
     * @param name the client's chosen name; must not contain whitespace, since
     * handshake messages are whitespace-split
     * @return the `Iam <name>` datagram body
     */
    fun handshakeMessage(name: String): String = "$HANDSHAKE_VERB $name"

    /**
     * True if [reply] is the server's bare handshake-accepted token.
     * @param reply the trimmed reply text
     * @return true if [reply] is the bare `REGISTERED` token
     */
    fun isRegistered(reply: String): Boolean = reply == REGISTERED_REPLY

    /**
     * True if [text] is a bare keepalive datagram (to be dropped, never dispatched
     * to application code, from either side).
     * @param text the trimmed datagram text
     * @return true if [text] is exactly the keepalive token
     */
    fun isKeepAlive(text: String): Boolean = text == KEEPALIVE_TOKEN
}
