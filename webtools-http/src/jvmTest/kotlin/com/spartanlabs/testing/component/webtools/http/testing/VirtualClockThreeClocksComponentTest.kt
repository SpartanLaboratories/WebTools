package com.spartanlabs.testing.component.webtools.http.testing

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Level 2 - the three clocks move together: a Caffeine cache whose ticker reads `clock.timeSource` and
 * whose maintenance runs on `clock.maintenanceExecutor`, a `delay` on `clock.dispatcher`, and a UTC-minute
 * index from `clock.wallClock`, all driven by one `advanceBy`. Caffeine stands in for the cache a later
 * unit builds. The control shows why the proof is needed: a cache on `TimeSource.Monotonic` beside the same
 * virtual clock does not expire, so one clock of three would pass for the wrong reason.
 */
class VirtualClockThreeClocksComponentTest {

    private fun minuteIndex(clock: VirtualClock): Long = Math.floorDiv(clock.wallClock.nowEpochMillis(), 60_000L)

    private fun cacheOn(ticker: Ticker, clock: VirtualClock): Cache<String, String> =
        Caffeine.newBuilder()
            .ticker(ticker)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .executor(clock.maintenanceExecutor)
            .build()

    @Test
    fun `the cache ticker the delay and the wall clock index move together`() = runTest {
        val clock = virtualClock()
        val origin = clock.timeSource.markNow()
        val cache = cacheOn({ origin.elapsedNow().inWholeNanoseconds }, clock)
        cache.put("k", "v")
        var resumedAt: Duration? = null
        launch(clock.dispatcher) { delay(5.minutes); resumedAt = clock.elapsed }
        val indexBefore = minuteIndex(clock)

        clock.advanceBy(9.minutes + 50.seconds)

        assertNotNull(cache.getIfPresent("k"), "the entry expired before 10 minutes")
        assertEquals(5.minutes, resumedAt, "the delay did not resume at exactly 5 virtual minutes")
        assertEquals(indexBefore + 9, minuteIndex(clock))

        clock.advanceBy(20.seconds)

        assertNull(cache.getIfPresent("k"), "the entry did not expire after 10 minutes")
    }

    @Test
    fun `control - a cache on the monotonic clock beside the virtual clock does not expire`() = runTest {
        val clock = virtualClock()
        val realOrigin = TimeSource.Monotonic.markNow()
        val cache = cacheOn({ realOrigin.elapsedNow().inWholeNanoseconds }, clock)
        cache.put("k", "v")

        clock.advanceBy(11.minutes)

        assertNotNull(cache.getIfPresent("k"), "a cache on the real clock expired under virtual time")
    }
}
