package com.spartanlabs.webtools.http

import kotlin.time.Duration

/** What the client does with a response, decided in this order. */
internal enum class Verdict { Throttled, Missing, Decode, Rejected }

/** The client's classification: pure and total. A throttle first, then a declared miss status, then a `2xx` to decode, else rejected. */
internal fun verdictOf(status: Int, throttled: Boolean, misses: Set<Int>): Verdict = when {
    throttled || status == 429 -> Verdict.Throttled
    status in misses -> Verdict.Missing
    status in 200..299 -> Verdict.Decode
    else -> Verdict.Rejected
}

/** What the fetcher does with a response. */
internal enum class FetchVerdict { Throttled, Delivered }

/** The fetcher's classification: only a throttle is not the response. */
internal fun fetchVerdictOf(status: Int, throttled: Boolean): FetchVerdict =
    if (throttled || status == 429) FetchVerdict.Throttled else FetchVerdict.Delivered

/** `RateLimited.retryAfter`: the longer of the server's `Retry-After` and the budget's pause in force; zero when neither named a wait. */
internal fun retryAfterOf(signals: RateSignals, pausedFor: Duration): Duration = maxOf(signals.retryAfter ?: Duration.ZERO, pausedFor)
