package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.Boom
import com.spartanlabs.testing.support.webtools.http.LoadLog
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.call
import com.spartanlabs.testing.support.webtools.http.key
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CacheStats
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `stats()` on a virtual clock: a miss, a hit and a join count as 2 hits and 1 miss for three callers; a `maxAge` reload counts one miss
 * for the call, not a hit and a miss; `failedLoads` counts once per failed or fatal load; `evictions` counts the size bound's removals; `stats()`
 * returns an immutable snapshot; it is readable after `close()`. `Disabled` is covered by its own class.
 */
class CaffeineResponseCacheStatsComponentTest {

    private val policy = CachePolicy(10.minutes)

    @Test
    fun `a miss a hit and a join count as two hits and one miss`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val starter = call(cache, key(), policy, loader = log.loader("v", after = 1.seconds))
            clock.advanceBy(Duration.ZERO)
            val joiner = call(cache, key(), policy, loader = log.loader("x"))
            clock.advanceBy(Duration.ZERO)
            clock.advanceBy(1.seconds)
            assertEquals("v", starter.value)
            assertEquals("v", joiner.value)
            cache.getOrLoad(key(), policy, log.loader("y"))
            assertEquals(CacheStats(2L, 1L, 0L, 0L), cache.stats())
        }
    }

    @Test
    fun `a maxAge reload counts one miss for the call`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key(), policy, log.loader("old"))
            clock.advanceBy(5.minutes)
            cache.getOrLoad(key(), policy, 1.minutes, log.loader("new"))
            assertEquals(CacheStats(0L, 2L, 0L, 0L), cache.stats())
        }
    }

    @Test
    fun `failedLoads counts once per failed or fatal load`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val waiters = List(3) { call(cache, key(), policy, loader = log.failing(IllegalStateException("x"), after = 1.seconds)) }
            clock.advanceBy(Duration.ZERO)
            clock.advanceBy(1.seconds)
            assertTrue(waiters.all { it.isCompleted })
            assertEquals(1L, cache.stats().failedLoads)

            val boom = Boom()
            val fatal = call(cache, key(), policy, loader = log.throwing(boom))
            clock.advanceBy(Duration.ZERO)
            assertSame(boom, fatal.thrown)
            assertEquals(2L, cache.stats().failedLoads)
            assertEquals(CacheStats(2L, 2L, 0L, 2L), cache.stats())
        }
    }

    @Test
    fun `evictions counts the size bound's removals`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock) { maxWeight = 2 }.use { cache ->
            repeat(3) { i -> cache.getOrLoad(key("k$i"), policy, log.loader("v$i")) }
            cache.cleanUp()
            assertEquals(3L - cache.weightedSize(), cache.stats().evictions)
            assertTrue(cache.stats().evictions >= 1L)
        }
    }

    @Test
    fun `stats returns an immutable snapshot and is readable after close`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val before = cache.stats()
            assertEquals(CacheStats.Empty, before)
            cache.getOrLoad(key(), policy, log.loader("a"))
            cache.getOrLoad(key(), policy, log.loader("b"))
            assertEquals(CacheStats.Empty, before, "an earlier reading changed")
            assertEquals(CacheStats(1L, 1L, 0L, 0L), cache.stats())
            cache.close()
            assertEquals(CacheStats(1L, 1L, 0L, 0L), cache.stats())
        }
    }
}
