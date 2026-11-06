package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.FETCH_URL
import com.spartanlabs.testing.support.webtools.http.PermitRecordingBudget
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.fetcherOn
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.testing.support.webtools.http.respondWith
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - what `UrlFetcher.fetch` returns for a response, over the fake transport and a recorded budget: every status is the response (a `200`
 * as the very instance the transport produced, a `304` with its headers, a `404`, a `500` and a returned `3xx`), except a throttle, which is
 * `RateLimited` with the longer of `Retry-After` and the budget's pause (a `429`; a `503` only with `Retry-After`); a transport failure is returned
 * with the permit left spent, and an `InvalidRequest` from the transport releases the permit.
 */
class UrlFetcherResponseComponentTest {

    private val request = HttpRequest(HttpMethod.Get, FETCH_URL)

    @Test
    fun `a 200 is returned as the response`() = runTest {
        val clock = virtualClock()
        val produced = HttpResponse(200, FETCH_URL) { body("hello".encodeToByteArray()) }
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond { produced })
        budgetOn(clock, minuteWindow(10)).use { real ->
            val recording = PermitRecordingBudget(real)
            fetcherOn(clock, fake) { budget = recording }.use { fetcher ->
                val response = fetcher.fetch(request).getOrThrow()

                assertSame(produced, response)
                assertEquals(200, response.status)
                assertEquals("hello", response.body().decodeToString())
                assertEquals(listOf("complete(signals)", "complete()"), recording.calls)
                assertEquals(1, fake.calls.size)
            }
        }
    }

    @Test
    fun `a 304 is the response`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond(304, HttpHeaders { add("ETag", "\"v\"") }))
        budgetOn(clock, minuteWindow(10)).use { real ->
            fetcherOn(clock, fake) { budget = PermitRecordingBudget(real) }.use { fetcher ->
                val response = fetcher.fetch(request).getOrThrow()

                assertEquals(304, response.status)
                assertEquals("\"v\"", response.headers["ETag"])
                assertEquals(0, response.bodySize)
            }
        }
    }

    @Test
    fun `a 404 and a 500 are the response`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(404, "nope"), respondWith(500, "broken"))
        budgetOn(clock, minuteWindow(10)).use { real ->
            fetcherOn(clock, fake) { budget = PermitRecordingBudget(real) }.use { fetcher ->
                val notFound = fetcher.fetch(request).getOrThrow()
                assertEquals(404, notFound.status)
                assertEquals("nope", notFound.body().decodeToString())

                val serverError = fetcher.fetch(request).getOrThrow()
                assertEquals(500, serverError.status)
                assertEquals("broken", serverError.body().decodeToString())
                assertEquals(2, fake.calls.size)
            }
        }
    }

    @Test
    fun `a returned 3xx is the response`() = runTest {
        val clock = virtualClock()
        // The fake follows no redirect, as the transport does not for a redirect it cannot follow.
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond(302, HttpHeaders { add("Location", "https://files.example.test/b") }))
        budgetOn(clock, minuteWindow(10)).use { real ->
            fetcherOn(clock, fake) { budget = PermitRecordingBudget(real) }.use { fetcher ->
                val response = fetcher.fetch(request).getOrThrow()

                assertEquals(302, response.status)
                assertEquals("https://files.example.test/b", response.headers["Location"])
                assertEquals(1, fake.calls.size)
            }
        }
    }

    @Test
    fun `a 429 is RateLimited with the longer of Retry-After and the pause`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond(429, HttpHeaders { add("Retry-After", "30") }))
        budgetOn(clock, minuteWindow(10)).use { real ->
            val recording = PermitRecordingBudget(real)
            fetcherOn(clock, fake) { budget = recording }.use { fetcher ->
                val limited = assertIs<UpstreamFailure.RateLimited>(fetcher.fetch(request).exceptionOrNull())
                assertEquals(429, limited.status)
                assertEquals(30.seconds, limited.retryAfter)
                assertEquals(30.seconds, real.utilisation().pausedFor)
                assertEquals(listOf("complete(signals)", "complete()"), recording.calls)

                // The budget has paused: the next fetch fails fast and never reaches the transport.
                val exhausted = assertIs<HttpFailure.BudgetExhausted>(fetcher.fetch(request).exceptionOrNull())
                assertEquals(ExhaustedReason.Paused, exhausted.reason)
                assertEquals(30.seconds, exhausted.wait)
                assertEquals(1, fake.calls.size)
                assertEquals(1, recording.permits.size)
            }
        }
    }

    @Test
    fun `a 503 with Retry-After is RateLimited and without it is the response`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource)
            .on(RequestMatch.Any, Step.respond(503, HttpHeaders { add("Retry-After", "10") }), respondWith(503, "down"))
        budgetOn(clock, minuteWindow(10)).use { real ->
            fetcherOn(clock, fake) { budget = PermitRecordingBudget(real) }.use { fetcher ->
                val limited = assertIs<UpstreamFailure.RateLimited>(fetcher.fetch(request).exceptionOrNull())
                assertEquals(503, limited.status)
                assertEquals(10.seconds, limited.retryAfter)

                clock.advanceBy(11.seconds)                          // past the pause the first answer started
                val response = fetcher.fetch(request).getOrThrow()
                assertEquals(503, response.status)
                assertEquals("down", response.body().decodeToString())
                assertEquals(2, fake.calls.size)
            }
        }
    }

    @Test
    fun `a transport failure is returned and the permit stays spent`() = runTest {
        val clock = virtualClock()
        val timeout = TransportFailure.Timeout()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.fail(timeout))
        budgetOn(clock, minuteWindow(10)).use { real ->
            val recording = PermitRecordingBudget(real)
            fetcherOn(clock, fake) { budget = recording }.use { fetcher ->
                assertSame(timeout, fetcher.fetch(request).exceptionOrNull())
                assertEquals(listOf("complete()"), recording.calls)
                assertEquals(1, real.utilisation().windows.single().used)
            }
        }
    }

    @Test
    fun `InvalidRequest from the transport releases the permit`() = runTest {
        val clock = virtualClock()
        val invalid = TransportFailure.InvalidRequest("x")
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.fail(invalid))
        budgetOn(clock, minuteWindow(10)).use { real ->
            val recording = PermitRecordingBudget(real)
            fetcherOn(clock, fake) { budget = recording }.use { fetcher ->
                assertSame(invalid, fetcher.fetch(request).exceptionOrNull())
                assertEquals(listOf("release()", "complete()"), recording.calls)
                assertEquals(0, real.utilisation().windows.single().used)
            }
        }
    }
}
