package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.webtools.http.CacheKey
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CaffeineResponseCache
import com.spartanlabs.webtools.http.Lookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val MAX_WEIGHT = 100L
private const val KEYS = 20_000
private const val WORKERS = 8

/**
 * Level 4c - the size bound on real threads with the common pool (the cache's production defaults but `maxWeight = 100`): 8 workers on
 * `Dispatchers.Default` insert 20,000 distinct keys of seeded weights 1 to 5; afterwards `cleanUp()` is looped until `weightedSize()` is stable
 * (two equal readings, bounded attempts, a short sleep between), and the stable weighted size is at most `maxWeight`, `evictions` is positive, and
 * no stored entry exceeds `maxWeight`. Real time under `runBlocking` within 60 s.
 */
class CaffeineResponseCacheSizeBoundNonFunctionalTest {

    private val policy = CachePolicy(10.minutes)

    private fun key(index: Int): CacheKey<String> = CacheKey("bound", listOf("i" to index.toString()))

    /** Drains Caffeine's maintenance until two consecutive readings agree, or 50 attempts have run. */
    private suspend fun stableWeightedSize(cache: CaffeineResponseCache): Long {
        var previous = -1L
        repeat(50) {
            cache.cleanUp()
            val reading = cache.weightedSize()
            if (reading == previous) return reading
            previous = reading
            delay(20.milliseconds)
        }
        return previous
    }

    @Test
    fun `20000 inserts from 8 workers leave the weighted size within the bound`(): Unit = runBlocking {
        val weights = Random(66).let { random -> IntArray(KEYS) { random.nextInt(1, 6) } }
        val cache = CaffeineResponseCache { maxWeight = MAX_WEIGHT }
        try {
            withTimeout(60.seconds) {
                (0 until WORKERS).map { worker ->
                    async(Dispatchers.Default) {
                        for (index in worker until KEYS step WORKERS) {
                            cache.getOrLoad(key(index), policy) { Result.success(Lookup.Found("v$index", weights[index])) }.getOrThrow()
                        }
                    }
                }.awaitAll()
                val weightedSize = stableWeightedSize(cache)
                assertTrue(weightedSize <= MAX_WEIGHT, "weightedSize $weightedSize exceeds maxWeight $MAX_WEIGHT")
                assertTrue(cache.stats().evictions > 0L, "no size eviction was counted: ${cache.stats()}")
                val storedWeights = (0 until KEYS).mapNotNull { cache.futureOf(key(it))?.getNow(null)?.weight }
                assertTrue(storedWeights.isNotEmpty(), "nothing is stored")
                storedWeights.forEach { assertTrue(it <= MAX_WEIGHT, "a stored entry weighs $it") }
            }
        } finally {
            cache.close()
        }
    }
}
