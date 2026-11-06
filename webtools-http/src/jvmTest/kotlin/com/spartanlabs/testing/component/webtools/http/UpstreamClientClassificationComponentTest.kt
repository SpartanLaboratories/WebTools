package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.PermitRecordingBudget
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.clientOn
import com.spartanlabs.testing.support.webtools.http.facadeEvents
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.testing.support.webtools.http.respondWith
import com.spartanlabs.testing.support.webtools.http.stringEndpoint
import com.spartanlabs.webtools.http.Endpoint
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UpstreamClient
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - how `UpstreamClient` classifies a response, with a cache and a recorded budget on a virtual clock: a `2xx` is decoded into `Found`; a
 * decoder's failure or exception is `DecodeFailure` and never stored, while its own `HttpFailure` passes through unchanged; a declared miss status
 * (a `2xx` included) is `Missing`, stored for `missTtl`, and the decoder is not called; any other status (an undeclared `404`, a `5xx`, a returned
 * `3xx`, a `304`) is `UpstreamError` and is not stored; a transport failure reaches the caller without the decoder running; a decoder's `Error`
 * propagates.
 */
class UpstreamClientClassificationComponentTest {

    // An `Error`, to prove propagation; compared by identity.
    private class Boom : Error()

    // What a test needs to drive one client: the clock, the fake transport, the recorded budget and the client.
    private class Rig(val clock: VirtualClock, val fake: FakeHttpTransport, val budget: PermitRecordingBudget, val client: UpstreamClient)

