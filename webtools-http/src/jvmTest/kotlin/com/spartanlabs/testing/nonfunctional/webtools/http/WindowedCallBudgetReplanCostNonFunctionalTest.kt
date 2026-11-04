package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.Permit
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.WindowedCallBudget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val WAITERS_PER_LANE = 1_000
private const val ROUNDS = 20
private const val LIMIT = 10
private val ROUND_BOUND: Duration = 2.seconds
private val MEAN_BOUND: Duration = 250.milliseconds

/**
 * Level 4c - the cost of a re-plan at `maxQueued` scale, on real time and the production clocks: a 10-per-minute window is filled (8 `Tracker`
 * and 2 `Interactive` grants, the default shares' split), then 1,000 waiters are queued on each of the two lanes with `maxWait = INFINITE`
 * (the default `maxQueued`), launched one at a time on a single-threaded dispatcher so the arrival order is the launch order; then 20 rounds
 * each cancel the head waiter (the earliest arrival still waiting) and wait until `utilisation().waiting` has fallen by one, timing the round.
 * Every round ends inside 2 s and the mean inside 250 ms (generous bounds; the numbers are in the failure message and recorded in the as-built
 * record); afterwards no waiter is lost: every call that was not cancelled is still waiting or holds a permit, and `waiting` equals the count
 * still running. The bound is on the measured wall time, so a run on a loaded host is rerun once before it is treated as a defect. Real time
 * under `runBlocking`, bounded to 60 s.
 */
class WindowedCallBudgetReplanCostNonFunctionalTest {

    /** One `acquire` launched as its own coroutine, with what it produced recorded. */
    private class Call(val lane: Lane) {
        lateinit var job: Job
        @Volatile var result: Result<Permit>? = null
        @Volatile var thrown: Throwable? = null
    }

    @Test
    fun `cancelling the head of 2000 waiters re-plans inside the bounds and loses no waiter`(): Unit = runBlocking {
        val budget = WindowedCallBudget(listOf(RateWindow("minute", LIMIT, 1.minutes)))
        val arrivals = Dispatchers.Default.limitedParallelism(1)
        val rounds = ArrayList<Duration>(ROUNDS)
        val calls = ArrayList<Call>(2 * WAITERS_PER_LANE)
        val scope = this                                           // the waiters outlive the bound's scope: they are children of runBlocking, ended by close()
        try {
            withTimeout(60.seconds) {
                // Fill the window so that every later call waits: 8 Tracker grants (the lane's capacity under the default 0.2 Interactive floor) and 2 Interactive.
                val fillers = List(8) { budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() } + List(2) { budget.acquire(1, Lane.Interactive, Duration.ZERO).getOrThrow() }
                assertEquals(LIMIT, budget.utilisation().windows.single().used)
                repeat(WAITERS_PER_LANE) {
                    for (lane in listOf(Lane.Tracker, Lane.Interactive)) {
                        val call = Call(lane)
                        call.job = scope.launch(arrivals) {
                            try {
                                call.result = budget.acquire(1, lane, Duration.INFINITE)
                            } catch (t: Throwable) {
                                call.thrown = t
                            }
                        }
                        calls += call
                    }
                }
                awaitWaiting(budget) { it == 2 * WAITERS_PER_LANE }
                repeat(ROUNDS) { round ->
                    val before = budget.utilisation().waiting
                    val head = calls[round]
                    val mark = TimeSource.Monotonic.markNow()
                    head.job.cancel()
                    awaitWaiting(budget) { it <= before - 1 }
                    rounds += mark.elapsedNow()
                }
                fillers.forEach { it.complete() }
            }
            val mean = rounds.fold(Duration.ZERO) { acc, d -> acc + d } / ROUNDS
            val report = "rounds=${rounds.joinToString { it.toString() }}, mean=$mean"
            rounds.forEachIndexed { i, d -> assertTrue(d <= ROUND_BOUND, "round $i took $d, above $ROUND_BOUND: $report") }
            assertTrue(mean <= MEAN_BOUND, "mean $mean above $MEAN_BOUND: $report")
            // No waiter is lost: the 20 cancelled calls ended with their own cancellation, every other one still waits or was granted at a boundary.
            val cancelled = calls.take(ROUNDS)
            cancelled.forEachIndexed { i, call ->
                assertTrue(call.job.isCompleted && call.thrown is CancellationException, "cancelled call $i: thrown=${call.thrown}, result=${call.result}")
            }
            val rest = calls.drop(ROUNDS)
            val granted = rest.count { it.result?.isSuccess == true }
            val running = rest.count { !it.job.isCompleted }
            rest.forEachIndexed { i, call -> if (call.job.isCompleted && call.result?.isSuccess != true) fail("call $i ended early: thrown=${call.thrown}, result=${call.result}") }
            assertEquals(rest.size, granted + running, "waiters lost: granted=$granted, running=$running of ${rest.size}")
            assertEquals(running, budget.utilisation().waiting, "the budget's waiting count disagrees with the calls still running")
            println("WindowedCallBudgetReplanCostNonFunctionalTest: $report")
        } finally {
            budget.close()
            withTimeout(30.seconds) { calls.map { it.job }.joinAll() }
        }
    }

    /** Polls `utilisation().waiting` until [until] holds; the enclosing bound ends a poll that never does. */
    private suspend fun awaitWaiting(budget: WindowedCallBudget, until: (Int) -> Boolean) {
        while (!until(budget.utilisation().waiting)) yield()
    }
}
