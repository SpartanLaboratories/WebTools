package com.spartanlabs.webtools.http

private const val CONTENT_TYPE = "Content-Type"

/**
 * An immutable request: method, URL, headers and an optional body.
 *
 * **Stable Core.** Built with the factory `HttpRequest(method, url) { headers = ...; body = ... }`. The
 * factory refuses, with `IllegalArgumentException` and in this order: the method `CONNECT`; a header value
 * that is not sendable (HTAB or U+0020..U+007E); a body on `GET`, `HEAD` or `TRACE`; a body with a
 * content type beside a `Content-Type` header. Equality is identity. `toString()` prints the method, the
 * redacted URL, the header names and the body's size, never a header value or the body.
 */
public class HttpRequest internal constructor(
    /** The method. */
    public val method: HttpMethod,
    /** The URL. */
    public val url: Url,
    /** The headers; empty when none were set. */
    public val headers: HttpHeaders,
    /** The body, or `null` for none. */
    public val body: RequestBody?,
) {
    init {
        require(method.name != "CONNECT") { "A CONNECT request is not supported" }
        headers.entries().forEach { entry ->
            require(HttpSyntax.isSendableValue(entry.value)) {
                "Value of header '${entry.name}' must be visible ASCII, space or tab to be sent"
            }
        }
        require(body == null || method.permitsBody) { "A ${method.name} request must not have a body" }
        require(body?.contentType == null || CONTENT_TYPE !in headers) {
            "A request with a body content type must not also set a Content-Type header"
        }
    }

    override fun toString(): String =
        "HttpRequest(method=$method, url=${url.redacted()}, headers=$headers, body=${body ?: "none"})"

    /**
     * Collects the optional parts of an [HttpRequest]. Not thread-safe; use it only inside the factory's
     * lambda.
     *
     * **Stable Core.**
     */
    @WebToolsDsl
    public class Builder internal constructor() {
        /** The request headers; empty by default. */
        public var headers: HttpHeaders = HttpHeaders()

        /** The request body; `null` (none) by default. */
        public var body: RequestBody? = null
    }
}

/**
 * Builds an [HttpRequest] for [method] and [url] with the optional parts set in [configure].
 *
 * **Stable Core.**
 *
 * @param method the method; not `CONNECT`.
 * @param url the URL.
 * @param configure sets the headers and the body; empty by default, for no headers and no body.
 * @return the request.
 * @throws IllegalArgumentException if the method is `CONNECT`, a header value is not sendable, a body is
 *   set on `GET`, `HEAD` or `TRACE`, or a body content type is combined with a `Content-Type` header.
 */
public fun HttpRequest(method: HttpMethod, url: Url, configure: HttpRequest.Builder.() -> Unit = {}): HttpRequest {
    val builder = HttpRequest.Builder().apply(configure)
    return HttpRequest(method, url, builder.headers, builder.body)
}
