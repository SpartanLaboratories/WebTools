package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoadLog
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.key
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CaffeineResponseCache
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Level 2 - the weight bound on a virtual clock, with `maxWeight = 10` and the clock's same-thread maintenance executor: ten weight-1 entries fill
 * the cache without an eviction; an eleventh keeps the weighted size at most 10 and counts one eviction; a `Found` weighing exactly `maxWeight` is
 * stored and hit; a `Found` over `maxWeight` is delivered but never stored; a heavy entry among light ones keeps the bound; the bound holds after
 * each of 200 inserts of mixed weights 1 to 5; `evictions` counts size removals only (expiry, invalidation, a failed load and a zero-ttl entry
 * add nothing); a `Missing` weighs 1.
 */
class CaffeineResponseCacheWeightComponentTest {

    private val policy = CachePolicy(10.minutes)
    private val maxWeight = 10L

    private suspend fun fill(cache: CaffeineResponseCache, log: LoadLog, count: Int, from: Int = 0) {
        for (i in from until from + count) cache.getOrLoad(key("k$i"), policy, log.loader("v$i"))
    }

    @Test
    fun `ten weight-1 entries fill the cache without an eviction`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock) { maxWeight = this@CaffeineResponseCacheWeightComponentTest.maxWeight }.use { cache ->
            fill(cache, log, 10)
            cache.cleanUp()
            assertEquals(10L, cache.weightedSize())
            assertEquals(0L, cache.stats().evictions)
        }
    }

    @Test
    fun `an eleventh entry keeps the bound and counts one eviction`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock) { maxWeight = this@CaffeineResponseCacheWeightComponentTest.maxWeight }.use { cache ->
            fill(cache, log, 11)
            cache.cleanUp()
            val weightedSize = cache.weightedSize()
            assertTrue(weightedSize <= maxWeight, "weighted size $weightedSize")
            assertEquals(11L - weightedSize, cache.stats().evictions)
            assertEquals(1L, cache.stats().evictions)
        }
    }

    @Test
    fun `a Found weighing exactly maxWeight is stored and hit`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock) { maxWeight = this@CaffeineResponseCacheWeightComponentTest.maxWeight }.use { cache ->
            assertEquals(Lookup.Found("v", 10), cache.getOrLoad(key(), policy, log.heavy("v", 10)).getOrThrow())
            cache.cleanUp()
            assertEquals(10L, cache.weightedSize())
            assertEquals(Lookup.Found("v", 10), cache.getOrLoad(key(), policy, log.loader("x")).getOrThrow())
            assertEquals(1, log.loads)
            assertEquals(0L, cache.stats().evictions)
        }
    }

    @Test
    fun `a Found over maxWeight is delivered but never stored`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock) { maxWeight = this@CaffeineResponseCacheWeightComponentTest.maxWeight }.use { cache ->
            fill(cache, log, 1)
            cache.cleanUp()
            assertEquals(1L, cache.weightedSize())
            assertEquals(Lookup.Found("big", 11), cache.getOrLoad(key("big"), policy, log.heavy("big", 11)).getOrThrow())
            assertNull(cache.futureOf(key("big")), "the oversized value was stored")
            cache.cleanUp()
            assertEquals(1L, cache.weightedSize())
            assertEquals(0L, cache.stats().evictions)
            assertEquals(Lookup.Found("big2", 11), cache.getOrLoad(key("big"), policy, log.heavy("big2", 11)).getOrThrow())
            assertEquals(3, log.loads)
        }
    }

    @Test
    fun `a heavy entry among light ones keeps the bound`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock) { maxWeight = this@CaffeineResponseCacheWeightComponentTest.maxWeight }.use { cache ->
            fill(cache, log, 5)
            cache.getOrLoad(key("heavy"), policy, log.heavy("h", 8))
            cache.cleanUp()
            val weightedSize = cache.weightedSize()
            assertTrue(weightedSize <= maxWeight, "weighted size $weightedSize")
            assertTrue(cache.stats().evictions >= 1L, "no eviction for 13 units of weight in a bound of 10")
        }
    }

    @Test
    fun `the bound holds after each of 200 inserts of mixed weights`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        val random = Random(66)
        cacheOn(clock) { maxWeight = this@CaffeineResponseCacheWeightComponentTest.maxWeight }.use { cache ->
            repeat(200) { i ->
                val weight = random.nextInt(1, 6)
                cache.getOrLoad(key("k$i"), policy, log.heavy("v$i", weight))
                cache.cleanUp()
                val weightedSize = cache.weightedSize()
                assertTrue(weightedSize <= maxWeight, "after insert $i (weight $weight): weighted size $weightedSize")
            }
            assertTrue(cache.stats().evictions > 0L)
        }
    }

    @Test
    fun `evictions counts size removals only`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock) { maxWeight = this@CaffeineResponseCacheWeightComponentTest.maxWeight }.use { cache ->
            cache.getOrLoad(key("expiring"), policy, log.loader("a"))
            clock.advanceBy(11.minutes)
            cache.getOrLoad(key("expiring"), policy, log.loader("b"))
            cache.cleanUp()
            assertEquals(0L, cache.stats().evictions, "expiry was counted")

            cache.getOrLoad(key("invalidated"), policy, log.loader("c"))
            cache.invalidate(key("invalidated"))
            cache.invalidateAll()
            cache.cleanUp()
            assertEquals(0L, cache.stats().evictions, "invalidation was counted")

            cache.getOrLoad(key("failing"), policy, log.failing(IllegalStateException("x")))
            cache.cleanUp()
            assertEquals(0L, cache.stats().evictions, "a failed load was counted")

            cache.getOrLoad(key("zero"), CachePolicy(Duration.ZERO), log.loader("d"))
            cache.cleanUp()
            assertEquals(0L, cache.stats().evictions, "a zero-ttl entry was counted")
        }
    }

    @Test
    fun `a Missing weighs 1`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock) { maxWeight = this@CaffeineResponseCacheWeightComponentTest.maxWeight }.use { cache ->
            repeat(10) { i -> cache.getOrLoad(key("m$i"), policy, log.missing()) }
            cache.cleanUp()
            assertEquals(10L, cache.weightedSize())
            assertEquals(0L, cache.stats().evictions)
            cache.getOrLoad(key("m10"), policy, log.missing())
            cache.cleanUp()
            assertTrue(cache.weightedSize() <= maxWeight)
            assertEquals(1L, cache.stats().evictions)
        }
    }
}
