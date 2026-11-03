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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `maxAge` on a virtual clock: one load for N stale callers (50 callers that refuse a 5-minute-old entry cost one reload, and every one
 * receives the new value); the age edge (an age equal to `maxAge` is served, one millisecond more reloads: the virtual clock's finest step);
 * `Duration.ZERO` serves an entry stored at the same instant and reloads one millisecond later; an in-flight load is joined as fresh even with
 * `maxAge = ZERO`; a reload replaces the entry; a failed reload returns the failure and leaves no entry (the old value is not resurrected); a
 * negative `maxAge` throws on a miss, on a hit and after `close()`; a `maxAge` larger than the age is a hit.
 */
class CaffeineResponseCacheMaxAgeComponentTest {

    private val policy = CachePolicy(10.minutes)

    @Test
    fun `one load for fifty stale callers`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key(), policy, log.loader("old"))
            clock.advanceBy(5.minutes)
            val waiters = List(50) { call(cache, key(), policy, maxAge = 1.minutes, loader = log.loader("new", after = 1.seconds)) }
            clock.advanceBy(Duration.ZERO)
            assertEquals(2, log.loads)
            clock.advanceBy(1.seconds)
            assertTrue(waiters.all { it.isCompleted })
            waiters.forEach { assertEquals("new", it.value) }
            assertEquals(2, log.loads)
            assertEquals(CacheStats(49L, 2L, 0L, 0L), cache.stats())
        }
    }

    @Test
    fun `an age equal to maxAge is served and one millisecond more reloads`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key(), policy, log.loader("a"))
            clock.advanceBy(5.minutes)
            assertEquals(Lookup.Found("a"), cache.getOrLoad(key(), policy, 5.minutes, log.loader("b")).getOrThrow())
            assertEquals(1, log.loads)
            clock.advanceBy(1.milliseconds)
            assertEquals(Lookup.Found("c"), cache.getOrLoad(key(), policy, 5.minutes, log.loader("c")).getOrThrow())
            assertEquals(2, log.loads)
        }
    }

    @Test
    fun `a zero maxAge serves an entry stored at the same instant and reloads one millisecond later`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key(), policy, log.loader("a"))
            assertEquals(Lookup.Found("a"), cache.getOrLoad(key(), policy, Duration.ZERO, log.loader("b")).getOrThrow())
            assertEquals(1, log.loads)
            clock.advanceBy(1.milliseconds)
            assertEquals(Lookup.Found("c"), cache.getOrLoad(key(), policy, Duration.ZERO, log.loader("c")).getOrThrow())
            assertEquals(2, log.loads)
        }
    }

    @Test
    fun `an in-flight load is joined as fresh even with a zero maxAge`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val first = call(cache, key(), policy, loader = log.loader("a", after = 1.seconds))
            clock.advanceBy(Duration.ZERO)
            val joiner = call(cache, key(), policy, maxAge = Duration.ZERO, loader = log.loader("b"))
            clock.advanceBy(Duration.ZERO)
            assertEquals(1, log.loads)
            clock.advanceBy(1.seconds)
            assertEquals("a", first.value)
            assertEquals("a", joiner.value)
            assertEquals(1, log.loads)
        }
    }

    @Test
    fun `a reload replaces the entry`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key(), policy, log.loader("old"))
            clock.advanceBy(5.minutes)
            assertEquals(Lookup.Found("new"), cache.getOrLoad(key(), policy, 1.minutes, log.loader("new")).getOrThrow())
            assertEquals(Lookup.Found("new"), cache.getOrLoad(key(), policy, log.loader("later")).getOrThrow())
            assertEquals(2, log.loads)
        }
    }

    @Test
    fun `a failed reload returns the failure and leaves no entry`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key(), policy, log.loader("old"))
            clock.advanceBy(5.minutes)
            val failure = IllegalStateException("reload failed")
            assertSame(failure, cache.getOrLoad(key(), policy, 1.minutes, log.failing(failure)).exceptionOrNull())
            assertNull(cache.futureOf(key()), "the failed reload left an entry")
            assertEquals(Lookup.Found("c"), cache.getOrLoad(key(), policy, log.loader("c")).getOrThrow(), "the old value was resurrected")
            assertEquals(3, log.loads)
            assertEquals(1L, cache.stats().failedLoads)
        }
    }

    @Test
    fun `a negative maxAge throws on a miss on a hit and after close`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val onMiss = assertFailsWith<IllegalArgumentException> { cache.getOrLoad(key(), policy, (-1).seconds, log.loader("a")) }
            assertEquals("maxAge must not be negative", onMiss.message)
            assertEquals(0, log.loads)

            cache.getOrLoad(key(), policy, log.loader("a"))
            val onHit = assertFailsWith<IllegalArgumentException> { cache.getOrLoad(key(), policy, (-1).seconds, log.loader("b")) }
            assertEquals("maxAge must not be negative", onHit.message)
            assertEquals(1, log.loads)

            cache.close()
            val afterClose = assertFailsWith<IllegalArgumentException> { cache.getOrLoad(key(), policy, (-1).seconds, log.loader("c")) }
            assertEquals("maxAge must not be negative", afterClose.message)
            assertEquals(1, log.loads)
        }
    }

    @Test
    fun `a maxAge larger than the age is a hit`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key(), policy, log.loader("a"))
            clock.advanceBy(1.minutes)
            assertEquals(Lookup.Found("a"), cache.getOrLoad(key(), policy, 5.minutes, log.loader("b")).getOrThrow())
            assertEquals(1, log.loads)
            assertEquals(CacheStats(1L, 1L, 0L, 0L), cache.stats())
        }
    }
}
