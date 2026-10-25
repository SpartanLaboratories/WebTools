package com.spartanlabs.webtools.http

/**
 * Why the outbound-URL policy refused a request or a redirect hop; the `reason` of
 * [TransportFailure.RefusedByPolicy].
 *
 * **Stable Core.** An open constants class: the constants in the companion are the only instances, two
 * reasons are equal when their [name]s are equal, and `toString()` is the name. This set may gain
 * constants in a feature release; a `when` over it keeps an `else`.
 *
 * @param name the reason's name, as its constant is spelled.
 */
public class RefusalReason private constructor(
    /** The reason's name, as its constant is spelled. */
    public val name: String,
) {
    override fun equals(other: Any?): Boolean = other is RefusalReason && other.name == name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = name

    /** The reasons. */
    public companion object {
        /** The URL's scheme is not allowed. */
        public val Scheme: RefusalReason = RefusalReason("Scheme")

        /** The URL carries user-info. */
        public val UserInfo: RefusalReason = RefusalReason("UserInfo")

        /** The host's form (an IP literal, a single label, a trailing dot, ...) is not allowed. */
        public val HostForm: RefusalReason = RefusalReason("HostForm")

        /** The host is a reserved or special-use name. */
        public val ReservedName: RefusalReason = RefusalReason("ReservedName")

        /** The host, or the address it resolved to, is in a denied range. */
        public val DeniedAddress: RefusalReason = RefusalReason("DeniedAddress")

        /** The port is not allowed. */
        public val Port: RefusalReason = RefusalReason("Port")

        /** The host is not among the pinned hosts. */
        public val HostNotPinned: RefusalReason = RefusalReason("HostNotPinned")

        /** A redirect would move from `https` to `http`. */
        public val InsecureRedirect: RefusalReason = RefusalReason("InsecureRedirect")

        /** A sensitive header would leave the origin it was meant for. */
        public val SensitiveHeader: RefusalReason = RefusalReason("SensitiveHeader")
    }
}
