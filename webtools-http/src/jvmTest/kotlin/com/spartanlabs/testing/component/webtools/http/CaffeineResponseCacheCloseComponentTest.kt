package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.LoadLog
import com.spartanlabs.testing.support.webtools.http.ParkingDispatcher
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.call
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.key
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CacheStats
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `close()` on a virtual clock: a load in flight at `close()` ends both waiters with `Closed("cache")` (neither cancelled) and the loader
 * observes its cancellation; a loader that ignores cancellation does not delay the waiters and throws nothing into `runTest` when it finishes;
 * closing before a parked load starts ends the waiter with `Closed` and the loader's first line never runs; `close()` twice does not throw and logs
 * once; every later `getOrLoad` (both overloads) returns `Closed("cache")` without running its loader; `invalidate`, `invalidateAll` and `stats()`
 * still work; a stored entry is gone; a call that was already cancelled gets `CancellationException` even after `close()`.
 */
class CaffeineResponseCacheCloseComponentTest {

    private val policy = CachePolicy(10.minutes)

    private fun assertClosed(failure: Throwable?, label: String) {
        val closed = assertIs<HttpFailure.Closed>(failure, label)
        assertEquals("cache", closed.component, label)
    }

    @Test
    fun `a load in flight at close ends every waiter with Closed and the loader observes its cancellation`() = runTest {
        val clock = virtualClock()
        cacheOn(clock).use { cache ->
            var observedCancellation = false
            val loader: suspend () -> Result<Lookup<String>> = {
                try {
                    delay(10.seconds)
                    Result.success(Lookup.Found("v"))
                } finally {
                    observedCancellation = !currentCoroutineContext().isActive
                }
            }
            val first = call(cache, key(), policy, loader = loader)
            val second = call(cache, key(), policy, loader = loader)
            clock.advanceBy(Duration.ZERO)
            assertFalse(first.isCompleted || second.isCompleted)

            cache.close()
            clock.advanceBy(Duration.ZERO)

            assertTrue(first.isCompleted && second.isCompleted)
            assertFalse(first.isCancelled || second.isCancelled)
            assertClosed(first.failure, "first")
            assertClosed(second.failure, "second")
            assertTrue(observedCancellation, "the loader did not observe its cancellation")
        }
    }

    @Test
    fun `a loader that ignores cancellation does not delay the waiters`() = runTest {
        val clock = virtualClock()
        cacheOn(clock).use { cache ->
            var finished = false
            val waiter = call(cache, key(), policy) {
                withContext(NonCancellable) { delay(10.seconds) }
                finished = true
                Result.success(Lookup.Found("v"))
            }
            clock.advanceBy(Duration.ZERO)
            cache.close()
            clock.advanceBy(Duration.ZERO)
            assertTrue(waiter.isCompleted)
            assertClosed(waiter.failure, "waiter")
            assertFalse(finished, "the loader finished before its delay")

            clock.advanceBy(10.seconds)
            assertTrue(finished, "the non-cancellable loader did not finish")
            assertClosed(waiter.failure, "waiter after the loader finished")
            assertNull(cache.futureOf(key()))
        }
    }

    @Test
    fun `close before the load starts ends the waiter with Closed and the loader never runs`() = runTest {
        val clock = virtualClock()
        val parking = ParkingDispatcher()
        cacheOn(clock) { loadDispatcher = parking }.use { cache ->
            var firstLineRan = false
            val waiter = call(cache, key(), policy) { firstLineRan = true; Result.success(Lookup.Found("v")) }
            clock.advanceBy(Duration.ZERO)
            assertEquals(1, parking.parkedCount)
            assertFalse(waiter.isCompleted)

            cache.close()
            clock.advanceBy(Duration.ZERO)
            assertTrue(waiter.isCompleted)
            assertClosed(waiter.failure, "waiter")

            parking.runParked()
            clock.advanceBy(Duration.ZERO)
            assertFalse(firstLineRan, "the loader ran after close()")
            assertClosed(waiter.failure, "waiter after the parked task ran")
            assertEquals(0L, cache.stats().failedLoads)
        }
    }

    @Test
    fun `close twice does not throw and logs once`() {
        val (_, events) = captureTransportLogs(Level.INFO) {
            runTest {
                val clock = virtualClock()
                val cache = cacheOn(clock)
                cache.getOrLoad(key(), policy) { Result.success(Lookup.Found("v")) }
                cache.close()
                cache.close()
                assertEquals("CaffeineResponseCache(maxWeight=1000, closed=true)", cache.toString())
            }
        }
        assertEquals(1, events.count { it.level == Level.INFO && it.formattedMessage == "closed" }, events.map { it.formattedMessage }.toString())
    }

    @Test
    fun `after close every call returns Closed without running its loader and the other members keep working`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key(), policy, log.loader("a"))
            cache.cleanUp()
            assertEquals(1L, cache.weightedSize())
            cache.close()

            assertClosed(cache.getOrLoad(key(), policy, Duration.INFINITE, log.loader("b")).exceptionOrNull(), "four-argument")
            assertClosed(cache.getOrLoad(key(), policy, log.loader("c")).exceptionOrNull(), "three-argument")
            assertClosed(cache.getOrLoad(key("other"), policy, log.loader("d")).exceptionOrNull(), "another key")
            assertEquals(1, log.loads)

            cache.invalidate(key())
            cache.invalidateAll()
            assertEquals(CacheStats(0L, 1L, 0L, 0L), cache.stats())
            cache.cleanUp()
            assertEquals(0L, cache.weightedSize(), "a stored entry survived close()")
            assertNull(cache.futureOf(key()))
        }
    }

    @Test
    fun `a call that was already cancelled gets CancellationException even after close`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.close()
            val waiter = call(cache, key(), policy, cancelledFirst = true, loader = log.loader("a"))
            clock.advanceBy(Duration.ZERO)
            assertTrue(waiter.isCancelled)
            assertIs<CancellationException>(waiter.thrown)
            assertNull(waiter.result)
            assertEquals(0, log.loads)
        }
    }
}
