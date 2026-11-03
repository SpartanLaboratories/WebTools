package com.spartanlabs.webtools.http

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

/** The constant message of the failure a loader's own cancellation becomes; it never echoes a value. */
internal const val LOADER_CANCELLED_MESSAGE: String = "A cancellation escaped the cache loader"

/** The constant message of the programmer error for a negative `maxAge`. */
internal const val NEGATIVE_MAX_AGE_MESSAGE: String = "maxAge must not be negative"

/**
 * Runs [loader] for a cache: the one place that decides what a loader's exception, error and cancellation mean.
 *
 * A non-cancellation `Exception` the loader throws becomes `Result.failure`; the loader's own `Result` is flattened. A [CancellationException] the loader throws, or
 * returns inside `Result.failure`, is checked against the runner's own coroutine: if that is cancelled the cancellation is rethrown (it is the caller's or the
 * cache's); otherwise it came from inside the loader (a `withTimeout` expiring) and becomes `Result.failure(IllegalStateException(LOADER_CANCELLED_MESSAGE, it))`.
 * An `Error` propagates.
 *
 * @param loader the loader the caller supplied.
 * @return the loader's outcome as a `Result`; never a `CancellationException` inside it.
 * @throws CancellationException if the coroutine running this function is cancelled.
 * @throws Error if the loader throws one.
 */
internal suspend fun <V : Any> runLoader(loader: suspend () -> Result<Lookup<V>>): Result<Lookup<V>> {
    val ran: Result<Lookup<V>> = try {
        catchNonFatal { loader() }.flatMap { it }
    } catch (escaped: CancellationException) {
        // catchNonFatal rethrows a cancellation; this is the one place that tells the runner's own from the loader's.
        return failureOfEscapedCancellation(escaped)
    }
    val failure = ran.exceptionOrNull()
    return if (failure is CancellationException) failureOfEscapedCancellation(failure) else ran
}

private suspend fun <V : Any> failureOfEscapedCancellation(escaped: Throwable): Result<Lookup<V>> {
    currentCoroutineContext().ensureActive()   // rethrows when this coroutine itself is cancelled
    return Result.failure(IllegalStateException(LOADER_CANCELLED_MESSAGE, escaped))
}
