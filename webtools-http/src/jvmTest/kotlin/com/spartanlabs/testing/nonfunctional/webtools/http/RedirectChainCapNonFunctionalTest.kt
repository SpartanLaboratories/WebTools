package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Level 4c - an unbounded redirect chain: a custom `mockwebserver3.Dispatcher` (written in full, to avoid the clash with
 * `okhttp3.Dispatcher`) answers every request `/<n>` with `302 Location: /<n+1>`; with `maxRedirects = 3` the result is
 * `TooManyRedirects(3)` after exactly four requests. The dispatcher is why this works where `enqueue(fixture)` would refuse it.
 */
class RedirectChainCapNonFunctionalTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var server: MockWebServer

    /** Answers `/<n>` with a redirect to `/<n+1>` on the same server. */
    private inner class NextHop : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val n = request.url.encodedPath.trimStart('/').toIntOrNull() ?: 0
            return MockResponse.Builder().code(302).addHeader("Location", server.webtoolsUrl("/${n + 1}").toString()).build()
        }
    }

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        server = fleet.server()
        server.dispatcher = NextHop()
    }

    @AfterTest
    fun stop() = fleet.close()

    @Test
    fun `the chain stops at the cap after exactly cap plus one requests`() {
        val transport = fleet.track(LoopbackTransports.untrusted("agent", OutboundUrlPolicy.untrusted { settings { maxRedirects = 3 } }))
        val failure = assertIs<TransportFailure.TooManyRedirects>(fleet.fetch(transport, fleet.get(server.webtoolsUrl("/0"))).exceptionOrNull())
        assertEquals(3, failure.limit)
        assertEquals(4, server.requestCount)
    }
}
