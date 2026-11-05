package com.spartanlabs.webtools.http

/**
 * Receives every [BudgetEvent].
 *
 * **Experimental (implement).** Implementing needs `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`; passing an observer
 * is Stable Core, and [None] needs no opt-in. Events of one call arrive in order; events of concurrent calls may interleave,
 * and each describes itself. The callback runs after the budget's lock is released, on the thread of the call that produced
 * the event, so it may call back into the budget (through `runBlocking` or a launch, as [onEvent] is not suspend); it should
 * return quickly. An exception it throws, a `CancellationException` included, is caught and logged and never fails a grant;
 * an `Error` propagates.
 */
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public interface BudgetObserver {
    /**
     * Receives one event.
     *
     * @param event what happened.
     */
    public fun onEvent(event: BudgetEvent)

    /**
     * The observer that ignores everything.
     *
     * **Stable Core.** Final, stateless and usable without the opt-in; `toString()` is `BudgetObserver.None`.
     */
    public object None : BudgetObserver {
        override fun onEvent(event: BudgetEvent) {}
        override fun toString(): String = "BudgetObserver.None"
    }
}
