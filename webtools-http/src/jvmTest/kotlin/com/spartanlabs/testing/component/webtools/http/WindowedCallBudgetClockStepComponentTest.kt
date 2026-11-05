package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.DAY_MS
import com.spartanlabs.testing.support.webtools.http.SteppableWallClock
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.callAcquire
import com.spartanlabs.testing.support.webtools.http.dayWindow
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.BudgetEvent
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.WindowedCallBudget
import com.spartanlabs.webtools.http.testing.FakeBudgetStore
import com.spartanlabs.webtools.http.testing.RecordingBudgetObserver
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `WindowedCallBudget` on a `SteppableWallClock` over the virtual clock (a hard day window, guard 0 unless stated): with no fault a call on a
 * full window waits exactly to the wall boundary and `Granted.waited` carries it; a backward step across a boundary copies the early period's count into
 * the current one, grants stop at the limit and `BudgetExhausted.wait` is the wall time to the next midnight; a step back of one year leaves the limit
 * enforced and a call with `INFINITE` completes within one period; a step back within one window changes no count and delays a waiter by the step at
 * most, never granting it before the wall index reaches its window; a forward jump while a waiter is parked grants it within the one-minute slice and
 * opens the hard window for the new day; a slewed wall clock grants at the first wake where the wall reaches the slot, within 0.05 % of the wait and never
 * before the wall boundary; a re-plan follows each copy (a waiter planned into the copied cell is failed); a forward jump with a queue re-plans the waiters
 * from the new wall, in arrival order, never charging a period that has ended; a waiter late across a boundary is charged to the current minute.
 */
class WindowedCallBudgetClockStepComponentTest {

    private val midnight = VirtualClock.DEFAULT_START_EPOCH_MILLIS
    private val nextMidnight = midnight + DAY_MS

    private class Rig(val clock: VirtualClock, val wall: SteppableWallClock, val budget: WindowedCallBudget, val recording: RecordingBudgetObserver)

    private fun TestScope.rig(startEpochMillis: Long, vararg windows: RateWindow, configure: WindowedCallBudget.Builder.() -> Unit = {}): Rig {
        val clock = virtualClock(startEpochMillis)
        val wall = SteppableWallClock(clock.wallClock)
        val recording = RecordingBudgetObserver()
        val budget = budgetOn(clock, *windows) { wallClock = wall; observer = recording; configure() }
        return Rig(clock, wall, budget, recording)
    }

    @Test
    fun `no fault - a call on a full window waits exactly to the wall boundary`() = runTest {
        val r = rig(nextMidnight - 10_000L, dayWindow(2))
        r.budget.use { budget ->
            repeat(2) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            val waiter = callAcquire(budget, r.clock)
            r.clock.advanceBy(10.seconds - 1.milliseconds)
            assertFalse(waiter.isCompleted)
            r.clock.advanceBy(1.milliseconds)
            assertEquals(10.seconds, waiter.completedAt)
            assertEquals(10.seconds, r.recording.eventsOf<BudgetEvent.Granted>().last().waited)
        }
    }

