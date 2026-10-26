package com.spartanlabs.webtools.http

/**
 * An immutable response: status, the URL it came from, headers, the redirect chain that led to it and
 * the body.
 *
 * **Stable Core.** Built with the factory `HttpResponse(status, url) { headers = ...; redirectChain = ...;
 * body(bytes) }`. The body and the chain are copied in and read back as copies, so a response shared by
 * several callers cannot be changed by one of them. Equality is identity. `toString()` prints the status,
 * the redacted URL, the header names, the body size and the number of redirects, never a header value or
 * the body. A transport maps a status outside 100..599 to [TransportFailure.Protocol] rather than build
 * a response.
 */
public class HttpResponse internal constructor(
    /** The status, in 100..599. */
    public val status: Int,
    /** The URL the response came from: the last hop's URL when redirects were followed. */
    public val url: Url,
    /** The headers; empty when none were set. */
    public val headers: HttpHeaders,
    redirectChain: List<RedirectHop>,
    body: ByteArray,
) {
    /** The followed redirects, in order; empty unless redirects were followed. */
    public val redirectChain: List<RedirectHop> = redirectChain.toList()
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

    override fun toString(): String =
        "HttpResponse(status=$status, url=${url.redacted()}, headers=$headers, bodySize=${data.size}, redirects=${redirectChain.size})"

    /**
     * Collects the optional parts of an [HttpResponse]. Not thread-safe; use it only inside the factory's
     * lambda.
     *
     * **Stable Core.**
     */
    @WebToolsDsl
    public class Builder internal constructor() {
        /** The response headers; empty by default. */
        public var headers: HttpHeaders = HttpHeaders()

        /** The followed redirects; empty by default. */
        public var redirectChain: List<RedirectHop> = emptyList()

        internal var bodyBytes: ByteArray = ByteArray(0)

        /**
         * Sets the body; [bytes] is copied when the response is built.
         *
         * @param bytes the body.
         */
        public fun body(bytes: ByteArray) {
            bodyBytes = bytes
        }
    }
}

/**
 * Builds an [HttpResponse] with [status] from [url] and the optional parts set in [configure].
 *
 * **Stable Core.**
 *
 * @param status the status, in 100..599.
 * @param url the URL the response came from.
 * @param configure sets the headers, the redirect chain and the body; empty by default.
 * @return the response.
 * @throws IllegalArgumentException if [status] is outside 100..599.
 */
public fun HttpResponse(status: Int, url: Url, configure: HttpResponse.Builder.() -> Unit = {}): HttpResponse {
    val builder = HttpResponse.Builder().apply(configure)
    return HttpResponse(status, url, builder.headers, builder.redirectChain, builder.bodyBytes)
}
