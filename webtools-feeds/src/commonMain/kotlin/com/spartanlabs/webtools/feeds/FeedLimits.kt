package com.spartanlabs.webtools.feeds

import com.spartanlabs.webtools.http.WebToolsDsl

/**
 * The bounds a [FeedParser] applies to a document. **Stable Core.** Built with `FeedLimits { maxEntries = 200 }`, or
 * derived with `FeedLimits(from) { … }`; every member can only be **tightened**: a value above its default or below
 * its floor is refused when the limits are built, so the generous side stays the safe one and a default can be
 * relied on as an upper bound.
 *
 * | Member | Default | Floor | Exceeding it |
 * |---|---|---|---|
 * | [maxEntries] | 1,000 | 1 | the parse fails with `LimitExceeded(Entries)` |
 * | [maxTextChars] | 262,144 | 16 | text is cut at that many UTF-16 units, never inside a surrogate pair |
 * | [maxIdChars] | 2,048 | 50 | an id falls back from the guid to the link to the digest; the digest id is always 50 characters |
 * | [maxLinkChars] | 8,000 | 32 | the link is `null` |
 * | [maxCategoriesPerEntry] | 32 | 0 | further categories are dropped |
 * | [maxEnclosuresPerEntry] | 8 | 0 | further enclosures are dropped |
 * | [maxDepth] | 256 | 4 | the parse fails with `LimitExceeded(Depth)` |
 * | [maxAttributesPerElement] | 100 | 4 | the parse fails with `LimitExceeded(Attributes)`; namespace declarations count as attributes |
 * | [maxElements] | 100,000 | 16 | the parse fails with `LimitExceeded(Elements)` |
 * | [maxNamespaceUris] | 64 | 2 | the parse fails with `LimitExceeded(NamespaceUris)`; it counts distinct URIs, not declarations |
 *
 * A floor keeps a setting from disabling the parser by mistake; it does not promise that any document parses. The
 * document itself is bounded by the transport's decoded-body cap (2 MiB under an `Untrusted` policy). Equality is on
 * all ten members; `toString()` prints them.
 */
