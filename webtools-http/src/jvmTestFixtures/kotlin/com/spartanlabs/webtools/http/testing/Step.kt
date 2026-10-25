package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.TransportFailure
import kotlin.time.Duration

/**
 * One scripted answer of a [FakeHttpTransport] stub: after a virtual-time [delay], respond with a built
 * response, a [Fixture], a response the consumer's lambda builds, or a [TransportFailure]. A stub's steps
 * run in order and the last repeats.
 *
 * **Stable Core, source compatibility only.** Built only through the companion's factories; immutable and
 * safe to share between stubs and threads. Overloads: `Step.respond(429, HttpHeaders { add("Retry-After", "30") })`
 * is the `String`-body overload with its defaults, `Step.respond { ... }` the lambda overload,
 * `Step.fail(TransportFailure.Timeout())` returns the same failure instance on every repeat and
 * `Step.fail { TransportFailure.Timeout() }` a fresh one per call. A step holds no request data; `toString()`
 * prints its kind and delay, never a header value or a body. An exception thrown inside a lambda propagates
 * out of `execute` (a test-authoring error).
 *
 * @property delay the virtual-time delay before the step answers; zero by default.
 */
public class Step private constructor(
    /** The delay before the step answers; virtual when the caller runs on the test scheduler. */
    public val delay: Duration,
    private val kind: String,
    /** The step's answer for a request; the fake runs it after [delay]. */
    internal val produce: suspend (HttpRequest) -> Result<HttpResponse>,
) {
    init {
        require(!delay.isNegative()) { "delay must not be negative" }
    }

    override fun toString(): String = "Step($kind, delay=$delay)"

    /** The factories. */
    public companion object {
        /**
         * Responds with [status], [headers] and [body] as UTF-8, from the request's own URL.
         *
         * @param status the status, in 100..599; 200 by default.
         * @param headers the response headers; none by default. Shared by reference (immutable).
         * @param body the body text, encoded as UTF-8; empty by default.
         * @param delay the delay before the answer; zero by default.
         * @return the step.
         * @throws IllegalArgumentException if [status] is outside 100..599 or [delay] is negative.
         */
        public fun respond(status: Int = 200, headers: HttpHeaders = HttpHeaders(), body: String = "", delay: Duration = Duration.ZERO): Step =
            respond(status, headers, body.encodeToByteArray(), delay)

        /**
         * Responds with [status], [headers] and the bytes of [body], from the request's own URL.
         *
         * @param status the status, in 100..599.
         * @param headers the response headers. Shared by reference (immutable).
         * @param body the body; copied now, so a later change to the array changes nothing.
         * @param delay the delay before the answer; zero by default.
         * @return the step.
         * @throws IllegalArgumentException if [status] is outside 100..599 or [delay] is negative.
         */
        public fun respond(status: Int, headers: HttpHeaders, body: ByteArray, delay: Duration = Duration.ZERO): Step {
            require(status in 100..599) { "Status code must be in 100..599" }
            val bytes = body.copyOf()
            val responseHeaders = headers    // the builder's own `headers` would shadow the parameter inside its lambda
            return Step(delay, "respond $status") { request ->
                Result.success(HttpResponse(status, request.url) { this.headers = responseHeaders; body(bytes) })
            }
        }

        /**
         * Responds with whatever [response] builds for the request: a response with a `redirectChain`, another
         * final URL, or anything else the built-in shapes cannot express. It runs once per call.
         *
         * @param delay the delay before [response] runs; zero by default.
         * @param response builds the response; an exception it throws propagates out of `execute`.
         * @return the step.
         * @throws IllegalArgumentException if [delay] is negative.
         */
        public fun respond(delay: Duration = Duration.ZERO, response: suspend (HttpRequest) -> HttpResponse): Step =
            Step(delay, "respond lambda") { request -> Result.success(response(request)) }

        /**
         * Responds with [fixture] ([Fixture.toResponse]) from the request's own URL.
         *
         * @param fixture the recorded or hand-written response.
         * @param delay the delay before the answer; zero by default.
         * @return the step.
         * @throws IllegalArgumentException if [delay] is negative.
         */
        public fun fixture(fixture: Fixture, delay: Duration = Duration.ZERO): Step =
            Step(delay, "fixture ${fixture.status}") { request -> Result.success(fixture.toResponse(request.url)) }

        /**
         * Fails with [failure]: `execute` returns `Result.failure(failure)`, the same instance on every repeat.
         *
         * @param failure the transport failure.
         * @param delay the delay before the failure; zero by default.
         * @return the step.
         * @throws IllegalArgumentException if [delay] is negative.
         */
        public fun fail(failure: TransportFailure, delay: Duration = Duration.ZERO): Step =
            Step(delay, "fail") { Result.failure(failure) }

        /**
         * Fails with a fresh failure from [failure] on every call.
         *
         * @param delay the delay before the failure; zero by default.
         * @param failure builds the transport failure, once per call.
         * @return the step.
         * @throws IllegalArgumentException if [delay] is negative.
         */
        public fun fail(delay: Duration = Duration.ZERO, failure: () -> TransportFailure): Step =
            Step(delay, "fail") { Result.failure(failure()) }
    }
}
