package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.testing.support.webtools.http.inet
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.MixedAnswers
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Level 3 - the policy's guards through the transport: the **public** factory with `Untrusted` (no relaxation) refuses the loopback literal
 * and `localhost` with no request made; a rebinding resolver (`127.0.0.1` then `10.0.0.5`) lets the first call through and refuses the second
 * with `DeniedAddress`, two lookups, one request; a mixed answer is refused under `Reject` (one lookup, no request) and served under
 * `DropDenied`; a pinned `Trusted` policy refuses a redirect to another host with `HostNotPinned` and the second server sees nothing.
 */
class PolicyEnforcementTransportIntegrationTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var server: MockWebServer

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        server = fleet.server()
    }

    @AfterTest
    fun stop() = fleet.close()

    private fun refusal(transport: OkHttpTransport, urlText: String): RefusalReason =
        assertIs<TransportFailure.RefusedByPolicy>(fleet.fetch(transport, fleet.get(url(urlText))).exceptionOrNull(), urlText).reason

    @Test
    fun `the public Untrusted factory refuses loopback with no request made`() {
        val transport = fleet.track(OkHttpTransport("agent", OutboundUrlPolicy.untrusted()))
        assertEquals(RefusalReason.DeniedAddress, refusal(transport, server.webtoolsUrl("/").toString()))
        assertEquals(RefusalReason.ReservedName, refusal(transport, "http://localhost:${server.port}/"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a rebinding resolver is refused on the second call`() {
        val answers = ArrayDeque(listOf(listOf(inet("127.0.0.1")), listOf(inet("10.0.0.5"))))
        val lookups = AtomicInteger()
        val resolve: (String) -> List<InetAddress> = { lookups.incrementAndGet(); answers.removeFirst() }
        val transport = fleet.track(LoopbackTransports.untrusted("agent", OutboundUrlPolicy.untrusted { allowedPorts = null }, resolve))
        // Connection: close, so the second call opens a new connection and asks the resolver again; a reused pooled connection would never see the rebind.
        server.enqueue(MockResponse.Builder().addHeader("Connection", "close").body("first").build())
        server.enqueue(MockResponse(body = "second"))
        val target = url("http://rebind.test:${server.port}/")
        assertEquals("first", fleet.fetch(transport, fleet.get(target)).getOrThrow().body().decodeToString())
        assertEquals(RefusalReason.DeniedAddress, assertIs<TransportFailure.RefusedByPolicy>(fleet.fetch(transport, fleet.get(target)).exceptionOrNull()).reason)
        assertEquals(2, lookups.get())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a mixed answer is refused under Reject and served under DropDenied`() {
        val lookups = AtomicInteger()
        val resolve: (String) -> List<InetAddress> = { lookups.incrementAndGet(); listOf(inet("127.0.0.1"), inet("10.0.0.5")) }
        val reject = fleet.track(LoopbackTransports.untrusted("agent", OutboundUrlPolicy.untrusted { allowedPorts = null }, resolve))
        val target = url("http://mixed.test:${server.port}/")
        assertEquals(RefusalReason.DeniedAddress, assertIs<TransportFailure.RefusedByPolicy>(fleet.fetch(reject, fleet.get(target)).exceptionOrNull()).reason)
        assertEquals(0, server.requestCount)
        assertEquals(1, lookups.get())
        server.enqueue(MockResponse(body = "ok"))
        val drop = fleet.track(LoopbackTransports.untrusted("agent", OutboundUrlPolicy.untrusted { allowedPorts = null; mixedAnswers = MixedAnswers.DropDenied }, resolve))
        assertEquals("ok", fleet.fetch(drop, fleet.get(target)).getOrThrow().body().decodeToString())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a pinned Trusted policy refuses a redirect to another host`() {
        val second = fleet.server()
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://other.test:${second.port}/").build())
        second.enqueue(MockResponse(body = "reached"))
        val pinned = fleet.track(LoopbackTransports.trusted("agent", OutboundUrlPolicy.trusted { pinnedHosts = setOf("127.0.0.1") }) { listOf(inet("127.0.0.1")) })
        assertEquals(RefusalReason.HostNotPinned, assertIs<TransportFailure.RefusedByPolicy>(fleet.fetch(pinned, fleet.get(server.webtoolsUrl("/"))).exceptionOrNull()).reason)
        assertEquals(1, server.requestCount)
        assertEquals(0, second.requestCount)
    }
}
