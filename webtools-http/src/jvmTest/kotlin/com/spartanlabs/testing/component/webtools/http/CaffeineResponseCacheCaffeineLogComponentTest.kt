package com.spartanlabs.testing.component.webtools.http

import com.github.benmanes.caffeine.cache.Caffeine
import com.spartanlabs.testing.support.webtools.http.Boom
import com.spartanlabs.testing.support.webtools.http.LoadLog
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.call
import com.spartanlabs.testing.support.webtools.http.captureCaffeineLogs
import com.spartanlabs.testing.support.webtools.http.key
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - Caffeine logs nothing for an expected failure, because the cache never completes a future exceptionally. First the positive control: a
 * throw-away `AsyncCache` whose mapping function returns a future completed exceptionally makes Caffeine's logger publish "Exception thrown during
 * asynchronous load" (through `System.Logger` to `java.util.logging`). Then a failed load, a fatal `Error` and a loader's own cancellation on the
 * real cache leave the handler with no Caffeine record, and the future held for each key is never `isCompletedExceptionally`.
 */
class CaffeineResponseCacheCaffeineLogComponentTest {

    private val policy = CachePolicy(10.minutes)

    @Test
    fun `positive control - an exceptionally completed load makes Caffeine log a warning`() {
        val (_, records) = captureCaffeineLogs {
            val plain = Caffeine.newBuilder().buildAsync<String, String>()
            plain.get("k") { _, _ -> CompletableFuture.failedFuture<String>(IllegalStateException("control")) }
        }
        assertTrue(
            records.any { it.message?.contains("Exception thrown during asynchronous load") == true },
            "no Caffeine warning was captured; records: ${records.map { "${it.loggerName}: ${it.message}" }}",
        )
    }

    @Test
    fun `a failed load a fatal error and a loader's own cancellation leave no Caffeine record and never an exceptional future`() {
        val (_, records) = captureCaffeineLogs {
            runTest {
                val clock = virtualClock()
                val log = LoadLog()
                cacheOn(clock).use { cache ->
                    val failed = call(cache, key("failed"), policy, loader = log.failing(IllegalStateException("x"), after = 1.seconds))
                    clock.advanceBy(Duration.ZERO)
                    val failedFuture = assertNotNull(cache.futureOf(key("failed")))
                    clock.advanceBy(1.seconds)
                    assertTrue(failed.isCompleted && failedFuture.isDone)
                    assertFalse(failedFuture.isCompletedExceptionally, "the failed load completed its future exceptionally")

                    val fatal = call(cache, key("fatal"), policy, loader = log.throwing(Boom(), after = 1.seconds))
                    clock.advanceBy(Duration.ZERO)
                    val fatalFuture = assertNotNull(cache.futureOf(key("fatal")))
                    clock.advanceBy(1.seconds)
                    assertTrue(fatal.isCompleted && fatalFuture.isDone)
                    assertFalse(fatalFuture.isCompletedExceptionally, "the fatal load completed its future exceptionally")

                    val cancelled = call(cache, key("cancelled"), policy) { withTimeout(1.seconds) { awaitCancellation() } }
                    clock.advanceBy(Duration.ZERO)
                    val cancelledFuture = assertNotNull(cache.futureOf(key("cancelled")))
                    clock.advanceBy(1.seconds)
                    assertTrue(cancelled.isCompleted && cancelledFuture.isDone)
                    assertFalse(cancelledFuture.isCompletedExceptionally, "the cancelled load completed its future exceptionally")
                    assertTrue(cancelled.failure is IllegalStateException)

                    assertEquals(Lookup.Found("ok"), cache.getOrLoad(key("ok"), policy, log.loader("ok")).getOrThrow())
                }
            }
        }
        assertEquals(emptyList(), records.map { "${it.loggerName}: ${it.message}" })
    }
}
