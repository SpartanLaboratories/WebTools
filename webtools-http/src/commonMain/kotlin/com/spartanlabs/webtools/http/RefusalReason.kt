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
    /** Two [RefusalReason]s are equal when their [name]s are equal. */
    override fun equals(other: Any?): Boolean = other is RefusalReason && other.name == name
    /** Returns a hash code consistent with [equals]: it is computed from the same properties. */
    override fun hashCode(): Int = name.hashCode()
    /** Returns the reason's [name]. */
    override fun toString(): String = name

    /** The reasons. */
    public companion object {
        /** The URL's scheme is not allowed. */
        public val Scheme: RefusalReason = RefusalReason("Scheme")

        /** The URL carries user-info. */
        public val UserInfo: RefusalReason = RefusalReason("UserInfo")

        /**
         * The host's form (a lenient or malformed IP literal such as `127.1`, an empty label, a last label that is a number, ...) is not
         * allowed. Also the reason for a redirect target that cannot be resolved or parsed, other than for its scheme, user-info or port:
         * the only `HostForm` a `Trusted` policy returns.
         */
        public val HostForm: RefusalReason = RefusalReason("HostForm")

        /**
         * The host is a single label, a reserved or special-use name (`localhost`, `local`, `internal`, `home.arpa`, `localdomain`) or a
         * subdomain of one, or one of the policy's `extraDeniedHostSuffixes` or a subdomain of one.
         */
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
