package com.spartanlabs.webtools.udp

/**
 * The public, socket-free subset of the [MultiConnectionUDPServer] wire
 * format a client needs: the verb that opens a handshake, the token that
 * confirms it, and the keepalive token (both directions) — without the
 * server's internal inbound-parsing rules ([HandshakeProtocol] stays internal
 * for those).
 *
 * ### The optional credential slot
 * A client may append one **optional**, whitespace-free token to its handshake:
 * `Iam <name> <credential>`. The library never parses [credential] - it is an
 * opaque string handed verbatim to [MultiConnectionUDPServer.admit]. A caller
 * with a structured or binary credential encodes it first (base64url is the
 * recommendation). An absent credential is delivered to `admit` as the empty
 * string. The credential rides **in cleartext**: this is a channel, not
 * confidentiality - a scheme needing secrecy must be self-protecting (short
 * TTL, channel binding) or run over an encrypted underlay.
 *
 * ### The `REFUSED` reply
 * A `1.4.0`+ server may answer an `Iam` with `REFUSED <reason>` (or a bare
 * `REFUSED`) instead of `REGISTERED` - see [MultiConnectionUDPServer.admit].
 * `REFUSED` is interpreted **only** by [MultiConnectionUDPClient.handshake]
 * reading its single reply datagram; a server -> client application datagram
 * that happens to read `REFUSED ...` mid-session is delivered normally (the
 * session classifier only checks `KA`). Same caveat class as `REGISTERED` / `KA`.
 *
 * ### Managed keepalive cadence
 * The `KA` cadence is also available as a library-managed scheduler -
 * [MultiConnectionUDPClient.startKeepAlive] / [Connection.startKeepAlive],
 * defaulting to [DEFAULT_KEEPALIVE_INTERVAL_MILLIS]. This changes no wire format:
 * the one-shot [MultiConnectionUDPClient.sendKeepAlive] / [Connection.keepAlive]
 * tokens are unchanged and the datagram is byte-identical either way.
 *
 * ### Link-quality probe
 * An opt-in transport-level round-trip probe adds two additive tokens:
 * `PING <token>` opens the round trip and `PONG <token>` echoes the token
 * verbatim. Both are consumed by the transport on either side - like `KA` they
 * are never dispatched to an application handler - and both are off unless a
 * consumer calls [MultiConnectionUDPClient.startProbe] / [Connection.startProbe].
 * The responder answers an inbound `PING` with a `PONG` unconditionally (a client
 * always, a server only for a registered origin), so the peer can measure even if
 * this side never opted in. This adds no obligation to any existing token: an old
 * peer never sends `PING`/`PONG` and a new peer only sends them once opted in.
 *
 * ### Binary application payloads
 * Application data after the handshake may be raw binary - [Connection.push] and
 * [Connection.actuateBytes], or [MultiConnectionUDPClient.send] and
 * [MultiConnectionUDPClient.startBytes], each have a `ByteArray` form. The `Iam` /
 * `KA` classifier runs on the trimmed UTF-8 view of **every** inbound datagram,
 * in both directions, so a binary payload that decodes and trims to exactly
 * [KEEPALIVE_TOKEN], or begins with the [HANDSHAKE_VERB] token followed by a
 * space, is intercepted by the control-traffic machinery and never delivered to
 * an application handler. The same applies to a datagram that trims to exactly
 * [PROBE_REQUEST_VERB] / [PROBE_REPLY_VERB] or begins `PING `/`PONG `.
 * Mitigation: lead every binary application datagram with a byte that cannot
 * start [HANDSHAKE_VERB], [KEEPALIVE_TOKEN], [PROBE_REQUEST_VERB] or
 * [PROBE_REPLY_VERB] and is not ASCII whitespace - e.g. `0x00`, or any byte
 * `>= 0x80` used as a version/format tag.
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
     * The verb of the server's handshake-refused reply: `REFUSED <reason>` (or a
     * bare `REFUSED`). Sent by a `1.4.0`+ server instead of [REGISTERED_REPLY]
     * when [MultiConnectionUDPServer.admit] returns [Admission.Refused].
     */
    const val REFUSED_REPLY = "REFUSED"

    /**
     * The token either side sends on an idle interval to keep a NAT mapping warm.
     * On the server, besides being dropped, an inbound `KA` also refreshes that
     * client's per-connection liveness timestamp when idle detection is enabled
     * (see `MultiConnectionUDPServer`'s `idleTimeoutMillis`).
     */
    const val KEEPALIVE_TOKEN = "KA"

    /**
     * The recommended output-idle interval, in milliseconds, between keepalive
     * datagrams on a NAT'd path (~20 s). The default for
     * [MultiConnectionUDPClient.startKeepAlive] and [Connection.startKeepAlive].
     */
    const val DEFAULT_KEEPALIVE_INTERVAL_MILLIS = 20_000L

    /**
     * Builds the `Iam <name>` (or `Iam <name> <credential>`) datagram body a
     * client sends to open a connection.
     * @param name the client's chosen name; must not contain whitespace, since
     * handshake messages are whitespace-split
     * @param credential an optional opaque token the server's
     * [MultiConnectionUDPServer.admit] receives verbatim; must be whitespace-free
     * (base64url-encode a structured or binary credential). The default (empty
     * string) omits the token entirely, producing the pre-`1.4.0` `Iam <name>`.
     * @return the `Iam <name>` datagram body, with ` <credential>` appended when
     * [credential] is non-empty
     */
    @JvmOverloads
    fun handshakeMessage(name: String, credential: String = ""): String =
        if (credential.isEmpty()) "$HANDSHAKE_VERB $name" else "$HANDSHAKE_VERB $name $credential"

    /**
     * True if [reply] is the server's bare handshake-accepted token.
     * @param reply the trimmed reply text
     * @return true if [reply] is the bare `REGISTERED` token
     */
    fun isRegistered(reply: String): Boolean = reply == REGISTERED_REPLY

    /**
     * Builds the server's `REFUSED <reason>` reply (bare `REFUSED` if [reason] is
     * blank). [reason] is trimmed and any control/newline/whitespace run collapsed
     * to a single space so the reply stays one clean line - cosmetic, since it is
     * a single datagram with no framing-injection risk.
     * @param reason the human-readable refusal cause
     * @return `REFUSED <clean reason>`, or a bare `REFUSED` when [reason] is blank
     */
    fun refusedMessage(reason: String): String {
        val clean = reason.trim().replace(Regex("[\\p{Cntrl}\\s]+"), " ")
        return if (clean.isEmpty()) REFUSED_REPLY else "$REFUSED_REPLY $clean"
    }

    /**
     * True if [reply] is the server's handshake-refused token (bare or with a reason).
     * @param reply the trimmed reply text
     * @return true if [reply] is `REFUSED` or begins with `REFUSED `
     */
    fun isRefused(reply: String): Boolean =
        reply == REFUSED_REPLY || reply.startsWith("$REFUSED_REPLY ")

    /**
     * The reason carried by a `REFUSED <reason>` reply - everything after
     * `REFUSED `, trimmed; the empty string for a bare `REFUSED`. Only meaningful
     * when [isRefused] is true.
     * @param reply the trimmed reply text
     * @return the refusal reason, or the empty string
     */
    fun refusalReason(reply: String): String = reply.removePrefix(REFUSED_REPLY).trim()

    /**
     * True if [text] is a bare keepalive datagram (to be dropped, never dispatched
     * to application code, from either side).
     * @param text the trimmed datagram text
     * @return true if [text] is exactly the keepalive token
     */
    fun isKeepAlive(text: String): Boolean = text == KEEPALIVE_TOKEN

    /** The verb that opens a transport-level round-trip probe: `PING <token>`. */
    const val PROBE_REQUEST_VERB = "PING"

    /** The verb of the probe echo: `PONG <token>`, the token copied verbatim. */
    const val PROBE_REPLY_VERB = "PONG"

    /**
     * The default probe interval, in milliseconds (1 s), for
     * [MultiConnectionUDPClient.startProbe] and [Connection.startProbe]. One tiny
     * datagram per second per probed connection; a consumer that wants lighter
     * passes a larger interval. The probe is off unless a consumer opts in.
     */
    const val DEFAULT_PROBE_INTERVAL_MILLIS = 1_000L

    /**
     * Builds the `PING <token>` datagram body that opens a probe round trip.
     * @param token the prober's sequence number for this probe
     * @return the `PING <token>` datagram body
     */
    fun probeRequestMessage(token: String): String = "$PROBE_REQUEST_VERB $token"

    /**
     * Builds the `PONG <token>` echo, [token] copied verbatim from the `PING`.
     * @param token the token carried by the `PING` being answered
     * @return the `PONG <token>` datagram body
     */
    fun probeReplyMessage(token: String): String = "$PROBE_REPLY_VERB $token"

    /**
     * True if [text] is a probe request (bare `PING` or `PING <token>`), to be
     * answered with a `PONG` and never dispatched to application code.
     * @param text the trimmed datagram text
     * @return true if [text] is a `PING` datagram
     */
    fun isProbeRequest(text: String): Boolean =
        text == PROBE_REQUEST_VERB || text.startsWith("$PROBE_REQUEST_VERB ")

    /**
     * True if [text] is a probe echo (bare `PONG` or `PONG <token>`), consumed by
     * the prober and never dispatched to application code.
     * @param text the trimmed datagram text
     * @return true if [text] is a `PONG` datagram
     */
    fun isProbeReply(text: String): Boolean =
        text == PROBE_REPLY_VERB || text.startsWith("$PROBE_REPLY_VERB ")

    /**
     * The token carried by a `PING`/`PONG` line - everything after the verb,
     * trimmed; `""` if bare.
     * @param text the trimmed datagram text
     * @return the probe token, or the empty string for a bare `PING`/`PONG`
     */
    fun probeToken(text: String): String =
        text.removePrefix(PROBE_REQUEST_VERB).removePrefix(PROBE_REPLY_VERB).trim()
}
