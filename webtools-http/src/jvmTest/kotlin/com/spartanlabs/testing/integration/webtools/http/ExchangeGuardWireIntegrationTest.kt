package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.testing.support.webtools.http.StubDns
import com.spartanlabs.webtools.http.FailureContext
import com.spartanlabs.webtools.http.GuardRelaxation
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.Phase
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UrlGuard
import com.spartanlabs.webtools.http.enforcement
import com.spartanlabs.webtools.http.hopRequest
import com.spartanlabs.webtools.http.mapFailure
import com.spartanlabs.webtools.http.newTransportClient
import com.spartanlabs.webtools.http.repeatsExchange
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Level 3 - the exchange guard on the wire: a client built by `newTransportClient` plus a test **application** interceptor that proceeds
 * twice has its second exchange refused (the call fails with the marker, which maps to `RetryRefused`) and the server saw exactly one request;
 * the variant that proceeds once succeeds.
 */
class ExchangeGuardWireIntegrationTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var server: MockWebServer
    private val clients = mutableListOf<OkHttpClient>()

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        server = fleet.server()
    }

    @AfterTest
    fun stop() {
        clients.forEach { it.dispatcher.executorService.shutdown(); it.connectionPool.evictAll() }
        fleet.close()
    }

    private fun client(proceedTwice: Boolean): OkHttpClient {
        val guard = UrlGuard(OutboundUrlPolicy.untrusted(), GuardRelaxation.LoopbackForTests)
        val base = newTransportClient(guard.policy.settings, guard.enforcement(StubDns.of("127.0.0.1")), null).client
        clients += base
        val twice = Interceptor { chain ->
            if (proceedTwice) chain.proceed(chain.request()).close()
            chain.proceed(chain.request())
        }
        return base.newBuilder().addInterceptor(twice).build().also { clients += it }
    }

    private fun request() = hopRequest(HttpMethod.Get, server.webtoolsUrl("/"), HttpHeaders(), null, "agent").getOrThrow()

    @Test
    fun `a second exchange of one hop is refused and maps to RetryRefused`() {
        server.enqueue(MockResponse(body = "one"))
        server.enqueue(MockResponse(body = "two"))
        val failure = assertFailsWith<IOException> { client(proceedTwice = true).newCall(request()).execute() }
        assertTrue(failure.repeatsExchange(), failure.toString())
        val mapped = mapFailure(failure, FailureContext("127.0.0.1", false, false, false, Phase.Exchange))
        assertIs<TransportFailure.RetryRefused>(mapped.failure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `one exchange per hop succeeds`() {
        server.enqueue(MockResponse(body = "one"))
        client(proceedTwice = false).newCall(request()).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("one", response.body.string())
        }
        assertEquals(1, server.requestCount)
    }
}
