package com.spartanlabs.webtools.http

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The name of the budget's logger. */
internal const val BUDGET_LOGGER_NAME: String = "com.spartanlabs.webtools.http.WindowedCallBudget"

/** The longest one wait lasts before the waiter re-reads both clocks (a step, a jump or a suspend takes effect within it). Not a builder property. */
internal val MAX_WAIT_SLICE: Duration = 1.minutes

private val budgetLog: Log = logger(BUDGET_LOGGER_NAME)
private val CAP_TOLERANCE: Duration = 1.milliseconds      // the wall clock has millisecond granularity
private const val MAX_WRITE_AHEAD_ATTEMPTS = 3
private const val BUDGET_COMPONENT = "budget"
private const val DEFAULT_INTERACTIVE_SHARE = 0.2

/**
 * The supplied [CallBudget]: an integer ledger of epoch-aligned fixed rate windows with lane floors, first-come-first-served
 * waits, pauses from server signals and a hard window that survives a restart through a [BudgetStore]. Built with the factory
 * `WindowedCallBudget(windows) { }`; **Stable Core**, usable without any opt-in.
 *
 * **Windows and the guard.** Each [RateWindow]'s fixed periods start at multiples of its period since the Unix epoch (UTC); a
 * call is charged to the period the wall clock is in, and, within [Builder.boundaryGuard] of a boundary, to the adjacent period
 * too, so a provider whose clock differs from this one by up to the guard never sees a period over its limit.
 *
 * **Lanes and the floor.** [Builder.laneShares] converts a lane's share into reserved units per window (`ceil(share * limit)`,
 * at least 1): a held-back floor the other lanes can never use, never lent. A lane may use the whole window itself; a call whose
 * cost exceeds `limit - (the other lanes' reserves)` can never fit and is refused with `CostExceedsLimit`. The default reserves
 * 20 % for [Lane.Interactive]; a window of limit 1 wants `laneShares = emptyMap()`, or every other lane is refused there.
 *
 * **First come first served and the fail-fast cap.** A call that fits now is granted at once when its lane has no waiter; one
 * that would wait is planned at the earliest slot that fits, never before the lane's last waiter, and waits in its own coroutine
 * (the budget owns no scope, thread or timer). A wait longer than `maxWait` is refused at once with `BudgetExhausted` carrying
 * the wait the call would have had; a lane with [Builder.maxQueued] waiters refuses with `QueueFull`.
 *
 * **Refunds and re-plans.** A cancelled wait is refunded and the waiters behind it are planned again, each from the ledger as
 * it then stands; `release()` refunds a grant and re-plans everyone. A waiter whose new wait exceeds its cap fails at once.
 *
 * **Signals and the pause.** `complete(signals)` with a window reading raises the local count to what the server counted (never
 * lowers it); a throttled signal pauses every lane until the longest of `Retry-After`, every exhausted reading's `resetIn`, or,
 * with no timing hint, the next boundary of the exhausted windows plus the guard, each capped by [Builder.maxPause]. A later
 * hint never shortens a pause.
 *
 * **Hard windows and persistence.** A hard window refuses until its boundary once full (raising [BudgetEvent.HardStop] once per
 * period) and is saved write-ahead to the [Builder.store] in blocks of [Builder.persistAhead] grants, under the budget's lock and
 * before the grant; a restart restores the stored count at the first `acquire`, `utilisation()` or `flush()` (a stale period is
 * dropped, the current one merged additively, a stamp further ahead than [Builder.maxRestoreSkew] has its stamp and pause
 * ignored but its counts kept). A store that fails is logged and ignored: grants continue from the in-process count and the next
 * attempt waits [Builder.storeRetryInterval]; no save is made before a restore has succeeded. Call [flush] before [close] for the
 * exact final write; `close()` itself writes nothing. A snapshot store is last-writer-wins and does not count one provider across
 * several processes.
 *
 * **Time.** The [Builder.wallClock] decides where in the provider's calendar now is (indices, boundaries, the guard, the
 * snapshot's stamp); the [Builder.timeSource] measures every wait and duration. A backward step of the wall clock across a
 * boundary copies the counts of the periods it left into the current one (never under-counting a window that guards a bill); a
 * forward jump is trusted, and a waiter whose period the jump left behind is re-planned from the new reading. The known limit: a
 * clock set forward by mistake and left uncorrected can under-count a hard window.
 *
 * **Closing.** [close] is non-suspending, idempotent, thread-safe and never throws; every waiting call and every later `acquire`
 * returns `Result.failure(HttpFailure.Closed("budget"))` as a value. After it, [flush], [utilisation] and the permits' `complete`
 * and `release` keep working.
 *
 * **Thread safety.** Every member may be called from any thread and coroutine; all state is under one coroutine `Mutex`, which
 * is held across a store call for at most [Builder.storeTimeout] (a blocking store cannot be interrupted: see [BudgetStore]).
 * Events are delivered to the [Builder.observer] after the lock is released, on the thread of the call that produced them, so an
 * observer may call back into the budget.
 *
 * **Logging** (`com.spartanlabs.webtools.http.WindowedCallBudget`): `INFO` on creation (settings numbers only), on `close()` and on
 * a restore; `WARN` on a pause, a hard stop and a store failure (the cause's class name only); `DEBUG` per refusal and per
 * reconciliation; nothing per grant. Never a header value, a body, a credential, a URL or a store exception's message: the budget
 * receives [RateSignals], never a response.
 *
 * **The builder's properties and defaults:** `laneShares` (`Interactive` 0.2), `boundaryGuard` (1 s), `maxPause` (24 h),
 * `maxQueued` (1,000 per lane), `persistAhead` (10), `maxRestoreSkew` (5 min), `storeTimeout` (2 s), `storeRetryInterval`
 * (30 s), `timeSource` ([TimeSource.Monotonic]), `wallClock` ([WallClock.System]), `store` ([InMemoryBudgetStore]), `observer`
 * ([BudgetObserver.None]). The caller closes the budget; the budget never closes its store or observer.
 */
