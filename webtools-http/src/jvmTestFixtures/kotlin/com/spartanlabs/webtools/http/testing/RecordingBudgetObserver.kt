package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.BudgetEvent
import com.spartanlabs.webtools.http.BudgetObserver
import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [BudgetObserver] that records every event, the worked example of the seam (the `@OptIn` on this class is exactly the line a
 * consumer's own implementation needs).
 *
 * **Stable Core, source compatibility only.** Thread-safe: events from concurrent calls are appended to a copy-on-write list.
 * [onEach], when given, runs for every event after it is recorded, on the thread of the call that produced it, so a test can
 * make the observer call back into the budget. `toString()` prints the event count.
 *
 * @param onEach an extra callback per event; does nothing by default. An exception it throws propagates to the budget, which
 *   logs it.
 */
@OptIn(ExperimentalForInheritanceWebToolsApi::class)
public class RecordingBudgetObserver(private val onEach: (BudgetEvent) -> Unit = {}) : BudgetObserver {
    private val recorded = CopyOnWriteArrayList<BudgetEvent>()

    /** Every event so far, in the order they arrived; a snapshot. */
    public val events: List<BudgetEvent> get() = recorded.toList()

    /** The recorded events of type [E], in order. */
    public inline fun <reified E : BudgetEvent> eventsOf(): List<E> = events.filterIsInstance<E>()

    /** Forgets every recorded event. */
    public fun clear() {
        recorded.clear()
    }

    override fun onEvent(event: BudgetEvent) {
        recorded += event
        onEach(event)
    }

    override fun toString(): String = "RecordingBudgetObserver(events=${recorded.size})"
}
