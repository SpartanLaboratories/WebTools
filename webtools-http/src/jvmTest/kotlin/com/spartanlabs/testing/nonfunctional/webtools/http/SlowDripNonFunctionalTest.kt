package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

/**
 * Level 4c - the call deadline against a slow server: a body dripped 10 bytes every 100 ms under a 1 s call timeout (and a 10 s read
 * timeout, which must exceed the drip interval) is `Timeout` in at least 0.9 s and under 3 s, and the server closes promptly afterwards; a
 * 3 s headers delay under a 700 ms call timeout ends in about 700 ms.
 */
class SlowDripNonFunctionalTest {

    private lateinit var fleet: LoopbackFleet

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
    }

    @AfterTest
    fun stop() = fleet.close()

    @Test
    fun `a slow drip ends with Timeout at the call deadline`() {
        val server = fleet.server()
        server.enqueue(MockResponse.Builder().body("x".repeat(100_000)).throttleBody(10, 100, TimeUnit.MILLISECONDS).build())
        val transport = fleet.track(LoopbackTransports.untrusted("agent", OutboundUrlPolicy.untrusted { settings { callTimeout = 1.seconds; readTimeout = 10.seconds } }))
        val (result, elapsed) = measureTimedValue { fleet.fetch(transport, fleet.get(server.webtoolsUrl("/"))) }
        assertIs<TransportFailure.Timeout>(result.exceptionOrNull())
        assertTrue(elapsed >= 900.milliseconds, "ended after $elapsed, before the deadline")
        assertTrue(elapsed < 3.seconds, "ended after $elapsed, long past the deadline")
        val (_, closing) = measureTimedValue { server.close() }
        assertTrue(closing < 2.seconds, "server.close() took $closing")
    }

    @Test
    fun `a headers delay past the call deadline ends in about the deadline`() {
        val server = fleet.server()
        server.enqueue(MockResponse.Builder().headersDelay(3, TimeUnit.SECONDS).body("late").build())
        val transport = fleet.track(LoopbackTransports.untrusted("agent", OutboundUrlPolicy.untrusted { settings { callTimeout = 700.milliseconds } }))
        val (result, elapsed) = measureTimedValue { fleet.fetch(transport, fleet.get(server.webtoolsUrl("/"))) }
        assertIs<TransportFailure.Timeout>(result.exceptionOrNull())
        assertTrue(elapsed >= 600.milliseconds, "ended after $elapsed, before the deadline")
        assertTrue(elapsed < 2500.milliseconds, "ended after $elapsed, long past the deadline")
    }
}
