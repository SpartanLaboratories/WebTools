package com.spartanlabs.webtools

/**
 * Chains a [Result]-returning [transform] onto this result, short-circuiting on failure.
 *
 * The stdlib provides [Result.map] for `(T) -> R` but has no equivalent for
 * `(T) -> Result<R>`, which would otherwise nest as `Result<Result<R>>`. This is the
 * building block the library uses to compose multi-step operations - each step returns
 * its own `Result` and the first failure propagates untouched, without a single
 * `try-catch` in the chain.
 *
 * @param transform applied to the encapsulated value if this result is a success
 * @return [transform]'s result if this is a success, otherwise this failure unchanged
 */
internal inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
    fold(onSuccess = transform, onFailure = { Result.failure(it) })
