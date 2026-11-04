package com.spartanlabs.testing.gating.webtools.http

import com.spartanlabs.webtools.http.CallBudget
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.WindowedCallBudget
import com.spartanlabs.webtools.http.withPermit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 1 - the supplied budget as a consumer sees it, compiled **without any opt-in** and on the production defaults (`TimeSource.Monotonic`,
 * `WallClock.System`, `InMemoryBudgetStore`): `WindowedCallBudget(windows) { }` is a `CallBudget`; one `acquire` round trip under `runBlocking` within a
 * short timeout; a `withPermit` call; `complete(RateSignals.None)`; `utilisation()` showing the grant; `flush()` success; `close()` twice; `acquire`
 * after `close()` is `Closed` with `component == "budget"`; `CallBudget.Unlimited` through the same calls.
 */
class WindowedCallBudgetConsumerGatingTest {

    private val windows = listOf(RateWindow("minute", 60, 1.minutes))

    @Test
    fun `the supplied budget is usable as a CallBudget without an opt-in`(): Unit = runBlocking {
        val budget: CallBudget = WindowedCallBudget(windows) { maxQueued = 10 }
        try {
            val permit = withTimeout(5.seconds) { budget.acquire(1, Lane.Tracker, Duration.ZERO) }.getOrThrow()
            assertEquals(1, permit.cost)
            assertEquals(Lane.Tracker, permit.lane)
            permit.complete(RateSignals.None)
            val viaWithPermit = withTimeout(5.seconds) { budget.withPermit(2, Lane.Interactive, Duration.ZERO) { Result.success("ok") } }
            assertEquals("ok", viaWithPermit.getOrThrow())
            val utilisation = withTimeout(5.seconds) { budget.utilisation() }
            assertEquals(3, utilisation.windows.single().used)
            assertEquals(0, utilisation.waiting)
            assertEquals(Result.success(Unit), withTimeout(5.seconds) { budget.flush() })
            assertEquals("WindowedCallBudget(windows=[minute], closed=false)", budget.toString())
        } finally {
            budget.close()
            budget.close()
        }
        val closed = assertIs<HttpFailure.Closed>(withTimeout(5.seconds) { budget.acquire(1, Lane.Tracker, Duration.ZERO) }.exceptionOrNull())
        assertEquals("budget", closed.component)
        assertTrue(budget.toString().endsWith("closed=true)"), budget.toString())
    }

    @Test
    fun `the unlimited budget answers the same calls`(): Unit = runBlocking {
        val budget: CallBudget = CallBudget.Unlimited
        val permit = withTimeout(5.seconds) { budget.acquire(1, Lane.Tracker, Duration.ZERO) }.getOrThrow()
        permit.complete(RateSignals.None)
        assertEquals("ok", withTimeout(5.seconds) { budget.withPermit(1, Lane.Tracker, Duration.ZERO) { Result.success("ok") } }.getOrThrow())
        assertTrue(withTimeout(5.seconds) { budget.utilisation() }.windows.isEmpty())
        assertEquals(Result.success(Unit), withTimeout(5.seconds) { budget.flush() })
        budget.close()
        budget.close()
        assertTrue(withTimeout(5.seconds) { budget.acquire(1, Lane.Tracker, Duration.ZERO) }.isSuccess)
    }
}