public class FeedLimits internal constructor(
    /** The most entries a document may hold; more fail the parse. */
    public val maxEntries: Int,
    /** The most UTF-16 units of any text field; longer text is cut. */
    public val maxTextChars: Int,
    /** The most UTF-16 units of a guid or link for it to serve as the id; a longer one falls back. */
    public val maxIdChars: Int,
    /** The most UTF-16 units of a link; a longer one is `null`. */
    public val maxLinkChars: Int,
    /** The most categories kept per entry. */
    public val maxCategoriesPerEntry: Int,
    /** The most enclosures kept per entry. */
    public val maxEnclosuresPerEntry: Int,
    /** The deepest element nesting; deeper fails the parse. */
    public val maxDepth: Int,
    /** The most attributes on one element, namespace declarations included; more fail the parse. */
    public val maxAttributesPerElement: Int,
    /** The most elements in a document; more fail the parse. */
    public val maxElements: Int,
    /** The most distinct namespace URIs in a document; more fail the parse. */
    public val maxNamespaceUris: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is FeedLimits &&
            other.maxEntries == maxEntries && other.maxTextChars == maxTextChars && other.maxIdChars == maxIdChars &&
            other.maxLinkChars == maxLinkChars && other.maxCategoriesPerEntry == maxCategoriesPerEntry &&
            other.maxEnclosuresPerEntry == maxEnclosuresPerEntry && other.maxDepth == maxDepth &&
            other.maxAttributesPerElement == maxAttributesPerElement && other.maxElements == maxElements &&
            other.maxNamespaceUris == maxNamespaceUris

    override fun hashCode(): Int =
        listOf(
            maxEntries, maxTextChars, maxIdChars, maxLinkChars, maxCategoriesPerEntry, maxEnclosuresPerEntry,
            maxDepth, maxAttributesPerElement, maxElements, maxNamespaceUris,
        ).fold(0) { acc, v -> 31 * acc + v }

    override fun toString(): String =
        "FeedLimits(maxEntries=$maxEntries, maxTextChars=$maxTextChars, maxIdChars=$maxIdChars, maxLinkChars=$maxLinkChars, " +
            "maxCategoriesPerEntry=$maxCategoriesPerEntry, maxEnclosuresPerEntry=$maxEnclosuresPerEntry, maxDepth=$maxDepth, " +
            "maxAttributesPerElement=$maxAttributesPerElement, maxElements=$maxElements, maxNamespaceUris=$maxNamespaceUris)"

    /**
     * Collects the members of a [FeedLimits], seeded from the value the factory was given. Not thread-safe; use it
     * only inside the factory's lambda.
     *
     * **Stable Core.**
     */
    @WebToolsDsl
    public class Builder internal constructor(from: FeedLimits) {
        /** The most entries a document may hold. In 1..1,000. */
        public var maxEntries: Int = from.maxEntries

        /** The most UTF-16 units of any text field. In 16..262,144. */
        public var maxTextChars: Int = from.maxTextChars

        /** The most UTF-16 units of a guid or link for it to serve as the id. In 50..2,048. */
        public var maxIdChars: Int = from.maxIdChars

        /** The most UTF-16 units of a link. In 32..8,000. */
        public var maxLinkChars: Int = from.maxLinkChars

        /** The most categories kept per entry. In 0..32. */
        public var maxCategoriesPerEntry: Int = from.maxCategoriesPerEntry

        /** The most enclosures kept per entry. In 0..8. */
        public var maxEnclosuresPerEntry: Int = from.maxEnclosuresPerEntry

        /** The deepest element nesting. In 4..256. */
        public var maxDepth: Int = from.maxDepth

        /** The most attributes on one element. In 4..100. */
        public var maxAttributesPerElement: Int = from.maxAttributesPerElement

        /** The most elements in a document. In 16..100,000. */
        public var maxElements: Int = from.maxElements

        /** The most distinct namespace URIs in a document. In 2..64. */
        public var maxNamespaceUris: Int = from.maxNamespaceUris

        // Checked in the order of the properties; a message names the property and never a value.
        internal fun build(): FeedLimits {
            requireRange("maxEntries", maxEntries, FLOOR_MAX_ENTRIES, DEFAULT_MAX_ENTRIES)
            requireRange("maxTextChars", maxTextChars, FLOOR_MAX_TEXT_CHARS, DEFAULT_MAX_TEXT_CHARS)
            requireRange("maxIdChars", maxIdChars, FLOOR_MAX_ID_CHARS, DEFAULT_MAX_ID_CHARS)
            requireRange("maxLinkChars", maxLinkChars, FLOOR_MAX_LINK_CHARS, DEFAULT_MAX_LINK_CHARS)
            requireRange("maxCategoriesPerEntry", maxCategoriesPerEntry, FLOOR_MAX_CATEGORIES_PER_ENTRY, DEFAULT_MAX_CATEGORIES_PER_ENTRY)
            requireRange("maxEnclosuresPerEntry", maxEnclosuresPerEntry, FLOOR_MAX_ENCLOSURES_PER_ENTRY, DEFAULT_MAX_ENCLOSURES_PER_ENTRY)
            requireRange("maxDepth", maxDepth, FLOOR_MAX_DEPTH, DEFAULT_MAX_DEPTH)
            requireRange("maxAttributesPerElement", maxAttributesPerElement, FLOOR_MAX_ATTRIBUTES_PER_ELEMENT, DEFAULT_MAX_ATTRIBUTES_PER_ELEMENT)
            requireRange("maxElements", maxElements, FLOOR_MAX_ELEMENTS, DEFAULT_MAX_ELEMENTS)
            requireRange("maxNamespaceUris", maxNamespaceUris, FLOOR_MAX_NAMESPACE_URIS, DEFAULT_MAX_NAMESPACE_URIS)
            return FeedLimits(
                maxEntries, maxTextChars, maxIdChars, maxLinkChars, maxCategoriesPerEntry, maxEnclosuresPerEntry,
                maxDepth, maxAttributesPerElement, maxElements, maxNamespaceUris,
            )
        }
    }

    /** The defaults, as constants and as a value. */
    public companion object {
        /** 1,000. */
        public const val DEFAULT_MAX_ENTRIES: Int = 1_000

        /** 262,144. */
        public const val DEFAULT_MAX_TEXT_CHARS: Int = 262_144

        /** 2,048. */
        public const val DEFAULT_MAX_ID_CHARS: Int = 2_048

        /** 8,000. */
        public const val DEFAULT_MAX_LINK_CHARS: Int = 8_000

        /** 32. */
        public const val DEFAULT_MAX_CATEGORIES_PER_ENTRY: Int = 32

        /** 8. */
        public const val DEFAULT_MAX_ENCLOSURES_PER_ENTRY: Int = 8

        /** 256. */
        public const val DEFAULT_MAX_DEPTH: Int = 256

        /** 100. */
        public const val DEFAULT_MAX_ATTRIBUTES_PER_ELEMENT: Int = 100

        /** 100,000. */
        public const val DEFAULT_MAX_ELEMENTS: Int = 100_000

        /** 64. */
        public const val DEFAULT_MAX_NAMESPACE_URIS: Int = 64

        /** All ten defaults as a value. */
        public val Defaults: FeedLimits = FeedLimits(
            DEFAULT_MAX_ENTRIES, DEFAULT_MAX_TEXT_CHARS, DEFAULT_MAX_ID_CHARS, DEFAULT_MAX_LINK_CHARS,
            DEFAULT_MAX_CATEGORIES_PER_ENTRY, DEFAULT_MAX_ENCLOSURES_PER_ENTRY,
            DEFAULT_MAX_DEPTH, DEFAULT_MAX_ATTRIBUTES_PER_ELEMENT, DEFAULT_MAX_ELEMENTS, DEFAULT_MAX_NAMESPACE_URIS,
        )
    }
}

