package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.Permit
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.WindowedCallBudget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val CALLS = 400
private const val CANCELLED = 120            // 30 %
private const val LIMIT = 20

/**
 * Level 4c - contention on real threads and the production clocks: 400 coroutines on `Dispatchers.Default` call `acquire` with
 * `maxWait = INFINITE` against a 20-per-second window with a 100 ms guard (`laneShares` empty, so the limit is the plain 20), while 120 of
 * them (30 %, seeded) are cancelled at seeded offsets of up to 50 ms each. Over the run the grants never exceed `limit × (whole seconds
 * spanned + 1)`, counting the whole-second periods the run touched from the first call to the last completion; every call that was not
 * cancelled is granted; afterwards no call waits and the ledger holds no planned units; the whole run ends inside a 60-second bound, so a
 * deadlock fails it. A run that fails is a defect, not a flake, so it is not rerun. Real time under `runBlocking`.
 */
class WindowedCallBudgetContentionNonFunctionalTest {

    /** One `acquire` launched as its own coroutine, with what it produced recorded. */
    private class Call {
        lateinit var job: Job
        @Volatile var result: Result<Permit>? = null
        @Volatile var thrown: Throwable? = null
    }

    @Test
    fun `400 contending callers never overspend a second and every uncancelled call is granted`(): Unit = runBlocking {
        // Fixed seed (the issue number) so the cancelled set and its offsets, and any failure, are reproducible.
        val random = Random(67)
        val toCancel = (0 until CALLS).shuffled(random).take(CANCELLED)
        val cancelOffsets = List(CANCELLED) { random.nextInt(0, 50).milliseconds }
        val budget = WindowedCallBudget(listOf(RateWindow("second", LIMIT, 1.seconds))) { laneShares = emptyMap(); boundaryGuard = 100.milliseconds }
        val calls = List(CALLS) { Call() }
        val gate = CompletableDeferred<Unit>()
        val startMillis = System.currentTimeMillis()
        try {
            withTimeout(60.seconds) {
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
                val canceller = launch(Dispatchers.Default) {
                    gate.await()
                    toCancel.forEachIndexed { i, index -> delay(cancelOffsets[i]); calls[index].job.cancel() }
                }
                gate.complete(Unit)
                canceller.join()
                calls.map { it.job }.joinAll()
            }
            val endMillis = System.currentTimeMillis()
            val periods = endMillis / 1_000 - startMillis / 1_000 + 1
            val cancelled = toCancel.toSet()
            var granted = 0
            calls.forEachIndexed { index, call ->
                val thrown = call.thrown
                if (thrown != null) {
                    assertTrue(thrown is CancellationException && index in cancelled, "call $index threw $thrown (cancelled: ${index in cancelled})")
                    return@forEachIndexed
                }
                val result = call.result ?: run {
                    // A call cancelled before its coroutine first ran recorded nothing: that is its own cancellation too.
                    assertTrue(index in cancelled && call.job.isCancelled, "call $index ended with neither a result nor a throwable")
                    return@forEachIndexed
                }
                val permit = result.getOrElse { fail("call $index failed: $it") }
                granted++
                permit.complete()
            }
            assertTrue(granted <= LIMIT * periods, "$granted grants over $periods whole-second period(s), above ${LIMIT * periods}")
            calls.forEachIndexed { index, call ->
                if (index !in cancelled) assertTrue(call.result?.isSuccess == true, "uncancelled call $index was not granted: thrown=${call.thrown}, result=${call.result}")
            }
            val utilisation = budget.utilisation()
            assertEquals(0, utilisation.waiting, "$utilisation")
            utilisation.windows.forEach { assertEquals(0, it.queued, "planned units left in $it") }
            println("WindowedCallBudgetContentionNonFunctionalTest: granted=$granted over $periods period(s) in ${endMillis - startMillis} ms")
        } finally {
            budget.close()
        }
    }
}
