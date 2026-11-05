package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.BudgetUtilisation
import com.spartanlabs.webtools.http.CallBudget
import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.Permit
import com.spartanlabs.webtools.http.RateSignals
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration

/**
 * A [CallBudget] that wraps [delegate] and records, per permit, the ordered calls made on it (`complete()`, `complete(signals)`, `release()`), so
 * `withPermit`'s `finally` is observable. Every other member delegates. The `@OptIn` is the line a consumer's own budget needs.
 */
@OptIn(ExperimentalForInheritanceWebToolsApi::class)
internal class PermitRecordingBudget(private val delegate: CallBudget) : CallBudget {
    private val granted = CopyOnWriteArrayList<RecordedPermit>()

    /** Every permit granted through this budget, in order. */
    val permits: List<RecordedPermit> get() = granted.toList()

    /** The recorded calls of the one permit granted so far; fails when there is not exactly one. */
    val calls: List<String> get() = permits.single().calls

    override suspend fun acquire(cost: Int, lane: Lane, maxWait: Duration): Result<Permit> =
        delegate.acquire(cost, lane, maxWait).map { permit -> RecordedPermit(permit).also { granted += it } }

    override suspend fun utilisation(): BudgetUtilisation = delegate.utilisation()
    override suspend fun flush(): Result<Unit> = delegate.flush()
    override fun close(): Unit = delegate.close()
    override fun toString(): String = "PermitRecordingBudget($delegate)"

    /** A permit that records each call before passing it to the wrapped one. */
    inner class RecordedPermit(private val wrapped: Permit) : Permit {
        private val recorded = CopyOnWriteArrayList<String>()

        /** The calls made on this permit, in order: `complete()`, `complete(signals)` or `release()`. */
        val calls: List<String> get() = recorded.toList()

        override val cost: Int get() = wrapped.cost
        override val lane: Lane get() = wrapped.lane

        override suspend fun complete() {
            recorded += "complete()"
            wrapped.complete()
        }

        override suspend fun complete(signals: RateSignals) {
            recorded += "complete(signals)"
            wrapped.complete(signals)
        }

        override suspend fun release() {
            recorded += "release()"
            wrapped.release()
        }

        override fun toString(): String = "RecordedPermit($wrapped, calls=$recorded)"
    }
}
