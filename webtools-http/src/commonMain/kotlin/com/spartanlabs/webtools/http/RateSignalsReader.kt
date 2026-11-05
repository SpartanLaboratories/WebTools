package com.spartanlabs.webtools.http

import kotlin.time.Duration

/**
 * Turns a response into the [RateSignals] the budget acts on. A consumer supplies a per-provider reader for the headers and
 * counts its provider sends.
 *
 * **Experimental (implement).** Implementing needs `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`; calling it and
 * passing it are Stable Core, and [Standard] needs no opt-in. An implementation never throws for bad input: it returns
 * [RateSignals.None] (or the part it could read). It must be safe for concurrent calls.
 */
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public interface RateSignalsReader {
    /**
     * Reads one response.
     *
     * @param response the response the call produced.
     * @param nowEpochMillis the wall clock at receipt, in epoch milliseconds; resolves a date.
     * @return the signals; never throws for bad input.
     */
    public fun read(response: HttpResponse, nowEpochMillis: Long): RateSignals

    /**
     * The reader for providers that follow RFC 9110: a `429` is throttled (with or without `Retry-After`); a `503` is throttled
     * only when a `Retry-After` parses; every other status is [RateSignals.None]. It parses every line of the `Retry-After`
     * header and takes the longest wait among those that parse; it reads the response's `Date` and, when that parses as an
     * HTTP-date, measures a date-valued `Retry-After` against it, else against `nowEpochMillis`. It reports no window readings.
     *
     * **Stable Core.** Final and stateless; usable without the opt-in; `toString()` is `RateSignalsReader.Standard`. It is the
     * worked example of the seam.
     */
    public object Standard : RateSignalsReader {
        private const val RETRY_AFTER: String = "Retry-After"
        private const val DATE: String = "Date"

        override fun read(response: HttpResponse, nowEpochMillis: Long): RateSignals {
            val status = response.status
            if (status != 429 && status != 503) return RateSignals.None
            // `Date` is a single-value field, so its last line is read (`get`); `Retry-After` lines are all read (`getAll`),
            // because a budget pauses until the maximum of every hint.
            val serverDate: Long? = response.headers[DATE]?.let { HttpDate.parse(it, nowEpochMillis).getOrNull() }
            val longest: Duration? = response.headers.getAll(RETRY_AFTER)
                .mapNotNull { line -> parseLine(line, nowEpochMillis, serverDate).getOrNull() }
                .maxOrNull()
            return if (status == 429 || longest != null) RateSignals { throttled = true; retryAfter = longest } else RateSignals.None
        }

        private fun parseLine(line: String, nowEpochMillis: Long, serverDate: Long?): Result<Duration> =
            if (serverDate != null) RetryAfter.parse(line, nowEpochMillis, serverDate) else RetryAfter.parse(line, nowEpochMillis)

        override fun toString(): String = "RateSignalsReader.Standard"
    }
}
