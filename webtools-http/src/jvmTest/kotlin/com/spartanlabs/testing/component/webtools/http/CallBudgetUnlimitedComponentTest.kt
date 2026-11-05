package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.CallBudget
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.Permit
import com.spartanlabs.webtools.http.RateSignals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `CallBudget.Unlimited`: grants at once on any lane with the cost and lane on the permit; cost 0 and -1 and a negative
 * `maxWait` are refused with the property's message, `ZERO` and `INFINITE` accepted; an already-cancelled caller gets its
 * `CancellationException`; `complete()`, `complete(signals)` and `release()` do nothing and repeat safely; `utilisation()` is
 * empty; `flush()` succeeds; `close()` changes nothing; `toString()`.
 */
class CallBudgetUnlimitedComponentTest {

    private val budget: CallBudget = CallBudget.Unlimited

    @Test
    fun `grants at once on any lane with the cost and lane on the permit`() = runTest {
        val tracker = budget.acquire(3, Lane.Tracker, Duration.ZERO).getOrThrow()
        assertEquals(3, tracker.cost)
        assertEquals(Lane.Tracker, tracker.lane)
        val own = budget.acquire(1, Lane("own"), Duration.INFINITE).getOrThrow()
        assertEquals(Lane("own"), own.lane)
        assertEquals("Permit(lane=own, cost=1)", own.toString())
    }

    @Test
    fun `cost below 1 and a negative maxWait are refused`() = runTest {
        assertEquals("cost must be at least 1", assertFailsWith<IllegalArgumentException> { budget.acquire(0, Lane.Tracker, Duration.ZERO) }.message)
        assertEquals("cost must be at least 1", assertFailsWith<IllegalArgumentException> { budget.acquire(-1, Lane.Tracker, Duration.ZERO) }.message)
        assertEquals("maxWait must not be negative", assertFailsWith<IllegalArgumentException> { budget.acquire(1, Lane.Tracker, (-1).seconds) }.message)
        assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess)
        assertTrue(budget.acquire(1, Lane.Tracker, Duration.INFINITE).isSuccess)
    }

    @Test
    fun `an already-cancelled caller gets its CancellationException and no permit`() = runTest {
        var thrown: Throwable? = null
        var permit: Permit? = null
        launch {
            coroutineContext.job.cancel()
            try {
                permit = budget.acquire(1, Lane.Tracker, Duration.INFINITE).getOrNull()
                fail("a cancelled caller was granted")
            } catch (e: CancellationException) {
                thrown = e
            }
        }.join()
        assertIs<CancellationException>(thrown)
        assertNull(permit)
    }

    @Test
    fun `complete and release do nothing and repeat safely`() = runTest {
        val permit = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
        permit.complete()
        permit.complete()
        permit.complete(RateSignals { throttled = true; retryAfter = 30.seconds })
        permit.release()
        permit.release()
        permit.complete()
        assertEquals(Duration.ZERO, budget.utilisation().pausedFor)
        assertTrue(budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess)
    }

    @Test
    fun `utilisation is empty and flush succeeds`() = runTest {
        val utilisation = budget.utilisation()
        assertTrue(utilisation.windows.isEmpty())
        assertEquals(Duration.ZERO, utilisation.pausedFor)
        assertEquals(0, utilisation.waiting)
        assertEquals(Result.success(Unit), budget.flush())
    }

    @Test
    fun `close changes nothing`() = runTest {
        budget.close()
        budget.close()
        assertTrue(budget.acquire(1, Lane.Interactive, Duration.ZERO).isSuccess)
        assertEquals(Result.success(Unit), budget.flush())
    }

    @Test
    fun `toString names the object`() {
        assertEquals("CallBudget.Unlimited", budget.toString())
    }
}
