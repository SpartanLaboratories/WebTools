package com.spartanlabs.testing.component.webtools.http.testing

import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.RedirectHop
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.Fixture
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.Unmatched
import com.spartanlabs.webtools.http.testing.UnmatchedRequestError
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - every behaviour of the fake's contract, each as its own test: lookup, script and cursors,
 * fidelity of each step kind, virtual stamps and delays, cancellation, the unmatched policies, the body
 * cap, construction errors, `close()`, snapshots, propagation of consumer exceptions and `toString()`.
 */
class FakeHttpTransportComponentTest {

    private fun url(text: String): Url = Url.parse(text).getOrThrow()
    private fun get(text: String): HttpRequest = HttpRequest(HttpMethod.Get, url(text))
    private val a = get("https://h/a")
    private val b = get("https://h/b")

    @Test
    fun `the most recently added matching stub wins`() = runTest {
        val transport = FakeHttpTransport()
            .on(RequestMatch.Any, Step.respond(500))
            .on(RequestMatch.get("/a"), Step.respond(200))
        assertEquals(200, transport.execute(a).getOrThrow().status)
        assertEquals(500, transport.execute(b).getOrThrow().status)
        transport.on(RequestMatch.get("/a"), Step.respond(204))
        assertEquals(204, transport.execute(a).getOrThrow().status)
    }

    @Test
    fun `steps run in order the last repeats and each stub has its own cursor`() = runTest {
        val transport = FakeHttpTransport()
            .on(RequestMatch.get("/a"), Step.respond(500), Step.respond(503), Step.respond(200))
            .on(RequestMatch.get("/b"), Step.respond(201), Step.respond(202))
        assertEquals(listOf(500, 503, 200, 200, 200), (1..5).map { transport.execute(a).getOrThrow().status })
        assertEquals(listOf(201, 202, 202), (1..3).map { transport.execute(b).getOrThrow().status })
    }

    @Test
    fun `a respond step carries status headers url and body`() = runTest {
        val headers = HttpHeaders { add("Content-Type", "text/plain") }
        val source = byteArrayOf(1, 2, 3)
        val transport = FakeHttpTransport()
            .on(RequestMatch.get("/a"), Step.respond(418, headers, "café"))                    // U+00E9
            .on(RequestMatch.get("/b"), Step.respond(200, headers, source))
        source[0] = 9
        val text = transport.execute(get("https://h/a?q=1")).getOrThrow()
        assertEquals(418, text.status)
        assertEquals(headers, text.headers)
        assertEquals(url("https://h/a?q=1"), text.url)
        assertContentEquals("café".encodeToByteArray(), text.body())
        assertContentEquals(byteArrayOf(1, 2, 3), transport.execute(b).getOrThrow().body())
    }

    @Test
    fun `a fixture step answers from the fixture`() = runTest {
        val fixture = Fixture(304, HttpHeaders { add("ETag", "\"v1\"") }, byteArrayOf())
        val response = FakeHttpTransport().on(RequestMatch.Any, Step.fixture(fixture)).execute(a).getOrThrow()
        assertEquals(304, response.status)
        assertEquals(fixture.headers, response.headers)
        assertEquals(a.url, response.url)
        assertEquals(0, response.bodySize)
    }

    @Test
    fun `a lambda step may build a redirected response with another final url`() = runTest {
        val final = url("https://h/final")
        val transport = FakeHttpTransport().on(RequestMatch.Any, Step.respond { request ->
            HttpResponse(200, final) { redirectChain = listOf(RedirectHop(request.url, 301)) }
        })
        val response = transport.execute(a).getOrThrow()
        assertEquals(final, response.url)
        assertEquals(listOf(RedirectHop(a.url, 301)), response.redirectChain)
    }

    @Test
    fun `a fail step returns the same instance on repeat and the factory overload a fresh one`() = runTest {
        val failure = TransportFailure.Timeout()
        val same = FakeHttpTransport().on(RequestMatch.Any, Step.fail(failure))
        assertSame(failure, same.execute(a).exceptionOrNull())
        assertSame(failure, same.execute(a).exceptionOrNull())
        val fresh = FakeHttpTransport().on(RequestMatch.Any, Step.fail { TransportFailure.Io() })
        val first = fresh.execute(a).exceptionOrNull()
        val second = fresh.execute(a).exceptionOrNull()
        assertIs<TransportFailure.Io>(first)
        assertNotSame(first, second)
    }

