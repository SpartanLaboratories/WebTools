package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.PermitRecordingBudget
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.clientOn
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.testing.support.webtools.http.respondWith
import com.spartanlabs.testing.support.webtools.http.stringEndpoint
import com.spartanlabs.webtools.http.CacheStats
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.UpstreamClient
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `UpstreamClient` with a cache and a recorded budget on a virtual clock: a call within `ttl` is served from the cache with no permit and
 * no transport call, and one after `ttl` or older than `maxAge` loads again; the key is the path and the query pairs whatever their order, never a
 * header, the lane or `maxWait`; a non-`GET` bypasses the cache; N concurrent `GET`s of one key cost one permit and one transport call; a zero
 * `ttl` stores nothing but coalesces calls in flight; a failure is never stored.
 */
class UpstreamClientCachingComponentTest {

    // What a test needs to drive one client: the clock, the fake transport, the recorded budget and the client.
    private class Rig(val clock: VirtualClock, val fake: FakeHttpTransport, val budget: PermitRecordingBudget, val client: UpstreamClient)

    // A client with a cache on the clock and a recorded budget of 100 a minute; the fake answers every request with the steps.
    private fun rigged(vararg steps: Step, block: suspend TestScope.(Rig) -> Unit): TestResult = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, *steps)
        cacheOn(clock).use { responseCache ->
            budgetOn(clock, minuteWindow(100)).use { real ->
                val recording = PermitRecordingBudget(real)
                clientOn(clock, fake) { cache(responseCache); budget(recording) }.use { client -> block(Rig(clock, fake, recording, client)) }
            }
        }
    }

    @Test
    fun `a second call within ttl is served from the cache with no permit and no transport call`() = rigged(respondWith(200, "x")) { rig ->
        val endpoint = stringEndpoint { ttl = 10.minutes }

        assertEquals(Lookup.Found("x"), rig.client.call(endpoint).getOrThrow())
        assertEquals(Lookup.Found("x"), rig.client.call(endpoint).getOrThrow())

        assertEquals(1, rig.fake.calls.size)
        assertEquals(1, rig.budget.permits.size)
        assertEquals(1L, rig.client.cacheStats().hits)
        assertEquals(1L, rig.client.cacheStats().misses)
    }

    @Test
    fun `a call after ttl loads again`() = rigged(respondWith(200, "x")) { rig ->
        val endpoint = stringEndpoint { ttl = 10.minutes }
        rig.client.call(endpoint).getOrThrow()
        rig.client.call(endpoint).getOrThrow()
        assertEquals(1, rig.fake.calls.size)

        rig.clock.advanceBy(11.minutes)
        assertEquals(Lookup.Found("x"), rig.client.call(endpoint).getOrThrow())

        assertEquals(2, rig.fake.calls.size)
        assertEquals(2, rig.budget.permits.size)
    }

    @Test
    fun `a maxAge older than the entry reloads`() = rigged(respondWith(200, "x")) { rig ->
        val endpoint = stringEndpoint { ttl = 10.minutes }
        rig.client.call(endpoint).getOrThrow()
        rig.clock.advanceBy(30.seconds)

        rig.client.call(endpoint) { maxAge = 10.seconds }.getOrThrow()          // the entry is 30 seconds old: too old for this call
        assertEquals(2, rig.fake.calls.size)

        rig.client.call(endpoint) { maxAge = 1.minutes }.getOrThrow()           // the reloaded entry is fresh
        assertEquals(2, rig.fake.calls.size)
    }

    @Test
    fun `query pair order does not change the key`() = rigged(respondWith(200, "x")) { rig ->
        val endpoint = stringEndpoint { ttl = 10.minutes }

        rig.client.call(endpoint) { query("a", "1"); query("b", "2") }.getOrThrow()
        rig.client.call(endpoint) { query("b", "2"); query("a", "1") }.getOrThrow()

        assertEquals(1, rig.fake.calls.size)
    }

    @Test
    fun `a different query value is a different key`() = rigged(respondWith(200, "x")) { rig ->
        val endpoint = stringEndpoint { ttl = 10.minutes }

        rig.client.call(endpoint) { query("a", "1") }.getOrThrow()
        rig.client.call(endpoint) { query("a", "2") }.getOrThrow()
        assertEquals(2, rig.fake.calls.size)

        // A different path likewise, and each key is then served from the cache.
        rig.client.call(endpoint) { path("p"); query("a", "1") }.getOrThrow()
        assertEquals(3, rig.fake.calls.size)
        rig.client.call(endpoint) { query("a", "1") }.getOrThrow()
        rig.client.call(endpoint) { query("a", "2") }.getOrThrow()
        rig.client.call(endpoint) { path("p"); query("a", "1") }.getOrThrow()
        assertEquals(3, rig.fake.calls.size)
    }

    @Test
    fun `headers, lane and maxWait are not part of the key`() = rigged(respondWith(200, "x")) { rig ->
        val endpoint = stringEndpoint { ttl = 10.minutes }

        rig.client.call(endpoint) { query("a", "1") }.getOrThrow()
        rig.client.call(endpoint) {
            query("a", "1")
            headers { add("X-Trace", "2") }
            lane = Lane.Tracker
            maxWait = 1.seconds
        }.getOrThrow()

        assertEquals(1, rig.fake.calls.size)
        assertEquals(1, rig.budget.permits.size)
    }

    @Test
    fun `a non-GET bypasses the cache`() = rigged(respondWith(200, "x")) { rig ->
        val endpoint = stringEndpoint { ttl = 10.minutes }

        repeat(2) { assertEquals(Lookup.Found("x"), rig.client.call(endpoint) { method = HttpMethod.Post }.getOrThrow()) }

        assertEquals(2, rig.fake.calls.size)
        assertEquals(2, rig.budget.permits.size)
        assertEquals(CacheStats.Empty, rig.client.cacheStats())
    }

    @Test
    fun `N concurrent GETs of one key cost one permit and one transport call`() = rigged(Step.respond(200, body = "x", delay = 1.seconds)) { rig ->
        val endpoint = stringEndpoint { ttl = 10.minutes }

        val calls = List(8) { async { rig.client.call(endpoint) } }
        rig.clock.advanceBy(2.seconds)

        assertTrue(calls.all { it.isCompleted })
        calls.forEach { assertEquals(Lookup.Found("x"), it.await().getOrThrow()) }
        assertEquals(1, rig.fake.calls.size)
        assertEquals(1, rig.budget.permits.size)
        assertEquals(7L, rig.client.cacheStats().hits)
        assertEquals(1L, rig.client.cacheStats().misses)
    }

    @Test
    fun `a zero ttl stores nothing but coalesces calls in flight`() = rigged(Step.respond(200, body = "x", delay = 1.seconds)) { rig ->
        val endpoint = stringEndpoint()                                             // ttl is zero by default

        rig.client.call(endpoint).getOrThrow()
        rig.client.call(endpoint).getOrThrow()
        assertEquals(2, rig.fake.calls.size)

        val calls = List(2) { async { rig.client.call(endpoint) } }
        rig.clock.advanceBy(2.seconds)
        assertTrue(calls.all { it.isCompleted })
        calls.forEach { assertEquals(Lookup.Found("x"), it.await().getOrThrow()) }
        assertEquals(3, rig.fake.calls.size)
    }

    @Test
    fun `a failure is never stored`() = rigged(respondWith(503), respondWith(200, "ok")) { rig ->
        val endpoint = stringEndpoint { ttl = 10.minutes }

        val failure = assertIs<UpstreamFailure.UpstreamError>(rig.client.call(endpoint).exceptionOrNull())
        assertEquals(503, failure.status)
        assertEquals(Lookup.Found("ok"), rig.client.call(endpoint).getOrThrow())

        assertEquals(2, rig.fake.calls.size)
    }
}
