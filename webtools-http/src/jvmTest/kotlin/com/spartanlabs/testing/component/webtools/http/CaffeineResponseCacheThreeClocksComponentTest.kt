package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoadLog
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.key
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Level 2 - the three clocks move together with the real cache in place of the plain Caffeine one of `VirtualClockThreeClocksComponentTest`: an
 * entry stored with a 10-minute `ttl` is a hit after 9 minutes 50 seconds and a miss after another 20 seconds, a 5-minute `delay` on
 * `clock.dispatcher` resumes at exactly 5 virtual minutes, and the UTC-minute index from `clock.wallClock` advances by 9 then 10, all under one
 * `advanceBy`. The control shows why the proof is needed: a cache on `TimeSource.Monotonic` beside the same virtual clock does not expire.
 */
class CaffeineResponseCacheThreeClocksComponentTest {

    private val policy = CachePolicy(10.minutes)

    private fun minuteIndex(clock: VirtualClock): Long = Math.floorDiv(clock.wallClock.nowEpochMillis(), 60_000L)

    @Test
    fun `the cache's expiry the delay and the wall clock index move together`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock).use { cache ->
            cache.getOrLoad(key(), policy, log.loader("v"))
            var resumedAt: Duration? = null
            launch(clock.dispatcher) { delay(5.minutes); resumedAt = clock.elapsed }
            val indexBefore = minuteIndex(clock)

            clock.advanceBy(9.minutes + 50.seconds)

            assertEquals(Lookup.Found("v"), cache.getOrLoad(key(), policy, log.loader("x")).getOrThrow(), "the entry expired before 10 minutes")
            assertEquals(1, log.loads)
            assertEquals(5.minutes, resumedAt, "the delay did not resume at exactly 5 virtual minutes")
            assertEquals(indexBefore + 9, minuteIndex(clock))

            clock.advanceBy(20.seconds)

            assertEquals(Lookup.Found("y"), cache.getOrLoad(key(), policy, log.loader("y")).getOrThrow(), "the entry did not expire after 10 minutes")
            assertEquals(2, log.loads)
            assertEquals(indexBefore + 10, minuteIndex(clock))
        }
    }

    @Test
    fun `control - a cache on the monotonic clock beside the virtual clock does not expire`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        cacheOn(clock) { timeSource = TimeSource.Monotonic }.use { cache ->
            cache.getOrLoad(key(), policy, log.loader("v"))
            clock.advanceBy(11.minutes)
            assertEquals(Lookup.Found("v"), cache.getOrLoad(key(), policy, log.loader("x")).getOrThrow(), "a cache on the real clock expired under virtual time")
            assertEquals(1, log.loads)
        }
    }
}
