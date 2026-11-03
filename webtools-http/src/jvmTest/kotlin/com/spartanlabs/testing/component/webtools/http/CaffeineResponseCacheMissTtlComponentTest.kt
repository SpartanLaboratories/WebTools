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
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - a `Missing` on a virtual clock: it is stored for `missTtl` (the default 60 seconds: a hit at 59 seconds, a reload at 61; a configured
 * 5 seconds); a `Missing` ignores `ttl` and a `Found` ignores `missTtl`; `Lookup.Missing` is delivered as the object; a zero `missTtl` stores
 * nothing but still coalesces the loads in flight; a `Missing` is subject to `maxAge` like a `Found`.
 */
class CaffeineResponseCacheMissTtlComponentTest {

    private val policy = CachePolicy(10.minutes)

    @Test
    fun `a Missing is stored for the default missTtl`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            assertSame(Lookup.Missing, cache.getOrLoad(key(), policy, log.missing()).getOrThrow())
            clock.advanceBy(59.seconds)
            assertSame(Lookup.Missing, cache.getOrLoad(key(), policy, log.loader("x")).getOrThrow())
            assertEquals(1, log.loads)
            clock.advanceBy(2.seconds)
            assertEquals(Lookup.Found("x"), cache.getOrLoad(key(), policy, log.loader("x")).getOrThrow())
            assertEquals(2, log.loads)
        }
    }

    @Test
    fun `a configured missTtl governs`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        val fiveSeconds = CachePolicy(10.minutes) { missTtl = 5.seconds }
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key(), fiveSeconds, log.missing())
            clock.advanceBy(4.seconds)
            assertSame(Lookup.Missing, cache.getOrLoad(key(), fiveSeconds, log.loader("x")).getOrThrow())
            assertEquals(1, log.loads)
            clock.advanceBy(2.seconds)
            assertEquals(Lookup.Found("x"), cache.getOrLoad(key(), fiveSeconds, log.loader("x")).getOrThrow())
            assertEquals(2, log.loads)
        }
    }

    @Test
    fun `a Missing ignores ttl and a Found ignores missTtl`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            val shortTtl = CachePolicy(1.seconds)
            cache.getOrLoad(key("missing"), shortTtl, log.missing())
            val shortMissTtl = CachePolicy(10.minutes) { missTtl = 1.seconds }
            cache.getOrLoad(key("found"), shortMissTtl, log.loader("a"))
            assertEquals(2, log.loads)

            clock.advanceBy(30.seconds)
            assertSame(Lookup.Missing, cache.getOrLoad(key("missing"), shortTtl, log.loader("x")).getOrThrow(), "the Missing expired with ttl")
            assertEquals(Lookup.Found("a"), cache.getOrLoad(key("found"), shortMissTtl, log.loader("x")).getOrThrow(), "the Found expired with missTtl")
            assertEquals(2, log.loads)
        }
    }

    @Test
    fun `a zero missTtl stores nothing but coalesces`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        val noMissStorage = CachePolicy(10.minutes) { missTtl = Duration.ZERO }
        cacheOn(clock).use { cache ->
            val waiters = List(5) { call(cache, key(), noMissStorage, loader = log.missing(after = 1.seconds)) }
            clock.advanceBy(Duration.ZERO)
            assertEquals(1, log.loads)
            clock.advanceBy(1.seconds)
            assertTrue(waiters.all { it.isCompleted })
            waiters.forEach { assertSame(Lookup.Missing, it.lookup) }
            assertSame(Lookup.Missing, cache.getOrLoad(key(), noMissStorage, log.missing()).getOrThrow())
            assertEquals(2, log.loads, "the zero-missTtl Missing was stored")
        }
    }

    @Test
    fun `a Missing is subject to maxAge like a Found`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key(), policy, log.missing())
            clock.advanceBy(30.seconds)
            assertSame(Lookup.Missing, cache.getOrLoad(key(), policy, 1.minutes, log.missing()).getOrThrow())
            assertEquals(1, log.loads)
            assertSame(Lookup.Missing, cache.getOrLoad(key(), policy, 10.seconds, log.missing()).getOrThrow())
            assertEquals(2, log.loads, "a 30-second-old Missing was served to a call accepting 10 seconds")
        }
    }
}
