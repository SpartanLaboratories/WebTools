package com.spartanlabs.webtools.http

/** Why a `3xx` is delivered as the response instead of followed. */
internal enum class DeliverReason { Method, Status, NoTarget, AmbiguousTarget }

/** The outcome of one `3xx`. */
internal sealed class RedirectOutcome {
    /** The response is delivered as it is, for [why]. */
    class Deliver(val why: DeliverReason) : RedirectOutcome()

    /** The redirect cap is reached. */
    object TooMany : RedirectOutcome()

    /** The target was refused for [refusal]. */
    class Refused(val refusal: GuardRefusal) : RedirectOutcome()

    /** The admitted next hop. */
    class Follow(val next: Url) : RedirectOutcome()
}

private val FOLLOWED_STATUSES: Set<Int> = setOf(301, 302, 303, 307, 308)

/** Trims SP, HTAB, LF, FF and CR from both ends. */
internal fun trimLocation(value: String): String = value.trim { it == ' ' || it == '\t' || it == '\n' || it == '\u000C' || it == '\r' }

/** True when [value] starts with `scheme:` (a letter, then letters, digits, `+`, `-`, `.`) and the scheme is not `http` or `https`. */
internal fun foreignScheme(value: String): Boolean {
    val colon = value.indexOf(':')
    if (colon <= 0) return false
    val scheme = value.substring(0, colon)
    val syntax = scheme[0] in 'A'..'Z' || scheme[0] in 'a'..'z'
    return syntax && scheme.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '-' || it == '.' } &&
        !HttpSyntax.equalsName(scheme, "http") && !HttpSyntax.equalsName(scheme, "https")
}

/**
 * Decides what to do with a response. Pure apart from [resolve], which stands for `HttpUrl.resolve(...)?.toString()` and may throw. Rows in
 * order, the first match decides: the method, the status, the `Location` values, the cap, resolution, admission, follow.
 *
 * @param method the request method; only `GET` and `HEAD` follow.
 * @param status the response status; only `301 302 303 307 308` follow.
 * @param locations every `Location` value of the response, raw.
 * @param followed the redirects already followed on this fetch.
 * @param current the hop that returned the response.
 * @param maxRedirects the cap; `0` follows none.
 * @param guard the guard that admits the resolved target.
 * @param resolve the resolver: the printed absolute URL, or `null`; may throw.
 * @return a sealed outcome; never throws (a throwing [resolve] is a `Refused(HostForm)` or `Refused(Scheme)`).
 */
internal fun decideRedirect(
    method: HttpMethod, status: Int, locations: List<String>, followed: Int, current: Url, maxRedirects: Int,
    guard: UrlGuard, resolve: (String) -> String?,
): RedirectOutcome {
    if (method != HttpMethod.Get && method != HttpMethod.Head) return RedirectOutcome.Deliver(DeliverReason.Method)
    if (status !in FOLLOWED_STATUSES) return RedirectOutcome.Deliver(DeliverReason.Status)
    val values = locations.map(::trimLocation)
    val distinct = values.distinct()
    return when {
        values.isEmpty() || distinct == listOf("") -> RedirectOutcome.Deliver(DeliverReason.NoTarget)
        distinct.size > 1 -> RedirectOutcome.Deliver(DeliverReason.AmbiguousTarget)
        // The cap precedes admission, so a cap of 0 fails every followable row before any target is judged.
        followed >= maxRedirects -> RedirectOutcome.TooMany
        else -> admit(distinct.single(), current, guard, resolve)
    }
}

private fun admit(target: String, current: Url, guard: UrlGuard, resolve: (String) -> String?): RedirectOutcome =
    catchNonFatal { resolve(target) }.fold(
        onSuccess = { printed ->
            if (printed == null) unresolvable(target)
            else when (val admission = guard.admitRedirect(printed, current)) {
                is RedirectAdmission.Admitted -> RedirectOutcome.Follow(admission.url)
                is RedirectAdmission.Refused -> RedirectOutcome.Refused(admission.refusal)
            }
        },
        onFailure = { unresolvable(target) },
    )

// resolve returns null for a foreign scheme and for a malformed authority alike; the raw value's scheme tells the two apart.
private fun unresolvable(target: String): RedirectOutcome =
    RedirectOutcome.Refused(GuardRefusal(if (foreignScheme(target)) RefusalReason.Scheme else RefusalReason.HostForm, null))

/**
 * The headers of the next hop: [headers] unchanged, or without every `isSensitive` entry when the scheme, host or effective port of [to] differs
 * from [from]. The result is carried forward, so a removal is permanent.
 *
 * @param headers the headers of the hop that redirected.
 * @param from the hop that redirected.
 * @param to the admitted next hop.
 * @return the next hop's headers.
 */
internal fun headersForHop(headers: HttpHeaders, from: Url, to: Url): HttpHeaders =
    if (from.scheme == to.scheme && from.host == to.host && from.port == to.port) headers
    else HttpHeaders(headers.entries().filterNot { it.isSensitive })