    @Test
    fun `a backward step across a boundary copies the early period's count and grants stop at the limit`() = runTest {
        val r = rig(nextMidnight - 30.minutes.inWholeMilliseconds, dayWindow(5))
        r.budget.use { budget ->
            repeat(2) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            r.wall.stepBy(1.hours)                                   // the day rolls early: 00:30 of the next day
            assertEquals(0, budget.utilisation().windows.single().used)
            repeat(2) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            assertEquals(2, budget.utilisation().windows.single().used)

            r.wall.stepBy((-1).hours)                                // back to 23:30 of the first day
            assertEquals(4, budget.utilisation().windows.single().used, "the lower cell did not gain the early cell's count")
            assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess)
            val refused = assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull())
            assertEquals(30.minutes, refused.wait)
            assertEquals(ExhaustedReason.Window, refused.reason)
            assertEquals("day", refused.window)
        }
    }

    @Test
    fun `a step back of one year keeps the limit and a call with INFINITE completes within one period`() = runTest {
        val r = rig(midnight + 12.hours.inWholeMilliseconds, dayWindow(5))
        r.budget.use { budget ->
            repeat(5) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            r.wall.stepBy((-365).days)
            assertEquals(5, budget.utilisation().windows.single().used)
            assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull())
            val waiter = callAcquire(budget, r.clock)
            r.clock.advanceBy(12.hours - 1.milliseconds)
            assertFalse(waiter.isCompleted)
            r.clock.advanceBy(1.milliseconds)
            assertEquals(12.hours, waiter.completedAt, "the call did not complete within one period")
            assertNotNull(waiter.permit)
            assertEquals(1, budget.utilisation().windows.single().used)
        }
    }

    @Test
    fun `a step back within one window changes no count and delays a waiter by the step at most`() = runTest {
        val r = rig(nextMidnight - 10_000L, dayWindow(5))
        r.budget.use { budget ->
            repeat(5) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            val waiter = callAcquire(budget, r.clock)
            r.clock.advanceBy(5.seconds)
            r.wall.stepBy((-1).hours)
            assertEquals(5, budget.utilisation().windows.single().used)
            r.clock.advanceBy(5.seconds)
            assertFalse(waiter.isCompleted, "the waiter was granted before the wall reached its window")
            r.clock.advanceBy(1.hours - 1.milliseconds)
            assertFalse(waiter.isCompleted)
            r.clock.advanceBy(1.milliseconds)
            assertEquals(10.seconds + 1.hours, waiter.completedAt)
            assertEquals(nextMidnight, r.wall.nowEpochMillis())
        }
    }

    @Test
    fun `a forward jump while a waiter is parked grants it within the one-minute slice and opens the hard window`() = runTest {
        val r = rig(midnight + 20.hours.inWholeMilliseconds, dayWindow(5))
        r.budget.use { budget ->
            repeat(5) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            val waiter = callAcquire(budget, r.clock)
            r.clock.advanceBy(Duration.ZERO)
            r.wall.stepBy(8.hours)                                   // a host that slept: 04:00 of the next day, no scheduler advance
            r.clock.advanceBy(1.minutes)
            assertTrue(waiter.isCompleted, "the waiter was not granted within the one-minute slice")
            assertNotNull(waiter.permit)
            assertTrue(waiter.completedAt!! <= 1.minutes)
            assertEquals(1, budget.utilisation().windows.single().used, "the hard window did not open fresh for the new day")
            assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess)
        }
    }

    @Test
    fun `a slewed wall clock grants at the first wake where the wall reaches the slot`() = runTest {
        suspend fun TestScope.grantOn(ppm: Long): Pair<Duration, Long> {
            val r = rig(midnight, RateWindow("hour", 1, 1.hours))
            return r.budget.use { budget ->
                budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
                r.wall.slewAt(ppm)
                val waiter = callAcquire(budget, r.clock)
                r.clock.advanceBy(1.hours - 2.seconds)
                assertFalse(waiter.isCompleted)
                r.clock.advanceBy(4.seconds)
                assertTrue(waiter.isCompleted, "the slewed waiter was not granted around the hour")
                // The slewed clock's reading at the grant: the rig's origin is midnight and nothing stepped it.
                val grantMillis = waiter.completedAt!!.inWholeMilliseconds
                waiter.completedAt!! to midnight + grantMillis + grantMillis * ppm / 1_000_000
            }
        }
        val (fastGrant, fastWall) = grantOn(500)                     // the wall runs fast: it reaches the slot before the monotonic hour
        assertTrue(fastGrant <= 1.hours && fastGrant >= 1.hours - 1.8.seconds, "fast: granted at $fastGrant")
        assertTrue(fastWall >= midnight + 1.hours.inWholeMilliseconds, "fast: granted before the wall boundary")
        val (slowGrant, slowWall) = grantOn(-500)                    // the wall runs slow: the grant is at most 0.05 % of the wait after the monotonic hour
        assertTrue(slowGrant >= 1.hours && slowGrant <= 1.hours + 1.8.seconds, "slow: granted at $slowGrant")
        assertTrue(slowWall >= midnight + 1.hours.inWholeMilliseconds, "slow: granted before the wall boundary")
    }

    @Test
    fun `a re-plan follows each copy - a waiter planned into the copied cell is failed`() = runTest {
        val r = rig(nextMidnight - 30.minutes.inWholeMilliseconds, dayWindow(5))
        r.budget.use { budget ->
            val permits = List(3) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            permits[0].complete(RateSignals { throttled = true; retryAfter = 10.minutes })
            val waiter = callAcquire(budget, r.clock, maxWait = 15.minutes)          // planned at the pause's end, 23:40, into the current cell
            r.clock.advanceBy(Duration.ZERO)
            assertFalse(waiter.isCompleted)

            r.wall.stepBy(1.hours)                                   // 00:30 of the next day: the pause is over on the wall axis and the waiter's period is behind
            repeat(2) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            assertEquals(2, budget.utilisation().windows.single().used)
            assertEquals(1, budget.utilisation().windows.single().queued)
            assertFalse(waiter.isCompleted)

            r.wall.stepBy((-1).hours)                                // back to 23:30: the copy fills the day (3 + 2) and the waiter is planned again
            assertIs<HttpFailure.BudgetExhausted>(budget.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull())
            r.clock.advanceBy(Duration.ZERO)
            val failure = assertIs<HttpFailure.BudgetExhausted>(waiter.failure, "the waiter was not failed by the re-plan: ${waiter.result} ${waiter.thrown}")
            assertEquals(30.minutes, failure.wait)
            assertEquals(ExhaustedReason.Window, failure.reason)
            assertEquals("day", failure.window)
            assertEquals(5, budget.utilisation().windows.single().used)
            assertEquals(0, budget.utilisation().waiting)
        }
    }

    @Test
    fun `a forward jump with a queue re-plans the waiters from the new wall in arrival order`() = runTest {
        val r = rig(midnight, minuteWindow(2))
        r.budget.use { budget ->
            val calls = List(32) { callAcquire(budget, r.clock) }
            r.clock.advanceBy(Duration.ZERO)
            assertEquals(2, calls.count { it.isCompleted })
            assertEquals(30, budget.utilisation().waiting)

            r.wall.stepBy(2.hours)                                   // no scheduler advance
            r.clock.advanceBy(1.minutes)                             // the one-minute slice: every waiter wakes and the first re-plans them all
            val grantedInSlice = calls.filter { it.completedAt != null && it.completedAt!! > Duration.ZERO }
            assertEquals(2, grantedInSlice.size, "more than the limit was granted in the new current minute")
            assertEquals(listOf(2, 3), grantedInSlice.map { calls.indexOf(it) }, "the re-plan did not keep the arrival order")
            assertEquals(2, budget.utilisation().windows.single().used)
            val fresh = callAcquire(budget, r.clock)
            r.clock.advanceBy(Duration.ZERO)
            assertFalse(fresh.isCompleted, "a fresh call after the jump did not queue behind the re-planned waiters")

            var previousGranted = 4
            repeat(15) { minute ->
                r.clock.advanceBy(1.minutes)
                val used = budget.utilisation().windows.single().used
                assertTrue(used <= 2, "minute ${minute + 1} after the jump: used $used")
                val granted = calls.count { it.isCompleted }
                assertTrue(granted >= previousGranted, "a waiter was lost")
                assertEquals(calls.take(granted), calls.filter { it.isCompleted }, "the grants left the arrival order")
                previousGranted = granted
            }
            assertTrue(calls.all { it.isCompleted })
            assertEquals(16.minutes, fresh.completedAt)
        }
    }

    @Test
    fun `a waiter late across a boundary is charged to the current minute`() = runTest {
        val clock = virtualClock(midnight)
        val wall = SteppableWallClock(clock.wallClock)
        val store = FakeBudgetStore()
        budgetOn(clock, minuteWindow(1)) { wallClock = wall; this.store = store; storeTimeout = 2.minutes }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            val waiter = callAcquire(budget, clock)                  // planned at the 60 s boundary
            clock.advanceBy(59.seconds)
            store.saveDelay = 62.seconds
            launch { budget.flush() }                                // holds the lock from 59 s to 121 s, past the next boundary
            clock.advanceBy(1.seconds)
            assertFalse(waiter.isCompleted)
            clock.advanceBy(60.seconds)
            assertFalse(waiter.isCompleted, "the waiter ran its section while the lock was held")
            clock.advanceBy(1.seconds)
            assertEquals(121.seconds, waiter.completedAt)
            assertNotNull(waiter.permit)
            val utilisation = budget.utilisation()
            assertEquals(midnight + 120_000L, utilisation.windows.single().windowStartEpochMillis)
            assertEquals(1, utilisation.windows.single().used, "the late waiter was not charged to the current minute")
            assertEquals(0, utilisation.windows.single().queued)
            assertNull(waiter.thrown)
        }
    }
}
