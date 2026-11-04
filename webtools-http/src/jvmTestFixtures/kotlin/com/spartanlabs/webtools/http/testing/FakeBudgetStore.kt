package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.BudgetSnapshot
import com.spartanlabs.webtools.http.BudgetStore
import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi
import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * A scripted [BudgetStore] for tests, the worked example of the seam. Holds one snapshot ([snapshot], preloaded through the
 * constructor), counts calls, records every successful save, injects failures and delays.
 *
 * **Stable Core, source compatibility only.** Thread-safe: one lock guards the state and is never held across a suspension. A
 * delay is `kotlinx.coroutines.delay` in the caller's context, so it is virtual when the budget runs on the test scheduler, and
 * a call the budget abandons at `storeTimeout` is cancelled in its delay and changes nothing (the store is cancellable, as the
 * port requires).
 *
 * @param initial what the first [load] returns; [BudgetSnapshot.Empty] by default.
 */
@OptIn(ExperimentalForInheritanceWebToolsApi::class)
public class FakeBudgetStore(initial: BudgetSnapshot = BudgetSnapshot.Empty) : BudgetStore {
    private val lock = Any()
    private var stored: BudgetSnapshot = initial
    private val savedList = ArrayList<BudgetSnapshot>()
    private var loads = 0
    private var saveAttempts = 0
    private var loadFailure: Failure? = null
    private var saveFailure: Failure? = null

    private class Failure(val cause: Throwable, val thrown: Boolean, var remaining: Int) {
        fun <T> deliver(): Result<T> = if (thrown) throw cause else Result.failure(cause)
    }

    /** How long [load] takes before it answers (virtual under a test scheduler). Zero by default. */
    @Volatile
    public var loadDelay: Duration = Duration.ZERO

    /** How long [save] takes before it answers. Zero by default. */
    @Volatile
    public var saveDelay: Duration = Duration.ZERO

    /** The snapshot the next [load] returns: the preloaded one, or the last one saved. */
    public val snapshot: BudgetSnapshot get() = synchronized(lock) { stored }

    /** Every snapshot saved successfully, in order. */
    public val saves: List<BudgetSnapshot> get() = synchronized(lock) { savedList.toList() }

    /** How many times [load] was called, whatever it returned. */
    public val loadCount: Int get() = synchronized(lock) { loads }

    /** How many times [save] was called, whatever it returned. */
    public val saveAttemptCount: Int get() = synchronized(lock) { saveAttempts }

    /**
     * Makes the next [times] loads fail with [cause]: returned inside `Result.failure`, or thrown when [thrown] is true (a store
     * that leaks `TimeoutCancellationException` throws it).
     *
     * @param cause the failure to deliver.
     * @param thrown whether to throw it instead of returning it; `false` by default.
     * @param times how many loads fail before the store heals; at least 1, every one by default.
     * @throws IllegalArgumentException if [times] is below 1 (a script of no failures is [heal]).
     */
    public fun failLoads(cause: Throwable, thrown: Boolean = false, times: Int = Int.MAX_VALUE) {
        require(times >= 1) { TIMES_MESSAGE }
        synchronized(lock) { loadFailure = Failure(cause, thrown, times) }
    }

    /**
     * As [failLoads], for [save]. A failed save stores nothing.
     *
     * @param cause the failure to deliver.
     * @param thrown whether to throw it instead of returning it; `false` by default.
     * @param times how many saves fail before the store heals; at least 1, every one by default.
     * @throws IllegalArgumentException if [times] is below 1 (a script of no failures is [heal]).
     */
    public fun failSaves(cause: Throwable, thrown: Boolean = false, times: Int = Int.MAX_VALUE) {
        require(times >= 1) { TIMES_MESSAGE }
        synchronized(lock) { saveFailure = Failure(cause, thrown, times) }
    }

    /** Stops failing loads and saves. */
    public fun heal() {
        synchronized(lock) { loadFailure = null; saveFailure = null }
    }

    override suspend fun load(): Result<BudgetSnapshot> {
        val failure = synchronized(lock) { loads++; consume(loadFailure) { loadFailure = null } }      // armed use chosen now, delivered after the delay
        delay(loadDelay)
        return failure?.deliver() ?: Result.success(snapshot)
    }

    override suspend fun save(snapshot: BudgetSnapshot): Result<Unit> {
        val failure = synchronized(lock) { saveAttempts++; consume(saveFailure) { saveFailure = null } }
        delay(saveDelay)                                           // a save cancelled here (the budget's timeout) stores nothing
        if (failure != null) return failure.deliver()
        synchronized(lock) { stored = snapshot; savedList += snapshot }
        return Result.success(Unit)
    }

    // Counts one use of an armed failure and disarms it when its uses are spent. Called under the lock.
    private fun consume(failure: Failure?, disarm: () -> Unit): Failure? {
        if (failure != null && failure.remaining != Int.MAX_VALUE && --failure.remaining <= 0) disarm()
        return failure
    }

    override fun toString(): String = "FakeBudgetStore(loads=$loadCount, saves=${saves.size})"

    private companion object {
        const val TIMES_MESSAGE = "times must be at least 1"
    }
}
