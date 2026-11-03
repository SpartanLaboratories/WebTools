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
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - hits and expiry of `CaffeineResponseCache` on a virtual clock: the first call loads; a call before the `ttl` is a hit returning the same
 * `Found` instance without running its loader; after the `ttl` the entry has expired and the loader runs again; the `ttl` runs from the load's
 * completion, not its start; keys equal after reordering their parameters share one entry and different keys load separately; an infinite `ttl`
 * survives a year; the three-argument overload serves any live entry.
 */
class CaffeineResponseCacheHitsComponentTest {

    private val policy = CachePolicy(10.minutes)

    @Test
    fun `the first call loads and a call before the ttl is a hit on the same instance`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val first = cache.getOrLoad(key(), policy, log.loader("a")).getOrThrow()
            assertEquals(Lookup.Found("a"), first)
            assertEquals(1, log.loads)
            assertEquals(CacheStats(0L, 1L, 0L, 0L), cache.stats())

            clock.advanceBy(10.minutes - 1.seconds)
            val second = cache.getOrLoad(key(), policy, log.loader("b")).getOrThrow()
            assertSame(first, second)
            assertEquals(1, log.loads)
            assertEquals(CacheStats(1L, 1L, 0L, 0L), cache.stats())
        }
    }

    @Test
    fun `after the ttl the entry has expired and the loader runs again`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key(), policy, log.loader("a"))
            clock.advanceBy(10.minutes + 1.seconds)
            assertEquals(Lookup.Found("b"), cache.getOrLoad(key(), policy, log.loader("b")).getOrThrow())
            assertEquals(2, log.loads)
            assertEquals(CacheStats(0L, 2L, 0L, 0L), cache.stats())
        }
    }

    @Test
    fun `the ttl runs from the load's completion`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val waiter = call(cache, key(), policy, loader = log.loader("a", after = 5.minutes))
            clock.advanceBy(Duration.ZERO)
            assertFalse(waiter.isCompleted)
            assertEquals(1, log.loads)
            clock.advanceBy(5.minutes)
            assertTrue(waiter.isCompleted)
            assertEquals("a", waiter.value)

            clock.advanceBy(9.minutes + 50.seconds)
            assertEquals(Lookup.Found("a"), cache.getOrLoad(key(), policy, log.loader("b")).getOrThrow(), "not a hit 9 minutes 50 seconds after completion")
            assertEquals(1, log.loads)

            clock.advanceBy(20.seconds)
            assertEquals(Lookup.Found("c"), cache.getOrLoad(key(), policy, log.loader("c")).getOrThrow(), "not a reload 10 minutes 10 seconds after completion")
            assertEquals(2, log.loads)
        }
    }

    @Test
    fun `keys equal after reordering their parameters share an entry and different keys load separately`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key("e", "a" to "1", "b" to "2"), policy, log.loader("a"))
            assertEquals(Lookup.Found("a"), cache.getOrLoad(key("e", "b" to "2", "a" to "1"), policy, log.loader("x")).getOrThrow())
            assertEquals(1, log.loads)
            assertEquals(Lookup.Found("c"), cache.getOrLoad(key("e", "a" to "1", "b" to "3"), policy, log.loader("c")).getOrThrow())
            assertEquals(2, log.loads)
            assertEquals(Lookup.Found("d"), cache.getOrLoad(key("f", "a" to "1", "b" to "2"), policy, log.loader("d")).getOrThrow())
            assertEquals(3, log.loads)
            assertEquals(CacheStats(1L, 3L, 0L, 0L), cache.stats())
        }
    }

    @Test
    fun `an infinite ttl survives a year`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val forever = CachePolicy(Duration.INFINITE)
            cache.getOrLoad(key(), forever, log.loader("a"))
            clock.advanceBy(365.days)
            assertEquals(Lookup.Found("a"), cache.getOrLoad(key(), forever, log.loader("b")).getOrThrow())
            assertEquals(1, log.loads)
        }
    }

    @Test
    fun `the three-argument overload serves any live entry`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key(), policy, Duration.INFINITE, log.loader("a"))
            clock.advanceBy(9.minutes)
            assertEquals(Lookup.Found("a"), cache.getOrLoad(key(), policy, log.loader("b")).getOrThrow())
            assertEquals(1, log.loads)
            assertEquals(CacheStats(1L, 1L, 0L, 0L), cache.stats())
        }
    }
}