/**
 * Builds a [FeedLimits] from [FeedLimits.Defaults]. `FeedLimits { maxEntries = 200 }`; every member may only be
 * tightened.
 *
 * **Stable Core.**
 *
 * @param configure tightens members; the defaults when omitted.
 * @return the limits.
 * @throws IllegalArgumentException if a member is above its default or below its floor; the message names the
 *   property and never a value.
 */
public fun FeedLimits(configure: FeedLimits.Builder.() -> Unit = {}): FeedLimits = FeedLimits(FeedLimits.Defaults, configure)

/**
 * Builds a [FeedLimits] starting from [from], which is never modified. A member may be raised back up to its
 * default, never above it.
 *
 * **Stable Core.**
 *
 * @param from the limits to start from.
 * @param configure changes members.
 * @return the limits.
 * @throws IllegalArgumentException if a member is above its default or below its floor; the message names the
 *   property and never a value.
 */
public fun FeedLimits(from: FeedLimits, configure: FeedLimits.Builder.() -> Unit): FeedLimits =
    FeedLimits.Builder(from).apply(configure).build()

// The floors: a setting below one would disable the parser by mistake.
internal const val FLOOR_MAX_ENTRIES: Int = 1
internal const val FLOOR_MAX_TEXT_CHARS: Int = 16
internal const val FLOOR_MAX_ID_CHARS: Int = DIGEST_ID_CHARS
internal const val FLOOR_MAX_LINK_CHARS: Int = 32
internal const val FLOOR_MAX_CATEGORIES_PER_ENTRY: Int = 0
internal const val FLOOR_MAX_ENCLOSURES_PER_ENTRY: Int = 0
internal const val FLOOR_MAX_DEPTH: Int = 4
internal const val FLOOR_MAX_ATTRIBUTES_PER_ELEMENT: Int = 4
internal const val FLOOR_MAX_ELEMENTS: Int = 16
internal const val FLOOR_MAX_NAMESPACE_URIS: Int = 2

private fun requireRange(name: String, value: Int, floor: Int, ceiling: Int) =
    require(value in floor..ceiling) { msgLimitRange(name, floor, ceiling) }
