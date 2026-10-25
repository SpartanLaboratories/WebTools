package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.HttpTransport
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.flatMap
import kotlinx.coroutines.delay
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A scripted [HttpTransport] for tests: stubs of a [RequestMatch] and a script of [Step]s, a call log with
 * virtual stamps, and the two transport behaviours it mirrors. It is the worked example of the
 * `HttpTransport` seam, written to be read: the `@OptIn(ExperimentalForInheritanceWebToolsApi::class)` on
 * this class is exactly the line a consumer's own implementation needs.
 *
 * **Stable Core, source compatibility only.** The fixtures and `webtools-http` are released and versioned
 * together: the fixtures call the module's `internal` members, and Gradle resolves both from one component.
 * Equality is identity. `toString()` prints the stub count, the call count and the closed flag, nothing else.
 *
 * The contract it keeps:
 * - **Lookup:** the most recently added stub whose match accepts the request wins, so a shared set-up
 *   registers defaults and a test overrides one path by adding a stub later.
 * - **Script:** a stub's steps run in order and the last repeats; each stub has its own cursor.
 * - **Recording:** every call is recorded under the lock before any delay, with [RecordedCall.receivedAt]
 *   the time elapsed on the injected [TimeSource] since construction; a request cancelled during its delay
 *   still counts. A call that arrives after [close] is not recorded and consumes no step.
 * - **Delay:** `delay(step.delay)` runs in the caller's context, so it is virtual when the caller runs on
 *   the test scheduler (under `runTest`, or on [VirtualClock.dispatcher]) and real otherwise. Cancellation
 *   throws `CancellationException`, never a `Result`.
 * - **Unmatched:** the call is recorded with `matched = false`, then [UnmatchedRequestError] is thrown
 *   (an `Error`: it passes through a cache loader to each waiter and fails the test with the method, the
 *   redacted URL and the stub count). [Unmatched.Respond] is the opt-in for a lenient fake. Nothing is
 *   defaulted to a `404`.
 * - **Fidelity:** a respond or fixture step builds `HttpResponse(status, request.url) { headers; body }`;
 *   a lambda step returns its own response. With [maxResponseBytes] set, a response whose declared
 *   `Content-Length` (a non-empty run of digits that parses as a non-negative `Long`, else ignored) or body
 *   is above the cap becomes `Result.failure(TransportFailure.BodyTooLarge(cap, declared))`. After [close]
 *   every call returns `Result.failure(HttpFailure.Closed("transport"))`.
 * - **Not mirrored:** URL policy, DNS, TLS, redirects and timeouts belong to the real transport.
 * - **Exceptions** from the consumer's matcher or lambda propagate out of [execute] (a test-authoring
 *   error); they are not wrapped in the `Result`. A matcher that throws leaves the call unrecorded.
 *
 * Thread-safety: one lock guards the stubs, every cursor, the call log and the closed flag, and is never
 * held across a suspension; a matcher runs under it (re-entrant on the same thread; one that blocks would
 * block other callers). [execute] may be called concurrently from many coroutines and never blocks a thread
 * (contract point 8). [calls], [unmatchedCalls] and [callsMatching] return snapshots.
 *
 * @param timeSource the source of [RecordedCall.receivedAt]; `TimeSource.Monotonic` by default,
 *   [VirtualClock.timeSource] for virtual stamps.
 * @param maxResponseBytes the body cap to mirror, or `null` (the default) for none; not negative.
 * @param unmatched what to do with a request no stub matches; [Unmatched.Fail] by default.
 * @throws IllegalArgumentException if [maxResponseBytes] is negative.
 */
