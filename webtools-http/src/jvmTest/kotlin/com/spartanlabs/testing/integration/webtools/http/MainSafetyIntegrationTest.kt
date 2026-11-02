package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Level 3 - main-safety: two calls to a server that delays its headers by 500 ms, started together from one single-threaded `runBlocking`,
 * complete in about 500 ms rather than 1 s, and a coroutine ticking every 50 ms on the same thread keeps ticking while they are in flight:
 * the caller's thread is never blocked.
 */
class MainSafetyIntegrationTest {

    private lateinit var fleet: LoopbackFleet

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
    }

    @AfterTest
    fun stop() = fleet.close()

    @Test
    fun `two delayed calls overlap and the caller's thread keeps ticking`() = runBlocking {
        val server = fleet.server()
        repeat(2) { server.enqueue(MockResponse.Builder().headersDelay(500, TimeUnit.MILLISECONDS).body("ok").build()) }
        val transport = fleet.track(LoopbackTransports.untrusted("agent"))
        val ticks = AtomicInteger()
        val ticker = launch { while (isActive) { ticks.incrementAndGet(); delay(50) } }
        val mark = TimeSource.Monotonic.markNow()
        val results = listOf(async { transport.execute(fleet.get(server.webtoolsUrl("/a"))) }, async { transport.execute(fleet.get(server.webtoolsUrl("/b"))) }).awaitAll()
        val elapsed = mark.elapsedNow()
        ticker.cancel()
        results.forEach { assertEquals(200, it.getOrThrow().status) }
        assertTrue(elapsed >= 450.milliseconds, "took $elapsed")
        assertTrue(elapsed < 900.milliseconds, "took $elapsed: the calls did not overlap")
        assertTrue(ticks.get() >= 5, "only ${ticks.get()} ticks: the caller's thread was blocked")
    }
}
