package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.dayWindow
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.BudgetObserver
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.InMemoryBudgetStore
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.WallClock
import com.spartanlabs.webtools.http.WindowedCallBudget
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Level 2 - constructing a `WindowedCallBudget`: every rule of the builder refused with a message that names the property and no value (no windows,
 * duplicate names; a share of 0, -0.1, 1.5, NaN and infinity; shares that do not fit; a guard that is negative or a millisecond above a quarter of the
 * shortest period, exactly a quarter accepted; `maxPause` zero, negative and infinite; `maxQueued` -1 refused and 0 accepted; `persistAhead` 0;
 * `maxRestoreSkew` negative; `storeTimeout` zero; `storeRetryInterval` negative refused and zero accepted); the builder reads its own defaults;
 * `maxBurst` is the smallest window's enforced limit; a window of limit 1 with the default shares makes a `Tracker` call `CostExceedsLimit` and works
 * with `laneShares = emptyMap()`; `toString()` before and after `close()`; the production-default budget constructs and closes; the factory copies the
 * list and the map it was given.
 */
class WindowedCallBudgetConstructionComponentTest {

    private val minute = minuteWindow(60)
    private val sentinel = "SENTINEL-VALUE-4f1c"

    private fun refused(windows: List<RateWindow> = listOf(minute), configure: WindowedCallBudget.Builder.() -> Unit): String {
        val failure = assertFailsWith<IllegalArgumentException> { WindowedCallBudget(windows, configure).close() }
        val message = failure.message ?: ""
        assertFalse(sentinel in message, message)
        assertFalse("4f1c" in message, message)
        return message
    }

    @Test
    fun `the window list must not be empty and names must be distinct`() {
        assertEquals("windows must not be empty", refused(emptyList()) {})
        assertEquals("window names must be distinct", refused(listOf(minute, minuteWindow(10, name = "minute"))) {})
    }

    @Test
    fun `a lane share must be above 0 and at most 1 and finite`() {
        val message = "laneShares must each be above 0 and at most 1"
        listOf(0.0, -0.1, 1.5, Double.NaN, Double.POSITIVE_INFINITY).forEach { share ->
            assertEquals(message, refused { laneShares = mapOf(Lane(sentinel) to share) }, "share $share")
        }
        WindowedCallBudget(listOf(minute)) { laneShares = mapOf(Lane.Interactive to 1.0) }.close()
    }

    @Test
    fun `the lanes' reserved units must fit every window`() {
        assertEquals("laneShares reserve more units than a window holds", refused { laneShares = mapOf(Lane.Interactive to 0.6, Lane.Tracker to 0.6) })
        assertEquals(
            "laneShares reserve more units than a window holds",
            refused(listOf(minute, minuteWindow(3, name = "small"))) { laneShares = mapOf(Lane.Interactive to 0.5, Lane.Tracker to 0.4, Lane("c") to 0.1) },
        )
        WindowedCallBudget(listOf(minute)) { laneShares = mapOf(Lane.Interactive to 0.5, Lane.Tracker to 0.5) }.close()
    }

    @Test
    fun `the guard is between zero and a quarter of the shortest period`() {
        val message = "boundaryGuard must be between zero and a quarter of the shortest period"
        assertEquals(message, refused { boundaryGuard = (-1).milliseconds })
        assertEquals(message, refused { boundaryGuard = 15.seconds + 1.milliseconds })
        assertEquals(message, refused(listOf(minute, RateWindow("second", 10, 1.seconds))) { boundaryGuard = 251.milliseconds })
        WindowedCallBudget(listOf(minute)) { boundaryGuard = 15.seconds }.close()
        WindowedCallBudget(listOf(minute)) { boundaryGuard = Duration.ZERO }.close()
    }

    @Test
    fun `maxPause is positive and finite`() {
        val message = "maxPause must be positive and finite"
        assertEquals(message, refused { maxPause = Duration.ZERO })
        assertEquals(message, refused { maxPause = (-1).seconds })
        assertEquals(message, refused { maxPause = Duration.INFINITE })
        WindowedCallBudget(listOf(minute)) { maxPause = 1.milliseconds }.close()
    }

    @Test
    fun `maxQueued is not negative`() {
        assertEquals("maxQueued must not be negative", refused { maxQueued = -1 })
        WindowedCallBudget(listOf(minute)) { maxQueued = 0 }.close()
    }

    @Test
    fun `persistAhead is at least 1`() {
        assertEquals("persistAhead must be at least 1", refused { persistAhead = 0 })
        assertEquals("persistAhead must be at least 1", refused { persistAhead = -3 })
        WindowedCallBudget(listOf(minute)) { persistAhead = 1 }.close()
    }

