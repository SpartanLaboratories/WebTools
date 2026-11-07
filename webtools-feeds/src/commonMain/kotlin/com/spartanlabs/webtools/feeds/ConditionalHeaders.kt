package com.spartanlabs.webtools.feeds

import com.spartanlabs.webtools.http.HttpHeaders

internal const val HEADER_ACCEPT: String = "Accept"
internal const val HEADER_IF_NONE_MATCH: String = "If-None-Match"
internal const val HEADER_IF_MODIFIED_SINCE: String = "If-Modified-Since"
internal const val HEADER_ETAG: String = "ETag"
internal const val HEADER_LAST_MODIFIED: String = "Last-Modified"
internal const val HEADER_CONTENT_TYPE: String = "Content-Type"

/** `Accept`, then `If-None-Match` and `If-Modified-Since` when known, each exactly as held: a date is never reprinted, a tag never normalised. */
internal fun requestHeaders(accept: String, validators: FeedValidators): HttpHeaders = HttpHeaders {
    add(HEADER_ACCEPT, accept)
    validators.etag?.let { add(HEADER_IF_NONE_MATCH, it) }
    validators.lastModified?.let { add(HEADER_IF_MODIFIED_SINCE, it) }
}

/** Whether a request carrying these validators is conditional: at least one is known. */
internal val FeedValidators.isConditional: Boolean get() = etag != null || lastModified != null

/** From a `200` or `203`: a header counts only when exactly one line of that name is present; the constructor applies the rules. */
internal fun validatorsOf(headers: HttpHeaders): FeedValidators =
    FeedValidators(headers.getAll(HEADER_ETAG).singleOrNull(), headers.getAll(HEADER_LAST_MODIFIED).singleOrNull())

/** From a `304`: each header the `304` carries exactly once replaces its counterpart (an unusable value becomes `null`); an absent or repeated header keeps the sent value. */
internal fun FeedValidators.refreshedBy(headers: HttpHeaders): FeedValidators =
    FeedValidators(carried(headers, HEADER_ETAG, etag), carried(headers, HEADER_LAST_MODIFIED, lastModified))

private fun carried(headers: HttpHeaders, name: String, kept: String?): String? = headers.getAll(name).let { if (it.size == 1) it[0] else kept }
