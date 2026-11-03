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
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - coalescing on a virtual clock: 20 callers of one key share one load and one `Found` instance (19 hits, 1 miss); a joiner's loader never
 * runs; the shared load carries the first caller's loader (its value) and the first caller's policy (its `ttl` governs); different keys load
 * independently; a caller joining after the load completed and was stored is a hit.
 */
class CaffeineResponseCacheCoalescingComponentTest {

    private val policy = CachePolicy(10.minutes)

    @Test
    fun `twenty callers of one key share one load and one Found instance`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val waiters = List(20) { call(cache, key(), policy, loader = log.loader("v", after = 1.seconds)) }
            clock.advanceBy(Duration.ZERO)
            assertEquals(1, log.loads)
            clock.advanceBy(1.seconds)
            assertTrue(waiters.all { it.isCompleted })
            val shared = waiters[0].lookup
            assertEquals(Lookup.Found("v"), shared)
            waiters.forEach { assertSame(shared, it.lookup) }
            assertEquals(CacheStats(19L, 1L, 0L, 0L), cache.stats())
        }
    }

    @Test
    fun `a joiner's loader never runs and the shared load carries the first caller's loader`() = runTest {
        val clock = virtualClock()
        val first = LoadLog()
        val joiner = LoadLog()
        cacheOn(clock).use { cache ->
            val starter = call(cache, key(), policy, loader = first.loader("a", after = 1.seconds))
            clock.advanceBy(Duration.ZERO)
            val joined = call(cache, key(), policy, loader = joiner.loader("b"))
            clock.advanceBy(Duration.ZERO)
            clock.advanceBy(1.seconds)
            assertEquals("a", starter.value)
            assertEquals("a", joined.value)
            assertEquals(1, first.loads)
            assertEquals(0, joiner.loads)
        }
    }

    @Test
    fun `the shared load carries the first caller's policy`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val oneMinute = CachePolicy(1.minutes)
            val starter = call(cache, key(), oneMinute, loader = log.loader("a", after = 1.seconds))
            clock.advanceBy(Duration.ZERO)
            val joined = call(cache, key(), policy, loader = log.loader("b"))
            clock.advanceBy(Duration.ZERO)
            clock.advanceBy(1.seconds)
            assertEquals("a", starter.value)
            assertEquals("a", joined.value)

            clock.advanceBy(2.minutes)
            assertEquals(Lookup.Found("c"), cache.getOrLoad(key(), policy, log.loader("c")).getOrThrow(), "the joiner's 10-minute ttl governed")
            assertEquals(2, log.loads)
        }
    }

    @Test
    fun `different keys load independently`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val one = call(cache, key("one"), policy, loader = log.loader("a", after = 1.seconds))
            val other = call(cache, key("other"), policy, loader = log.loader("b", after = 1.seconds))
            clock.advanceBy(Duration.ZERO)
            assertEquals(2, log.loads)
            clock.advanceBy(1.seconds)
            assertEquals("a", one.value)
            assertEquals("b", other.value)
            assertEquals(CacheStats(0L, 2L, 0L, 0L), cache.stats())
        }
    }

    @Test
    fun `a caller joining after the load completed and was stored is a hit`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val starter = call(cache, key(), policy, loader = log.loader("a", after = 1.seconds))
            clock.advanceBy(Duration.ZERO)
            clock.advanceBy(1.seconds)
            assertEquals("a", starter.value)
            assertEquals(Lookup.Found("a"), cache.getOrLoad(key(), policy, log.loader("b")).getOrThrow())
            assertEquals(1, log.loads)
            assertEquals(CacheStats(1L, 1L, 0L, 0L), cache.stats())
        }
    }
}
