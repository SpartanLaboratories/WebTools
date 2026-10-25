package com.spartanlabs.testing.component.webtools.http.testing

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.Fixture
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `Step` in isolation, played through a `FakeHttpTransport` with `RequestMatch.Any`: the
 * factories' validation, String and ByteArray bodies, the shared immutable headers, the six overload shapes,
 * `delay`, the once-per-call lambda, and `toString()`.
 */
class StepComponentTest {

    private val url = Url.parse("https://example.com/x?secret=1").getOrThrow()
    private val request = HttpRequest(HttpMethod.Get, url)

    private suspend fun play(step: Step): Result<HttpResponse> = FakeHttpTransport().on(RequestMatch.Any, step).execute(request)

    @Test
    fun `the factories validate the status and the delay`() {
        assertFailsWith<IllegalArgumentException> { Step.respond(99) }
        assertFailsWith<IllegalArgumentException> { Step.respond(600) }
        assertFailsWith<IllegalArgumentException> { Step.respond(200, HttpHeaders(), byteArrayOf(), (-1).seconds) }
        assertFailsWith<IllegalArgumentException> { Step.respond(delay = (-1).seconds) { HttpResponse(200, it.url) } }
        assertFailsWith<IllegalArgumentException> { Step.fail(TransportFailure.Timeout(), (-1).seconds) }
        assertFailsWith<IllegalArgumentException> { Step.fixture(Fixture(200, HttpHeaders(), byteArrayOf()), (-1).seconds) }
        assertEquals(Duration.ZERO, Step.respond(100).delay)
        assertEquals(Duration.ZERO, Step.respond(599).delay)
    }

    @Test
    fun `a String body is UTF-8 and a ByteArray body is copied`() = runTest {
        assertContentEquals("café".encodeToByteArray(), play(Step.respond(200, body = "café")).getOrThrow().body())   // U+00E9
        val source = byteArrayOf(1, 2, 3)
        val step = Step.respond(200, HttpHeaders(), source)
        source[0] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), play(step).getOrThrow().body())
    }

    @Test
    fun `the headers object is shared by reference`() = runTest {
        val headers = HttpHeaders { add("Retry-After", "30") }
        val response = play(Step.respond(429, headers)).getOrThrow()
        assertSame(headers, response.headers)
        assertEquals(429, response.status)
        assertEquals(url, response.url)
    }

    @Test
    fun `the six overload shapes resolve`() = runTest {
        val a: Step = Step.respond(429, HttpHeaders { add("Retry-After", "30") })                 // String overload with defaults
        val b: Step = Step.respond(200, HttpHeaders(), byteArrayOf(0x41))                          // ByteArray overload
        val c: Step = Step.respond { HttpResponse(201, it.url) }                                   // lambda overload
        val d: Step = Step.fixture(Fixture(204, HttpHeaders(), byteArrayOf()))                      // fixture
        val e: Step = Step.fail(TransportFailure.Timeout())                                        // instance overload
        val f: Step = Step.fail { TransportFailure.Timeout() }                                     // factory overload
        assertEquals(429, play(a).getOrThrow().status)
        assertEquals("A", play(b).getOrThrow().body().decodeToString())
        assertEquals(201, play(c).getOrThrow().status)
        assertEquals(204, play(d).getOrThrow().status)
        assertIs<TransportFailure.Timeout>(play(e).exceptionOrNull())
        assertIs<TransportFailure.Timeout>(play(f).exceptionOrNull())
    }

    @Test
    fun `delay is kept`() {
        assertEquals(3.seconds, Step.respond(200, delay = 3.seconds).delay)
        assertEquals(2.seconds, Step.fail(2.seconds) { TransportFailure.Io() }.delay)
        assertEquals(Duration.ZERO, Step.fail(TransportFailure.Io()).delay)
    }

    @Test
    fun `a lambda step runs once per call and receives the request`() = runTest {
        var calls = 0
        val received = ArrayList<HttpRequest>()
        val transport = FakeHttpTransport().on(RequestMatch.Any, Step.respond { calls++; received.add(it); HttpResponse(200, it.url) })
        transport.execute(request)
        transport.execute(request)
        assertEquals(2, calls)
        assertEquals(listOf(request, request), received)
    }

    @Test
    fun `toString shows the kind and the delay and no header value or body`() {
        val step = Step.respond(200, HttpHeaders { add("X-Api-Key", "SECRET-VALUE") }, "SECRET-BODY", 1.seconds)
        assertEquals("Step(respond 200, delay=1s)", step.toString())
        assertFalse("SECRET" in step.toString())
        assertEquals("Step(respond lambda, delay=0s)", Step.respond { HttpResponse(200, it.url) }.toString())
        assertEquals("Step(fixture 304, delay=0s)", Step.fixture(Fixture(304, HttpHeaders(), byteArrayOf())).toString())
        assertEquals("Step(fail, delay=0s)", Step.fail(TransportFailure.Timeout()).toString())
    }
}