    @Test
    fun `receivedAt follows the injected time source`() = runTest {
        val clock = virtualClock()
        val transport = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond())
        transport.execute(a)
        clock.advanceBy(5.seconds)
        transport.execute(a)
        assertEquals(listOf(Duration.ZERO, 5.seconds), transport.calls.map { it.receivedAt })
    }

    @Test
    fun `a call is recorded before its delay`() = runTest {
        val transport = FakeHttpTransport().on(RequestMatch.Any, Step.respond(delay = 10.seconds))
        val inFlight = async { transport.execute(a) }
        testScheduler.runCurrent()
        assertFalse(inFlight.isCompleted)
        assertEquals(1, transport.calls.size)
        assertTrue(transport.calls.single().matched)
        testScheduler.advanceUntilIdle()
        assertEquals(200, inFlight.await().getOrThrow().status)
    }

    @Test
    fun `a delay is virtual`() = runTest {
        val clock = virtualClock()
        val transport = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond(delay = 10.seconds))
        val deferred = async { transport.execute(a) }
        testScheduler.runCurrent()
        assertFalse(deferred.isCompleted)
        clock.advanceBy(9.seconds)
        assertFalse(deferred.isCompleted)
        clock.advanceBy(1.seconds)
        assertTrue(deferred.isCompleted)
        assertEquals(200, deferred.await().getOrThrow().status)
    }

    @Test
    fun `cancelling during the delay throws CancellationException and the call stays recorded`() = runTest {
        val transport = FakeHttpTransport().on(RequestMatch.Any, Step.respond(delay = 10.seconds))
        var caught: Throwable? = null
        val job = launch {
            try {
                transport.execute(a)
            } catch (e: CancellationException) {
                caught = e
                throw e
            }
        }
        testScheduler.runCurrent()
        job.cancel()
        testScheduler.runCurrent()
        assertTrue(job.isCancelled)
        assertIs<CancellationException>(caught)
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun `an unmatched request under Fail throws with method redacted url and stub count and is recorded`() = runTest {
        val transport = FakeHttpTransport().on(RequestMatch.get("/a"), Step.respond()).on(RequestMatch.get("/b"), Step.respond())
        val error = assertFailsWith<UnmatchedRequestError> { transport.execute(get("https://h/c?api_key=SECRET-CANARY")) }
        assertEquals(HttpMethod.Get, error.method)
        assertEquals("https://h/c?...", error.redactedUrl)
        assertEquals(2, error.stubCount)
        assertEquals("No stub of the FakeHttpTransport matches GET https://h/c?... (2 stubs)", error.message)
        assertFalse("SECRET" in error.message!!)
        assertEquals(1, transport.calls.size)
        assertFalse(transport.calls.single().matched)
    }

    @Test
    fun `unmatchedCalls lists the calls no stub matched`() = runTest {
        val transport = FakeHttpTransport(unmatched = Unmatched.Respond(Step.respond(404))).on(RequestMatch.get("/a"), Step.respond())
        transport.execute(a)
        transport.execute(b)
        transport.execute(a)
        assertEquals(listOf(b), transport.unmatchedCalls.map { it.request })
        assertEquals(3, transport.calls.size)
    }

    @Test
    fun `Unmatched Respond serves its step`() = runTest {
        val transport = FakeHttpTransport(unmatched = Unmatched.Respond(Step.respond(404, body = "nope")))
        val response = transport.execute(b).getOrThrow()
        assertEquals(404, response.status)
        assertEquals("nope", response.body().decodeToString())
        assertFalse(transport.calls.single().matched)
    }

    @Test
    fun `maxResponseBytes mirrors BodyTooLarge`() = runTest {
        val transport = FakeHttpTransport(maxResponseBytes = 10)
            .on(RequestMatch.get("/declared"), Step.respond(200, HttpHeaders { add("Content-Length", "5000") }, "tiny"))
            .on(RequestMatch.get("/body"), Step.respond(200, body = "x".repeat(11)))
            .on(RequestMatch.get("/nonnumeric"), Step.respond(200, HttpHeaders { add("Content-Length", "many") }, "x".repeat(11)))
            .on(RequestMatch.get("/exact"), Step.respond(200, body = "x".repeat(10)))
        val declared = assertIs<TransportFailure.BodyTooLarge>(transport.execute(get("https://h/declared")).exceptionOrNull())
        assertEquals(10L, declared.limit)
        assertEquals(5000L, declared.declared)
        val body = assertIs<TransportFailure.BodyTooLarge>(transport.execute(get("https://h/body")).exceptionOrNull())
        assertEquals(10L, body.limit)
        assertNull(body.declared)
        val nonNumeric = assertIs<TransportFailure.BodyTooLarge>(transport.execute(get("https://h/nonnumeric")).exceptionOrNull())
        assertNull(nonNumeric.declared)
        assertEquals(200, transport.execute(get("https://h/exact")).getOrThrow().status)
    }

    @Test
    fun `a negative cap and a stub without steps are refused`() {
        assertFailsWith<IllegalArgumentException> { FakeHttpTransport(maxResponseBytes = -1) }
        assertFailsWith<IllegalArgumentException> { FakeHttpTransport().on(RequestMatch.Any) }
    }

    @Test
    fun `after close every call is Closed not recorded and no step consumed`() = runTest {
        val transport = FakeHttpTransport().on(RequestMatch.Any, Step.respond(500), Step.respond(200))
        transport.close()
        val failure = assertIs<HttpFailure.Closed>(transport.execute(a).exceptionOrNull())
        assertEquals("transport", failure.component)
        assertEquals(0, transport.calls.size)
        assertTrue(transport.isClosed)
        transport.close()   // idempotent
        assertTrue(transport.isClosed)
    }

    @Test
    fun `closing during a delay yields Closed`() = runTest {
        val transport = FakeHttpTransport().on(RequestMatch.Any, Step.respond(delay = 10.seconds))
        val deferred = async { transport.execute(a) }
        testScheduler.runCurrent()
        transport.close()
        testScheduler.advanceUntilIdle()
        assertIs<HttpFailure.Closed>(deferred.await().exceptionOrNull())
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun `isClosed is false until close`() {
        val transport = FakeHttpTransport()
        assertFalse(transport.isClosed)
        transport.close()
        assertTrue(transport.isClosed)
    }

    @Test
    fun `calls is a snapshot and callsMatching filters it`() = runTest {
        val transport = FakeHttpTransport().on(RequestMatch.Any, Step.respond())
        transport.execute(a)
        val snapshot = transport.calls
        transport.execute(b)
        assertEquals(1, snapshot.size)
        assertEquals(2, transport.calls.size)
        assertEquals(listOf(b), transport.callsMatching(RequestMatch.get("/b")).map { it.request })
    }

    @Test
    fun `an exception from a matcher or a lambda step propagates`() = runTest {
        val matcherThrows = FakeHttpTransport().on(RequestMatch { throw IllegalStateException("matcher") }, Step.respond())
        assertEquals("matcher", assertFailsWith<IllegalStateException> { matcherThrows.execute(a) }.message)
        assertEquals(0, matcherThrows.calls.size)
        val lambdaThrows = FakeHttpTransport().on(RequestMatch.Any, Step.respond { throw IllegalStateException("lambda") })
        assertEquals("lambda", assertFailsWith<IllegalStateException> { lambdaThrows.execute(a) }.message)
    }

    @Test
    fun `toString shows counts and the closed flag`() = runTest {
        val transport = FakeHttpTransport().on(RequestMatch.Any, Step.respond())
        transport.execute(get("https://h/a?api_key=SECRET-CANARY"))
        assertEquals("FakeHttpTransport(stubs=1, calls=1, closed=false)", transport.toString())
        transport.close()
        assertEquals("FakeHttpTransport(stubs=1, calls=1, closed=true)", transport.toString())
        assertEquals("RecordedCall(GET https://h/a?..., receivedAt=${transport.calls.single().receivedAt}, matched=true)", transport.calls.single().toString())
        assertEquals("Unmatched.Fail", Unmatched.Fail.toString())
        assertEquals("Unmatched.Respond(Step(respond 404, delay=0s))", Unmatched.Respond(Step.respond(404)).toString())
    }
}
