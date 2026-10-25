package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.HttpTransport

/**
 * The recorder's decorating transport: forwards every call to [delegate], hands each successful response to
 * [record], and returns the delegate's result untouched. It is the decorator pattern that is the transport's
 * observation hook, written to be read: a consumer's own decorator (a logger, a counter, a recorder) has this
 * shape, and the `@OptIn(ExperimentalForInheritanceWebToolsApi::class)` on it is exactly the line it needs.
 * Failures pass through unrecorded; cancellation is thrown; `close()` forwards, as a decorator behaves as its
 * delegate.
 */
@OptIn(ExperimentalForInheritanceWebToolsApi::class)
internal class RecordingHttpTransport(
    private val delegate: HttpTransport,
    private val record: suspend (HttpRequest, HttpResponse) -> Unit,
) : HttpTransport {
    // Result.onSuccess is inline, so the suspend record call compiles inside it.
    override suspend fun execute(request: HttpRequest): Result<HttpResponse> =
        delegate.execute(request).onSuccess { record(request, it) }

    override fun close() {
        delegate.close()
    }

    override fun toString(): String = "RecordingHttpTransport($delegate)"
}
