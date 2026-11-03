package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.spartanlabs.testing.support.webtools.http.Boom
import com.spartanlabs.testing.support.webtools.http.LoadLog
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.call
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.key
import com.spartanlabs.webtools.http.CACHE_LOGGER_NAME
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val SECRET_VALUE = "SECRET-VALUE-7f3a"
private const val SECRET_PARAM = "SECRET-PARAM-9c1d"
private const val SECRET_MESSAGE = "SECRET-MESSAGE-2b8e"

/**
 * Level 2 - what the cache logs, captured at `DEBUG` on the module's logger: exactly one `INFO` on creation naming `maxWeight`; one `DEBUG` per load
 * end, not per hit or join (20 callers, one line), whose text carries the key's id and parameter names, the outcome kind (`found`, `missing`,
 * `failed (<class>)`, `error (<class>)`, `closed`) and the storage note (`stored`, `not stored: zero TTL`, `not stored: over maxWeight`); exactly one
 * `INFO` on `close()` even when called twice; never a value, a parameter value or a failure's message text; the logger is the cache's; no `WARN` for
 * any loader outcome; the load-end line is written by whoever completes the shared future, so a loader that ignores cancellation and finishes after
 * `close()` adds none (one `closed` line for its key, never a `stored` one); a load whose key was invalidated in flight logs its one line as
 * `not stored: removed before the load ended`, and the next call reloads.
 */
class CaffeineResponseCacheLoggingComponentTest {

    private val policy = CachePolicy(10.minutes)

    private fun ILoggingEvent.text(): String =
        formattedMessage + " " + (argumentArray?.joinToString(" ") { it.toString() } ?: "") + " " + (throwableProxy?.message ?: "")

    private fun List<ILoggingEvent>.loadLines(): List<String> = filter { it.level == Level.DEBUG && "load ended" in it.formattedMessage }.map { it.formattedMessage }

    @Test
    fun `creation logs one INFO naming maxWeight and close logs one INFO even when called twice`() {
        val (_, events) = captureTransportLogs(Level.DEBUG) {
            runTest {
                val clock = virtualClock()
                val cache = cacheOn(clock) { maxWeight = 42 }
                cache.close()
                cache.close()
            }
        }
        val infos = events.filter { it.level == Level.INFO }
        assertEquals(2, infos.size, infos.map { it.formattedMessage }.toString())
        assertTrue("maxWeight" in infos[0].formattedMessage && "42" in infos[0].formattedMessage, infos[0].formattedMessage)
        assertEquals("closed", infos[1].formattedMessage)
        events.forEach { assertEquals(CACHE_LOGGER_NAME, it.loggerName) }
    }

    @Test
    fun `one DEBUG per load end not per hit or join`() {
        val (_, events) = captureTransportLogs(Level.DEBUG) {
            runTest {
                val clock = virtualClock()
                val log = LoadLog()
                cacheOn(clock).use { cache ->
                    val players = key("players", "region" to "eu", "id" to "7")
                    val waiters = List(20) { call(cache, players, policy, loader = log.loader("v", after = 1.seconds)) }
                    clock.advanceBy(Duration.ZERO)
                    clock.advanceBy(1.seconds)
                    assertTrue(waiters.all { it.isCompleted })
                    cache.getOrLoad(players, policy, log.loader("x"))
                    assertEquals(1, log.loads)
                }
            }
        }
        val lines = events.loadLines()
        assertEquals(1, lines.size, lines.toString())
        val line = lines[0]
        assertTrue("players" in line && "id" in line && "region" in line, line)
        assertTrue("outcome=found" in line && "storage=stored" in line, line)
        assertFalse("eu" in line.substringAfter("params=").substringBefore("]"), line)
    }

    @Test
    fun `each load end logs its outcome kind and storage note`() {
        val (_, events) = captureTransportLogs(Level.DEBUG) {
            runTest {
                val clock = virtualClock()
                val log = LoadLog()
                cacheOn(clock) { maxWeight = 10 }.use { cache ->
                    cache.getOrLoad(key("found"), policy, log.loader("v"))
                    cache.getOrLoad(key("missing"), policy, log.missing())
                    cache.getOrLoad(key("failed"), policy, log.failing(IllegalStateException("x")))
                    val fatal = call(cache, key("error"), policy, loader = log.throwing(Boom()))
                    clock.advanceBy(Duration.ZERO)
                    assertTrue(fatal.thrown is Boom)
                    cache.getOrLoad(key("zero"), CachePolicy(Duration.ZERO), log.loader("v"))
                    cache.getOrLoad(key("heavy"), policy, log.heavy("v", 11))
                    val closed = call(cache, key("closed"), policy, loader = log.loader("v", after = 10.seconds))
                    clock.advanceBy(Duration.ZERO)
                    cache.close()
                    clock.advanceBy(Duration.ZERO)
                    assertTrue(closed.isCompleted)
                }
            }
        }
        val lines = events.loadLines()
        fun assertLine(id: String, outcome: String, storage: String) {
            val line = lines.singleOrNull { "endpointId=$id," in it }
            assertTrue(line != null, "no single load line for $id in $lines")
            assertTrue("outcome=$outcome" in line, line)
            assertTrue("storage=$storage" in line, line)
        }
        assertLine("found", "found", "stored")
        assertLine("missing", "missing", "stored")
        assertLine("failed", "failed (java.lang.IllegalStateException)", "not stored: not a result")
        assertLine("error", "error (${Boom::class.java.name})", "not stored: not a result")
        assertLine("zero", "found", "not stored: zero TTL")
        assertLine("heavy", "found", "not stored: over maxWeight")
        assertLine("closed", "closed", "not stored: not a result")
        assertEquals(emptyList(), events.filter { it.level == Level.WARN }.map { it.formattedMessage })
        events.forEach { assertEquals(CACHE_LOGGER_NAME, it.loggerName) }
    }

