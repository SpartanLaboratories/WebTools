package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.webtools.http.CacheKey
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CaffeineResponseCache
import com.spartanlabs.webtools.http.Lookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val MIN_RETRIES = 2_000

/**
 * Level 4c - the failure-identity check on real threads: until at least 2,000 retries have run on `Dispatchers.Default`, each round takes a fresh
 * key and 8 to 32 joiners (seeded) that all call a loader failing fast with a **new** exception instance per load; each joiner, on its failure,
 * retries immediately and asserts that the retry's failure is not the instance it just received. No retry ever receives a previous failure. The
 * failure message records the host's core count, because the race needs at least two cores to open. Real time under `runBlocking` within 60 s.
 */
class CaffeineResponseCacheFailureIdentityNonFunctionalTest {

    private val policy = CachePolicy(10.minutes)

    /** Fails fast with a fresh instance on every load. */
    private val failing: suspend () -> Result<Lookup<String>> = { Result.failure(IllegalStateException("fails fast")) }

    @Test
    fun `no retry receives the failure its joiner just received`(): Unit = runBlocking {
        val cores = Runtime.getRuntime().availableProcessors()
        val random = Random(66)
        val cache = CaffeineResponseCache()
        try {
            withTimeout(60.seconds) {
                var retries = 0
                var round = 0
                while (retries < MIN_RETRIES) {
                    val key = CacheKey<String>("round", listOf("n" to round.toString()))
                    val joiners = random.nextInt(8, 33)
                    val pairs = List(joiners) {
                        async(Dispatchers.Default) {
                            val first = cache.getOrLoad(key, policy, failing).exceptionOrNull()
                            val retry = cache.getOrLoad(key, policy, failing).exceptionOrNull()
                            first to retry
                        }
                    }.awaitAll()
                    pairs.forEach { (first, retry) ->
                        assertNotNull(first, "round $round: the first call did not fail (cores=$cores)")
                        assertNotNull(retry, "round $round: the retry did not fail (cores=$cores)")
                        assertNotSame(first, retry, "round $round: a retry received the failure its joiner just received (cores=$cores)")
                    }
                    retries += joiners
                    round++
                }
                assertTrue(retries >= MIN_RETRIES, "only $retries retries ran (cores=$cores)")
            }
        } finally {
            cache.close()
        }
    }
}
