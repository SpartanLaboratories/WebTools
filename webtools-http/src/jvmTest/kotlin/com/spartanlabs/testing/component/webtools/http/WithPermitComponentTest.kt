package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.PermitRecordingBudget
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.BudgetEvent
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.Permit
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.testing.RecordingBudgetObserver
import com.spartanlabs.webtools.http.testing.virtualClock
import com.spartanlabs.webtools.http.withPermit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `withPermit` on a real budget: a block that returns normally leaves the permit spent (used 1); a block that calls `release()` refunds it and
 * the `finally` does not charge again; a block that completes with a throttle pauses once and the `finally` adds nothing; a block that throws propagates
 * its exception with the permit completed as spent; a block cancelled mid-way still completes the permit; a refused acquire returns `BudgetExhausted` and
 * the block never runs; a closed budget returns `Closed("budget")`; the block's own `Result.failure` is returned unchanged; cost 0 and a negative `maxWait`
 * throw `IllegalArgumentException`; with `PermitRecordingBudget`, the `finally` calls `complete()` exactly once when the block did nothing and the recorded
 * order is `[complete(signals), complete()]` when the block completed first.
 */
class WithPermitComponentTest {

    @Test
    fun `a block that returns normally leaves the permit spent`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(2)).use { budget ->
            var seen: Permit? = null
            val result = budget.withPermit(1, Lane.Tracker, Duration.ZERO) { permit -> seen = permit; Result.success("ok") }
            assertEquals("ok", result.getOrThrow())
            assertEquals(1, seen?.cost)
            assertEquals(Lane.Tracker, seen?.lane)
            assertEquals(1, budget.utilisation().windows.single().used)
        }
    }

    @Test
    fun `a block that releases refunds the permit and the finally does not charge again`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(2)).use { budget ->
            val result = budget.withPermit(1, Lane.Tracker, Duration.ZERO) { permit -> permit.release(); Result.success("released") }
            assertEquals("released", result.getOrThrow())
            assertEquals(0, budget.utilisation().windows.single().used)
        }
    }

    @Test
    fun `a block that completes with a throttle pauses once and the finally adds nothing`() = runTest {
        val clock = virtualClock()
        val recording = RecordingBudgetObserver()
        budgetOn(clock, minuteWindow(2)) { observer = recording }.use { budget ->
            budget.withPermit(1, Lane.Tracker, Duration.ZERO) { permit ->
                permit.complete(RateSignals { throttled = true; retryAfter = 30.seconds })
                Result.success(Unit)
            }
            assertEquals(30.seconds, budget.utilisation().pausedFor)
            assertEquals(1, recording.eventsOf<BudgetEvent.Paused>().size)
            assertEquals(1, budget.utilisation().windows.single().used)
        }
    }

    @Test
    fun `a block that throws propagates its exception with the permit completed as spent`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(2)).use { budget ->
            val thrown = IllegalStateException("from the block")
            val failure = assertFailsWith<IllegalStateException> { budget.withPermit<Unit>(1, Lane.Tracker, Duration.ZERO) { throw thrown } }
            assertSame(thrown, failure)
            assertEquals(1, budget.utilisation().windows.single().used)
            assertEquals(Duration.ZERO, budget.utilisation().pausedFor)
        }
    }

    @Test
    fun `a block cancelled mid-way still completes the permit`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(2)).use { budget ->
            var thrown: Throwable? = null
            val job = launch {
                try {
                    budget.withPermit(1, Lane.Tracker, Duration.ZERO) { delay(10.seconds); Result.success(Unit) }
                } catch (t: Throwable) {
                    thrown = t
                }
            }
            clock.advanceBy(5.seconds)
            job.cancel()
            clock.advanceBy(Duration.ZERO)
            assertTrue(job.isCompleted)
            assertIs<CancellationException>(thrown)
            assertEquals(1, budget.utilisation().windows.single().used)
            assertEquals(Duration.ZERO, budget.utilisation().pausedFor)
        }
    }

    @Test
    fun `a refused acquire returns BudgetExhausted and the block never runs`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(1)).use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            var ran = false
            val result = budget.withPermit(1, Lane.Tracker, Duration.ZERO) { ran = true; Result.success(Unit) }
            val failure = assertIs<HttpFailure.BudgetExhausted>(result.exceptionOrNull())
            assertEquals(ExhaustedReason.Window, failure.reason)
            assertFalse(ran)
        }
    }

    @Test
    fun `a closed budget returns Closed and the block never runs`() = runTest {
        val clock = virtualClock()
        val budget = budgetOn(clock, minuteWindow(1))
        budget.close()
        var ran = false
        val result = budget.withPermit(1, Lane.Tracker, Duration.INFINITE) { ran = true; Result.success(Unit) }
        assertEquals("budget", assertIs<HttpFailure.Closed>(result.exceptionOrNull()).component)
        assertFalse(ran)
    }

    @Test
    fun `the block's own failure is returned unchanged`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(2)).use { budget ->
            val cause = IllegalStateException("the block's failure")
            val result = budget.withPermit<String>(1, Lane.Tracker, Duration.ZERO) { Result.failure(cause) }
            assertSame(cause, result.exceptionOrNull())
            assertEquals(1, budget.utilisation().windows.single().used)
        }
    }

    @Test
    fun `cost 0 and a negative maxWait throw IllegalArgumentException`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(2)).use { budget ->
            assertEquals("cost must be at least 1", assertFailsWith<IllegalArgumentException> { budget.withPermit(0, Lane.Tracker, Duration.ZERO) { Result.success(Unit) } }.message)
            assertEquals("maxWait must not be negative", assertFailsWith<IllegalArgumentException> { budget.withPermit(1, Lane.Tracker, (-1).seconds) { Result.success(Unit) } }.message)
            assertEquals(0, budget.utilisation().windows.single().used)
        }
    }

    @Test
    fun `the finally completes exactly once and after the block's own complete`() = runTest {
        val clock = virtualClock()
        budgetOn(clock, minuteWindow(10)).use { real ->
            val idle = PermitRecordingBudget(real)
            idle.withPermit(1, Lane.Tracker, Duration.ZERO) { Result.success(Unit) }
            assertEquals(listOf("complete()"), idle.calls)

            val completing = PermitRecordingBudget(real)
            completing.withPermit(1, Lane.Tracker, Duration.ZERO) { permit -> permit.complete(RateSignals.None); Result.success(Unit) }
            assertEquals(listOf("complete(signals)", "complete()"), completing.calls)

            val releasing = PermitRecordingBudget(real)
            releasing.withPermit(1, Lane.Tracker, Duration.ZERO) { permit -> permit.release(); Result.success(Unit) }
            assertEquals(listOf("release()", "complete()"), releasing.calls)
            assertNotNull(releasing.permits.single())
        }
    }
}
