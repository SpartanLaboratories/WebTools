package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.webtools.http.CacheKey
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CaffeineResponseCache
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lookup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val ROUNDS = 300
private const val CALLERS = 16

/**
 * Level 4c - no waiter hangs when `close()` races `getOrLoad`: over 300 rounds, 16 callers on `Dispatchers.Default` call `getOrLoad` on a fresh
 * cache (loaders that finish at once, that suspend briefly, or that suspend until cancelled) while another coroutine, released by the same gate
 * and after a seeded spin, closes the cache; every call returns within a bound with a `Found` or `Closed("cache")`, never hangs and never throws
 * a `CancellationException`. Probabilistic: it exercises the window between a call's insert of its future and its re-check of the closed flag, which
 * no deterministic seam reaches. Real time under `runBlocking`; each round is bounded to 10 s.
 */
class CaffeineResponseCacheCloseRaceNonFunctionalTest {

    private val policy = CachePolicy(10.minutes)

    /** One of three loaders by [index]: immediate, a short suspension, or a suspension that ends only with the load's cancellation. */
    private fun loader(index: Int): suspend () -> Result<Lookup<String>> = when (index % 3) {
        0 -> { { Result.success(Lookup.Found("fast")) } }
        1 -> { { delay(2.milliseconds); Result.success(Lookup.Found("slow")) } }
        else -> { { awaitCancellation() } }
    }

    @Volatile
    private var sink = 0

    /** Spins for [iterations] volatile writes, so the closer lands at a different point of the callers' progress on each round. */
    private fun spin(iterations: Int) {
        repeat(iterations) { sink++ }
    }

    @Test
    fun `every call returns a value or Closed while close races getOrLoad`(): Unit = runBlocking {
        val random = Random(66)
        repeat(ROUNDS) { round ->
            val cache = CaffeineResponseCache { maxWeight = 100 }
            val gate = CompletableDeferred<Unit>()
            val spinBeforeClose = random.nextInt(0, 20_000)
            // The callers and the closer are children of the bound, so a hang cancels them and fails the round instead of waiting on runBlocking.
            val results = withTimeout(10.seconds) {
                val callers = List(CALLERS) { index ->
                    async(Dispatchers.Default) {
                        gate.await()
                        try {
                            cache.getOrLoad(CacheKey<String>("race", listOf("k" to (index % 4).toString())), policy, loader(index))
                        } catch (cancelled: CancellationException) {
                            currentCoroutineContext().ensureActive()      // the bound's own cancellation passes; anything else is the cache's
                            fail("round $round, caller $index: getOrLoad threw a CancellationException: $cancelled")
                        }
                    }
                }
                val closer = launch(Dispatchers.Default) {
                    gate.await()
                    spin(spinBeforeClose)
                    cache.close()
                }
                gate.complete(Unit)
                closer.join()
                callers.awaitAll()
            }
            results.forEachIndexed { index, result ->
                result.fold(
                    onSuccess = { assertTrue(it is Lookup.Found, "round $round, caller $index: $it") },
                    onFailure = { failure ->
                        val closed = failure as? HttpFailure.Closed ?: fail("round $round, caller $index: $failure")
                        assertEquals("cache", closed.component)
                    },
                )
            }
        }
    }
}
