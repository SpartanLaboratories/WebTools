package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoadLog
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.callAcquire
import com.spartanlabs.testing.support.webtools.http.key
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.BudgetEvent
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.testing.RecordingBudgetObserver
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Level 2 - the three clocks move together with the real budget in place of the plain cache of `VirtualClockThreeClocksComponentTest`: a minute window
 * of 2 with both permits taken and a third caller waiting is granted at exactly the boundary, the wall-clock minute index has advanced by one, a
 * `CaffeineResponseCache` entry stored with a 10-minute TTL is still a hit at 9 minutes 50 seconds and expired after 20 more seconds, and a 5-minute
 * `delay` resumed at exactly 5 virtual minutes, all under one `advanceBy`. The control shows why the proof is needed: a budget on `TimeSource.Monotonic`
 * beside the same virtual wall clock measures a 60-second virtual wait as (near) zero in `Granted.waited`, so one clock of three would pass for the wrong
 * reason.
 */
class WindowedCallBudgetThreeClocksComponentTest {

    private val policy = CachePolicy(10.minutes)

    private fun minuteIndex(clock: VirtualClock): Long = Math.floorDiv(clock.wallClock.nowEpochMillis(), 60_000L)

    @Test
    fun `the budget's window the cache's expiry the delay and the wall clock index move together`() = runTest {
        val clock = virtualClock()
        val log = LoadLog()
        val recording = RecordingBudgetObserver()
        cacheOn(clock).use { cache ->
            budgetOn(clock, minuteWindow(2)) { observer = recording }.use { budget ->
                repeat(2) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
                val third = callAcquire(budget, clock)
                cache.getOrLoad(key(), policy, log.loader("v"))
                var resumedAt: Duration? = null
                launch(clock.dispatcher) { delay(5.minutes); resumedAt = clock.elapsed }
                val indexBefore = minuteIndex(clock)
                clock.advanceBy(Duration.ZERO)
                assertFalse(third.isCompleted)

                clock.advanceBy(1.minutes - 1.seconds)
                assertFalse(third.isCompleted, "the waiter was granted before the boundary")
                clock.advanceBy(1.seconds)
                assertEquals(1.minutes, third.completedAt, "the waiter was not granted at exactly the boundary")
                assertNotNull(third.permit)
                assertEquals(indexBefore + 1, minuteIndex(clock))
                assertEquals(1.minutes, recording.eventsOf<BudgetEvent.Granted>().last().waited)
                assertEquals(1, budget.utilisation().windows.single().used)

                clock.advanceBy(8.minutes + 50.seconds)
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
    }

    @Test
    fun `control - a budget on the monotonic clock beside the virtual wall clock measures a virtual wait as near zero`() = runTest {
        val clock = virtualClock()
        val recording = RecordingBudgetObserver()
        budgetOn(clock, minuteWindow(2)) { timeSource = TimeSource.Monotonic; observer = recording }.use { budget ->
            repeat(2) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            val third = callAcquire(budget, clock)
            clock.advanceBy(1.minutes)
            assertEquals(1.minutes, third.completedAt, "the wall clock did not reach the boundary")
            val waited = recording.eventsOf<BudgetEvent.Granted>().last().waited
            assertTrue(waited < 5.seconds, "a budget on the real clock measured the virtual wait as $waited")
        }
    }
}
