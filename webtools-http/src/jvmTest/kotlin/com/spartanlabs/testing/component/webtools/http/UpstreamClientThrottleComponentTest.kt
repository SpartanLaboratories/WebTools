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
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - a throttle through `UpstreamClient`, on a virtual clock that starts at a UTC midnight and, unless a test says otherwise, a budget the
 * client builds from one minute window (the default one-second guard): a `429` (or a `503` with `Retry-After`) is `RateLimited`, pauses the budget
 * and is sent exactly once; the next call fails fast with `BudgetExhausted(Paused)` and a call after the pause proceeds; `retryAfter` is the longer
 * of the server's `Retry-After` and the budget's pause (the pause when the server names none, the server's value above `maxPause`, an earlier longer
 * pause, zero under `CallBudget.Unlimited`); two coalesced callers receive one failure instance; the permit is completed with the signals.
 */
class UpstreamClientThrottleComponentTest {

    private val windows: List<RateWindow> = listOf(RateWindow("minute", 60, 1.minutes))

    private fun retryAfter(value: String): HttpHeaders = HttpHeaders { add("Retry-After", value) }

    private fun limited(result: Result<*>): UpstreamFailure.RateLimited = assertIs<UpstreamFailure.RateLimited>(result.exceptionOrNull())

    @Test
    fun `a 429 with Retry-After in seconds pauses the budget and returns RateLimited`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond(429, retryAfter("30")))
        clientOn(clock, fake) { budget(windows) }.use { client ->
            val failure = limited(client.call(stringEndpoint()))

            assertEquals(429, failure.status)
            assertEquals(30.seconds, failure.retryAfter)
            assertEquals(30.seconds, client.utilisation().pausedFor)
        }
    }

    @Test
    fun `a 429 with Retry-After as an HTTP date`() = runTest {
        val clock = virtualClock()                                                  // 2026-01-01T00:00:00Z
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond(429, retryAfter("Thu, 01 Jan 2026 00:00:45 GMT")))
        clientOn(clock, fake) { budget(windows) }.use { client ->
            val failure = limited(client.call(stringEndpoint()))

            assertEquals(45.seconds, failure.retryAfter)
            assertEquals(45.seconds, client.utilisation().pausedFor)
        }
    }

    @Test
    fun `the next call fails fast with BudgetExhausted Paused and never reaches the transport`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond(429, retryAfter("30")))
        clientOn(clock, fake) { budget(windows) }.use { client ->
            limited(client.call(stringEndpoint()))

            val exhausted = assertIs<HttpFailure.BudgetExhausted>(client.call(stringEndpoint()) { query("n", "2") }.exceptionOrNull())

            assertEquals(ExhaustedReason.Paused, exhausted.reason)
            assertEquals(30.seconds, exhausted.wait)
            assertEquals(1, fake.calls.size)
        }
    }

    @Test
    fun `after the pause a call proceeds`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond(429, retryAfter("30")), respondWith(200, "ok"))
        clientOn(clock, fake) { budget(windows) }.use { client ->
            limited(client.call(stringEndpoint()))
            clock.advanceBy(31.seconds)

            assertEquals(Lookup.Found("ok"), client.call(stringEndpoint()).getOrThrow())
            assertEquals(2, fake.calls.size)
        }
    }

    @Test
    fun `the throttled request is sent exactly once`() = runTest {
        val clock = virtualClock()
        // A second request would be answered with a 200: a retry would turn the failure into a value.
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond(429, retryAfter("30")), respondWith(200, "ok"))
        clientOn(clock, fake) { budget(windows) }.use { client ->
            limited(client.call(stringEndpoint()))

            assertEquals(1, fake.calls.size)
        }
    }

    @Test
    fun `a 429 with no Retry-After under a windowed budget reports the budget's pause`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(429))
        clientOn(clock, fake) { budget(windows) }.use { client ->
            val failure = limited(client.call(stringEndpoint()))

            // The minute window's next boundary from the midnight start, plus the one-second guard.
            assertEquals(61.seconds, failure.retryAfter)
            assertEquals(failure.retryAfter, client.utilisation().pausedFor)
        }
    }

    @Test
    fun `a Retry-After above maxPause reports the server's value`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond(429, retryAfter("7200")))
        clientOn(clock, fake) { budget(windows) { maxPause = 1.hours } }.use { client ->
            val failure = limited(client.call(stringEndpoint()))

            assertEquals(2.hours, failure.retryAfter)
            assertEquals(1.hours, client.utilisation().pausedFor)
        }
    }

    @Test
    fun `a longer earlier pause is reported`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond(429, retryAfter("5"), delay = 1.seconds))
        budgetOn(clock, minuteWindow(10)).use { supplied ->
            clientOn(clock, fake) { budget(supplied) }.use { client ->
                val call = async { client.call(stringEndpoint()) }
                clock.advanceBy(Duration.ZERO)                                      // the call holds its permit and waits in the fake
                assertEquals(1, fake.calls.size)

                // Another call's answer puts a 100-second pause in force meanwhile.
                val other = supplied.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
                other.complete(RateSignals { throttled = true; retryAfter = 100.seconds })

                clock.advanceBy(1.seconds)                                          // delivers the 429 with its 5 seconds
                assertTrue(call.isCompleted)
                val failure = limited(call.await())
                assertEquals(99.seconds, failure.retryAfter)                        // the pause remaining, longer than the server's value
                assertEquals(failure.retryAfter, supplied.utilisation().pausedFor)
            }
        }
    }

    @Test
    fun `a 503 with Retry-After is RateLimited with status 503`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond(503, retryAfter("10")))
        clientOn(clock, fake) { budget(windows) }.use { client ->
            val failure = limited(client.call(stringEndpoint()))

            assertEquals(503, failure.status)
            assertEquals(10.seconds, failure.retryAfter)
        }
    }

    @Test
    fun `a 503 without Retry-After is UpstreamError`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(503))
        clientOn(clock, fake) { budget(windows) }.use { client ->
            val failure = assertIs<UpstreamFailure.UpstreamError>(client.call(stringEndpoint()).exceptionOrNull())

            assertEquals(503, failure.status)
            assertEquals(Duration.ZERO, client.utilisation().pausedFor)
        }
    }

    @Test
    fun `under CallBudget Unlimited a 429 without Retry-After reports zero`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(429), Step.respond(429, retryAfter("30")))
        clientOn(clock, fake).use { client ->
            assertEquals(Duration.ZERO, limited(client.call(stringEndpoint())).retryAfter)
            assertEquals(30.seconds, limited(client.call(stringEndpoint())).retryAfter)
            assertEquals(2, fake.calls.size)
        }
    }

    @Test
    fun `two coalesced callers receive one RateLimited instance`() {
        val events = captureTransportLogs {
            runTest {
                val clock = virtualClock()
                val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond(429, retryAfter("30"), delay = 1.seconds))
                cacheOn(clock).use { responseCache ->
                    clientOn(clock, fake) { cache(responseCache); budget(windows) }.use { client ->
                        val endpoint = stringEndpoint { ttl = 10.minutes }

                        val calls = List(2) { async { client.call(endpoint) } }
                        clock.advanceBy(2.seconds)

                        assertTrue(calls.all { it.isCompleted })
                        assertSame(limited(calls[0].await()), limited(calls[1].await()))
                        assertEquals(1, fake.calls.size)
                    }
                }
            }
        }.second

        val warns = facadeEvents(events, "UpstreamClient").filter { it.level == Level.WARN }.map { it.formattedMessage }
        assertEquals(listOf("throttled: subject=hero, status=429, retryAfter=30s"), warns)
    }

    @Test
    fun `a throttle completes the permit with the signals`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond(429, retryAfter("30")))
        budgetOn(clock, minuteWindow(10)).use { real ->
            val recording = PermitRecordingBudget(real)
            clientOn(clock, fake) { budget(recording) }.use { client ->
                limited(client.call(stringEndpoint()))

                assertEquals(listOf("complete(signals)", "complete()"), recording.calls)
                assertEquals(30.seconds, real.utilisation().pausedFor)
            }
        }
    }
}