    // A client with a cache on the clock and a recorded budget of 100 a minute; the fake answers every request with the steps.
    private fun rigged(vararg steps: Step, maxResponseBytes: Long? = null, block: suspend TestScope.(Rig) -> Unit): TestResult = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource, maxResponseBytes).on(RequestMatch.Any, *steps)
        cacheOn(clock).use { responseCache ->
            budgetOn(clock, minuteWindow(100)).use { real ->
                val recording = PermitRecordingBudget(real)
                clientOn(clock, fake) { cache(responseCache); budget(recording) }.use { client -> block(Rig(clock, fake, recording, client)) }
            }
        }
    }

    // An endpoint whose decoder counts its calls and then answers with the body as UTF-8 text.
    private fun countingEndpoint(counter: AtomicInteger, configure: Endpoint.Builder.() -> Unit = {}): Endpoint<String> =
        Endpoint("hero", { response: HttpResponse -> counter.incrementAndGet(); Result.success(response.body().decodeToString()) }, configure)

    @Test
    fun `a 2xx is decoded into Found`() = rigged(respondWith(201, "made")) { rig ->
        val found = assertIs<Lookup.Found<String>>(rig.client.call(stringEndpoint()).getOrThrow())
        assertEquals("made", found.value)
        assertEquals(1, found.weight)
    }

    @Test
    fun `a decode failure is wrapped in DecodeFailure and never stored`() {
        val cause = IllegalStateException("x")
        val events = captureTransportLogs {
            rigged(respondWith(200, "body")) { rig ->
                val endpoint = Endpoint<String>("hero", { Result.failure(cause) }) { ttl = 10.minutes }

                val failure = assertIs<HttpFailure.DecodeFailure>(rig.client.call(endpoint).exceptionOrNull())
                assertSame(cause, failure.cause)
                assertIs<HttpFailure.DecodeFailure>(rig.client.call(endpoint).exceptionOrNull())

                assertEquals(2, rig.fake.calls.size)                             // the failure was not stored: the second call loaded again
            }
        }.second

        val lines = facadeEvents(events, "UpstreamClient").filter { it.level == Level.DEBUG }.map { it.formattedMessage }
        assertEquals(2, lines.count { it == "decode failed: endpoint=hero, cause=IllegalStateException" }, lines.toString())     // one per load
    }

    @Test
    fun `a decoder that throws becomes DecodeFailure`() = rigged(respondWith(200, "body")) { rig ->
        val thrown = IllegalArgumentException("bad body")
        val endpoint = Endpoint<String>("hero", { throw thrown })

        val failure = assertIs<HttpFailure.DecodeFailure>(rig.client.call(endpoint).exceptionOrNull())

        assertSame(thrown, failure.cause)
    }

    @Test
    fun `a decoder's HttpFailure passes through unchanged`() {
        val own = TransportFailure.Protocol()
        val events = captureTransportLogs {
            rigged(respondWith(200, "body")) { rig ->
                val endpoint = Endpoint<String>("hero", { Result.failure(own) })

                assertSame(own, rig.client.call(endpoint).exceptionOrNull())
            }
        }.second

        assertFalse(facadeEvents(events, "UpstreamClient").any { it.formattedMessage.startsWith("decode failed") })
    }

    @Test
    fun `a declared miss status is Missing and stored for missTtl`() = rigged(respondWith(404)) { rig ->
        val endpoint = stringEndpoint { misses(404); ttl = 10.minutes; missTtl = 30.seconds }

        assertSame(Lookup.Missing, rig.client.call(endpoint).getOrThrow())
        rig.clock.advanceBy(29.seconds)
        assertSame(Lookup.Missing, rig.client.call(endpoint).getOrThrow())
        assertEquals(1, rig.fake.calls.size)

        rig.clock.advanceBy(2.seconds)
        assertSame(Lookup.Missing, rig.client.call(endpoint).getOrThrow())
        assertEquals(2, rig.fake.calls.size)
    }

    @Test
    fun `a 204 declared a miss is a miss and the decoder is not called`() = rigged(respondWith(204)) { rig ->
        val decoded = AtomicInteger()
        val endpoint = countingEndpoint(decoded) { misses(204) }

        assertSame(Lookup.Missing, rig.client.call(endpoint).getOrThrow())

        assertEquals(0, decoded.get())
    }

    @Test
    fun `an undeclared 404 is UpstreamError carrying status, headers and body`() = rigged(Step.respond(404, HttpHeaders { add("X-A", "1") }, "nope")) { rig ->
        val failure = assertIs<UpstreamFailure.UpstreamError>(rig.client.call(stringEndpoint()).exceptionOrNull())

        assertEquals(404, failure.status)
        assertEquals("1", failure.headers["X-A"])
        assertEquals("nope", failure.body().decodeToString())
    }

    @Test
    fun `a 5xx is UpstreamError and is not stored`() = rigged(respondWith(500)) { rig ->
        val endpoint = stringEndpoint { ttl = 10.minutes }

        repeat(2) { assertEquals(500, assertIs<UpstreamFailure.UpstreamError>(rig.client.call(endpoint).exceptionOrNull()).status) }

        assertEquals(2, rig.fake.calls.size)
    }

    @Test
    fun `a 3xx the transport returned is UpstreamError`() = rigged(respondWith(302)) { rig ->
        assertEquals(302, assertIs<UpstreamFailure.UpstreamError>(rig.client.call(stringEndpoint()).exceptionOrNull()).status)
    }

    @Test
    fun `a 304 is UpstreamError unless declared a miss`() = rigged(respondWith(304)) { rig ->
        assertEquals(304, assertIs<UpstreamFailure.UpstreamError>(rig.client.call(stringEndpoint()).exceptionOrNull()).status)
        assertSame(Lookup.Missing, rig.client.call(stringEndpoint("conditional") { misses(304) }).getOrThrow())
    }

    @Test
    fun `a response over the fake's maxResponseBytes is BodyTooLarge and the decoder is not called`() =
        rigged(respondWith(200, "x".repeat(100)), maxResponseBytes = 10) { rig ->
            val decoded = AtomicInteger()

            val failure = assertIs<TransportFailure.BodyTooLarge>(rig.client.call(countingEndpoint(decoded)).exceptionOrNull())

            assertEquals(10L, failure.limit)
            assertEquals(0, decoded.get())
            assertEquals(listOf("complete()"), rig.budget.calls)                 // the permit is left spent
        }

    @Test
    fun `a decoder Error propagates to the caller`() = rigged(respondWith(200, "body")) { rig ->
        val boom = Boom()
        val endpoint = Endpoint<String>("hero", { throw boom })

        val thrown = assertFailsWith<Boom> { rig.client.call(endpoint) }

        assertSame(boom, thrown)
        assertEquals(listOf("complete(signals)", "complete()"), rig.budget.calls)
    }
}
