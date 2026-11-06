package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.testing.support.webtools.http.BASE_URL
import com.spartanlabs.testing.support.webtools.http.PermitRecordingBudget
import com.spartanlabs.testing.support.webtools.http.stringEndpoint
import com.spartanlabs.webtools.http.CaffeineResponseCache
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.UpstreamClient
import com.spartanlabs.webtools.http.WindowedCallBudget
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val CALLERS = 64

/**
 * Level 4c - `UpstreamClient` under real concurrency: 64 callers on `Dispatchers.Default`, real time under `runBlocking` within 30 s, a fake
 * transport that answers after 200 ms, a real `CaffeineResponseCache` and a real `WindowedCallBudget` behind a recording wrapper. Callers of one
 * key cost one permit and one transport call between them (the cache coalesces the load and the budget is taken inside it); callers of distinct
 * keys each cost one permit, none fails, and the window counts every one.
 */
class UpstreamClientConcurrencyNonFunctionalTest {

    // A window of 100 over an hour, so the run cannot cross a boundary between the calls and the reading of the window.
    private val windows: List<RateWindow> = listOf(RateWindow("hour", 100, 1.hours))

    private fun slowFake(): FakeHttpTransport = FakeHttpTransport().on(RequestMatch.Any, Step.respond(200, body = "x", delay = 200.milliseconds))

    @Test
    fun `64 concurrent callers of one key on real dispatchers cost one permit and one transport call`(): Unit = runBlocking {
        val fake = slowFake()
        CaffeineResponseCache().use { responseCache ->
            WindowedCallBudget(windows).use { real ->
                val recording = PermitRecordingBudget(real)
                UpstreamClient(fake, BASE_URL) { cache(responseCache); budget(recording) }.use { client ->
                    val endpoint = stringEndpoint { ttl = 10.minutes }

                    val lookups = withTimeout(30.seconds) {
                        List(CALLERS) { async(Dispatchers.Default) { client.call(endpoint) { path("players", "7") } } }.awaitAll()
                    }

                    lookups.forEachIndexed { index, lookup -> assertEquals(Lookup.Found("x"), lookup.getOrThrow(), "caller $index") }
                    assertEquals(1, fake.calls.size)
                    assertEquals(1, recording.permits.size)
                    assertEquals(63L, client.cacheStats().hits)
                    assertEquals(1L, client.cacheStats().misses)
                }
            }
        }
    }

    @Test
    fun `64 concurrent callers of distinct keys each cost one permit within the window`(): Unit = runBlocking {
        val fake = slowFake()
        CaffeineResponseCache().use { responseCache ->
            WindowedCallBudget(windows).use { real ->
                val recording = PermitRecordingBudget(real)
                UpstreamClient(fake, BASE_URL) { cache(responseCache); budget(recording) }.use { client ->
                    val endpoint = stringEndpoint { ttl = 10.minutes }

                    val lookups = withTimeout(30.seconds) {
                        List(CALLERS) { n -> async(Dispatchers.Default) { client.call(endpoint) { query("n", n.toString()) } } }.awaitAll()
                    }

                    lookups.forEachIndexed { index, lookup -> assertEquals(Lookup.Found("x"), lookup.getOrThrow(), "caller $index") }
                    assertEquals(CALLERS, fake.calls.size)
                    assertEquals(CALLERS, recording.permits.size)
                    assertEquals(CALLERS, client.utilisation().windows.single().used)
                    assertEquals(CALLERS.toLong(), client.cacheStats().misses)
                }
            }
        }
    }
}
