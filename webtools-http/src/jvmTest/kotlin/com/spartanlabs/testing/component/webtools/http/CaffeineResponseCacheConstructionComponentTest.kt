package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoadLog
import com.spartanlabs.testing.support.webtools.http.ParkingDispatcher
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.call
import com.spartanlabs.testing.support.webtools.http.key
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CaffeineResponseCache
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * Level 2 - construction of `CaffeineResponseCache`: a `maxWeight` of 0 or -1 is refused with the constant message (no value echoed) and 1 is
 * accepted; the production defaults construct and close; `DEFAULT_MAX_WEIGHT` is 1,000 and is the default; `toString()` before and after `close()`;
 * a custom `maintenanceExecutor` receives Caffeine's maintenance tasks after a load; a custom `loadDispatcher` runs the loads (one parked task after
 * one miss, none for a second equal-key call); the builder reads its own defaults.
 */
class CaffeineResponseCacheConstructionComponentTest {

    private val policy = CachePolicy(10.minutes)

    @Test
    fun `a maxWeight below 1 is refused with the constant message`() {
        listOf(0L, -1L).forEach { weight ->
            val exception = assertFailsWith<IllegalArgumentException> { CaffeineResponseCache { maxWeight = weight } }
            assertEquals("maxWeight must be at least 1", exception.message)
        }
    }

    @Test
    fun `a maxWeight of 1 and the production defaults construct and close`() {
        CaffeineResponseCache { maxWeight = 1 }.use { assertEquals("CaffeineResponseCache(maxWeight=1, closed=false)", it.toString()) }
        CaffeineResponseCache().use { assertEquals("CaffeineResponseCache(maxWeight=1000, closed=false)", it.toString()) }
        assertEquals(1_000L, CaffeineResponseCache.DEFAULT_MAX_WEIGHT)
    }

    @Test
    fun `toString names maxWeight and closed before and after close`() {
        val cache = CaffeineResponseCache { maxWeight = 7 }
        assertEquals("CaffeineResponseCache(maxWeight=7, closed=false)", cache.toString())
        cache.close()
        assertEquals("CaffeineResponseCache(maxWeight=7, closed=true)", cache.toString())
    }

    @Test
    fun `a custom maintenanceExecutor receives the maintenance tasks after a load`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        val tasks = AtomicInteger()
        val counting = Executor { task -> tasks.incrementAndGet(); task.run() }
        cacheOn(clock) { maintenanceExecutor = counting }.use { cache ->
            assertEquals(0, tasks.get())
            cache.getOrLoad(key(), policy, log.loader("a"))
            cache.cleanUp()
            assertTrue(tasks.get() > 0, "the custom executor ran no maintenance task")
        }
    }

    @Test
    fun `a custom loadDispatcher runs the loads`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        val parking = ParkingDispatcher()
        cacheOn(clock) { loadDispatcher = parking }.use { cache ->
            val first = call(cache, key(), policy, loader = log.loader("a"))
            clock.advanceBy(Duration.ZERO)
            assertEquals(1, parking.parkedCount)
            val second = call(cache, key(), policy, loader = log.loader("b"))
            clock.advanceBy(Duration.ZERO)
            assertEquals(1, parking.parkedCount, "a second equal-key call parked a task")
            assertEquals(0, log.loads)

            parking.runParked()
            clock.advanceBy(Duration.ZERO)
            assertEquals(1, log.loads)
            assertEquals("a", first.value)
            assertEquals("a", second.value)
            assertEquals(0, parking.parkedCount)
        }
    }

    @Test
    fun `the builder reads its own defaults`() {
        var timeSource: TimeSource? = null
        var maxWeight: Long? = null
        var loadDispatcher: CoroutineDispatcher? = null
        var maintenanceExecutor: Executor? = null
        CaffeineResponseCache {
            timeSource = this.timeSource
            maxWeight = this.maxWeight
            loadDispatcher = this.loadDispatcher
            maintenanceExecutor = this.maintenanceExecutor
        }.use { assertFalse(it.toString().endsWith("closed=true)")) }
        assertSame(TimeSource.Monotonic, timeSource)
        assertEquals(CaffeineResponseCache.DEFAULT_MAX_WEIGHT, maxWeight)
        assertSame(Dispatchers.Default, loadDispatcher)
        assertSame(ForkJoinPool.commonPool(), maintenanceExecutor)
    }
}
