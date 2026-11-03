package com.spartanlabs.testing.nonfunctional.webtools.http

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.webtools.http.CacheKey
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CaffeineResponseCache
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.ResponseCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val SECRET_PARAM = "SECRET-PARAM-4c1f"
private const val SECRET_VALUE = "SECRET-VALUE-8d2a"
private const val SECRET_MESSAGE = "SECRET-MESSAGE-3e7b"

/**
 * Level 4c - secrecy at `DEBUG` on the production defaults: a key whose parameter values, a `Found` value and a failure message all carry
 * unmistakable sentinel strings go through a found, a missing, a failed, a fatal and a closed load; across every log event (formatted message,
 * arguments, throwable) and across `toString()` of the key, the policy, the `Found`, the `CacheStats`, the cache and `Disabled`, no sentinel appears,
 * while the key's id and parameter names do appear in the load lines. Real time under `runBlocking` within 30 s.
 */
class CaffeineResponseCacheSecrecyNonFunctionalTest {

    private val sentinels = listOf(SECRET_PARAM, SECRET_VALUE, SECRET_MESSAGE)
    private val policy = CachePolicy(10.minutes) { missTtl = 30.seconds }
    private val key = CacheKey<String>("players", listOf("token" to SECRET_PARAM, "id" to SECRET_PARAM))

    private fun ILoggingEvent.texts(): List<String> =
        listOf(formattedMessage) + (argumentArray?.map { it.toString() } ?: emptyList()) +
            (throwableProxy?.let { listOf(it.className, it.message.orEmpty()) } ?: emptyList())

    private fun assertClean(label: String, text: String) {
        sentinels.forEach { sentinel -> assertFalse(sentinel in text, "'$sentinel' leaked in $label: $text") }
    }

    @Test
    fun `no sentinel reaches a log event or a toString`() {
        val fatal = Error(SECRET_MESSAGE)
        val failure = IllegalStateException(SECRET_MESSAGE)
        val texts = ArrayList<Pair<String, String>>()
        val (_, events) = captureTransportLogs(Level.DEBUG) {
            runBlocking {
                withTimeout(30.seconds) {
                    val cache = CaffeineResponseCache()
                    try {
                        val found = cache.getOrLoad(key, policy) { Result.success(Lookup.Found(SECRET_VALUE)) }.getOrThrow()
                        assertEquals(Lookup.Found(SECRET_VALUE), found)
                        assertSame(Lookup.Missing, cache.getOrLoad(CacheKey<String>("missing", key.params), policy) { Result.success(Lookup.Missing) }.getOrThrow())
                        assertSame(failure, cache.getOrLoad(CacheKey<String>("failed", key.params), policy) { Result.failure(failure) }.exceptionOrNull())
                        val thrown = try {
                            cache.getOrLoad(CacheKey<String>("fatal", key.params), policy) { throw fatal }
                            null
                        } catch (error: Error) {
                            error
                        }
                        assertSame(fatal, thrown)
                        val started = CompletableDeferred<Unit>()
                        val waiter = async(Dispatchers.Default) {
                            cache.getOrLoad(CacheKey<String>("closed", key.params), policy) { started.complete(Unit); awaitCancellation() }
                        }
                        started.await()
                        cache.close()
                        assertEquals("cache", assertIs<HttpFailure.Closed>(waiter.await().exceptionOrNull()).component)
                        texts += "key" to key.toString()
                        texts += "policy" to policy.toString()
                        texts += "Found" to found.toString()
                        texts += "CacheStats" to cache.stats().toString()
                        texts += "cache" to cache.toString()
                        texts += "Disabled" to ResponseCache.Disabled.toString()
                    } finally {
                        cache.close()
                    }
                }
            }
        }
        assertTrue(events.isNotEmpty())
        events.forEach { event -> event.texts().forEach { assertClean("a ${event.level} event", it) } }
        texts.forEach { (label, text) -> assertClean("$label.toString()", text) }
        val loadLines = events.filter { it.level == Level.DEBUG && "load ended" in it.formattedMessage }.map { it.formattedMessage }
        assertEquals(5, loadLines.size, loadLines.toString())
        listOf("players", "missing", "failed", "fatal", "closed").forEach { id ->
            val line = loadLines.single { "endpointId=$id," in it }
            assertTrue("token" in line && "id" in line, line)
        }
    }
}
