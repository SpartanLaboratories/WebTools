package com.spartanlabs.webtools.http

/**
 * The policy a client's transport is built with: when [budget] is a `WindowedCallBudget`, both concurrency limits are raised to at least its `maxBurst` (never lowered, no ceiling), so a permit the
 * budget granted never queues in OkHttp outside the call timeout and the wait cap; otherwise the receiver, unchanged.
 *
 * @param budget the budget the client will use.
 * @return the receiver when [budget] is not a `WindowedCallBudget`; otherwise a copy whose two concurrency limits are at least the budget's `maxBurst`.
 */
// Both limits, not the total alone: a client talks to one host, where OkHttp's per-host limit binds, and OkHttp's call timeout starts only when a
// dispatcher thread runs a queued call. The result is valid because both are raised to at least the burst, so the per-host limit stays at or below the total.
internal fun OutboundUrlPolicy.sizedFor(budget: CallBudget): OutboundUrlPolicy {
    if (budget !is WindowedCallBudget) return this
    val burst = budget.maxBurst
    return withSettings(
        TransportSettings(settings) {
            maxConcurrentRequests = maxOf(maxConcurrentRequests, burst)
            maxConcurrentRequestsPerHost = maxOf(maxConcurrentRequestsPerHost, burst)
        },
    )
}
