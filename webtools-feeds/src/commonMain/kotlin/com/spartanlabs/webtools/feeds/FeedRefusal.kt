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
        /**
         * The document carries a DOCTYPE, or another markup declaration (`<!ENTITY`, `<!ELEMENT`, …), before the root element.
         * A web page's `<!DOCTYPE html>` is `NotAFeed(Html)` instead.
         */
        public val Doctype: FeedRefusal = FeedRefusal("Doctype")

        /**
         * An encoding the parser will not read safely: UCS-4, EBCDIC, or a charset named in the `Content-Type` header or the XML
         * declaration that is outside the set the parser reads. `RomeFeedParser` reads UTF-8, UTF-16, US-ASCII, ISO-8859-n,
         * windows-125n, KOI8-R, KOI8-U, Shift_JIS, EUC-KR, GBK, GB2312, Big5, EUC-JP and GB18030, plus only these aliases:
         * `utf8`, `ascii`, `latin1`, `sjis`, `windows-31j`, `iso8859-n`, `iso_8859-n` and `cp125n`.
         */
        public val UnsupportedEncoding: FeedRefusal = FeedRefusal("UnsupportedEncoding")

        /** What comes before the root element (byte-order marks, white space, comments, processing instructions) does not end within the first 64 KiB. */
        public val PrologTooLong: FeedRefusal = FeedRefusal("PrologTooLong")

        /**
         * The start of the document decodes two ways, so a DOCTYPE might be hidden: an unpaired surrogate in UTF-16, or a non-ASCII
         * byte before the root element under EUC-JP or GB18030, whose decoders can drop the character that follows.
         */
        public val AmbiguousProlog: FeedRefusal = FeedRefusal("AmbiguousProlog")
    }
}
