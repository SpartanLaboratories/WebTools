package com.spartanlabs.webtools.http

/**
 * A request body: its bytes and an optional content type.
 *
 * **Stable Core.** The constructor's shape is frozen. The bytes are copied in and read back as copies.
 * Equality is identity. `toString()` prints the size and whether a content type is set, never the bytes
 * or the type.
 *
 * @param bytes the body; copied.
 * @param contentType an RFC 9110 media type (its visible-ASCII subset), or `null` for none.
 * @throws IllegalArgumentException if [contentType] is not a media type; the message holds the failing
 *   index, not the text.
 */
public class RequestBody(
    bytes: ByteArray,
    /** The media type, or `null` for none. */
    public val contentType: String?,
) {
    private val data: ByteArray = bytes.copyOf()

    init {
        if (contentType != null) {
            val bad = HttpSyntax.mediaTypeProblemIndex(contentType)
            require(bad < 0) { "Content type is not an RFC 9110 media type (first offending character at index $bad)" }
        }
    }

    /** The body's size in bytes. */
    public val size: Int get() = data.size

    /**
     * Returns the body.
     *
     * @return a fresh copy of the bytes on every call.
     */
    public fun bytes(): ByteArray = data.copyOf()

    override fun toString(): String = "RequestBody(size=${data.size}, hasContentType=${contentType != null})"
}
