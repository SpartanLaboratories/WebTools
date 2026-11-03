package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.Boom
import com.spartanlabs.testing.support.webtools.http.LoadLog
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.call
import com.spartanlabs.testing.support.webtools.http.key
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.UnmatchedRequestError
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - a loader's `Error` on a virtual clock: a loader that throws `Boom` makes two waiters each catch the thrown instance inside their
 * coroutine, and the two are the same `Boom`; `runTest` completes normally (nothing reached the exception collector); the entry is gone and a third
 * call reloads; `failedLoads` counts once per load; an `UnmatchedRequestError` from the fake transport behaves as `Boom`; the cache stays open.
 */
class CaffeineResponseCacheFatalErrorComponentTest {

    private val policy = CachePolicy(10.minutes)

    @Test
    fun `a loader's Error reaches every waiter as the same instance and the cache stays open`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val boom = Boom()
            val first = call(cache, key(), policy, loader = log.throwing(boom, after = 1.seconds))
            val second = call(cache, key(), policy, loader = log.loader("x"))
            clock.advanceBy(Duration.ZERO)
            clock.advanceBy(1.seconds)
            assertTrue(first.isCompleted && second.isCompleted)
            assertFalse(first.isCancelled || second.isCancelled)
            assertSame(boom, first.thrown)
            assertSame(boom, second.thrown)
            assertNull(first.result)
            assertNull(cache.futureOf(key()), "the fatal entry is still held")
            assertEquals(1L, cache.stats().failedLoads)

            assertEquals(Lookup.Found("c"), cache.getOrLoad(key(), policy, log.loader("c")).getOrThrow())
            assertEquals(2, log.loads)
            assertEquals(1L, cache.stats().failedLoads)
            assertEquals("CaffeineResponseCache(maxWeight=1000, closed=false)", cache.toString())
        }
    }

    @Test
    fun `an UnmatchedRequestError from the fake transport behaves as Boom`() = runTest {
        val clock = virtualClock()
        cacheOn(clock).use { cache ->
            val fake = FakeHttpTransport()
            val request = HttpRequest(HttpMethod.Get, Url.parse("http://example.test/players").getOrThrow())
            val waiter = call(cache, key(), policy) { fake.execute(request).map { Lookup.Found(it.status.toString()) } }
            clock.advanceBy(Duration.ZERO)
            assertTrue(waiter.isCompleted)
            assertIs<UnmatchedRequestError>(waiter.thrown)
            assertNull(cache.futureOf(key()))
            assertEquals(1L, cache.stats().failedLoads)
            assertEquals(Lookup.Found("c"), cache.getOrLoad(key(), policy) { Result.success(Lookup.Found("c")) }.getOrThrow())
        }
    }
}
