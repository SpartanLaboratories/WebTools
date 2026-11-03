package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoadLog
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.call
import com.spartanlabs.testing.support.webtools.http.key
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - a loader's failure on a virtual clock: a returned `Result.failure` and a thrown exception reach every waiter as the same instance; an
 * `HttpFailure` leaf keeps its instance; a failure is never stored (the next call reloads, nothing is held for the key once the waiters have
 * returned, a later success is stored); `failedLoads` is 1 for a failure shared by N waiters and `evictions` is unchanged; a failure after a
 * stored entry expired leaves nothing; a failure under a zero `ttl` behaves alike.
 */
class CaffeineResponseCacheFailureComponentTest {

    private val policy = CachePolicy(10.minutes)

    @Test
    fun `a returned failure reaches every waiter as the same instance`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val failure = IllegalStateException("returned")
            val waiters = List(5) { call(cache, key(), policy, loader = log.failing(failure, after = 1.seconds)) }
            clock.advanceBy(Duration.ZERO)
            clock.advanceBy(1.seconds)
            assertTrue(waiters.all { it.isCompleted })
            waiters.forEach { assertSame(failure, it.failure) }
            assertEquals(1, log.loads)
            assertEquals(1L, cache.stats().failedLoads)
            assertEquals(0L, cache.stats().evictions)
        }
    }

    @Test
    fun `a thrown exception reaches every waiter as the same instance`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val thrown = IllegalStateException("thrown")
            val waiters = List(5) { call(cache, key(), policy, loader = log.throwing(thrown, after = 1.seconds)) }
            clock.advanceBy(Duration.ZERO)
            clock.advanceBy(1.seconds)
            waiters.forEach { assertSame(thrown, it.failure) }
            assertEquals(1L, cache.stats().failedLoads)
        }
    }

    @Test
    fun `an HttpFailure leaf keeps its instance`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val timeout = TransportFailure.Timeout()
            assertSame(timeout, cache.getOrLoad(key(), policy, log.failing(timeout)).exceptionOrNull())
        }
    }

    @Test
    fun `a failure is never stored and a later success is`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val failure = IllegalStateException("x")
            assertSame(failure, cache.getOrLoad(key(), policy, log.failing(failure)).exceptionOrNull())
            assertNull(cache.futureOf(key()), "the failure is still held")
            assertEquals(Lookup.Found("b"), cache.getOrLoad(key(), policy, log.loader("b")).getOrThrow())
            assertEquals(2, log.loads)
            assertEquals(Lookup.Found("b"), cache.getOrLoad(key(), policy, log.loader("c")).getOrThrow())
            assertEquals(2, log.loads)
            assertEquals(1L, cache.stats().failedLoads)
        }
    }

    @Test
    fun `a failure after a stored entry expired leaves nothing`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key(), policy, log.loader("a"))
            clock.advanceBy(11.minutes)
            val failure = IllegalStateException("x")
            assertSame(failure, cache.getOrLoad(key(), policy, log.failing(failure)).exceptionOrNull())
            assertNull(cache.futureOf(key()))
            assertEquals(Lookup.Found("c"), cache.getOrLoad(key(), policy, log.loader("c")).getOrThrow())
            assertEquals(3, log.loads)
        }
    }

    @Test
    fun `a failure under a zero ttl behaves alike`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val zero = CachePolicy(Duration.ZERO)
            val failure = IllegalStateException("x")
            val waiters = List(3) { call(cache, key(), zero, loader = log.failing(failure, after = 1.seconds)) }
            clock.advanceBy(Duration.ZERO)
            clock.advanceBy(1.seconds)
            waiters.forEach { assertSame(failure, it.failure) }
            assertNull(cache.futureOf(key()))
            assertEquals(Lookup.Found("b"), cache.getOrLoad(key(), zero, log.loader("b")).getOrThrow())
            assertEquals(2, log.loads)
            assertEquals(1L, cache.stats().failedLoads)
        }
    }
}
