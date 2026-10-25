package com.spartanlabs.webtools.http

/**
 * The seam between the stack and the network: sends one [HttpRequest] and returns the [HttpResponse]
 * or the [HttpFailure] inside a `Result`.
 *
 * The contract an implementation (a consumer's own transport or a fake) meets:
 * 1. [execute] sends exactly one request per hop. It never retries, and it does not follow a redirect
 *    unless the implementation documents that it does and records each hop in
 *    [HttpResponse.redirectChain].
 * 2. It enforces the implementation's documented caps (body size, redirects, timeouts) and reports a
 *    breach as the matching [TransportFailure] ([TransportFailure.BodyTooLarge],
 *    [TransportFailure.TooManyRedirects], [TransportFailure.Timeout]).
 * 3. A `304` is a response, never a failure. Any status in 100..599 is an [HttpResponse]; a status
 *    outside that range is [TransportFailure.Protocol].
 * 4. Response entries the types cannot hold are handled, not failed: an entry whose name is not an
 *    RFC 9110 token is dropped; CR, LF and NUL in a value are replaced with SP.
 * 5. Cancellation is thrown (`CancellationException`), never wrapped in a `Result`.
 * 6. After [close], [execute] returns `Result.failure(HttpFailure.Closed("transport"))`. [close] is
 *    idempotent and does not throw.
 * 7. [execute] may be called concurrently from many coroutines.
 * 8. [execute] is main-safe: an implementation moves blocking I/O off the caller's thread and documents
 *    which dispatcher it uses.
 *
 * **Experimental (implement).** Implementing or delegating to this interface needs
 * `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`; calling it and passing it are Stable Core, and
 * the supplied implementations are usable without the opt-in.
 */
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public interface HttpTransport : AutoCloseable {
    /**
     * Sends [request] and returns the response, or the failure.
     *
     * @param request the request to send.
     * @return the response for any status in 100..599, or a failure holding an [HttpFailure] (normally a
     *   [TransportFailure]; [HttpFailure.Closed] after [close]). Cancellation is thrown, never returned.
     */
    public suspend fun execute(request: HttpRequest): Result<HttpResponse>

    /**
     * Releases the transport's resources. Idempotent, non-suspending, does not throw. The default does
     * nothing.
     */
    override fun close() {}
}
