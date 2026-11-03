package com.spartanlabs.webtools.http

import okhttp3.Response

/**
 * What `settleResponse` needs about the hop.
 *
 * @property method the request method.
 * @property url the hop's URL.
 * @property chain the redirects followed before this hop.
 * @property settings the policy's numbers.
 * @property guard the guard that admits a redirect target.
 */
internal class HopContext(val method: HttpMethod, val url: Url, val chain: List<RedirectHop>, val settings: TransportSettings, val guard: UrlGuard)

private val NO_BODY = ByteArray(0)

/**
 * Turns a received response into the hop's outcome. Runs on the dispatcher thread inside `onResponse`: the status (and `Protocol` outside
 * 100..599, before any body is read), the redirect decision (before any body is read), the headers, the capped body, the response.
 * [onBodyFailure] maps an exception of the body read (phase `Body`). Never throws for an `Exception`.
 *
 * @param context the hop.
 * @param response the response OkHttp delivered.
 * @param onBodyFailure maps an exception of the body read.
 * @return the hop's outcome.
 */
internal fun settleResponse(context: HopContext, response: Response, onBodyFailure: (Throwable) -> MappedFailure): HopOutcome {
    val status = restoredStatus(response.code, response.header(HIDDEN_STATUS_HEADER))
    if (status !in 100..599) return HopOutcome.Failed(plainFailure(TransportFailure.Protocol()))      // decided before any body is read
    // Every Location value, never the last one only; the resolver is the hop's own HttpUrl.
    val decision = decideRedirect(
        context.method, status, response.headers("Location"), context.chain.size, context.url,
        context.settings.maxRedirects, context.guard,
    ) { location -> response.request.url.resolve(location)?.toString() }
    return when (decision) {
        is RedirectOutcome.Follow -> HopOutcome.Redirect(status, decision.next)                       // its body is never read
        RedirectOutcome.TooMany -> HopOutcome.Failed(plainFailure(TransportFailure.TooManyRedirects(context.settings.maxRedirects)))
        is RedirectOutcome.Refused -> HopOutcome.Failed(refusalFailure(decision.refusal, RefusalStage.RedirectTarget))
        is RedirectOutcome.Deliver -> deliver(context, response, status, onBodyFailure)
    }
}

private fun deliver(context: HopContext, response: Response, status: Int, onBodyFailure: (Throwable) -> MappedFailure): HopOutcome {
    val received = receiveHeaders(response.headerPairs())
    if (received.dropped > 0) transportLog.debug("dropped {} received header entries with a name that is not a token", received.dropped)
    val bytes: Result<ByteArray> =
        if (!bodyExpected(context.method, status)) Result.success(NO_BODY)
        else readCappedBody(response.body.source(), response.body.contentLength().takeIf { it >= 0 }, context.settings.maxResponseBytes)
    return bytes.fold(
        onSuccess = { HopOutcome.Done(HttpResponse(status, context.url, received.headers, context.chain, it)) },
        onFailure = { e -> HopOutcome.Failed(if (e is TransportFailure.BodyTooLarge) plainFailure(e) else onBodyFailure(e)) },
    )
}
