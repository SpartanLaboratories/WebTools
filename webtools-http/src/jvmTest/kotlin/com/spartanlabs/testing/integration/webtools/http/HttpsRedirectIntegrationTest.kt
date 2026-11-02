package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.testing.support.webtools.http.StubDns
import com.spartanlabs.testing.support.webtools.http.TlsFixtures
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.GuardRelaxation
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RedirectHop
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UrlGuard
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Level 3 - the `https`-to-`http` downgrade on the wire: an HTTPS server (OkHttp's TLS helper, HTTP/1.1) redirects to a plain server; the
 * transport is built through the internal constructor with the loopback relaxation, a stub resolver and a donor client that trusts the
 * certificate. With the default settings the hop is `RefusedByPolicy(InsecureRedirect)` and the plain server sees nothing; with
 * `allowHttpsToHttp = true` it is followed, the plain server sees one request and the chain holds the `https` hop.
 */
class HttpsRedirectIntegrationTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var secure: MockWebServer
    private lateinit var plain: MockWebServer

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        secure = TlsFixtures.httpsServer(fleet)
        plain = fleet.server()
        secure.enqueue(MockResponse.Builder().code(302).addHeader("Location", plain.webtoolsUrl("/down").toString()).build())
        plain.enqueue(MockResponse(body = "downgraded"))
    }

    @AfterTest
    fun stop() = fleet.close()

    private fun transport(policy: OutboundUrlPolicy): OkHttpTransport =
        fleet.track(OkHttpTransport("agent", UrlGuard(policy, GuardRelaxation.LoopbackForTests), StubDns.of("127.0.0.1"), TlsFixtures.donorClient()))

    private val secureUrl get() = url("https://127.0.0.1:${secure.port}/start")

    @Test
    fun `the downgrade is refused by default`() {
        val failure = assertIs<TransportFailure.RefusedByPolicy>(fleet.fetch(transport(OutboundUrlPolicy.untrusted()), fleet.get(secureUrl)).exceptionOrNull())
        assertEquals(RefusalReason.InsecureRedirect, failure.reason)
        assertEquals(1, secure.requestCount)
        assertEquals(0, plain.requestCount)
    }

    @Test
    fun `the downgrade is followed when allowed`() {
        val response = fleet.fetch(transport(OutboundUrlPolicy.untrusted { settings { allowHttpsToHttp = true } }), fleet.get(secureUrl)).getOrThrow()
        assertEquals("downgraded", response.body().decodeToString())
        assertEquals(plain.webtoolsUrl("/down"), response.url)
        assertEquals(listOf(RedirectHop(secureUrl, 302)), response.redirectChain)
        assertEquals(1, plain.requestCount)
    }
}
