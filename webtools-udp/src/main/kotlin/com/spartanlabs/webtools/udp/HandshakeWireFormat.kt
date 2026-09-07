package com.spartanlabs.webtools.udp

/**
 * The public, socket-free subset of the [MultiConnectionUDPServer] wire
 * format a client needs: the verb that opens a handshake, the token that
 * confirms it, and the keepalive token (both directions) — without the
 * server's internal inbound-parsing rules ([HandshakeProtocol] stays internal
 * for those).
 *
 * ### Binary application payloads
 * Application data after the handshake may be raw binary - [Connection.push] and
 * [Connection.actuateBytes], or [MultiConnectionUDPClient.send] and
 * [MultiConnectionUDPClient.startBytes], each have a `ByteArray` form. The `Iam` /
 * `KA` classifier runs on the trimmed UTF-8 view of **every** inbound datagram,
 * in both directions, so a binary payload that decodes and trims to exactly
 * [KEEPALIVE_TOKEN], or begins with the [HANDSHAKE_VERB] token followed by a
 * space, is intercepted by the control-traffic machinery and never delivered to
 * an application handler. Mitigation: lead every binary application datagram with
 * a byte that cannot start [HANDSHAKE_VERB] or [KEEPALIVE_TOKEN] and is not ASCII
 * whitespace - e.g. `0x00`, or any byte `>= 0x80` used as a version/format tag.
 * The maximum datagram the library will receive whole is 65507 bytes; a larger
 * one is truncated, not rejected. On the send side a payload above the OS
 * datagram limit fails the returned `Result` (cause logged) rather than being
 * sent; for real-network use keep frames under the path MTU (~1200 bytes) to
 * avoid IP fragmentation.
 */
object HandshakeWireFormat {
    /** The verb that opens a client handshake: `Iam <name>`. */
    const val HANDSHAKE_VERB = "Iam"

    /** The entire server handshake reply: a single token, no arguments. */
    const val REGISTERED_REPLY = "REGISTERED"

    /**
     * The token either side sends on an idle interval to keep a NAT mapping warm.
     * On the server, besides being dropped, an inbound `KA` also refreshes that
     * client's per-connection liveness timestamp when idle detection is enabled
     * (see `MultiConnectionUDPServer`'s `idleTimeoutMillis`).
     */
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
