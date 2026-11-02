package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.testing.support.webtools.http.inet
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Level 3 - a refused second hop: server 1 redirects to `http://second.test:<port>/` and the stub resolver would answer `127.0.0.1`; under
 * `untrusted` the port rule refuses (the relaxation exempts the port rule for `localhost`, `127.0.0.1` and `::1` only), server 2 sees no
 * request and the resolver is never asked (refused before DNS); the positive control with `allowedPorts = null` reaches server 2 once with
 * `Host: second.test:<port>`; a redirect to a denied literal is `RefusedByPolicy(DeniedAddress)` with no connection.
 */
class RedirectRefusalIntegrationTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var first: MockWebServer
    private lateinit var second: MockWebServer
    private val lookups = AtomicInteger()
    private val resolve: (String) -> List<InetAddress> = { lookups.incrementAndGet(); listOf(inet("127.0.0.1")) }

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        first = fleet.server()
        second = fleet.server()
    }

    @AfterTest
    fun stop() = fleet.close()

    @Test
    fun `the port rule refuses the second hop before DNS`() {
        first.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://second.test:${second.port}/").build())
        second.enqueue(MockResponse(body = "reached"))
        val transport = fleet.track(LoopbackTransports.untrusted("agent", resolve = resolve))
        val failure = assertIs<TransportFailure.RefusedByPolicy>(fleet.fetch(transport, fleet.get(first.webtoolsUrl("/"))).exceptionOrNull())
        assertEquals(RefusalReason.Port, failure.reason)
        assertEquals(1, first.requestCount)
        assertEquals(0, second.requestCount)
        assertEquals(0, lookups.get())
    }

    @Test
    fun `with any port allowed the second hop reaches server 2 once with its Host`() {
        first.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://second.test:${second.port}/").build())
        second.enqueue(MockResponse(body = "reached"))
        val transport = fleet.track(LoopbackTransports.untrusted("agent", OutboundUrlPolicy.untrusted { allowedPorts = null }, resolve))
        val response = fleet.fetch(transport, fleet.get(first.webtoolsUrl("/"))).getOrThrow()
        assertEquals("reached", response.body().decodeToString())
        assertEquals(1, second.requestCount)
        assertEquals("second.test:${second.port}", assertNotNull(second.takeRequest(1, TimeUnit.SECONDS)).headers["Host"])
        assertEquals(1, lookups.get())
    }

    @Test
    fun `a redirect to a denied literal is refused with no connection`() {
        first.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://169.254.169.254/").build())
        val transport = fleet.track(LoopbackTransports.untrusted("agent", resolve = resolve))
        val failure = assertIs<TransportFailure.RefusedByPolicy>(fleet.fetch(transport, fleet.get(first.webtoolsUrl("/"))).exceptionOrNull())
        assertEquals(RefusalReason.DeniedAddress, failure.reason)
        assertEquals(0, lookups.get())
        assertEquals(1, first.requestCount)
    }
}
