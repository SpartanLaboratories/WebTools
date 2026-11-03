package com.spartanlabs.testing.gating.webtools.http

import com.spartanlabs.webtools.http.CacheKey
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CaffeineResponseCache
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.ResponseCache
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 1 - the supplied cache as a consumer sees it, compiled **without any opt-in** and on the production defaults:
 * `CaffeineResponseCache { maxWeight = 10 }` is a `ResponseCache`; one `getOrLoad` round trip under `runBlocking` within a short timeout; a
 * second call is served as a hit (the loader ran once); `close()` twice; `getOrLoad` after `close()` is `Closed("cache")` and does not run the
 * loader; `toString()` names `maxWeight` and `closed`.
 */
class CaffeineResponseCacheConsumerGatingTest {

    private val key = CacheKey<String>("endpoint", listOf("id" to "1"))
    private val policy = CachePolicy(10.minutes)

    @Test
    fun `the supplied cache is usable as a ResponseCache without an opt-in`(): Unit = runBlocking {
        val cache: ResponseCache = CaffeineResponseCache { maxWeight = 10 }
        val loads = AtomicInteger()
        try {
            val first = withTimeout(5.seconds) { cache.getOrLoad(key, policy) { loads.incrementAndGet(); Result.success(Lookup.Found("v")) } }
            assertEquals(Lookup.Found("v"), first.getOrThrow())
            val second = withTimeout(5.seconds) { cache.getOrLoad(key, policy) { loads.incrementAndGet(); Result.success(Lookup.Found("other")) } }
            assertEquals(Lookup.Found("v"), second.getOrThrow())
            assertEquals(1, loads.get())
            assertEquals("CaffeineResponseCache(maxWeight=10, closed=false)", cache.toString())
        } finally {
            cache.close()
            cache.close()
        }
        var ran = false
        val closed = assertIs<HttpFailure.Closed>(
            withTimeout(5.seconds) { cache.getOrLoad(key, policy) { ran = true; Result.success(Lookup.Found("x")) } }.exceptionOrNull(),
        )
        assertEquals("cache", closed.component)
        assertFalse(ran)
        assertTrue(cache.toString().endsWith("closed=true)"), cache.toString())
    }
}
