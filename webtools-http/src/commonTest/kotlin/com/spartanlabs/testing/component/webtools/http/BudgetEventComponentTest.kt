package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.BudgetEvent
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.PauseSource
import com.spartanlabs.webtools.http.StoreOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - each of the seven `BudgetEvent` leaves builds and refuses what its rules say, with the property in the message;
 * `toString()` prints the fields and `StoreFailed` prints its cause's simple class name only; `Refused.window` may be `null`;
 * equality is identity; a `when` over the events with an `else` compiles.
 */
class BudgetEventComponentTest {

    private inline fun <reified E : BudgetEvent> refused(block: () -> E): String? = assertFailsWith<IllegalArgumentException> { block() }.message

    @Test
    fun `Granted builds and checks its fields`() {
        val granted = BudgetEvent.Granted(Lane.Tracker, 2, 3.seconds)
        assertEquals(Lane.Tracker, granted.lane)
        assertEquals(2, granted.cost)
        assertEquals(3.seconds, granted.waited)
        assertEquals("cost must be at least 1", refused { BudgetEvent.Granted(Lane.Tracker, 0, Duration.ZERO) })
        assertEquals("waited must not be negative", refused { BudgetEvent.Granted(Lane.Tracker, 1, (-1).seconds) })
        assertEquals("Granted(lane=Tracker, cost=2, waited=3s)", granted.toString())
    }

    @Test
    fun `Refused builds, allows a null window and checks its fields`() {
        val refused = BudgetEvent.Refused(Lane.Interactive, 1, 30.seconds, ExhaustedReason.Window, "minute")
        assertEquals("minute", refused.window)
        assertEquals(ExhaustedReason.Window, refused.reason)
        assertNull(BudgetEvent.Refused(Lane.Interactive, 1, 30.seconds, ExhaustedReason.Paused, null).window)
        assertEquals(Duration.INFINITE, BudgetEvent.Refused(Lane.Interactive, 1, Duration.INFINITE, ExhaustedReason.CostExceedsLimit, "day").wait)
        assertEquals("cost must be at least 1", refused { BudgetEvent.Refused(Lane.Tracker, 0, Duration.ZERO, ExhaustedReason.Window, null) })
        assertEquals("wait must not be negative", refused { BudgetEvent.Refused(Lane.Tracker, 1, (-1).seconds, ExhaustedReason.Window, null) })
        assertEquals("window must be null or not blank", refused { BudgetEvent.Refused(Lane.Tracker, 1, Duration.ZERO, ExhaustedReason.Window, " ") })
        assertEquals("Refused(lane=Interactive, cost=1, wait=30s, reason=Window, window=minute)", refused.toString())
    }

    @Test
    fun `Paused builds and checks its length`() {
        val paused = BudgetEvent.Paused(1_000L, 30.seconds, PauseSource.RetryAfter)
        assertEquals(1_000L, paused.untilEpochMillis)
        assertEquals(PauseSource.RetryAfter, paused.source)
        assertEquals(Duration.ZERO, BudgetEvent.Paused(0L, Duration.ZERO, PauseSource.Snapshot).length)
        assertEquals("length must not be negative", refused { BudgetEvent.Paused(1_000L, (-1).seconds, PauseSource.Boundary) })
        assertEquals("Paused(until=1000, length=30s, source=RetryAfter)", paused.toString())
    }

    @Test
    fun `HardStop builds and checks its window`() {
        val stop = BudgetEvent.HardStop("day", 86_400_000L)
        assertEquals("day", stop.window)
        assertEquals(86_400_000L, stop.resetsAtEpochMillis)
        assertEquals("window must not be blank", refused { BudgetEvent.HardStop("", 0L) })
        assertEquals("HardStop(window=day, resetsAt=86400000)", stop.toString())
    }

    @Test
    fun `Reconciled builds and checks its fields`() {
        val reconciled = BudgetEvent.Reconciled("minute", 20, 19)
        assertEquals(20, reconciled.serverUsed)
        assertEquals(19, reconciled.raisedBy)
        assertEquals("window must not be blank", refused { BudgetEvent.Reconciled(" ", 20, 19) })
        assertEquals("serverUsed must not be negative", refused { BudgetEvent.Reconciled("minute", -1, 1) })
        assertEquals("raisedBy must be at least 1", refused { BudgetEvent.Reconciled("minute", 20, 0) })
        assertEquals("Reconciled(window=minute, serverUsed=20, raisedBy=19)", reconciled.toString())
    }

    @Test
    fun `StoreFailed prints its operation and the cause's class name only`() {
        val failed = BudgetEvent.StoreFailed(StoreOperation.Save, IllegalStateException("SENTINEL-secret"))
        assertEquals(StoreOperation.Save, failed.operation)
        assertEquals("SENTINEL-secret", failed.cause.message)
        assertEquals("StoreFailed(operation=Save, cause=IllegalStateException)", failed.toString())
        assertFalse("SENTINEL" in failed.toString())
    }

    @Test
    fun `Restored builds and checks its counts`() {
        val restored = BudgetEvent.Restored(2, 1, true)
        assertEquals(2, restored.windowsRestored)
        assertEquals(1, restored.windowsDropped)
        assertTrue(restored.ignoredForSkew)
        assertEquals(0, BudgetEvent.Restored(0, 0, false).windowsRestored)
        assertEquals("windowsRestored must not be negative", refused { BudgetEvent.Restored(-1, 0, false) })
        assertEquals("windowsDropped must not be negative", refused { BudgetEvent.Restored(0, -1, false) })
        assertEquals("Restored(windowsRestored=2, windowsDropped=1, ignoredForSkew=true)", restored.toString())
    }

    @Test
    fun `equality is identity`() {
        assertNotEquals(BudgetEvent.Granted(Lane.Tracker, 1, Duration.ZERO), BudgetEvent.Granted(Lane.Tracker, 1, Duration.ZERO))
        val one = BudgetEvent.HardStop("day", 0L)
        assertEquals(one, one)
    }

    @Test
    fun `a when over the events with an else compiles`() {
        val events: List<BudgetEvent> = listOf(
            BudgetEvent.Granted(Lane.Tracker, 1, Duration.ZERO),
            BudgetEvent.Refused(Lane.Tracker, 1, Duration.ZERO, ExhaustedReason.QueueFull, null),
            BudgetEvent.Paused(0L, Duration.ZERO, PauseSource.ResetIn),
            BudgetEvent.HardStop("day", 0L),
            BudgetEvent.Reconciled("day", 1, 1),
            BudgetEvent.StoreFailed(StoreOperation.Load, RuntimeException()),
            BudgetEvent.Restored(0, 0, false),
        )
        val described = events.map { event ->
            when (event) {
                is BudgetEvent.Granted -> "granted"
                is BudgetEvent.Refused -> "refused"
                is BudgetEvent.Paused -> "paused"
                else -> "other"
            }
        }
        assertEquals(listOf("granted", "refused", "paused", "other", "other", "other", "other"), described)
    }
}
