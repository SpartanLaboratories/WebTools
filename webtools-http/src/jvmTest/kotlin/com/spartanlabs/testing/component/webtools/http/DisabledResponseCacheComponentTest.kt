package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.Boom
import com.spartanlabs.webtools.http.CacheKey
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CacheStats
import com.spartanlabs.webtools.http.LOADER_CANCELLED_MESSAGE
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.ResponseCache
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `ResponseCache.Disabled`: every call runs its own loader (two sequential calls and ten concurrent callers run two
 * and ten loaders; equal keys share nothing); the loader runs on the caller's coroutine (it sees the caller's `CoroutineName`);
 * `stats()` stays `Empty`; `invalidate`, `invalidateAll` and `close()` do nothing and the object works after `close()`;
 * `toString()`; a negative `maxAge` throws before the loader runs; an already-cancelled caller gets `CancellationException` and
 * its loader does not run; the three-argument overload behaves as `maxAge = INFINITE`; a loader's own cancellation is an
 * `IllegalStateException` failure that leaves the caller active; a loader's `Exception` is the failure; a loader's `Error` is
 * rethrown as the same instance.
 */
class DisabledResponseCacheComponentTest {

    private val cache: ResponseCache = ResponseCache.Disabled
    private val key = CacheKey<String>("endpoint", listOf("id" to "1"))
    private val policy = CachePolicy(10.minutes)

    @Test
    fun `two sequential calls run two loaders`() = runTest {
        val loads = AtomicInteger()
        val first = cache.getOrLoad(key, policy) { loads.incrementAndGet(); Result.success(Lookup.Found("a")) }
        val second = cache.getOrLoad(key, policy) { loads.incrementAndGet(); Result.success(Lookup.Found("b")) }
        assertEquals(Lookup.Found("a"), first.getOrThrow())
        assertEquals(Lookup.Found("b"), second.getOrThrow())
        assertEquals(2, loads.get())
    }

    @Test
    fun `ten concurrent callers of one key run ten loaders`() = runTest {
        val loads = AtomicInteger()
        val results = List(10) { i ->
            async {
                cache.getOrLoad(key, policy) {
                    loads.incrementAndGet()
                    delay(1.seconds)
                    Result.success(Lookup.Found("v$i"))
                }
            }
        }.awaitAll()
        assertEquals(10, loads.get())
        assertEquals(List(10) { Lookup.Found("v$it") }, results.map { it.getOrThrow() })
    }

    @Test
    fun `the loader runs on the caller's coroutine`() = runTest {
        var seen: String? = null
        withContext(CoroutineName("the-caller")) {
            cache.getOrLoad(key, policy) {
                seen = currentCoroutineContext()[CoroutineName]?.name
                Result.success(Lookup.Found("v"))
            }
        }
        assertEquals("the-caller", seen)
    }

    @Test
    fun `stats stay Empty and invalidation and close do nothing`() = runTest {
        cache.getOrLoad(key, policy) { Result.success(Lookup.Found("v")) }
        cache.getOrLoad(key, policy) { Result.failure(IllegalStateException("x")) }
        assertEquals(CacheStats.Empty, cache.stats())
        cache.invalidate(key)
        cache.invalidateAll()
        cache.close()
        cache.close()
        val after = cache.getOrLoad(key, policy) { Result.success(Lookup.Found("after")) }
        assertEquals(Lookup.Found("after"), after.getOrThrow())
        assertEquals(CacheStats.Empty, cache.stats())
        assertEquals("ResponseCache.Disabled", cache.toString())
    }

    @Test
    fun `a negative maxAge throws before the loader runs`() = runTest {
        var ran = false
        val exception = assertFailsWith<IllegalArgumentException> {
            cache.getOrLoad(key, policy, (-1).seconds) { ran = true; Result.success(Lookup.Found("v")) }
        }
        assertEquals("maxAge must not be negative", exception.message)
        assertFalse(ran)
    }

    @Test
    fun `an already-cancelled caller gets CancellationException and its loader does not run`() = runTest {
        var ran = false
        var thrown: Throwable? = null
        val caller = launch {
            cancel()
            try {
                cache.getOrLoad(key, policy) { ran = true; Result.success(Lookup.Found("v")) }
            } catch (e: Throwable) {
                thrown = e
            }
        }
        advanceUntilIdle()
        assertTrue(caller.isCancelled)
        assertIs<CancellationException>(thrown)
        assertFalse(ran)
    }

    @Test
    fun `the three-argument overload behaves as maxAge INFINITE`() = runTest {
        val loads = AtomicInteger()
        val three = cache.getOrLoad(key, policy) { loads.incrementAndGet(); Result.success(Lookup.Found("v")) }
        val four = cache.getOrLoad(key, policy, Duration.INFINITE) { loads.incrementAndGet(); Result.success(Lookup.Found("v")) }
        assertEquals(three, four)
        assertEquals(2, loads.get())
    }

    @Test
    fun `a loader's own cancellation is a failure that leaves the caller active`() = runTest {
        val loaders: List<suspend () -> Result<Lookup<String>>> = listOf(
            { withTimeout(1.seconds) { awaitCancellation() } },
            { throw CancellationException("thrown") },
            { Result.failure(CancellationException("returned")) },
        )
        loaders.forEachIndexed { i, loader ->
            val result = cache.getOrLoad(key, policy, loader)
            val failure = assertIs<IllegalStateException>(result.exceptionOrNull(), "loader $i")
            assertEquals(LOADER_CANCELLED_MESSAGE, failure.message)
            assertIs<CancellationException>(failure.cause, "loader $i")
            assertTrue(currentCoroutineContext().isActive, "loader $i cancelled the caller")
        }
    }

    @Test
    fun `a loader's exception is the failure`() = runTest {
        val thrown = IllegalStateException("thrown")
        assertSame(thrown, cache.getOrLoad(key, policy) { throw thrown }.exceptionOrNull())
        val returned = IllegalArgumentException("returned")
        assertSame(returned, cache.getOrLoad(key, policy) { Result.failure(returned) }.exceptionOrNull())
    }

    @Test
    fun `a loader's Error is rethrown as the same instance`() = runTest {
        val boom = Boom()
        val caught = async {
            try {
                cache.getOrLoad(key, policy) { throw boom }
                null
            } catch (e: Boom) {
                e
            }
        }.await()
        assertSame(boom, caught)
    }
}
