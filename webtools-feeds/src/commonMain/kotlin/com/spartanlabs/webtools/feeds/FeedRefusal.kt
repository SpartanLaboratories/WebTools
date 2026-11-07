package com.spartanlabs.webtools.feeds

/**
 * Why a document was refused before or while it was read; the `reason` of [FeedFailure.Refused].
 *
 * **Stable Core.** An open constants class: the constants in the companion are the only instances, two reasons are
 * equal when their [name]s are equal, and `toString()` is the name. This set may gain constants in a feature
 * release; a `when` over it keeps an `else`.
 *
 * @param name the reason's name, as its constant is spelled.
 */
public class FeedRefusal private constructor(
    /** The reason's name, as its constant is spelled. */
    public val name: String,
) {
    override fun equals(other: Any?): Boolean = other is FeedRefusal && other.name == name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = name

    /** The reasons. */
    public companion object {
        /** The document carries a DOCTYPE. */
        public val Doctype: FeedRefusal = FeedRefusal("Doctype")

        /** An encoding the prolog scan cannot read: UCS-4, EBCDIC, or a name outside its allowlist. */
        public val UnsupportedEncoding: FeedRefusal = FeedRefusal("UnsupportedEncoding")

        /** The prolog does not end within 64 KiB. */
        public val PrologTooLong: FeedRefusal = FeedRefusal("PrologTooLong")

        /** A decoder could read the prolog differently from the scan. */
        public val AmbiguousProlog: FeedRefusal = FeedRefusal("AmbiguousProlog")
    }
}
