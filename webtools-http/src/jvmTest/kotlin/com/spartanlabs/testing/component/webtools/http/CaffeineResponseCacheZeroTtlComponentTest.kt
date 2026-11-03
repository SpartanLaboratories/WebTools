package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoadLog
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.call
import com.spartanlabs.testing.support.webtools.http.key
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CacheStats
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - a zero `ttl` on a virtual clock: two sequential calls run two loads; 20 concurrent callers share one load and all receive the value;
 * a call after the load completed, at the same virtual instant, starts a new load (the completed lifetime-0 entry is treated as absent); nothing
 * is held for the key once the waiters have returned, the weighted size is 0 and no eviction is counted.
 */
class CaffeineResponseCacheZeroTtlComponentTest {

    private val zero = CachePolicy(Duration.ZERO)

    @Test
    fun `two sequential calls run two loads`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            assertEquals(Lookup.Found("a"), cache.getOrLoad(key(), zero, log.loader("a")).getOrThrow())
            assertEquals(Lookup.Found("b"), cache.getOrLoad(key(), zero, log.loader("b")).getOrThrow())
            assertEquals(2, log.loads)
            assertEquals(CacheStats(0L, 2L, 0L, 0L), cache.stats())
        }
    }

    @Test
    fun `twenty concurrent callers share one load and all receive the value`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val waiters = List(20) { call(cache, key(), zero, loader = log.loader("v", after = 1.seconds)) }
            clock.advanceBy(Duration.ZERO)
            assertEquals(1, log.loads)
            assertTrue(waiters.none { it.isCompleted })
            clock.advanceBy(1.seconds)
            assertTrue(waiters.all { it.isCompleted })
            waiters.forEach { assertEquals("v", it.value) }
            assertEquals(1, log.loads)
            assertEquals(CacheStats(19L, 1L, 0L, 0L), cache.stats())
        }
    }

    @Test
    fun `a call after completion at the same instant starts a new load and nothing is held`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val waiter = call(cache, key(), zero, loader = log.loader("a", after = 1.seconds))
            clock.advanceBy(Duration.ZERO)
            clock.advanceBy(1.seconds)
            assertTrue(waiter.isCompleted)
            assertEquals("a", waiter.value)
            assertNull(cache.futureOf(key()), "the completed zero-ttl entry is still held")

            assertEquals(Lookup.Found("b"), cache.getOrLoad(key(), zero, log.loader("b")).getOrThrow())
            assertEquals(2, log.loads)
            assertNull(cache.futureOf(key()))
            cache.cleanUp()
            assertEquals(0L, cache.weightedSize())
            assertEquals(0L, cache.stats().evictions)
        }
    }
}