@OptIn(ExperimentalForInheritanceWebToolsApi::class)
public class FakeHttpTransport(
    timeSource: TimeSource = TimeSource.Monotonic,
    private val maxResponseBytes: Long? = null,
    private val unmatched: Unmatched = Unmatched.Fail,
) : HttpTransport {
    init {
        require(maxResponseBytes == null || maxResponseBytes >= 0) { "maxResponseBytes must not be negative" }
    }

    private class Stub(val match: RequestMatch, val steps: List<Step>) {
        var cursor: Int = 0
        fun next(): Step = steps[cursor].also { if (cursor < steps.lastIndex) cursor++ }   // the last step repeats
    }

    // What one call does, decided under the lock; played outside it.
    private sealed interface Plan {
        object Refuse : Plan
        class Run(val step: Step) : Plan
        class Throw(val error: UnmatchedRequestError) : Plan
    }

    private val origin: TimeMark = timeSource.markNow()
    private val lock = Any()                       // guards stubs, every cursor, callLog and closed; never held across a suspension
    private val stubs = ArrayList<Stub>()
    private val callLog = ArrayList<RecordedCall>()
    private var closed = false

    /**
     * Adds a stub: requests [match] accepts are answered by [steps], in order, the last repeating. A stub
     * added later wins over an earlier one that also matches.
     *
     * @param match the requests the stub answers.
     * @param steps the script; at least one.
     * @return this transport, for chaining.
     * @throws IllegalArgumentException if [steps] is empty.
     */
    public fun on(match: RequestMatch, vararg steps: Step): FakeHttpTransport {
        require(steps.isNotEmpty()) { "a stub needs at least one step" }
        synchronized(lock) { stubs.add(Stub(match, steps.toList())) }
        return this
    }

    /** Every call received so far, in arrival order, matched or not; a snapshot. */
    public val calls: List<RecordedCall> get() = synchronized(lock) { callLog.toList() }

    /**
     * The recorded calls whose request [match] accepts.
     *
     * @param match the filter.
     * @return a snapshot, in arrival order.
     */
    public fun callsMatching(match: RequestMatch): List<RecordedCall> = calls.filter { match.matches(it.request) }

    /** The recorded calls no stub matched; a snapshot. */
    public val unmatchedCalls: List<RecordedCall> get() = calls.filterNot { it.matched }

    /** Whether [close] has been called. */
    public val isClosed: Boolean get() = synchronized(lock) { closed }

    /**
     * Plays the script for [request].
     *
     * @param request the request.
     * @return the step's response or failure, `Result.failure(TransportFailure.BodyTooLarge(...))` over the
     *   cap, or `Result.failure(HttpFailure.Closed("transport"))` after [close].
     * @throws UnmatchedRequestError if no stub matches and the policy is [Unmatched.Fail].
     * @throws kotlinx.coroutines.CancellationException if the caller is cancelled during the step's delay.
     */
    override suspend fun execute(request: HttpRequest): Result<HttpResponse> =
        when (val plan = plan(request)) {
            Plan.Refuse -> closedFailure()
            is Plan.Throw -> throw plan.error
            is Plan.Run -> play(plan.step, request)
        }

    /** Closes the fake: idempotent, never throws; every later call returns `Closed("transport")`. */
    override fun close() {
        synchronized(lock) { closed = true }
    }

    override fun toString(): String = synchronized(lock) { "FakeHttpTransport(stubs=${stubs.size}, calls=${callLog.size}, closed=$closed)" }

    // Everything that needs the lock happens here, in one non-suspending function.
    private fun plan(request: HttpRequest): Plan = synchronized(lock) {
        if (closed) Plan.Refuse else {
            val stub = stubs.lastOrNull { it.match.matches(request) }
            callLog.add(RecordedCall(request, origin.elapsedNow(), matched = stub != null))
            when {
                stub != null -> Plan.Run(stub.next())
                unmatched is Unmatched.Respond -> Plan.Run(unmatched.step)
                else -> Plan.Throw(UnmatchedRequestError(request.method, request.url.redacted(), stubs.size))
            }
        }
    }

    private suspend fun play(step: Step, request: HttpRequest): Result<HttpResponse> {
        if (step.delay.isPositive()) delay(step.delay)               // virtual under the test scheduler; CancellationException when cancelled
        return if (isClosed) closedFailure() else step.produce(request).flatMap { capped(it) }
    }

    private fun capped(response: HttpResponse): Result<HttpResponse> {
        val cap = maxResponseBytes ?: return Result.success(response)
        val declared = response.headers["Content-Length"]?.let(::declaredLength)
        return if ((declared != null && declared > cap) || response.bodySize > cap) Result.failure(TransportFailure.BodyTooLarge(cap, declared))
        else Result.success(response)
    }

    private fun closedFailure(): Result<HttpResponse> = Result.failure(HttpFailure.Closed("transport"))
}

// A non-empty run of digits that fits a Long; anything else is no declaration.
private fun declaredLength(text: String): Long? = text.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.toLongOrNull()
