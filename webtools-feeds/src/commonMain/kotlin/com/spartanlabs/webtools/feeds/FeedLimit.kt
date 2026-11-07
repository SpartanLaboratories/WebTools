package com.spartanlabs.webtools.feeds

/**
 * The [FeedLimits] bound a document exceeded; the `limit` of [FeedFailure.LimitExceeded]. Each constant is the
 * `FeedLimits` member of that name.
 *
 * **Stable Core.** An open constants class: the constants in the companion are the only instances, two limits are
 * equal when their [name]s are equal, and `toString()` is the name. This set may gain constants in a feature
 * release; a `when` over it keeps an `else`.
 *
 * @param name the limit's name, as its constant is spelled.
 */
public class FeedLimit private constructor(
    /** The limit's name, as its constant is spelled. */
    public val name: String,
) {
    override fun equals(other: Any?): Boolean = other is FeedLimit && other.name == name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = name

    /** The limits that fail a parse. */
    public companion object {
        /** More entries than `FeedLimits.maxEntries`. */
        public val Entries: FeedLimit = FeedLimit("Entries")

        /** Elements nested deeper than `FeedLimits.maxDepth`. */
        public val Depth: FeedLimit = FeedLimit("Depth")

        /** More attributes on one element than `FeedLimits.maxAttributesPerElement`. */
        public val Attributes: FeedLimit = FeedLimit("Attributes")

        /** More elements than `FeedLimits.maxElements`. */
        public val Elements: FeedLimit = FeedLimit("Elements")

        /** More distinct namespace URIs than `FeedLimits.maxNamespaceUris`. */
        public val NamespaceUris: FeedLimit = FeedLimit("NamespaceUris")
    }
}
