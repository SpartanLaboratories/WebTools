package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoadLog
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.call
import com.spartanlabs.testing.support.webtools.http.key
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - invalidation on a virtual clock: `invalidate(K)` on a stored entry makes the next call reload and leaves another key untouched;
 * `invalidateAll()` drops every key; invalidating a key whose load is in flight lets the waiters receive that load's outcome, starts a second load
 * for the next caller and stores only the second result; `invalidateAll()` during two loads completes both for their waiters and stores neither;
 * invalidating an absent key is a no-op; no eviction is counted.
 */
class CaffeineResponseCacheInvalidationComponentTest {

    private val policy = CachePolicy(10.minutes)

    @Test
    fun `invalidate on a stored entry reloads that key and leaves another untouched`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key("a"), policy, log.loader("a1"))
            cache.getOrLoad(key("b"), policy, log.loader("b1"))
            cache.invalidate(key("a"))
            assertEquals(Lookup.Found("a2"), cache.getOrLoad(key("a"), policy, log.loader("a2")).getOrThrow())
            assertEquals(Lookup.Found("b1"), cache.getOrLoad(key("b"), policy, log.loader("b2")).getOrThrow())
            assertEquals(3, log.loads)
            assertEquals(0L, cache.stats().evictions)
        }
    }

    @Test
    fun `invalidateAll drops every key`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key("a"), policy, log.loader("a1"))
            cache.getOrLoad(key("b"), policy, log.loader("b1"))
            cache.invalidateAll()
            assertEquals(Lookup.Found("a2"), cache.getOrLoad(key("a"), policy, log.loader("a2")).getOrThrow())
            assertEquals(Lookup.Found("b2"), cache.getOrLoad(key("b"), policy, log.loader("b2")).getOrThrow())
            assertEquals(4, log.loads)
            assertEquals(0L, cache.stats().evictions)
        }
    }

    @Test
    fun `invalidating an in-flight load completes it for its waiters and stores only the next load`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val first = call(cache, key(), policy, loader = log.loader("a", after = 1.seconds))
            clock.advanceBy(Duration.ZERO)
            cache.invalidate(key())
            val second = call(cache, key(), policy, loader = log.loader("b", after = 1.seconds))
            clock.advanceBy(Duration.ZERO)
            assertEquals(2, log.loads)
            clock.advanceBy(1.seconds)
            assertEquals("a", first.value)
            assertEquals("b", second.value)
            assertEquals(Lookup.Found("b"), cache.getOrLoad(key(), policy, log.loader("c")).getOrThrow())
            assertEquals(2, log.loads)
            assertEquals(0L, cache.stats().evictions)
        }
    }

    @Test
    fun `invalidateAll during two loads completes both for their waiters and stores neither`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val a = call(cache, key("a"), policy, loader = log.loader("a", after = 1.seconds))
            val b = call(cache, key("b"), policy, loader = log.loader("b", after = 1.seconds))
            clock.advanceBy(Duration.ZERO)
            cache.invalidateAll()
            clock.advanceBy(1.seconds)
            assertEquals("a", a.value)
            assertEquals("b", b.value)
            assertEquals(Lookup.Found("a2"), cache.getOrLoad(key("a"), policy, log.loader("a2")).getOrThrow())
            assertEquals(Lookup.Found("b2"), cache.getOrLoad(key("b"), policy, log.loader("b2")).getOrThrow())
            assertEquals(4, log.loads)
        }
    }

    @Test
    fun `invalidating an absent key is a no-op`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.invalidate(key("absent"))
            cache.getOrLoad(key(), policy, log.loader("a"))
            cache.invalidate(key("absent"))
            assertEquals(Lookup.Found("a"), cache.getOrLoad(key(), policy, log.loader("b")).getOrThrow())
            assertEquals(1, log.loads)
            assertEquals(0L, cache.stats().evictions)
        }
    }
}
