package com.spartanlabs.webtools.feeds

import com.spartanlabs.webtools.http.Url

/**
 * One document for a [FeedParser]. **Stable Core.** The bytes are copied in and read back as copies. `toString()`
 * prints the size and whether a content type and a base URL are present, never the content.
 *
 * @param document the response body as received.
 * @param contentType the response's `Content-Type` header value, or `null`.
 * @param baseUrl the URL the document came from, the base for relative links, or `null`.
 */
public class FeedInput(
    document: ByteArray,
    /** The response's `Content-Type` header value, or `null`. */
    public val contentType: String?,
    /** The URL the document came from, the base for relative links, or `null`. */
    public val baseUrl: Url?,
) {
    private val bytes: ByteArray = document.copyOf()

    /** The document's size in bytes. */
    public val documentSize: Int get() = bytes.size

    /**
     * Returns the document.
     *
     * @return a fresh copy of the document on every call.
     */
    public fun document(): ByteArray = bytes.copyOf()

    // For this module's own parsers: the stored array itself, never to be modified.
    internal fun documentView(): ByteArray = bytes

    override fun toString(): String =
        "FeedInput(documentSize=${bytes.size}, contentType=${if (contentType == null) "none" else "present"}, baseUrl=${baseUrl?.origin() ?: "none"})"
}
