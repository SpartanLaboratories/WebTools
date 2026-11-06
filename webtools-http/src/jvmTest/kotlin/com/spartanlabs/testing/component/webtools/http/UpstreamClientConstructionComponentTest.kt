package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.SteppableWallClock
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.clientOn
import com.spartanlabs.testing.support.webtools.http.respondWith
import com.spartanlabs.testing.support.webtools.http.stringEndpoint
import com.spartanlabs.webtools.http.BudgetUtilisation
import com.spartanlabs.webtools.http.CacheStats
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.RateSignalsReader
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - what the common `UpstreamClient` factory builds: an invalid window list is refused with `IllegalArgumentException` and nothing is built;
 * a budget built from `budget(windows)` runs on the client's clocks, and an explicit assignment in its block wins; without `cache(...)` the client
 * uses `ResponseCache.Disabled` and without a budget `CallBudget.Unlimited`; the configured signal reader decides what a throttle is.
 */
class UpstreamClientConstructionComponentTest {

    private val windows: List<RateWindow> = listOf(RateWindow("minute", 60, 1.minutes))

    // A provider's reader: a 418 is its throttle. The `@OptIn` is the line a consumer's own reader needs.
    @OptIn(ExperimentalForInheritanceWebToolsApi::class)
    private class TeapotReader : RateSignalsReader {
        override fun read(response: HttpResponse, nowEpochMillis: Long): RateSignals =
            if (response.status == 418) RateSignals { throttled = true } else RateSignals.None
    }

    @Test
    fun `an invalid window list is refused by the factory with IllegalArgumentException`() {
        val events = captureTransportLogs {
            runTest {
                val clock = virtualClock()
                assertFailsWith<IllegalArgumentException> { clientOn(clock, FakeHttpTransport(clock.timeSource)) { budget(emptyList()) } }
            }
        }.second

        // Nothing was built: neither a budget nor a client logged its creation.
        assertFalse(events.any { it.formattedMessage.startsWith("created") }, events.map { it.loggerName + " " + it.formattedMessage }.toString())
    }

    @Test
    fun `budget(windows) is seeded with the client's clocks`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource)
            .on(RequestMatch.Any, Step.respond(429, HttpHeaders { add("Retry-After", "30") }), respondWith(200, "ok"))
        clientOn(clock, fake) { budget(windows) }.use { client ->
            assertIs<UpstreamFailure.RateLimited>(client.call(stringEndpoint()).exceptionOrNull())
            val paused = assertIs<HttpFailure.BudgetExhausted>(client.call(stringEndpoint()).exceptionOrNull())
            assertEquals(ExhaustedReason.Paused, paused.reason)

            clock.advanceBy(31.seconds)                                           // the pause ends by the virtual clock

            assertEquals(Lookup.Found("ok"), client.call(stringEndpoint()).getOrThrow())
            assertEquals(2, fake.calls.size)
        }
    }

    @Test
    fun `an explicit assignment in budget(windows) wins`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "ok"))
        val steppable = SteppableWallClock(clock.wallClock)
        steppable.stepBy(10.days)                                                 // far from the client's own wall clock
        clientOn(clock, fake) { budget(windows) { wallClock = steppable } }.use { client ->
            client.call(stringEndpoint()).getOrThrow()

            // The call was counted in the minute the steppable clock is in.
            val window = client.utilisation().windows.single()
            assertEquals(1, window.used)
            assertEquals(VirtualClock.DEFAULT_START_EPOCH_MILLIS + 10.days.inWholeMilliseconds, window.windowStartEpochMillis)
        }
    }

    @Test
    fun `without a cache the client uses Disabled`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "ok"))
        clientOn(clock, fake).use { client ->
            val endpoint = stringEndpoint { ttl = 10.minutes }

            repeat(2) { assertEquals(Lookup.Found("ok"), client.call(endpoint).getOrThrow()) }

            assertEquals(2, fake.calls.size)
            assertEquals(CacheStats.Empty, client.cacheStats())
        }
    }

    @Test
    fun `without a budget the client uses Unlimited`() = runTest {
        val clock = virtualClock()
        clientOn(clock, FakeHttpTransport(clock.timeSource)).use { client ->
            assertEquals(BudgetUtilisation(emptyList(), Duration.ZERO, 0), client.utilisation())
        }
    }

    @Test
    fun `the client's rateSignals reader is used`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(418))
        clientOn(clock, fake) { rateSignals = TeapotReader() }.use { client ->
            val failure = assertIs<UpstreamFailure.RateLimited>(client.call(stringEndpoint()).exceptionOrNull())

            assertEquals(418, failure.status)
            assertEquals(Duration.ZERO, failure.retryAfter)
        }
    }
}
