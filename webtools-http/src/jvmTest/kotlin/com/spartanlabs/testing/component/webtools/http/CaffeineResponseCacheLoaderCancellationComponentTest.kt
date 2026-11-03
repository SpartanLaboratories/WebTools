package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.call
import com.spartanlabs.testing.support.webtools.http.key
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.LOADER_CANCELLED_MESSAGE
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - a loader's own cancellation on a virtual clock. For a loader whose `withTimeout` expires, one that throws a `CancellationException`
 * and one that returns `Result.failure(CancellationException())`: the waiter's result is a failure that is an `IllegalStateException` with the
 * constant message and a `CancellationException` cause, not `Closed` and not a thrown `CancellationException`; the waiter's job is neither
 * cancelled nor failed; a second call for the same key runs the loader again; a call for another key still works and the cache is open;
 * `failedLoads` counts each once.
 */
class CaffeineResponseCacheLoaderCancellationComponentTest {

    private val policy = CachePolicy(10.minutes)

    private class Cancelling(val label: String, val loader: suspend () -> Result<Lookup<String>>)

    private val loaders = listOf(
        Cancelling("timing out") { withTimeout(1.seconds) { awaitCancellation() } },
        Cancelling("throwing") { throw CancellationException("thrown") },
        Cancelling("returning") { Result.failure(CancellationException("returned")) },
    )

    @Test
    fun `a loader's own cancellation is an IllegalStateException failure that leaves the waiter and the cache intact`() = runTest {
        val clock = virtualClock()
        cacheOn(clock).use { cache ->
            loaders.forEachIndexed { i, cancelling ->
                var runs = 0
                val counting: suspend () -> Result<Lookup<String>> = { runs++; cancelling.loader() }
                val waiter = call(cache, key("k$i"), policy, loader = counting)
                clock.advanceBy(Duration.ZERO)
                clock.advanceBy(1.seconds)

                assertTrue(waiter.isCompleted, cancelling.label)
                assertFalse(waiter.isCancelled, cancelling.label)
                assertNull(waiter.thrown, "${cancelling.label}: the waiter threw ${waiter.thrown}")
                val failure = assertIs<IllegalStateException>(waiter.failure, cancelling.label)
                assertEquals(LOADER_CANCELLED_MESSAGE, failure.message, cancelling.label)
                assertIs<CancellationException>(failure.cause, cancelling.label)
                assertFalse(failure is HttpFailure.Closed, cancelling.label)
                assertEquals(1, runs, cancelling.label)
                assertEquals((i + 1).toLong() * 2 - 1, cache.stats().failedLoads, cancelling.label)

                val again = call(cache, key("k$i"), policy, loader = counting)
                clock.advanceBy(Duration.ZERO)
                clock.advanceBy(1.seconds)
                assertEquals(2, runs, "${cancelling.label}: the second call did not run the loader again")
                assertIs<IllegalStateException>(again.failure, cancelling.label)
                assertEquals((i + 1).toLong() * 2, cache.stats().failedLoads, cancelling.label)
            }
            assertEquals(Lookup.Found("ok"), cache.getOrLoad(key("other"), policy) { Result.success(Lookup.Found("ok")) }.getOrThrow())
            assertEquals("CaffeineResponseCache(maxWeight=1000, closed=false)", cache.toString())
        }
    }
}