    @Test
    fun `a load whose result close discarded logs one closed load end and none when its loader finishes`() {
        val (_, events) = captureTransportLogs(Level.DEBUG) {
            runTest {
                val clock = virtualClock()
                cacheOn(clock).use { cache ->
                    var finished = false
                    val waiter = call(cache, key("ignores-cancellation"), policy) {
                        withContext(NonCancellable) { delay(10.seconds) }
                        finished = true
                        Result.success(Lookup.Found("v"))
                    }
                    clock.advanceBy(Duration.ZERO)
                    cache.close()
                    clock.advanceBy(Duration.ZERO)
                    assertTrue(waiter.isCompleted)
                    assertFalse(finished, "the loader finished before its delay")

                    clock.advanceBy(10.seconds)
                    assertTrue(finished, "the non-cancellable loader did not finish")
                }
            }
        }
        val lines = events.loadLines()
        assertEquals(1, lines.size, lines.toString())
        val line = lines[0]
        assertTrue("endpointId=ignores-cancellation," in line, line)
        assertTrue("outcome=closed" in line, line)
        assertTrue("storage=not stored: not a result" in line, line)
        assertFalse("storage=stored" in line, line)
    }

    @Test
    fun `a load invalidated in flight logs one found load end as removed and the next call reloads`() {
        val (_, events) = captureTransportLogs(Level.DEBUG) {
            runTest {
                val clock = virtualClock()
                val log = LoadLog()
                cacheOn(clock).use { cache ->
                    val waiter = call(cache, key("invalidated"), policy, loader = log.loader("v", after = 1.seconds))
                    clock.advanceBy(Duration.ZERO)
                    cache.invalidate(key("invalidated"))
                    clock.advanceBy(1.seconds)
                    assertTrue(waiter.isCompleted)
                    assertEquals("v", waiter.value)

                    assertEquals(Lookup.Found("w"), cache.getOrLoad(key("invalidated"), policy, log.loader("w")).getOrThrow())
                    assertEquals(2, log.loads)
                }
            }
        }
        val lines = events.loadLines().filter { "endpointId=invalidated," in it }
        assertEquals(2, lines.size, lines.toString())
        assertTrue("outcome=found" in lines[0] && "storage=not stored: removed before the load ended" in lines[0], lines[0])
        assertTrue("outcome=found" in lines[1] && "storage=stored" in lines[1], lines[1])
    }

    @Test
    fun `never a value a parameter value or a failure message`() {
        val (_, events) = captureTransportLogs(Level.DEBUG) {
            runTest {
                val clock = virtualClock()
                val log = LoadLog()
                cacheOn(clock).use { cache ->
                    val secretKey = key("players", "token" to SECRET_PARAM)
                    cache.getOrLoad(secretKey, policy, log.loader(SECRET_VALUE))
                    cache.getOrLoad(key("failed", "token" to SECRET_PARAM), policy, log.failing(IllegalStateException(SECRET_MESSAGE)))
                    cache.getOrLoad(key("thrown", "token" to SECRET_PARAM), policy, log.throwing(IllegalStateException(SECRET_MESSAGE)))
                    val fatal = call(cache, key("error", "token" to SECRET_PARAM), policy, loader = log.throwing(Boom()))
                    clock.advanceBy(Duration.ZERO)
                    assertTrue(fatal.thrown is Boom)
                }
            }
        }
        assertTrue(events.isNotEmpty())
        events.forEach { event ->
            val text = event.text()
            assertFalse(SECRET_VALUE in text, text)
            assertFalse(SECRET_PARAM in text, text)
            assertFalse(SECRET_MESSAGE in text, text)
        }
        val loadLines = events.loadLines()
        assertEquals(4, loadLines.size, loadLines.toString())
        loadLines.forEach { assertTrue("token" in it, it) }
    }
}
