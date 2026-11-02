package com.spartanlabs.webtools.http

/**
 * The request headers the transport controls: a caller-supplied value would change the exchange's framing, its decoding, the host or protocol the
 * request reaches, or the transport's own identity. A request carrying one is refused before anything is sent. The names are compared with
 * [HttpSyntax.equalsName] (an ASCII-only fold) and reported in this list's spelling. `Range` is not on the list: the transport sends
 * `Accept-Encoding: identity` itself on a hop whose request carries it. Lives in `commonMain` so the check module compiles it for `js` and the call
 * facades can refuse the same names in their default headers.
 */
internal object TransportControlledHeaders {
    /** The twelve names, in the spelling the rule text uses. */
    val names: List<String> = listOf(
        "User-Agent", "Accept-Encoding", "Host", "Connection", "Content-Length", "Transfer-Encoding",
        "Keep-Alive", "Proxy-Connection", "TE", "Trailer", "Upgrade", "Expect",
    )

    /**
     * The rule text for the first entry of [headers], in request order, whose name is controlled; `null` when none is.
     *
     * @param headers the request's headers.
     * @return `header <Name> is controlled by the transport` with the list's spelling of the name, or `null`.
     */
    fun ruleFor(headers: HttpHeaders): String? =
        headers.entries().firstNotNullOfOrNull { entry -> names.firstOrNull { HttpSyntax.equalsName(it, entry.name) } }
            ?.let { "header $it is controlled by the transport" }
}
