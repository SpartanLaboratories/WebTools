package com.spartanlabs.webtools.http

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and returns its value as a success. An [Exception] other than [CancellationException]
 * becomes `Result.failure`; a [CancellationException] is rethrown so cancellation stays cooperative; an
 * [Error] is not caught. Being `inline`, [block] may call suspend functions when the caller is a suspend
 * function. This is the module's one `try`/`catch`; it replaces `runCatching`, which catches everything.
 *
 * @param block the code to run.
 * @return the value, or the non-fatal exception it threw.
 * @throws CancellationException if [block] was cancelled.
 */
internal inline fun <T> catchNonFatal(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
