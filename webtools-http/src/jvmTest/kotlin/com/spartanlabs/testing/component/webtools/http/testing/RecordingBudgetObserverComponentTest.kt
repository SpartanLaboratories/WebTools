package com.spartanlabs.testing.component.webtools.http.testing

import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.BudgetEvent
import com.spartanlabs.webtools.http.BudgetObserver
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.PauseSource
import com.spartanlabs.webtools.http.testing.RecordingBudgetObserver
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `RecordingBudgetObserver`: records in order; `eventsOf<E>()` filters by type; `clear()`; thread-safe (1,000 events from 8 threads all
 * recorded); `onEach` runs after the event is recorded and its exception propagates; `toString()`; usable as `observer` of a budget. No opt-in
 * anywhere: the test is a consumer of the fixture.
 */
class RecordingBudgetObserverComponentTest {

    private val granted = BudgetEvent.Granted(Lane.Tracker, 1, Duration.ZERO)
    private val paused = BudgetEvent.Paused(1_000L, 30.seconds, PauseSource.RetryAfter)
    private val stop = BudgetEvent.HardStop("day", 2_000L)

    @Test
    fun `records every event in order`() {
        val observer: BudgetObserver = RecordingBudgetObserver()
        observer.onEvent(granted)
        observer.onEvent(paused)
        observer.onEvent(stop)
        val recording = observer as RecordingBudgetObserver
        assertEquals(listOf<BudgetEvent>(granted, paused, stop), recording.events)
        assertSame(paused, recording.events[1])
    }

    @Test
    fun `eventsOf filters by type in order`() {
        val observer = RecordingBudgetObserver()
        val second = BudgetEvent.Granted(Lane.Interactive, 2, 1.seconds)
        listOf(granted, paused, second, stop).forEach(observer::onEvent)
        assertEquals(listOf(granted, second), observer.eventsOf<BudgetEvent.Granted>())
        assertEquals(listOf(paused), observer.eventsOf<BudgetEvent.Paused>())
        assertEquals(emptyList<BudgetEvent.Refused>(), observer.eventsOf<BudgetEvent.Refused>())
    }

    @Test
    fun `clear forgets every recorded event`() {
        val observer = RecordingBudgetObserver()
        observer.onEvent(granted)
        observer.clear()
        assertEquals(emptyList<BudgetEvent>(), observer.events)
        observer.onEvent(stop)
        assertEquals(listOf<BudgetEvent>(stop), observer.events)
    }

    @Test
    fun `events is a snapshot`() {
        val observer = RecordingBudgetObserver()
        observer.onEvent(granted)
        val before = observer.events
        observer.onEvent(stop)
        assertEquals(1, before.size)
        assertEquals(2, observer.events.size)
    }

    @Test
    fun `is thread-safe - 1000 events from 8 threads are all recorded`() {
        val observer = RecordingBudgetObserver()
        val threads = (0 until 8).map { t ->
            Thread { repeat(125) { i -> observer.onEvent(BudgetEvent.Granted(Lane("t$t"), i + 1, Duration.ZERO)) } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(1_000, observer.events.size)
        (0 until 8).forEach { t -> assertEquals(125, observer.events.count { (it as BudgetEvent.Granted).lane == Lane("t$t") }) }
    }

    @Test
    fun `onEach runs after the event is recorded and its exception propagates`() {
        var seenWhenCalled = -1
        lateinit var observer: RecordingBudgetObserver
        observer = RecordingBudgetObserver { seenWhenCalled = observer.events.size }
        observer.onEvent(granted)
        assertEquals(1, seenWhenCalled)

        val throwing = RecordingBudgetObserver { throw IllegalStateException("from onEach") }
        val failure = assertFailsWith<IllegalStateException> { throwing.onEvent(stop) }
        assertEquals("from onEach", failure.message)
        assertEquals(listOf<BudgetEvent>(stop), throwing.events, "the event was not recorded before onEach ran")
    }

    @Test
    fun `toString prints the event count`() {
        val observer = RecordingBudgetObserver()
        assertEquals("RecordingBudgetObserver(events=0)", observer.toString())
        observer.onEvent(granted)
        observer.onEvent(stop)
        assertEquals("RecordingBudgetObserver(events=2)", observer.toString())
    }

    @Test
    fun `is usable as the observer of a budget`() = runTest {
        val clock = virtualClock()
        val recording = RecordingBudgetObserver()
        budgetOn(clock, minuteWindow(2)) { observer = recording }.use { budget ->
            budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            assertTrue(recording.events.isNotEmpty())
            val grant = assertIs<BudgetEvent.Granted>(recording.events.last())
            assertEquals(Lane.Tracker, grant.lane)
        }
    }
}
