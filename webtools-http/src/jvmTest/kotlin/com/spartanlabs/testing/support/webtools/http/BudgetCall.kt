package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.CallBudget
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.Permit
import com.spartanlabs.webtools.http.testing.VirtualClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * One `acquire` call launched as its own coroutine, with what it produced recorded, so a Level 2 test drives it with the virtual clock and asserts on
 * [isCompleted], [isCancelled], [completedAt], [result] and [thrown] without ever joining it (a hang then fails an assertion at once instead of timing
 * out). Built by [callAcquire], the budget's sibling of `Waiter`. A `Throwable` the call threw (the caller's `CancellationException`) is caught inside
 * the coroutine and kept in [thrown] as the instance, so a test may compare it by identity.
 */
internal class BudgetCall {
    /** The call's coroutine. */
    lateinit var job: Job

    /** The call's returned `Result`, or `null` while it runs or if it threw. */
    var result: Result<Permit>? = null

    /** What the call threw, or `null`. */
    var thrown: Throwable? = null

    /** The clock's `elapsed` when the call returned or threw, or `null` while it runs. */
    var completedAt: Duration? = null

    /** Whether the coroutine has ended, by a return, a throw or a cancellation. */
    val isCompleted: Boolean get() = job.isCompleted

    /** Whether the coroutine ended cancelled. */
    val isCancelled: Boolean get() = job.isCancelled

    /** The granted permit, or `null`. */
    val permit: Permit? get() = result?.getOrNull()

    /** The returned failure, or `null`. */
    val failure: Throwable? get() = result?.exceptionOrNull()

    /** Cancels the call's coroutine. */
    fun cancel(): Unit = job.cancel()
}

/**
 * Launches `budget.acquire(cost, lane, maxWait)` in this scope and records its outcome.
 *
 * @param budget the budget under test.
 * @param clock the clock whose `elapsed` stamps [BudgetCall.completedAt].
 * @param cost the call's cost; 1 by default.
 * @param lane the lane; [Lane.Tracker] by default.
 * @param maxWait the call's `maxWait`; `INFINITE` by default.
 * @param cancelledFirst whether the coroutine cancels itself before calling, so the call sees an already-cancelled caller.
 * @return the call; drive the clock, then read it.
 */
internal fun CoroutineScope.callAcquire(
    budget: CallBudget,
    clock: VirtualClock,
    cost: Int = 1,
    lane: Lane = Lane.Tracker,
    maxWait: Duration = Duration.INFINITE,
    cancelledFirst: Boolean = false,
): BudgetCall {
    val call = BudgetCall()
    call.job = launch {
        if (cancelledFirst) cancel()
        try {
            call.result = budget.acquire(cost, lane, maxWait)
        } catch (t: Throwable) {
            call.thrown = t
        } finally {
            call.completedAt = clock.elapsed
        }
    }
    return call
}
