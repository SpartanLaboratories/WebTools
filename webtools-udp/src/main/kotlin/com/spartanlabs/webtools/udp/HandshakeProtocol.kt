package com.spartanlabs.webtools.udp

/**
 * The pure, socket-free rules of the [MultiConnectionUDPServer] handshake: how an
 * `Iam` line is classified and parsed, and the two fixed-text replies
 * (`REGISTERED <version>` and `REFUSED malformed`), whose bytes never come from the
 * application (unlike `REFUSED <reason>`).
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

    /**
     * The reason on the refusal of a malformed `Iam`. Informative wire text, not a
     * contract. It is one fixed word for every [HandshakeDefect], so the reply does
     * not reveal which slot was defective - that goes to the server log only.
     */
    private const val MALFORMED_REASON = "malformed"

    /**
     * The exact refusal datagram for a malformed `Iam`: the 17 bytes of
     * `REFUSED malformed` in UTF-8. Shared and read-only, like [REGISTERED_DATAGRAM]:
     * callers must not mutate it.
     */
    val MALFORMED_REFUSAL_DATAGRAM: ByteArray =
        // Built once, not per reply: the malformed path runs at a rate the sender chooses, and
        // HandshakeWireFormat.refusedMessage compiles a Regex on every call.
        HandshakeWireFormat.refusedMessage(MALFORMED_REASON).toByteArray(Charsets.UTF_8)

    /** Index of the client-supplied name within a whitespace-split handshake line. */
    private const val NAME_INDEX = 1

    /** Index of the optional client-supplied opaque credential token. */
    private const val CREDENTIAL_INDEX = 2

    /**
     * Classifies the space-split text of an unframed datagram as not a handshake, a
     * malformed handshake, or a valid one, and parses a valid one into its name and
     * optional opaque credential.
     *
     * Pure and **total**: it never throws and returns no [Result], because a malformed
     * line is an expected domain outcome the caller routes on (see [HandshakeParse]),
     * not an operational failure.
     *
     * The first token must be exactly [VERB] ([isHandshake]'s definition), otherwise
     * the outcome is [HandshakeParse.NotAHandshake]. The slots are then checked in
     * order, so a line with two defects reports the first:
     * 1. `tokens[1]` missing -> [HandshakeDefect.NAME_MISSING];
     * 2. `tokens[1]` empty -> [HandshakeDefect.NAME_EMPTY];
     * 3. `tokens[2]` present but empty -> [HandshakeDefect.CREDENTIAL_EMPTY].
     *
     * An absent `tokens[2]` is not malformed: it becomes `""` in the [Handshake]. Any
     * token past the credential slot is never inspected, empty ones included (see
     * [extraTokenCount]).
     *
     * @param tokens the datagram text split on a single space; may be empty
     * @return [HandshakeParse.NotAHandshake], [HandshakeParse.Malformed] carrying the
     * first defect, or [HandshakeParse.Valid] carrying the parsed [Handshake]
     */
    fun parseHandshake(tokens: List<String>): HandshakeParse {
        val name = tokens.getOrNull(NAME_INDEX)
        val credential = tokens.getOrNull(CREDENTIAL_INDEX)
        return when {
            !isHandshake(tokens) -> HandshakeParse.NotAHandshake
            name == null -> HandshakeParse.Malformed(HandshakeDefect.NAME_MISSING)
            name.isEmpty() -> HandshakeParse.Malformed(HandshakeDefect.NAME_EMPTY)
            credential?.isEmpty() == true -> HandshakeParse.Malformed(HandshakeDefect.CREDENTIAL_EMPTY)
            else -> HandshakeParse.Valid(Handshake(name, credential.orEmpty()))
        }
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
     * [parseHandshake]'s job, which reports the same recognition as
     * [HandshakeParse.NotAHandshake]).
     * @param tokens the datagram text split on spaces
     * @return true if [tokens] begins with the handshake verb
     */
    fun isHandshake(tokens: List<String>): Boolean = tokens.firstOrNull() == VERB
}

/**
 * The outcome of [HandshakeProtocol.parseHandshake]. Sealed because the coordinator
 * routes on it with an exhaustive `when`: a future arm cannot go unrouted.
 */
internal sealed interface HandshakeParse {
    /** The first token is not exactly [HandshakeProtocol.VERB]: not a handshake line. */
    data object NotAHandshake : HandshakeParse

    /**
     * An `Iam` line with a defective slot.
     * @property defect which slot is defective; [HandshakeDefect.label] is safe to log
     */
    data class Malformed(val defect: HandshakeDefect) : HandshakeParse

    /**
     * A well-formed `Iam` line.
     * @property handshake the parsed name and credential
     */
    data class Valid(val handshake: Handshake) : HandshakeParse
}

/**
 * Which slot of an `Iam` line is defective. The [label]s are the server-log
 * vocabulary for a malformed line; they are fixed text and never contain input.
 * @property label fixed, log-safe description of the defect
 */
internal enum class HandshakeDefect(val label: String) {
    /** The name slot is missing: a bare `Iam`. */
    NAME_MISSING("name missing"),

    /** The name slot is present but empty: `Iam  x`. */
    NAME_EMPTY("name empty"),

    /** The credential slot is present but empty: `Iam bob  tok`. */
    CREDENTIAL_EMPTY("credential empty"),
}

/**
 * The parsed content of a valid client `Iam` handshake line. Only
 * [HandshakeProtocol.parseHandshake] builds one, from a line whose name is non-empty
 * and whose credential slot is absent or non-empty.
 * @property name the client's chosen name (`tokens[1]`); never empty
 * @property credential the opaque credential token (`tokens[2]`), or the empty string
 * when the client sent a bare `Iam <name>`; never interpreted by the library. Empty
 * therefore means absent: a present-but-empty slot is [HandshakeDefect.CREDENTIAL_EMPTY]
 */
internal data class Handshake(val name: String, val credential: String)