public class WindowedCallBudget internal constructor(
    windows: List<RateWindow>,
    laneShares: Map<Lane, Double>,
    boundaryGuard: Duration,
    maxPause: Duration,
    private val maxQueued: Int,
    private val persistAhead: Int,
    maxRestoreSkew: Duration,
    private val storeTimeout: Duration,
    private val storeRetryInterval: Duration,
    timeSource: TimeSource,
    wallClock: WallClock,
    private val store: BudgetStore,
    private val observer: BudgetObserver,
) : CallBudget {
    init {
        validateSettings(windows, laneShares, boundaryGuard, maxPause, maxQueued, persistAhead, maxRestoreSkew, storeTimeout, storeRetryInterval)
    }

    //region state — every field below is read and written only inside lock.withLock, except closing
    private val ledger = BudgetLedger(windows, laneShares, boundaryGuard.inWholeMilliseconds, maxPause.inWholeMilliseconds)
    private val maxRestoreSkewMillis: Long = maxRestoreSkew.inWholeMilliseconds
    private val clock = LedgerClock(timeSource, wallClock)
    private val lock = Mutex()
    private val closing: CompletableJob = Job()           // the close latch: completed normally, never cancelled; isCompleted is the closed flag
    private val waiting = ArrayList<Waiter>()             // arrival order
    private var nextSeq = 0L
    private var restore: RestoreState = RestoreState.NotAttempted
    private var saveGateUntil: Duration? = null           // elapsed instant before which no write-ahead save is attempted
    //endregion

    /** The smallest window's enforced limit: the most permits that can be granted at one instant. The facades size the transport's concurrency from it. */
    public val maxBurst: Int = ledger.maxBurst

    init {
        budgetLog.info("created: windows={}, maxBurst={}, maxQueued={}, persistAhead={}", windows.size, maxBurst, maxQueued, persistAhead)
    }

    //region records
    private enum class WaiterState { Waiting, Granted, Failed, Gone }
    private enum class PermitState { Open, Completed, Released }
    private enum class RestoreMode { FirstAttemptOnly, Gated, Forced }

    private sealed interface RestoreState {
        object NotAttempted : RestoreState
        object Restored : RestoreState
        class Failed(val at: Duration, val failure: Throwable) : RestoreState
    }

    private class Waiter(val seq: Long, val cost: Int, val lane: Lane, val arrivalElapsed: Duration, val deadlineElapsed: Duration?, var plan: Plan) {
        val wake: Channel<Unit> = Channel(Channel.CONFLATED)      // "re-read your plan": sticky, lossless, value-free
        var state: WaiterState = WaiterState.Waiting
        var failure: HttpFailure? = null                          // set with state = Failed by a re-plan
    }

    private sealed interface Admission {
        class Settled(val result: Result<Permit>) : Admission
        class Queued(val waiter: Waiter) : Admission
    }

    private sealed interface Step {
        class Done(val result: Result<Permit>) : Step
        class Sleep(val wait: Duration) : Step                     // strictly positive
    }
    //endregion

    //region acquire
    override suspend fun acquire(cost: Int, lane: Lane, maxWait: Duration): Result<Permit> {
        requireValidAcquire(cost, maxWait)
        currentCoroutineContext().ensureActive()                   // a cancelled caller gets CancellationException, even after close()
        if (closing.isCompleted) return closedFailure()
        val events = ArrayList<BudgetEvent>(3)
        val admission = try {
            lock.withLock { admitLocked(cost, lane, maxWait, events) }
        } finally {
            emit(events)
        }
        return when (admission) {
            is Admission.Settled -> admission.result
            is Admission.Queued -> awaitSlot(admission.waiter)
        }
    }

    // Under the lock. Suspends (restore, write-ahead) only before the mutation that ends the section.
    private suspend fun admitLocked(cost: Int, lane: Lane, maxWait: Duration, events: MutableList<BudgetEvent>): Admission {
        currentCoroutineContext().ensureActive()
        if (closing.isCompleted) return Admission.Settled(closedFailure())
        ensureRestored(RestoreMode.Gated, events)                  // a failure is a StoreFailed event and a fail-open: the call goes on
        var attempts = 0
        while (true) {
            currentCoroutineContext().ensureActive()               // again after a save: a cancelled caller is never granted
            val now = begin(events)
            val tooBig = ledger.capacityProblem(lane, cost)        // before the slot search: a cost above capacity never fits, and the search would not end
            if (tooBig != null) return refuse(lane, cost, Duration.INFINITE, ExhaustedReason.CostExceedsLimit, tooBig.name, events)
            val queued = waiting.filter { it.lane == lane }
            val plan = ledger.plan(lane, cost, now, queued.lastOrNull()?.plan)
            val waitMillis = ledger.waitMillis(plan, now)
            if (waitMillis == 0L && queued.isEmpty()) {             // behind a waiter that is due but has not run yet, a call queues: first come first served
                if (attempts < MAX_WRITE_AHEAD_ATTEMPTS && writeAheadDue(plan.charges, cost, now)) {
                    attempts++
                    saveAhead(plan.charges, cost, events)          // suspends; then decide again with a fresh clock
                    continue
                }
                return Admission.Settled(Result.success(grantNow(lane, cost, plan, now, events)))
            }
            val wait = waitMillis.milliseconds
            if (wait > maxWait) return refuse(lane, cost, wait, plan.reason, plan.window, events)
            if (queued.size >= maxQueued) return refuse(lane, cost, wait, ExhaustedReason.QueueFull, plan.window, events)
            val deadline = if (maxWait.isInfinite()) null else now.elapsed + maxWait
            val waiter = Waiter(nextSeq++, cost, lane, now.elapsed, deadline, plan)
            ledger.reserve(plan.charges, lane, cost)
            waiting += waiter
            events += ledger.raiseHardStops(now)
            return Admission.Queued(waiter)
        }
    }

    private fun refuse(lane: Lane, cost: Int, wait: Duration, reason: ExhaustedReason, window: String?, events: MutableList<BudgetEvent>): Admission {
        events += BudgetEvent.Refused(lane, cost, wait, reason, window)
        return Admission.Settled(Result.failure(HttpFailure.BudgetExhausted(wait, reason, window)))
    }

    private fun grantNow(lane: Lane, cost: Int, plan: Plan, now: LedgerNow, events: MutableList<BudgetEvent>): Permit {
        ledger.grantDirect(plan.charges, lane, cost)
        events += BudgetEvent.Granted(lane, cost, Duration.ZERO)
        events += ledger.raiseHardStops(now)
        return WindowedPermit(cost, lane, plan.charges)
    }
    //endregion

    //region waiting
    private suspend fun awaitSlot(waiter: Waiter): Result<Permit> {
        var settled = false
        try {
            while (true) {
                val events = ArrayList<BudgetEvent>(3)
                val step = try {
                    lock.withLock { stepLocked(waiter, events) }
                } finally {
                    emit(events)
                }
                when (step) {
                    is Step.Done -> { settled = true; return step.result }
                    is Step.Sleep -> withTimeoutOrNull(minOf(step.wait, MAX_WAIT_SLICE)) {
                        // Close is listed first: it wins a tie. The result is ignored: every wake re-reads the plan under the lock, so a lost,
                        // stale or double wake costs one lock section and nothing else. The wake channel is conflated: a signal is sticky and
                        // never suspends its sender.
                        select<Unit> { closing.onJoin { }; waiter.wake.onReceive { } }
                    }
                }
            }
        } finally {
            if (!settled) withdraw(waiter)                         // CancellationException or any throwable: refund and re-plan
        }
    }

    private suspend fun stepLocked(w: Waiter, events: MutableList<BudgetEvent>): Step {
        if (w.state == WaiterState.Failed) return Step.Done(Result.failure(checkNotNull(w.failure)))
        if (closing.isCompleted) { if (w.state == WaiterState.Waiting) unlink(w); return Step.Done(closedFailure()) }     // no re-plan: everyone is leaving
        var attempts = 0
        while (true) {
            currentCoroutineContext().ensureActive()               // before any mutation, and again after a save: a cancelled caller is not granted
            val now = begin(events)                                // may re-plan (a copy, a stale period) and may fail this very waiter
            if (w.state == WaiterState.Failed) return Step.Done(Result.failure(checkNotNull(w.failure)))
            val remaining = maxOf(ledger.waitMillis(w.plan, now).milliseconds, ledger.pause?.remaining(now) ?: Duration.ZERO)
            if (remaining.isPositive()) return Step.Sleep(remaining)
            if (attempts < MAX_WRITE_AHEAD_ATTEMPTS && writeAheadDue(w.plan.charges, w.cost, now)) {
                attempts++
                saveAhead(w.plan.charges, w.cost, events)         // suspends; the caller's cancellation leaves the ledger as it was
                if (closing.isCompleted) { unlink(w); return Step.Done(closedFailure()) }
                continue                                           // decide again with a fresh clock: the save may have crossed a boundary
            }
            return grantReserved(w, now, events)
        }
    }

    // Under the lock; never suspends: the plan was re-read after the last suspension of the section.
    private fun grantReserved(w: Waiter, now: LedgerNow, events: MutableList<BudgetEvent>): Step {
        ledger.commit(w.plan.charges, w.lane, w.cost)
        waiting.remove(w)
        w.state = WaiterState.Granted
        events += BudgetEvent.Granted(w.lane, w.cost, (now.elapsed - w.arrivalElapsed).coerceAtLeast(Duration.ZERO))
        events += ledger.raiseHardStops(now)
        return Step.Done(Result.success(WindowedPermit(w.cost, w.lane, w.plan.charges)))
    }

    private fun unlink(w: Waiter) {
        ledger.unreserve(w.plan.charges, w.lane, w.cost)
        waiting.remove(w)
        w.state = WaiterState.Gone
    }

    // The waiter's own cancellation (or any throwable): refund its reservation and re-plan the waiters behind it. Never called while this coroutine holds the lock.
    private suspend fun withdraw(w: Waiter) {
        withContext(NonCancellable) {
            val events = ArrayList<BudgetEvent>(2)
            try {
                lock.withLock {
                    if (w.state == WaiterState.Waiting) {
                        val now = begin(events)                    // may re-plan, and may fail this very waiter
                        if (w.state == WaiterState.Waiting) {
                            unlink(w)
                            if (!closing.isCompleted) replan(w.seq + 1, now, events)
                        }
                    }
                }
            } finally {
                emit(events)
            }
        }
    }

    // Under the lock; never suspends. Takes back the reservations of every waiting waiter with seq >= fromSeq and plans them again in arrival order.
    private fun replan(fromSeq: Long, now: LedgerNow, events: MutableList<BudgetEvent>) {
        if (waiting.none { it.seq >= fromSeq }) return
        waiting.filter { it.seq >= fromSeq }.forEach { ledger.unreserve(it.plan.charges, it.lane, it.cost) }
        val tails = HashMap<Lane, Plan>()
        val survivors = ArrayList<Waiter>(waiting.size)
        for (w in waiting) {
            if (w.seq < fromSeq) { tails[w.lane] = w.plan; survivors += w; continue }
            val fresh = ledger.plan(w.lane, w.cost, now, tails[w.lane])
            val wait = ledger.waitMillis(fresh, now).milliseconds
            val deadline = w.deadlineElapsed
            if (deadline != null && now.elapsed + wait > deadline + CAP_TOLERANCE) {
                w.state = WaiterState.Failed
                w.failure = HttpFailure.BudgetExhausted(wait, fresh.reason, fresh.window)
                w.wake.trySend(Unit)
                events += BudgetEvent.Refused(w.lane, w.cost, wait, fresh.reason, fresh.window)
                continue
            }
            ledger.reserve(fresh.charges, w.lane, w.cost)
            if (fresh.targetWall < w.plan.targetWall) w.wake.trySend(Unit)      // only a move earlier needs a signal
            w.plan = fresh
            tails[w.lane] = fresh
            survivors += w
        }
        waiting.clear()
        waiting.addAll(survivors)
    }

    // Reads both clocks, advances the cells and expires the pause. A copy of the cells re-plans everyone, and so does a waiting plan whose reserved period the wall has left
    // behind (a forward jump, or a waiter late across a boundary): granting it would charge a period that has ended and leave the current one unprotected.
    private fun begin(events: MutableList<BudgetEvent>): LedgerNow {
        val now = clock.read()
        val folded = ledger.advance(now)                           // advance first: it must run on every reading
        if (folded || waiting.any { ledger.isStale(it.plan, now) }) replan(0L, now, events)
        return now
    }
    //endregion

    //region permits
    private inner class WindowedPermit(override val cost: Int, override val lane: Lane, private val charges: List<Charge>) : Permit {
        private var state: PermitState = PermitState.Open         // guarded by lock: idempotency without an atomic

        override suspend fun complete(signals: RateSignals) = settle { now, events ->
            if (state == PermitState.Open) {
                state = PermitState.Completed
                applySignals(charges, signals, now, events)
            }
        }

        override suspend fun release() = settle { now, events ->
            if (state == PermitState.Open) {
                state = PermitState.Released
                ledger.refundGranted(charges, lane, cost)
                replan(0L, now, events)
            }
        }

        override fun toString(): String = "Permit(lane=${lane.name}, cost=$cost)"
    }

    // The shell of complete and release: NonCancellable, so it takes effect from a cancelled coroutine; the lock wait is bounded by storeTimeout.
    private suspend fun settle(body: (LedgerNow, MutableList<BudgetEvent>) -> Unit) {
        withContext(NonCancellable) {
            val events = ArrayList<BudgetEvent>(2)
            try {
                lock.withLock { body(begin(events), events) }
            } finally {
                emit(events)
            }
        }
    }

    private fun applySignals(charges: List<Charge>, signals: RateSignals, now: LedgerNow, events: MutableList<BudgetEvent>) {
        var changed = false
        for ((name, reading) in signals.readings) {
            ledger.reconcile(charges, name, reading.remaining, now)?.let { raised ->
                events += BudgetEvent.Reconciled(raised.window, raised.serverUsed, raised.by)
                changed = true
            }
        }
        ledger.pauseHint(signals, now)?.let { hint ->
            ledger.extendPause(hint, now)?.let { pause ->
                events += BudgetEvent.Paused(pause.untilEpochMillis, pause.remaining(now), hint.source)
                changed = true
            }
        }
        if (changed) {
            replan(0L, now, events)
            events += ledger.raiseHardStops(now)
        }
    }
    //endregion

    //region the rest of the port
    override suspend fun utilisation(): BudgetUtilisation {
        val events = ArrayList<BudgetEvent>(3)
        return try {
            lock.withLock {
                ensureRestored(RestoreMode.FirstAttemptOnly, events)
                ledger.utilisation(begin(events), waiting.size)
            }
        } finally {
            emit(events)
        }
    }

    override suspend fun flush(): Result<Unit> {
        val events = ArrayList<BudgetEvent>(3)
        return try {
            lock.withLock { ensureRestored(RestoreMode.Forced, events).flatMap { writeExact(events) } }
        } finally {
            emit(events)
        }
    }

    // Under the lock, after a successful restore.
    private suspend fun writeExact(events: MutableList<BudgetEvent>): Result<Unit> {
        val snapshot = ledger.exactSnapshot(begin(events))
        return storeCall { store.save(snapshot) }
            .onSuccess { ledger.markPersisted(snapshot) }
            .onFailure { events += BudgetEvent.StoreFailed(StoreOperation.Save, it) }
    }

    override fun close() {
        // A completed Job, never a cancelled one: a waiter that returns Closed is not involved in a CancellationException at all, so nothing reaches a
        // caller (the response cache's loader included) as a cancellation. Job is thread-safe and complete() has one winner.
        if (closing.complete()) budgetLog.info("closed")           // thread-safe, idempotent, never suspends; no lock; no handler of ours runs
    }

    override fun toString(): String = "WindowedCallBudget(windows=${ledger.windows.map { it.name }}, closed=${closing.isCompleted})"
    //endregion

    //region the store
    // Under the lock. Suspends in load(); everything it changes happens after the load returned.
    private suspend fun ensureRestored(mode: RestoreMode, events: MutableList<BudgetEvent>): Result<Unit> {
        when (val state = restore) {
            RestoreState.Restored -> return Result.success(Unit)
            is RestoreState.Failed -> {
                val retry = when (mode) {
                    RestoreMode.Forced -> true
                    RestoreMode.Gated -> clock.read().elapsed - state.at >= storeRetryInterval
                    RestoreMode.FirstAttemptOnly -> false
                }
                if (!retry) return Result.failure(state.failure)
            }
            RestoreState.NotAttempted -> {}
        }
        return storeCall { store.load() }.fold(
            onSuccess = { snapshot -> applySnapshot(snapshot, events); Result.success(Unit) },
            onFailure = { failure ->
                restore = RestoreState.Failed(clock.read().elapsed, failure)
                events += BudgetEvent.StoreFailed(StoreOperation.Load, failure)
                Result.failure(failure)
            },
        )
    }

    private fun applySnapshot(snapshot: BudgetSnapshot, events: MutableList<BudgetEvent>) {
        val now = begin(events)                                    // a fresh reading: the load may have taken storeTimeout
        val outcome = ledger.restore(snapshot, now, maxRestoreSkewMillis)
        restore = RestoreState.Restored
        events += BudgetEvent.Restored(outcome.restored, outcome.dropped, outcome.ignoredForSkew)
        outcome.pause?.let { events += BudgetEvent.Paused(it.untilEpochMillis, it.remaining(now), PauseSource.Snapshot) }
        replan(0L, now, events)
        events += ledger.raiseHardStops(now)
    }

    private fun writeAheadDue(charges: List<Charge>, cost: Int, now: LedgerNow): Boolean =
        restore is RestoreState.Restored && (saveGateUntil?.let { now.elapsed >= it } ?: true) && ledger.walNeeded(charges, cost, now)

    // Under the lock. A failure is logged and ignored: it defers the next attempt and the in-process stop holds.
    private suspend fun saveAhead(charges: List<Charge>, cost: Int, events: MutableList<BudgetEvent>) {
        val snapshot = ledger.writeAheadSnapshot(charges, cost, persistAhead, clock.read())
        storeCall { store.save(snapshot) }
            .onSuccess { ledger.markPersisted(snapshot) }
            .onFailure {
                saveGateUntil = clock.read().elapsed + storeRetryInterval
                events += BudgetEvent.StoreFailed(StoreOperation.Save, it)
            }
    }

    // One store call: bounded by storeTimeout; a timeout, a dropped result and a cancellation the store leaked are failures; the caller's own cancellation propagates.
    private suspend fun <T> storeCall(block: suspend () -> Result<T>): Result<T> {
        val outcome: Result<T> = try {
            // null is a timeout, or a result the timeout's asynchronous cancellation dropped: a failure either way, never trusted as persisted.
            withTimeoutOrNull(storeTimeout) { block() } ?: Result.failure(BudgetStoreFailure(BudgetStoreFailure.TIMEOUT_MESSAGE))
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()               // rethrows when the caller itself is cancelled; otherwise the store leaked a cancellation
            Result.failure(BudgetStoreFailure(BudgetStoreFailure.CANCELLED_MESSAGE, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
        val failure = outcome.exceptionOrNull()
        if (failure is CancellationException) {                    // a cancellation returned inside the store's own Result
            currentCoroutineContext().ensureActive()
            return Result.failure(BudgetStoreFailure(BudgetStoreFailure.CANCELLED_MESSAGE, failure))
        }
        return outcome
    }
    //endregion

    //region events
    // Called after the unlock by the function that opened the section, never inside it: the Mutex is not reentrant, so an observer that called back
    // into the budget would deadlock if events were delivered under the lock.
    private fun emit(events: List<BudgetEvent>) {
        for (event in events) {
            logEvent(event)
            try {
                observer.onEvent(event)
            } catch (e: Exception) {                               // includes CancellationException: onEvent is not suspend, so it cannot be the caller's cancellation; an Error propagates
                budgetLog.warn("observer failed: {}", e::class.simpleName)
            }
        }
    }

    private fun logEvent(event: BudgetEvent) {
        when (event) {
            is BudgetEvent.Refused -> budgetLog.debug("refused: lane={}, reason={}, window={}, wait={}", event.lane.name, event.reason, event.window, event.wait)
            is BudgetEvent.Reconciled -> budgetLog.debug("reconciled: window={}, serverUsed={}, raisedBy={}", event.window, event.serverUsed, event.raisedBy)
            is BudgetEvent.Paused -> budgetLog.warn("paused: source={}, length={}, untilEpochMillis={}", event.source, event.length, event.untilEpochMillis)
            is BudgetEvent.HardStop -> budgetLog.warn("hard stop: window={}, resetsAtEpochMillis={}", event.window, event.resetsAtEpochMillis)
            is BudgetEvent.StoreFailed -> budgetLog.warn("store {} failed: {}", event.operation, event.cause::class.simpleName)
            is BudgetEvent.Restored -> budgetLog.info("restored: windows={}, dropped={}, ignoredForSkew={}", event.windowsRestored, event.windowsDropped, event.ignoredForSkew)
            is BudgetEvent.Granted -> {}                           // one line per call is noise; the observer has it
            else -> {}                                             // a leaf added later
        }
    }
    //endregion

    private fun <T> closedFailure(): Result<T> = Result.failure(HttpFailure.Closed(BUDGET_COMPONENT))

    /** Collects the properties of a [WindowedCallBudget]. Not thread-safe; use it only inside the factory's lambda. **Stable Core.** */
    @WebToolsDsl
    public class Builder internal constructor() {
        /** Each lane's share of every window as a held-back floor (never lent), `ceil` to units, at least 1 per positive share. Each in `(0, 1]`; the reserved units must fit every window. `Interactive` 0.2 by default — set `emptyMap()` for a window of limit 1. */
        public var laneShares: Map<Lane, Double> = mapOf(Lane.Interactive to DEFAULT_INTERACTIVE_SHARE)

        /** A grant within this of a boundary is charged to both adjacent windows. 1 s by default; not negative; at most a quarter of the shortest period; truncated to whole milliseconds. */
        public var boundaryGuard: Duration = 1.seconds

        /** The cap on any pause. 24 h by default; positive and finite. */
        public var maxPause: Duration = 24.hours

        /** The most calls that may wait on each lane; a lane at the cap refuses with `QueueFull`. 1,000 by default; not negative. */
        public var maxQueued: Int = 1_000

        /** A hard window is saved write-ahead in blocks of this many grants. 10 by default; at least 1 (1 saves every grant). A crash over-counts by at most `persistAhead - 1`. */
        public var persistAhead: Int = 10

        /** A snapshot stamped further ahead than this has its stamp and pause ignored (its counts are kept). 5 minutes by default; not negative. */
        public var maxRestoreSkew: Duration = 5.minutes

        /** Bounds each store call. 2 s by default; positive. A blocking store cannot be interrupted: see [BudgetStore]. */
        public var storeTimeout: Duration = 2.seconds

        /** After a failed restore or a failed write-ahead save, the next attempt waits this long. 30 s by default; not negative. */
        public var storeRetryInterval: Duration = 30.seconds

        /** The clock of every wait and duration; one mark is taken at construction. [TimeSource.Monotonic] by default. */
        public var timeSource: TimeSource = TimeSource.Monotonic

        /** The clock that decides window indices and boundaries. [WallClock.System] by default; its reading may step. */
        public var wallClock: WallClock = WallClock.System

        /** Where hard windows and the pause survive a restart. An [InMemoryBudgetStore] by default (survives nothing). */
        public var store: BudgetStore = InMemoryBudgetStore()

        /** Receives every event. [BudgetObserver.None] by default. */
        public var observer: BudgetObserver = BudgetObserver.None
    }
}

private fun validateSettings(
    windows: List<RateWindow>, laneShares: Map<Lane, Double>, boundaryGuard: Duration, maxPause: Duration, maxQueued: Int,
    persistAhead: Int, maxRestoreSkew: Duration, storeTimeout: Duration, storeRetryInterval: Duration,
) {
    require(windows.isNotEmpty()) { "windows must not be empty" }
    require(windows.map { it.name }.toSet().size == windows.size) { "window names must be distinct" }
    require(laneShares.values.all { it.isFinite() && it > 0.0 && it <= 1.0 }) { "laneShares must each be above 0 and at most 1" }
    require(windows.all { w -> laneShares.values.sumOf { reservedUnits(it, w.limit) } <= w.limit }) { "laneShares reserve more units than a window holds" }
    require(!boundaryGuard.isNegative() && boundaryGuard <= windows.minOf { it.period } / 4) {
        "boundaryGuard must be between zero and a quarter of the shortest period"
    }
    require(maxPause.isPositive() && maxPause.isFinite()) { "maxPause must be positive and finite" }
    require(maxQueued >= 0) { "maxQueued must not be negative" }
    require(persistAhead >= 1) { "persistAhead must be at least 1" }
    require(!maxRestoreSkew.isNegative()) { "maxRestoreSkew must not be negative" }
    require(storeTimeout.isPositive()) { "storeTimeout must be positive" }
    require(!storeRetryInterval.isNegative()) { "storeRetryInterval must not be negative" }
}

/**
 * Builds a [WindowedCallBudget]. **Stable Core.** `WindowedCallBudget(listOf(RateWindow("minute", 60, 1.minutes) { margin = 10 })) { maxQueued = 200 }`; without the lambda every property has
 * its default. The caller closes the budget; the budget never closes its `store` or `observer`.
 *
 * @param windows the provider's windows: not empty, distinct names.
 * @param configure assigns the properties to change.
 * @return the budget.
 * @throws IllegalArgumentException if a window list or a number is outside its range; the message names the property and never a value.
 */
public fun WindowedCallBudget(windows: List<RateWindow>, configure: WindowedCallBudget.Builder.() -> Unit = {}): WindowedCallBudget {
    val b = WindowedCallBudget.Builder().apply(configure)
    return WindowedCallBudget(
        windows.toList(), b.laneShares.toMap(), b.boundaryGuard, b.maxPause, b.maxQueued, b.persistAhead, b.maxRestoreSkew, b.storeTimeout, b.storeRetryInterval,
        b.timeSource, b.wallClock, b.store, b.observer,
    )
}
