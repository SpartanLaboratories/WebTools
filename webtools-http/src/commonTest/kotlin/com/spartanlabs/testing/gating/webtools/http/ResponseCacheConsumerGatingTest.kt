package com.spartanlabs.testing.gating.webtools.http

import com.spartanlabs.webtools.http.CacheKey
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CacheStats
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.ResponseCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// A function and a class whose default names the port: a consumer writes exactly this without any opt-in.
// Both must keep compiling in this source set, which has no opt-in.
private fun cacheOf(cache: ResponseCache = ResponseCache.Disabled): ResponseCache = cache

private class UsesCache(val cache: ResponseCache = ResponseCache.Disabled)

// The port and a key in a constructor signature. A consumer holds and passes them; it cannot implement the port here.
private class CachePorts(val cache: ResponseCache?, val key: CacheKey<String>, val policy: CachePolicy)

/**
 * Level 1 - the permanent consumer of the response cache, compiled in `commonTest` **without any opt-in**. It names
 * `ResponseCache` in a function, a class and a constructor signature, takes `ResponseCache.Disabled` as a default, builds
 * every value type, matches a `Lookup` with a `when` that has no `else`, and calls the non-suspending members of
 * `Disabled`. It cannot implement `ResponseCache`, which is the point: compiling it proves the test source set is a consumer.
 */
class ResponseCacheConsumerGatingTest {

    // The port in a function signature, and a when over the sealed Lookup with no else.
    private fun describe(cache: ResponseCache, lookup: Lookup<String>): String = when (lookup) {
        is Lookup.Found -> "found weight ${lookup.weight} via $cache"
        Lookup.Missing -> "missing via $cache"
    }

    @Test
    fun `the disabled cache is usable without the opt-in`() {
        assertSame(ResponseCache.Disabled, cacheOf())
        assertSame(ResponseCache.Disabled, UsesCache().cache)
        assertSame(ResponseCache.Disabled, CachePorts(ResponseCache.Disabled, CacheKey("endpoint"), CachePolicy(1.minutes)).cache)
        assertEquals("ResponseCache.Disabled", ResponseCache.Disabled.toString())
        assertEquals(CacheStats.Empty, ResponseCache.Disabled.stats())
        ResponseCache.Disabled.invalidate(CacheKey<String>("endpoint"))
        ResponseCache.Disabled.invalidateAll()
        ResponseCache.Disabled.close()
        assertEquals(CacheStats.Empty, ResponseCache.Disabled.stats())
    }

    @Test
    fun `every value type is constructible`() {
        val withParams: CacheKey<String> = CacheKey("endpoint", listOf("b" to "2", "a" to "1"))
        val bare: CacheKey<String> = CacheKey("endpoint")
        assertEquals(listOf("a" to "1", "b" to "2"), withParams.params)
        assertEquals(emptyList(), bare.params)

        val plain = CachePolicy(10.minutes)
        val configured = CachePolicy(10.minutes) { missTtl = 30.seconds }
        assertEquals(CachePolicy.DEFAULT_MISS_TTL, plain.missTtl)
        assertEquals(30.seconds, configured.missTtl)

        val light: Lookup<String> = Lookup.Found("value")
        val heavy: Lookup<String> = Lookup.Found("value", 7)
        val missing: Lookup<String> = Lookup.Missing
        assertEquals(1, (light as Lookup.Found).weight)
        assertEquals(7, (heavy as Lookup.Found).weight)
        assertSame(Lookup.Missing, missing)

        assertEquals(CacheStats(0L, 0L, 0L, 0L), CacheStats.Empty)
        assertEquals(3L, CacheStats(3L, 2L, 1L, 0L).hits)
    }

    @Test
    fun `a when over a lookup needs no else`() {
        assertEquals("found weight 2 via ResponseCache.Disabled", describe(ResponseCache.Disabled, Lookup.Found("v", 2)))
        assertEquals("missing via ResponseCache.Disabled", describe(ResponseCache.Disabled, Lookup.Missing))
    }
}
