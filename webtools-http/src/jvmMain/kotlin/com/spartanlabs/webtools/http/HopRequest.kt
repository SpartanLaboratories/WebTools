package com.spartanlabs.webtools.http

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val BODY_REQUIRED_METHODS: Set<String> = setOf("POST", "PUT", "PATCH", "PROPPATCH", "REPORT", "QUERY")

/**
 * Whether `Request.Builder.method(name, null)` would throw for [method]. A Level 4a differential test checks the set against OkHttp itself.
 *
 * @param method the method name.
 * @return `true` when OkHttp requires a body for it.
 */
internal fun requiresRequestBody(method: String): Boolean = method in BODY_REQUIRED_METHODS

/**
 * Builds the OkHttp request of one hop. The URL is the hop's `Url` text (never rebuilt from parts); the caller's headers go in order with
 * `addHeader`, then the factory's `User-Agent`, then `Accept-Encoding: identity` when the headers carry `Range`; a body's content type is a
 * plain `Content-Type` header and the OkHttp body's `MediaType` stays `null`; a body-required method without a body gets an empty one; a fresh
 * [HopExchanges] tag is attached.
 *
 * @param method the request method, as is.
 * @param url the hop's URL.
 * @param headers the caller's headers for this hop.
 * @param body the body, or `null` for none.
 * @param userAgent the transport's `User-Agent`.
 * @return the request, or a failure holding OkHttp's `IllegalArgumentException` (never expected: the inputs were validated).
 */
internal fun hopRequest(method: HttpMethod, url: Url, headers: HttpHeaders, body: RequestBody?, userAgent: String): Result<Request> =
    catchNonFatal {
        Request.Builder().apply {
            url(url.toString().toHttpUrl())
            headers.entries().forEach { addHeader(it.name, it.value) }
            addHeader("User-Agent", userAgent)
            // With Range, identity keeps OkHttp from adding gzip and decoding: a compliant server then answers without a content coding.
            if ("Range" in headers) addHeader("Accept-Encoding", "identity")
            // A plain header, not the body's MediaType: toMediaType() rejects RFC-valid forms and silently replaces a user header.
            body?.contentType?.let { addHeader("Content-Type", it) }
            tag(HopExchanges::class.java, HopExchanges())
            method(
                method.name,
                when {
                    body != null -> body.bytes().toRequestBody(null)
                    requiresRequestBody(method.name) -> ByteArray(0).toRequestBody(null)
                    else -> null
                },
            )
        }.build()
    }
