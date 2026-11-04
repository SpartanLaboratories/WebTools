package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.Permit
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.WindowedCallBudget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val ROUNDS = 20
private const val CALLS = 300
private const val CANCELLED = 60

/**
 * Level 4c - no waiter hangs when `close()` races `acquire` and cancellation: over 20 rounds, 300 calls on `Dispatchers.Default` (real threads,
 * the production clocks) call `acquire` with `maxWait = INFINITE` on a fresh budget whose one window admits 1 call per minute (`laneShares`
 * empty, so every call fits the window and all but one wait), while a plain `Thread` closes the budget after a seeded spin and a third thread
 * cancels 60 of the calls at seeded spin offsets. Every call ends within a 30-second bound with a permit granted before the close (at most one
 * per minute period the round touched), `Closed("budget")` or its own `CancellationException` (only a cancelled call may see one), never
 * anything else and never a hang; afterwards `utilisation().waiting == 0` and the ledger holds no planned units. Probabilistic: it exercises
 * the window between a call's lock section and its wait, which no deterministic seam reaches; a run that fails is a defect, not a flake, so it
 * is not rerun. Real time under `runBlocking`; each round is bounded to 30 s.
 */
class WindowedCallBudgetCloseRaceNonFunctionalTest {

    /** One `acquire` launched as its own coroutine, with what it produced recorded (a `Throwable` by instance), never joined before the bound. */
    private class Call {
        lateinit var job: Job
        @Volatile var result: Result<Permit>? = null
        @Volatile var thrown: Throwable? = null
    }

    @Volatile
    private var sink = 0

    /** Spins for [iterations] volatile writes: a few microseconds to a few milliseconds, spanning the callers' section-to-wait window. */
    private fun spin(iterations: Int) {
        repeat(iterations) { sink++ }
    }

    @Test
    fun `every call ends with a permit, Closed or its own cancellation while close races acquire and cancellation`(): Unit = runBlocking {
        // Fixed seed (the issue number) so a failing round is reproducible.
        val random = Random(67)
        repeat(ROUNDS) { round ->
            val budget = WindowedCallBudget(listOf(RateWindow("minute", 1, 1.minutes))) { laneShares = emptyMap() }
            val gate = CompletableDeferred<Unit>()
            val threadGate = CountDownLatch(1)
            val spinBeforeClose = random.nextInt(0, 20_000)
            val toCancel = (0 until CALLS).shuffled(random).take(CANCELLED)
            val cancelOffsets = List(CANCELLED) { random.nextInt(0, 2_000) }
            val startMinute = System.currentTimeMillis() / 60_000
            val calls = List(CALLS) { Call() }
            // The callers are children of the bound, so a hang cancels them and fails the round instead of waiting on runBlocking.
            withTimeout(30.seconds) {
                calls.forEach { call ->
                    call.job = launch(Dispatchers.Default) {
                        try {
                            gate.await()                           // inside the try: a call cancelled at the gate records its own CancellationException
                            call.result = budget.acquire(1, Lane.Tracker, Duration.INFINITE)
                        } catch (t: Throwable) {
                            call.thrown = t
                        }
                    }
                }
                val closer = Thread {
                    threadGate.await()
                    spin(spinBeforeClose)
                    budget.close()
                }
                val canceller = Thread {
                    threadGate.await()
                    toCancel.forEachIndexed { i, index -> spin(cancelOffsets[i]); calls[index].job.cancel() }
                }
                closer.start()
                canceller.start()
                threadGate.countDown()
                gate.complete(Unit)
                closer.join()
                canceller.join()
                calls.map { it.job }.joinAll()
            }
            val endMinute = System.currentTimeMillis() / 60_000
            val cancelled = toCancel.toSet()
            var granted = 0
            calls.forEachIndexed { index, call ->
                val thrown = call.thrown
                if (thrown != null) {
                    assertTrue(thrown is CancellationException, "round $round, call $index threw $thrown")
                    assertTrue(index in cancelled, "round $round, call $index was not cancelled yet threw $thrown")
                    return@forEachIndexed
                }
                val result = call.result ?: run {
                    // A call cancelled before its coroutine first ran recorded nothing: that is its own cancellation too.
                    assertTrue(index in cancelled && call.job.isCancelled, "round $round, call $index ended with neither a result nor a throwable")
                    return@forEachIndexed
                }
                result.fold(
                    onSuccess = { permit -> granted++; permit.complete() },
                    onFailure = { failure ->
                        val closed = failure as? HttpFailure.Closed ?: fail("round $round, call $index: $failure")
                        assertEquals("budget", closed.component, "round $round, call $index")
                    },
                )
            }
            assertTrue(granted <= 1 + (endMinute - startMinute), "round $round granted $granted permits over ${endMinute - startMinute + 1} minute period(s)")
            val utilisation = budget.utilisation()
            assertEquals(0, utilisation.waiting, "round $round: $utilisation")
            utilisation.windows.forEach { assertEquals(0, it.queued, "round $round: planned units left in $it") }
        }
    }
}
