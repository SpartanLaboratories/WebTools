package com.spartanlabs.webtools.http

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

/** What one hop produced. A value, never a throwable. */
internal sealed interface HopOutcome {
    /** The hop delivered a response. */
    class Done(val response: HttpResponse) : HopOutcome

    /** The hop answered a followable redirect to [next]. */
    class Redirect(val status: Int, val next: Url) : HopOutcome

    /** The hop failed with [mapped]. */
    class Failed(val mapped: MappedFailure) : HopOutcome

    /** Resumed into a continuation that was already cancelled; ignored by it and never seen by the loop. */
    object Abandoned : HopOutcome
}

/**
 * Enqueues [call] and suspends for its outcome. The handlers run on the OkHttp dispatcher thread: [handleResponse] may read the body (so
 * cancelling the calling coroutine, which cancels the call, stops the read); both must be total for `Exception`. The continuation is resumed
 * exactly once with a value, from a `finally`, so an `Error` out of a handler resumes the caller with `Io` and still propagates to OkHttp.
 *
 * @param call the call to enqueue; one request, one hop.
 * @param handleResponse turns the delivered response into the outcome; the response is closed afterwards.
 * @param handleFailure turns the delivered exception into the outcome.
 * @return the outcome.
 * @throws kotlinx.coroutines.CancellationException when the calling coroutine is cancelled; the call is cancelled with it.
 */
internal suspend fun awaitHop(
    call: Call,
    handleResponse: (Response) -> HopOutcome,
    handleFailure: (IOException) -> HopOutcome,
): HopOutcome = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { call.cancel() }                  // registered before enqueue: a rejected enqueue fails synchronously
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = settle(continuation) {
            if (continuation.isCancelled) HopOutcome.Abandoned else handleFailure(e)
        }

        override fun onResponse(call: Call, response: Response) = settle(continuation) {
            try {
                if (continuation.isCancelled) HopOutcome.Abandoned else handleResponse(response)
            } finally {
                catchNonFatal { response.close() }
            }
        }
    })
}

// A value is resumed, never a throwable: under -ea kotlinx-coroutines recreates a resumed exception through its (Throwable) constructor with
// the original as the cause, which would rewrite the transport's decision about `cause`. The finally guarantees exactly one resume even when
// an Error leaves the handler.
private inline fun settle(continuation: CancellableContinuation<HopOutcome>, produce: () -> HopOutcome) {
    var outcome: HopOutcome? = null
    try {
        outcome = produce()
    } finally {
        continuation.resume(outcome ?: HopOutcome.Failed(plainFailure(TransportFailure.Io())))
    }
}
