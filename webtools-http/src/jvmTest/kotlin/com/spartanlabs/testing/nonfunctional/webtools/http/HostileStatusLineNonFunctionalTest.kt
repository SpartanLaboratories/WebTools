package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import org.junit.jupiter.api.parallel.Isolated
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * Level 4c - a hostile status line (`HTTP/1.1 -12 X`): the result is `Protocol`, nothing is written to `System.err` (swapped for a capture
 * stream and restored in `finally`), no `Exception in thread` appears, and the dispatcher still serves the next call on the same transport. The
 * class replaces a process-wide stream and is isolated.
 */
@Isolated
class HostileStatusLineNonFunctionalTest {

    private val previousErr: PrintStream = System.err
    private lateinit var fleet: LoopbackFleet

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
    }

    @AfterTest
    fun stop() {
        try {
            fleet.close()
        } finally {
            System.setErr(previousErr)
        }
    }

    @Test
    fun `a negative status is Protocol prints nothing and leaves the dispatcher serving`() {
        val server = fleet.server()
        server.enqueue(MockResponse.Builder().status("HTTP/1.1 -12 X").body("x").build())
        server.enqueue(MockResponse(body = "ok"))
        val transport = fleet.track(LoopbackTransports.untrusted("agent"))
        val captured = ByteArrayOutputStream()
        System.setErr(PrintStream(captured, true))
        try {
            val failure = assertIs<TransportFailure.Protocol>(fleet.fetch(transport, fleet.get(server.webtoolsUrl("/"))).exceptionOrNull())
            assertEquals(null, failure.cause)
            val next = fleet.fetch(transport, fleet.get(server.webtoolsUrl("/"))).getOrThrow()
            assertEquals("ok", next.body().decodeToString())
        } finally {
            System.setErr(previousErr)
        }
        val text = captured.toString(Charsets.UTF_8)
        assertEquals("", text, "System.err received: $text")
        assertFalse("Exception in thread" in text)
    }
}
