package com.spartanlabs.webtools.http

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Duration

/** The constant message `acquire` throws with for a `cost` below 1; tests assert on its text. */
internal const val COST_MESSAGE: String = "cost must be at least 1"

/** The constant message `acquire` throws with for a negative `maxWait`; tests assert on its text. */
internal const val MAX_WAIT_MESSAGE: String = "maxWait must not be negative"

/** The checks `acquire` makes of its arguments, shared by every implementation the module supplies. */
internal fun requireValidAcquire(cost: Int, maxWait: Duration) {
    require(cost >= 1) { COST_MESSAGE }
    require(!maxWait.isNegative()) { MAX_WAIT_MESSAGE }
}

/**
 * Grants permits under a provider's rate windows and lane shares, pauses on server signals and, when a store is supplied, holds a
 * hard daily stop across a restart.
 *
 * The contract an implementation (the supplied `WindowedCallBudget`, [CallBudget.Unlimited], a consumer's own budget or a fake)
 * meets:
 * 1. **Charged at the grant.** A granted permit has been charged to the budget's counts; the budget holds no lock, semaphore or
 *    lease for it. A permit never completed is simply spent.
 * 2. **Fail fast.** [acquire] returns `Result.failure(HttpFailure.BudgetExhausted(wait, reason, window))` when the wait the call
 *    would need exceeds `maxWait` ([Duration.INFINITE] waits as long as the plan needs; zero fails unless the call fits now),
 *    charging nothing. `wait` is the wait the call would have had; [Duration.INFINITE] for `CostExceedsLimit`.
 * 3. **First come first served** within a lane. A call that would wait is queued behind the lane's earlier waiters.
 * 4. **Cancellation.** The caller's own cancellation is thrown as `CancellationException`; a cancelled wait is refunded and the
 *    waiters behind it are re-planned. A caller that is already cancelled gets its `CancellationException` even after [close],
 *    and never receives a permit.
 * 5. **Closing.** After [close], [acquire] and every call waiting return `Result.failure(HttpFailure.Closed("budget"))` as a
 *    value, never as a thrown cancellation. [close] is idempotent, non-suspending, never throws and may be called from any
 *    thread. After it, [flush], [utilisation] and `complete()`/`release()` on permits granted before the close keep working.
 * 6. **Permits.** [Permit.complete], `complete(signals)` and [Permit.release] are idempotent and run under `NonCancellable`, so
 *    they take effect from a cancelled coroutine.
 * 7. **Utilisation.** [utilisation] is a snapshot of the current window of each rate window, the pause remaining and the number
 *    of waiting calls.
 * 8. **Flush.** [flush] writes the exact counts to the budget's store and returns the failure instead of a success when it could
 *    not. The default body succeeds.
 * 9. **Programmer errors.** A `cost` below 1 and a negative `maxWait` throw `IllegalArgumentException`.
 * 10. **Concurrency.** Every member may be called concurrently from any thread and any number of coroutines.
 *
 * **Experimental (implement).** Implementing needs `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`; calling the port,
 * passing it, and the supplied `WindowedCallBudget` and [Unlimited] are Stable Core.
 */
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public interface CallBudget : AutoCloseable {
    /**
     * Charges [cost] units to [lane] now, or once a slot within [maxWait] comes (contract points 1 to 5).
     *
     * @param cost the units to charge; at least 1.
     * @param lane the lane to charge.
     * @param maxWait the longest the call may wait for its slot; [Duration.INFINITE] waits as long as the plan needs.
     * @return a permit; `Result.failure` of [HttpFailure.BudgetExhausted] when the call would wait longer than [maxWait] (or can
     *   never fit); `Result.failure` of [HttpFailure.Closed] (component `"budget"`) after [close].
     * @throws IllegalArgumentException if [cost] is below 1 or [maxWait] is negative.
     * @throws kotlinx.coroutines.CancellationException if the caller is cancelled.
     */
    public suspend fun acquire(cost: Int, lane: Lane, maxWait: Duration): Result<Permit>

    /**
     * Reads the budget (contract point 7).
     *
     * @return a snapshot of the current period of each window, the pause remaining and the number of waiting calls.
     */
    public suspend fun utilisation(): BudgetUtilisation

    /**
     * Writes the exact counts to the store (contract point 8). The default succeeds, as a budget without a store has nothing to
     * write.
     *
     * @return success, or why the write failed.
     */
    public suspend fun flush(): Result<Unit> = Result.success(Unit)

    /** Marks the budget closed (contract point 5). Idempotent, non-suspending, does not throw. The default does nothing. */
    override fun close() {}

    /**
     * The budget that grants everything at once.
     *
     * **Stable Core.** Final, stateless and shared; usable without the opt-in. Ignores signals and [close]; [flush] succeeds;
     * [utilisation] is empty (no windows, no pause, nothing waiting); it checks its arguments as the real budget does, and a
     * cancelled caller gets its `CancellationException`. `toString()` is `CallBudget.Unlimited`.
     */
    public object Unlimited : CallBudget {
        override suspend fun acquire(cost: Int, lane: Lane, maxWait: Duration): Result<Permit> {
            requireValidAcquire(cost, maxWait)
            currentCoroutineContext().ensureActive()
            return Result.success(UnlimitedPermit(cost, lane))
        }

        override suspend fun utilisation(): BudgetUtilisation = BudgetUtilisation(emptyList(), Duration.ZERO, 0)
        /** Returns the fixed text `CallBudget.Unlimited`, for diagnostics and logs. */
        override fun toString(): String = "CallBudget.Unlimited"
    }
}

/** The permit [CallBudget.Unlimited] grants: completing and releasing it change nothing. */
private class UnlimitedPermit(override val cost: Int, override val lane: Lane) : Permit {
    override suspend fun complete(signals: RateSignals) {}
    override suspend fun release() {}
    override fun toString(): String = "Permit(lane=${lane.name}, cost=$cost)"
}
