package com.spartanlabs.webtools.http

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** The constant message of [SecondExchangeRefused]. */
internal const val SECOND_EXCHANGE_MESSAGE: String = "The transport refused to send the request a second time."

/** The private header that carries a hidden status between [ExchangeGuard] and the response conversion; never surfaced. */
internal const val HIDDEN_STATUS_HEADER: String = "X-Webtools-Hidden-Status"

/** The status a hidden response travels under; OkHttp's follow-up logic does nothing for it. */
internal const val PLACEHOLDER_STATUS: Int = 599

/** The statuses OkHttp 5.5.0's follow-up logic re-sends on or throws for; each is hidden behind the placeholder and delivered once. */
internal val HIDDEN_STATUSES: Set<Int> = setOf(407, 408, 421, 503)

/** A per-hop counter carried as a `Request` tag; `Request.newBuilder()` copies it, so every re-send of one hop sees the same instance. */
internal class HopExchanges {
    private val entered = AtomicInteger()

    /** @return `true` for the first exchange of the hop, `false` for every later one. */
    fun enter(): Boolean = entered.incrementAndGet() == 1
}

/** Thrown for a second wire exchange of one hop. An `IOException` (the interceptor chain declares it), constant message, no stack trace. */
internal class SecondExchangeRefused : IOException(SECOND_EXCHANGE_MESSAGE) {
    override fun fillInStackTrace(): Throwable = this
}

/** Whether this throwable, its cause chain or its suppressed lists hold a [SecondExchangeRefused] (the same bounded walk as the policy markers). */
internal fun Throwable.repeatsExchange(): Boolean = firstInChain { it as? SecondExchangeRefused } != null

/** The one network interceptor: refuses a second exchange for a hop, and hides what would make OkHttp send the request again. */
internal object ExchangeGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val exchanges = request.tag(HopExchanges::class.java)
        if (exchanges == null || !exchanges.enter()) throw SecondExchangeRefused()
        return hideForFollowUp(chain.proceed(request))
    }

    override fun toString(): String = "ExchangeGuard"
}

/**
 * Strips a server-sent copy of the private header, then hides a status in [HIDDEN_STATUSES] from OkHttp's follow-up logic: the placeholder
 * [PLACEHOLDER_STATUS] replaces it and the original goes into [HIDDEN_STATUS_HEADER]. No other header is touched. Total: never throws (the one
 * entry added has a token name and a digits value, which `Headers.Builder.add` accepts).
 *
 * @param response the response as the wire delivered it.
 * @return the same response, or a copy with the status hidden or the forged header removed.
 */
internal fun hideForFollowUp(response: Response): Response {
    // A status is hidden rather than a header: Headers has no public rename and Headers.Builder.add validates values, so moving a
    // server-sent header aside could throw on an entry OkHttp itself accepted leniently.
    val cleaned = response.headers.newBuilder().removeAll(HIDDEN_STATUS_HEADER).build()
    return when (response.code) {
        in HIDDEN_STATUSES -> response.newBuilder().code(PLACEHOLDER_STATUS)
            .headers(cleaned.newBuilder().add(HIDDEN_STATUS_HEADER, response.code.toString()).build()).build()
        else -> if (cleaned == response.headers) response else response.newBuilder().headers(cleaned).build()
    }
}

/**
 * The status a response really has: the hidden one when the code is the placeholder and the private header is a number, else the code.
 *
 * @param code the response code as OkHttp delivered it.
 * @param hiddenStatus the private header's value, or `null` when absent.
 * @return the restored status.
 */
internal fun restoredStatus(code: Int, hiddenStatus: String?): Int =
    if (code == PLACEHOLDER_STATUS) hiddenStatus?.toIntOrNull() ?: code else code
