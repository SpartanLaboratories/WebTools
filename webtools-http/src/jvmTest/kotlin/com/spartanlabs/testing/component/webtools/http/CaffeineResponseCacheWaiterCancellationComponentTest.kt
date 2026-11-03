package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoadLog
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.call
import com.spartanlabs.testing.support.webtools.http.key
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CacheStats
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
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
 * Level 2 - a waiter's cancellation on a virtual clock: one waiter cancelling while the load runs cancels only itself, the other waiter receives
 * the value, the loader ran once and a later call is a hit; when every waiter cancels the load still completes and its value is stored; the
 * caller that started the load may cancel and the load continues for the others; a caller that is already cancelled gets `CancellationException`
 * on a miss and on a hit and its loader does not run; a waiter's cancellation never produces a `Closed` for another; a cancellation of the shared
 * future that is not the waiter's own (the future cancelled from outside while the waiter is active) reads as `Closed("cache")`, with the waiter
 * neither cancelled nor thrown into.
 */
class CaffeineResponseCacheWaiterCancellationComponentTest {

    private val policy = CachePolicy(10.minutes)

    @Test
    fun `one waiter cancels while the load runs and the other receives the value`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val first = call(cache, key(), policy, loader = log.loader("v", after = 1.seconds))
            val second = call(cache, key(), policy, loader = log.loader("x"))
            clock.advanceBy(Duration.ZERO)
            assertEquals(1, log.loads)
            first.cancel()
            clock.advanceBy(Duration.ZERO)
            assertTrue(first.isCancelled)
            assertFalse(second.isCompleted)
            clock.advanceBy(1.seconds)
            assertEquals("v", second.value)
            assertNull(second.failure)
            assertEquals(1, log.loads)
            assertEquals(Lookup.Found("v"), cache.getOrLoad(key(), policy, log.loader("y")).getOrThrow())
            assertEquals(1, log.loads)
            assertEquals(CacheStats(2L, 1L, 0L, 0L), cache.stats())
        }
    }

    @Test
    fun `when every waiter cancels the load still completes and is stored`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val waiters = List(3) { call(cache, key(), policy, loader = log.loader("v", after = 1.seconds)) }
            clock.advanceBy(Duration.ZERO)
            waiters.forEach { it.cancel() }
            clock.advanceBy(Duration.ZERO)
            assertTrue(waiters.all { it.isCancelled })
            clock.advanceBy(1.seconds)
            assertEquals(Lookup.Found("v"), cache.getOrLoad(key(), policy, log.loader("y")).getOrThrow())
            assertEquals(1, log.loads)
        }
    }

    @Test
    fun `the caller that started the load cancels and the load continues for the others`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val starter = call(cache, key(), policy, loader = log.loader("v", after = 1.seconds))
            clock.advanceBy(Duration.ZERO)
            val joiner = call(cache, key(), policy, loader = log.loader("x"))
            clock.advanceBy(Duration.ZERO)
            starter.cancel()
            clock.advanceBy(Duration.ZERO)
            assertTrue(starter.isCancelled)
            clock.advanceBy(1.seconds)
            assertEquals("v", joiner.value)
            assertEquals(1, log.loads)
        }
    }

    @Test
    fun `an already-cancelled caller gets CancellationException on a miss and on a hit and its loader does not run`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val onMiss = call(cache, key(), policy, cancelledFirst = true, loader = log.loader("a"))
            clock.advanceBy(Duration.ZERO)
            assertTrue(onMiss.isCancelled)
            assertIs<CancellationException>(onMiss.thrown)
            assertNull(onMiss.result)
            assertEquals(0, log.loads)

            cache.getOrLoad(key(), policy, log.loader("a"))
            val onHit = call(cache, key(), policy, cancelledFirst = true, loader = log.loader("b"))
            clock.advanceBy(Duration.ZERO)
            assertTrue(onHit.isCancelled)
            assertIs<CancellationException>(onHit.thrown)
            assertNull(onHit.result)
            assertEquals(1, log.loads)
        }
    }

    @Test
    fun `a waiter's cancellation never produces a Closed for another`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val cancelling = call(cache, key(), policy, loader = log.loader("v", after = 1.seconds))
            val others = List(4) { call(cache, key(), policy, loader = log.loader("x")) }
            clock.advanceBy(Duration.ZERO)
            cancelling.cancel()
            clock.advanceBy(Duration.ZERO)
            clock.advanceBy(1.seconds)
            others.forEach {
                assertTrue(it.isCompleted)
                assertFalse(it.failure is HttpFailure.Closed, "a waiter received Closed: ${it.failure}")
                assertEquals("v", it.value)
            }
        }
    }

    @Test
    fun `a cancellation of the shared future that is not the waiter's own reads as Closed`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val waiter = call(cache, key(), policy, loader = log.loader("v", after = 1.seconds))
            clock.advanceBy(Duration.ZERO)
            assertEquals(1, log.loads)
            assertFalse(waiter.isCompleted)

            // Not the waiter's cancellation and not close(): the shared future itself is cancelled while the waiter is still active.
            cache.futureOf(key())!!.cancel(false)
            clock.advanceBy(Duration.ZERO)

            assertTrue(waiter.isCompleted)
            assertFalse(waiter.isCancelled)
            assertNull(waiter.thrown)
            val closed = assertIs<HttpFailure.Closed>(waiter.failure)
            assertEquals("cache", closed.component)
        }
    }
}
