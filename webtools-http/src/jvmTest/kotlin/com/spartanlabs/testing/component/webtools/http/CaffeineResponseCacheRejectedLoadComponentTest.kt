package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.Boom
import com.spartanlabs.testing.support.webtools.http.LoadLog
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.call
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.key
import com.spartanlabs.webtools.http.CACHE_LOGGER_NAME
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.runTest
import java.util.concurrent.RejectedExecutionException
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private const val REJECTION_TEXT = "SENTINEL-REJECTION-TEXT"

// A dispatcher that rejects every dispatch by throwing what `refusal` produces: a `RejectedExecutionException` by default, as an executor that was shut
// down while the cache is still in use does; an `Error` for the defect case.
private class RejectingDispatcher(
    private val refusal: () -> Throwable = { RejectedExecutionException(REJECTION_TEXT) },
) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable): Unit = throw refusal()
}

/**
 * Level 2 - a `loadDispatcher` that rejects the dispatch, under `runTest`: the scope's `CoroutineExceptionHandler` keeps the coroutine's failure
 * away from the test library's collector, so `runTest` completing normally is itself an assertion; the call returns a failure value whose exception
 * is the rejection (or kotlinx's wrapper of it) and neither hangs nor throws; exactly one `WARN` from the cache's logger names the exception's class
 * and carries no message text; `failedLoads` is 1; the entry is gone; the cache is open and a second call fails the same way, with a second `WARN`.
 * A dispatch that throws an `Error` (`Boom`) instead is fatal: the waiter has the same `Boom` rethrown to it, `failedLoads` is 1, the entry is gone,
 * one `WARN` names `Boom`'s class, and `runTest` still completes normally.
 */
class CaffeineResponseCacheRejectedLoadComponentTest {

    private val policy = CachePolicy(10.minutes)

    private fun isRejection(failure: Throwable): Boolean = failure is RejectedExecutionException || failure.cause is RejectedExecutionException

    @Test
    fun `a rejected dispatch is a failure value one WARN and no hang`() {
        val (_, events) = captureTransportLogs(Level.DEBUG) {
            runTest {
                val clock = virtualClock()
                val log = LoadLog()
                cacheOn(clock) { loadDispatcher = RejectingDispatcher() }.use { cache ->
                    val first = assertIs<Exception>(cache.getOrLoad(key(), policy, log.loader("a")).exceptionOrNull())
                    assertTrue(isRejection(first), first.toString())
                    assertEquals(0, log.loads)
                    assertEquals(1L, cache.stats().failedLoads)
                    assertNull(cache.futureOf(key()), "the rejected load's entry is still held")
                    assertEquals("CaffeineResponseCache(maxWeight=1000, closed=false)", cache.toString())

                    val second = assertIs<Exception>(cache.getOrLoad(key(), policy, log.loader("b")).exceptionOrNull())
                    assertTrue(isRejection(second), second.toString())
                    assertEquals(0, log.loads)
                    assertEquals(2L, cache.stats().failedLoads)
                }
            }
        }
        val warns = events.filter { it.level == Level.WARN }
        assertEquals(2, warns.size, events.map { "${it.level} ${it.formattedMessage}" }.toString())
        warns.forEach { warn ->
            assertEquals(CACHE_LOGGER_NAME, warn.loggerName)
            assertTrue(RejectedExecutionException::class.java.name in warn.formattedMessage, warn.formattedMessage)
            assertFalse(REJECTION_TEXT in warn.formattedMessage, warn.formattedMessage)
        }
    }

    @Test
    fun `a dispatch that throws an Error is rethrown to the waiter as the same instance with one WARN and no hang`() {
        val boom = Boom()
        val (_, events) = captureTransportLogs(Level.DEBUG) {
            runTest {
                val clock = virtualClock()
                val log = LoadLog()
                cacheOn(clock) { loadDispatcher = RejectingDispatcher { boom } }.use { cache ->
                    val waiter = call(cache, key(), policy, loader = log.loader("a"))
                    clock.advanceBy(Duration.ZERO)
                    assertTrue(waiter.isCompleted)
                    assertFalse(waiter.isCancelled)
                    assertSame(boom, waiter.thrown)
                    assertNull(waiter.result)
                    assertEquals(0, log.loads)
                    assertEquals(1L, cache.stats().failedLoads)
                    assertNull(cache.futureOf(key()), "the fatal entry is still held")
                    assertEquals("CaffeineResponseCache(maxWeight=1000, closed=false)", cache.toString())
                }
            }
        }
        val warns = events.filter { it.level == Level.WARN }
        assertEquals(1, warns.size, events.map { "${it.level} ${it.formattedMessage}" }.toString())
        assertEquals(CACHE_LOGGER_NAME, warns[0].loggerName)
        assertTrue(Boom::class.java.name in warns[0].formattedMessage, warns[0].formattedMessage)
    }
}