    @Test
    fun `maxRestoreSkew is not negative`() {
        assertEquals("maxRestoreSkew must not be negative", refused { maxRestoreSkew = (-1).milliseconds })
        WindowedCallBudget(listOf(minute)) { maxRestoreSkew = Duration.ZERO }.close()
    }

    @Test
    fun `storeTimeout is positive`() {
        assertEquals("storeTimeout must be positive", refused { storeTimeout = Duration.ZERO })
        assertEquals("storeTimeout must be positive", refused { storeTimeout = (-1).seconds })
    }

    @Test
    fun `storeRetryInterval is not negative`() {
        assertEquals("storeRetryInterval must not be negative", refused { storeRetryInterval = (-1).seconds })
        WindowedCallBudget(listOf(minute)) { storeRetryInterval = Duration.ZERO }.close()
    }

    @Test
    fun `the builder reads its own defaults`() {
        lateinit var seen: WindowedCallBudget.Builder
        WindowedCallBudget(listOf(minute)) { seen = this }.close()
        assertEquals(mapOf(Lane.Interactive to 0.2), seen.laneShares)
        assertEquals(1.seconds, seen.boundaryGuard)
        assertEquals(24.hours, seen.maxPause)
        assertEquals(1_000, seen.maxQueued)
        assertEquals(10, seen.persistAhead)
        assertEquals(5.minutes, seen.maxRestoreSkew)
        assertEquals(2.seconds, seen.storeTimeout)
        assertEquals(30.seconds, seen.storeRetryInterval)
        assertSame(TimeSource.Monotonic, seen.timeSource)
        assertSame(WallClock.System, seen.wallClock)
        assertIs<InMemoryBudgetStore>(seen.store)
        assertSame(BudgetObserver.None, seen.observer)
    }

    @Test
    fun `maxBurst is the smallest window's enforced limit`() {
        WindowedCallBudget(listOf(RateWindow("minute", 60, 1.minutes) { margin = 10 }, RateWindow("day", 3_000, 24.hours))).use { assertEquals(50, it.maxBurst) }
        WindowedCallBudget(listOf(RateWindow("day", 3_000, 24.hours), minuteWindow(7))).use { assertEquals(7, it.maxBurst) }
    }

    @Test
    fun `a window of limit 1 with the default shares refuses Tracker and works with no shares`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(1)) { laneShares = mapOf(Lane.Interactive to 0.2) }.use { budget ->
            val failure = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.INFINITE).exceptionOrNull())
            assertEquals(ExhaustedReason.CostExceedsLimit, failure.reason)
            assertEquals(Duration.INFINITE, failure.wait)
            assertEquals("minute", failure.window)
            assertTrue(budget.acquire(1, Lane.Interactive, Duration.ZERO).isSuccess)
        }
        budgetOn(clock, minuteWindow(1)) { laneShares = emptyMap() }.use { budget ->
            assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess)
        }
    }

    @Test
    fun `toString names the windows and the closed flag`() {
        val budget = WindowedCallBudget(listOf(minute, dayWindow(100)))
        assertEquals("WindowedCallBudget(windows=[minute, day], closed=false)", budget.toString())
        budget.close()
        assertEquals("WindowedCallBudget(windows=[minute, day], closed=true)", budget.toString())
        budget.close()
        assertEquals("WindowedCallBudget(windows=[minute, day], closed=true)", budget.toString())
    }

    @Test
    fun `the production-default budget constructs and closes`() {
        val budget = WindowedCallBudget(listOf(minute))
        assertEquals(60, budget.maxBurst)
        budget.close()
    }

    @Test
    fun `the factory copies the list and the map it was given`() = runTest {
        val clock = virtualClock()
        val windows = mutableListOf(minuteWindow(10))
        val shares = mutableMapOf(Lane.Interactive to 0.2)
        budgetOn(clock, *windows.toTypedArray()) { laneShares = shares }.use { budget ->
            windows += dayWindow(5)
            shares.clear()
            assertEquals("WindowedCallBudget(windows=[minute], closed=false)", budget.toString())
            // The reserve of 2 units for Interactive still holds: a Tracker call of 9 cannot fit a window of 10.
            val failure = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(9, Lane.Tracker, Duration.INFINITE).exceptionOrNull())
            assertEquals(ExhaustedReason.CostExceedsLimit, failure.reason)
            assertTrue(budget.acquire(8, Lane.Tracker, Duration.ZERO).isSuccess)
        }
    }
}
