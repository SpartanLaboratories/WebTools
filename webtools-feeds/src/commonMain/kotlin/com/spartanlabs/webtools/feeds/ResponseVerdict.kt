package com.spartanlabs.webtools.feeds

/** What the fetcher does with a response status. */
internal enum class StatusVerdict { Parse, NotModified, Upstream }

/**
 * `200` and `203` are parsed; a `304` to a request that carried a validator is not modified; every other status (a `304` to
 * an unconditional request included) is an upstream error. A `204` or `205` has no content, a `206` answers only a range
 * request and a `226` only a request with `A-IM`: none is the complete representation of a plain `GET`.
 */
internal fun statusVerdictOf(status: Int, conditional: Boolean): StatusVerdict = when {
    status == 200 || status == 203 -> StatusVerdict.Parse
    status == 304 && conditional -> StatusVerdict.NotModified
    else -> StatusVerdict.Upstream
}
