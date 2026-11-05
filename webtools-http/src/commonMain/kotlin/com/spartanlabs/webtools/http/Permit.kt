package com.spartanlabs.webtools.http

/**
 * A granted unit of budget. Charged at the grant, so a permit never completed is simply spent: the budget holds no lock or lease
 * for it. Not `AutoCloseable`: its completion is suspend.
 *
 * **Experimental (implement).** Implementing needs `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`; calling [complete]
 * and [release] is Stable Core. `complete` means the request was sent; `release` means it never was and refunds the units. Both
 * are idempotent, run under `NonCancellable` (so they take effect from a cancelled coroutine), and a `complete` after a
 * `release`, or the reverse, is ignored.
 */
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public interface Permit {
    /** The units this permit charged. */
    public val cost: Int

    /** The lane it was granted on. */
    public val lane: Lane

    /** [complete] with [RateSignals.None]: the request was sent and nothing is known about the answer. */
    public suspend fun complete() { complete(RateSignals.None) }

    /**
     * The request was sent and [signals] were read from its answer: reconcile counts and pause on a throttle.
     *
     * @param signals what the response said; [RateSignals.None] changes nothing.
     */
    public suspend fun complete(signals: RateSignals)

    /** The request was never sent: refund the units and re-plan the waiters. */
    public suspend fun release()
}
