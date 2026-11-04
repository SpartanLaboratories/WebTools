package com.spartanlabs.webtools.http

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/**
 * Acquires a permit, runs [block] with it and completes the permit as spent (no signals) in a `finally` unless the block
 * completed or released it. An exception the block throws propagates after the permit is completed; a refused or closed acquire
 * is returned as the failure and [block] does not run.
 *
 * **Stable Core.** Not `inline`: a public inline function would put its body in every caller; the cost of a call through here is
 * one lambda.
 *
 * @param cost the units to charge; at least 1.
 * @param lane the lane to charge.
 * @param maxWait the longest the call may wait for its slot.
 * @param block the work; it decides `complete(signals)` or `release()` for the permit.
 * @return the block's result, or the acquire's failure.
 * @throws IllegalArgumentException if [cost] is below 1 or [maxWait] is negative.
 * @throws kotlinx.coroutines.CancellationException if the caller is cancelled.
 */
public suspend fun <T> CallBudget.withPermit(cost: Int, lane: Lane, maxWait: Duration, block: suspend (Permit) -> Result<T>): Result<T> =
    acquire(cost, lane, maxWait).flatMap { permit ->
        try {
            block(permit)
        } finally {
            withContext(NonCancellable) { permit.complete() }      // idempotent: a no-op when the block completed or released it
        }
    }
