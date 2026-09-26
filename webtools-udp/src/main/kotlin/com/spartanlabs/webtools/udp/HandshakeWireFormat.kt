package com.spartanlabs.webtools.udp

/**
 * The public, socket-free **handshake-bootstrap** wire format a client needs: the
 * verb that opens a handshake (`Iam <name> [<credential>]`), the accepted reply
 * (`REGISTERED <version>`), and the refusal reply (`REFUSED <reason>`).
 *
 * Everything **after** the handshake — the 1-byte [DatagramType] prefix, the
 * keepalive / probe control datagrams, and the unreliable application-data frame
 * — lives in [TransportWireFormat]. The handshake itself stays plain UTF-8 text
 * so a first datagram from an unknown origin is still classifiable by text match,
 * and a cross-major peer mismatch fails cleanly at `handshake()`.
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
 * ### The `REGISTERED <version>` reply and cross-version failure
 * A `2.x` server answers an accepted `Iam` with `REGISTERED 2` — the bare verb
 * [REGISTERED_REPLY] plus [TransportWireFormat.FRAMED_PROTOCOL_VERSION]. This is
 * a one-way **version assertion**, not a negotiation: there is no downgrade path.
 *
 * | Client | Server | Reply seen | `handshake()` result |
 * |---|---|---|---|
 * | `2.0` | `2.0` | `REGISTERED 2` | `Result.success` |
 * | `2.0` | `1.x` | `REGISTERED` (bare) | `Result.failure(IncompatibleProtocolException(remote = 1, local = 2))` |
 * | `1.x` | `2.0` | `REGISTERED 2` | `Result.failure(IllegalStateException("Expected 'REGISTERED' but got 'REGISTERED 2'"))` |
 *
 * ### The `REFUSED` reply
 * A `1.4.0`+ server may answer an `Iam` with `REFUSED <reason>` (or a bare
 * `REFUSED`) instead of an accepted reply - see [MultiConnectionUDPServer.admit].
 * `REFUSED` is interpreted **only** by [MultiConnectionUDPClient.handshake]
 * reading its single reply datagram; framed session traffic never carries it.
 */
object HandshakeWireFormat {
    /** The verb that opens a client handshake: `Iam <name>`. */
    const val HANDSHAKE_VERB = "Iam"

    /**
     * The **verb** of the server's handshake-accepted reply. A `2.x` server sends
     * this verb followed by [TransportWireFormat.FRAMED_PROTOCOL_VERSION] - see
     * [registeredMessage] / [isRegistered].
     */
    const val REGISTERED_REPLY = "REGISTERED"

    /**
     * The verb of the server's handshake-refused reply: `REFUSED <reason>` (or a
     * bare `REFUSED`). Sent by a `1.4.0`+ server instead of an accepted reply
     * when [MultiConnectionUDPServer.admit] returns [Admission.Refused].
     */
    const val REFUSED_REPLY = "REFUSED"

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
     * The full accepted reply this build sends and requires: `REGISTERED 2`.
     * @return `REGISTERED` + a space + [TransportWireFormat.FRAMED_PROTOCOL_VERSION]
     */
    fun registeredMessage(): String = "$REGISTERED_REPLY ${TransportWireFormat.FRAMED_PROTOCOL_VERSION}"

    /**
     * The wire-protocol major a `REGISTERED` reply announces: `2` for the exact
     * `REGISTERED 2`, `1` for a bare `REGISTERED` (a pre-`2.0` server), or `null`
     * if [reply] is not a `REGISTERED[ <canonical-decimal>]` line.
     *
     * Strict (see the resolved decision OD-4): exactly one canonical decimal
     * token. `REGISTERED 02`, `REGISTERED 2 3`, `REGISTERED x` all yield `null`.
     * @param reply the trimmed reply text
     * @return the announced major, or `null`
     */
    fun registeredProtocolVersion(reply: String): Int? {
        if (reply == REGISTERED_REPLY) return 1
        val rest = reply.removePrefix("$REGISTERED_REPLY ")
        // removePrefix returns the string unchanged when the prefix is absent.
        if (rest === reply || rest.isEmpty() || rest.contains(' ')) return null
        val version = rest.toIntOrNull() ?: return null
        // Reject a non-canonical spelling (leading zero, sign) - version.toString() is canonical.
        return if (version.toString() == rest) version else null
    }

    /**
     * True only for this build's exact accepted reply (`REGISTERED 2`).
     * @param reply the trimmed reply text
     * @return true if [reply] announces [TransportWireFormat.FRAMED_PROTOCOL_VERSION]
     */
    fun isRegistered(reply: String): Boolean =
        registeredProtocolVersion(reply) == TransportWireFormat.FRAMED_PROTOCOL_VERSION

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
}
