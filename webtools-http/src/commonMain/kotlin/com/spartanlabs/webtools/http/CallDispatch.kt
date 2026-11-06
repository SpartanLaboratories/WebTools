package com.spartanlabs.webtools.http

import kotlin.time.Duration
import kotlin.time.TimeSource

/** A response that was sent, the signals read from it and how long the call waited for its permit. */
internal class Sent(val response: HttpResponse, val signals: RateSignals, val waited: Duration)

/**
 * The one place that touches a permit, shared by the client and the fetcher: it takes the permit, sends the request, reads the rate signals from the
 * response and settles the permit.
 */
internal class CallDispatch(
    private val transport: HttpTransport,
    private val budget: CallBudget,
    private val rateSignals: RateSignalsReader,
    private val wallClock: WallClock,
    private val timeSource: TimeSource,
    private val log: Log,
) {
    /**
     * Takes a permit, sends [request] and settles the permit: a response completes it with the signals read from it, an `InvalidRequest` from the
     * transport releases it, and every other failure, a cancellation and an `Error` leave it spent with no signals.
     *
     * @param request the request to send; the facade has already checked it.
     * @param cost the budget units to charge; at least 1.
     * @param lane the lane to charge.
     * @param maxWait the longest the call waits for its permit; not negative.
     * @return the response with its signals, or the budget's refusal (`BudgetExhausted`, `Closed("budget")`) or the transport's failure, all as values.
     * @throws IllegalArgumentException if [cost] is below 1 or [maxWait] is negative (the facade has already refused both).
     * @throws kotlinx.coroutines.CancellationException if the caller is cancelled (the permit stays spent).
     * @throws Error if the transport, the reader or the budget's store throws one (the permit stays spent).
     */
    suspend fun send(request: HttpRequest, cost: Int, lane: Lane, maxWait: Duration): Result<Sent> {
        val requested = timeSource.markNow()
        return budget.withPermit(cost, lane, maxWait) { permit ->
            val waited = requested.elapsedNow()
            executeCatching(request).fold(
                onSuccess = { response ->
                    val signals = readSignals(response)
                    permit.complete(signals)
                    Result.success(Sent(response, signals, waited))
                },
                onFailure = { failure ->
                    // Only `InvalidRequest` proves nothing was sent. A failure carries no hop number, so any other may follow a request that was
                    // written, and a window that guards a bill is never under-counted: the permit stays spent (`withPermit`'s `finally`).
                    if (failure is TransportFailure.InvalidRequest) permit.release()
                    Result.failure(failure)
                },
            )
        }
    }

    /**
     * Builds the throttle failure for [sent]: the utilisation is read once, now (after `complete`), and a throwing `utilisation()` falls back to zero with one `WARN`.
     * Logs the throttle once at `WARN`.
     *
     * @param sent the throttled response with its signals; its permit is already completed.
     * @param subject what the `WARN` line names: the endpoint id (client) or the redacted URL (fetcher).
     * @return the failure; the one instance every caller of a shared load receives.
     * @throws kotlinx.coroutines.CancellationException if the caller is cancelled while the budget is read.
     * @throws Error if the budget's `utilisation()` throws one.
     */
    suspend fun throttleOf(sent: Sent, subject: String): UpstreamFailure.RateLimited {
        // Read after `complete(signals)`, so the pause this throttle started is in the reading; read here, once, so every caller of a shared load
        // receives the same failure.
        val pausedFor = catchNonFatal { budget.utilisation().pausedFor }.getOrElse { failure ->
            log.warn("budget utilisation failed: {}", failure::class.simpleName)
            Duration.ZERO
        }
        val failure = UpstreamFailure.RateLimited(sent.response.status, retryAfterOf(sent.signals, pausedFor))
        log.warn("throttled: subject={}, status={}, retryAfter={}", subject, failure.status, failure.retryAfter)
        return failure
    }

    // A transport that throws instead of returning a Result has broken its contract; the facade still returns a value.
    private suspend fun executeCatching(request: HttpRequest): Result<HttpResponse> =
        catchNonFatal { transport.execute(request) }.fold(onSuccess = { it }, onFailure = { Result.failure(TransportFailure.Io(it)) })

    private fun readSignals(response: HttpResponse): RateSignals =
        catchNonFatal { rateSignals.read(response, wallClock.nowEpochMillis()) }.getOrElse { failure ->
            log.warn("rate signal reader failed: {}", failure::class.simpleName)
            RateSignals.None
        }
}
