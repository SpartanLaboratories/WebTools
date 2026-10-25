package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.Url

/**
 * A recorded or hand-written response: a status, headers and body bytes, read from and written to the
 * `.http` format.
 *
 * The format: optional leading `# ...` provenance lines (ignored on read), a status line
 * `HTTP/<d>[.<d>] <three digits>[ <reason>]` (the reason is ignored; `HTTP/1.1 200 OK`, `HTTP/2 404` and
 * `HTTP/1.1 304` are all valid), `Name: value` header lines (the name an RFC 9110 token, the value trimmed of
 * SP and HTAB, strict UTF-8, no NUL, no obs-fold), a blank line, and every remaining byte the body, verbatim; no
 * blank line means an empty body. LF or CRLF ends a head line; a lone CR is refused. [toBytes] writes LF,
 * `HTTP/1.1`, the RFC 9110 reason for the common statuses and `Name: value` per entry in order, so writing
 * the parse of a canonical file reproduces it, and the body round-trips byte for byte. A consumer keeps its
 * fixture files under its own test resources and marks them `-text` in its `.gitattributes` (a pattern over
 * every `.http` file below a `fixtures` directory) so a checkout cannot rewrite their line endings.
 *
 * **Stable Core, source compatibility only.** The body is copied in and read back as copies. Equality and
 * hash are on the status, the headers and the body bytes. `toString()` prints the status, the header names
 * (through `HttpHeaders.toString()`) and the body size, never a header value or the body. Immutable, so
 * safe to share between threads.
 *
 * @param status the status, in 100..599.
 * @param headers the response headers.
 * @param body the body; copied.
 * @throws IllegalArgumentException if [status] is outside 100..599.
 */
public class Fixture(
    /** The status, in 100..599. */
    public val status: Int,
    /** The response headers. */
    public val headers: HttpHeaders,
    body: ByteArray,
) {
    private val data: ByteArray = body.copyOf()

    init {
        require(status in 100..599) { "Status code must be in 100..599" }
    }

    /** The body's size in bytes. */
    public val bodySize: Int get() = data.size

    /**
     * Returns the body.
     *
     * @return a fresh copy of the bytes on every call.
     */
    public fun body(): ByteArray = data.copyOf()

    /**
     * Returns the body as text.
     *
     * @return the body decoded as UTF-8, with every malformed sequence replaced by U+FFFD.
     */
    public fun bodyText(): String = String(data, Charsets.UTF_8)

    /**
     * Builds the response a transport would return for this fixture.
     *
     * @param url the URL the response is reported to come from.
     * @return a response with this status, these headers, a copy of the body and an empty redirect chain.
     */
    public fun toResponse(url: Url): HttpResponse = HttpResponse(status, url) { this.headers = this@Fixture.headers; body(data) }

    /**
     * Serialises the fixture in the `.http` format.
     *
     * @param comments provenance lines written as `# <comment>`, each without CR or LF; none by default.
     * @return the file's bytes: the comments, the status line with the common reason phrase, the headers in
     *   order, a blank line and the body verbatim.
     * @throws IllegalArgumentException if a comment contains CR or LF.
     */
    public fun toBytes(comments: List<String> = emptyList()): ByteArray {
        require(comments.none { '\r' in it || '\n' in it }) { "a comment must not contain CR or LF" }
        return HttpMessageCodec.write(status, headers, data, comments)
    }

    override fun equals(other: Any?): Boolean =
        other is Fixture && other.status == status && other.headers == headers && other.data.contentEquals(data)

    override fun hashCode(): Int = (status * 31 + headers.hashCode()) * 31 + data.contentHashCode()

    // Header names only (HttpHeaders prints no value); never the body.
    override fun toString(): String = "Fixture(status=$status, headers=$headers, bodySize=${data.size})"

    /** The parser. */
    public companion object {
        /**
         * Parses the bytes of a `.http` file.
         *
         * @param bytes the file.
         * @param origin the label the error message names, for example the resource name; `<fixture>` by default.
         * @return the fixture.
         * @throws FixtureError if [bytes] is not a well-formed `.http` file; the message names [origin] and, for a
         *   syntax fault, the line; never a header value or a body byte.
         */
        public fun parse(bytes: ByteArray, origin: String = "<fixture>"): Fixture = HttpMessageCodec.parse(bytes, origin).getOrThrow()
    }
}
