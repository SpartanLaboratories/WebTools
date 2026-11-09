package com.spartanlabs.webtools.feeds

/**
 * Why a document is not a feed; the `reason` of [FeedFailure.NotAFeed].
 *
 * **Stable Core.** An open constants class: the constants in the companion are the only instances, two reasons are
 * equal when their [name]s are equal, and `toString()` is the name. This set may gain constants in a feature
 * release; a `when` over it keeps an `else`.
 *
 * @param name the reason's name, as its constant is spelled.
 */
public class NotAFeedReason private constructor(
    /** The reason's name, as its constant is spelled. */
    public val name: String,
) {
    /** Two [NotAFeedReason]s are equal when their [name]s are equal. */
    override fun equals(other: Any?): Boolean = other is NotAFeedReason && other.name == name
    /** Returns a hash code consistent with [equals]: it is computed from the same properties. */
    override fun hashCode(): Int = name.hashCode()
    /** Returns the constant's [name]. */
    override fun toString(): String = name

    /** The reasons. */
    public companion object {
        /** A web page: `<!DOCTYPE html>` or an unprefixed `html` root. */
        public val Html: NotAFeedReason = NotAFeedReason("Html")

        /** No content: nothing but byte-order marks, white space, comments and processing instructions. */
        public val Empty: NotAFeedReason = NotAFeedReason("Empty")

        /** The first significant character is not `<`. */
        public val NotXml: NotAFeedReason = NotAFeedReason("NotXml")

        /** Well-formed XML whose root is not an RSS, RDF or Atom feed. */
        public val UnknownFormat: NotAFeedReason = NotAFeedReason("UnknownFormat")
    }
}
