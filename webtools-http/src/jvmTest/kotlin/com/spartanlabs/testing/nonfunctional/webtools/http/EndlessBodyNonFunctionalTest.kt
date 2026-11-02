package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.testing.support.webtools.http.EndlessBody
import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

/**
 * Level 4c - an endless chunked body under a 1 MB cap is `BodyTooLarge(1_000_000, null)` and the server's writer stops once the client
 * closes (never a byte bound: the server may have written megabytes before it noticed); the same body throttled to 64 KiB every 10 ms ends
 * within a bounded overshoot.
 */
class EndlessBodyNonFunctionalTest {

    private lateinit var fleet: LoopbackFleet

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
    }

    @AfterTest
    fun stop() = fleet.close()

    private fun run(body: EndlessBody, throttled: Boolean) {
        val server = fleet.server()
        server.enqueue(body.response { if (throttled) throttleBody(65536, 10, TimeUnit.MILLISECONDS) })
        val transport = fleet.track(LoopbackTransports.untrusted("agent", OutboundUrlPolicy.untrusted { settings { maxResponseBytes = 1_000_000; callTimeout = 30.seconds; readTimeout = 10.seconds } }))
        val (result, elapsed) = measureTimedValue { fleet.fetch(transport, fleet.get(server.webtoolsUrl("/"))) }
        val failure = assertIs<TransportFailure.BodyTooLarge>(result.exceptionOrNull())
        assertEquals(1_000_000L, failure.limit)
        assertNull(failure.declared)
        assertTrue(body.stopped.await(5, TimeUnit.SECONDS), "the server's writer never stopped")
        assertTrue(elapsed < 10.seconds, "took $elapsed")
    }

    @Test
    fun `an endless body is BodyTooLarge and the server stops writing`() = run(EndlessBody(), throttled = false)

    @Test
    fun `a throttled endless body ends within a bounded overshoot`() = run(EndlessBody(), throttled = true)
}
