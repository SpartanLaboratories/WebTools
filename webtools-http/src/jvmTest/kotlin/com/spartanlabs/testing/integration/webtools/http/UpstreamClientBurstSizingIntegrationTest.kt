package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.ConcurrencyGateDispatcher
import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.testing.support.webtools.http.stringEndpoint
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.UpstreamClient
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 3 - the transport sizing of the owning factory against a loopback server whose dispatcher holds every request until eight have arrived (or
 * five seconds have passed), on real time under `runBlocking` within 30 s. The budget's window allows a burst of eight, above the `Trusted` per-host
 * default of five: the owning factory's transport puts all eight calls in flight at once, and the control, the same client over a transport that
 * was not sized, holds the calls beyond five back until the first five were answered. The sized test fails if only the total limit is raised.
 */
class UpstreamClientBurstSizingIntegrationTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var gate: ConcurrencyGateDispatcher
    private lateinit var server: MockWebServer
    private lateinit var baseUrl: Url
    private val windows: List<RateWindow> = listOf(RateWindow("minute", 8, 1.minutes))        // maxBurst is 8

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        gate = ConcurrencyGateDispatcher(expected = 8, timeout = 5.seconds)
        server = fleet.server { dispatcher = gate }
        baseUrl = server.webtoolsUrl("/v1/")
    }

    @AfterTest
    fun stop() = fleet.close()

    private fun cleartext(): OutboundUrlPolicy.Trusted = OutboundUrlPolicy.trusted { allowCleartext = true; pinnedHosts = setOf(baseUrl.host) }

    // Eight concurrent calls of distinct keys, so none is coalesced or served from the cache.
    private fun eightCalls(client: UpstreamClient): List<Lookup<String>> = runBlocking {
        withTimeout(30.seconds) {
            (1..8).map { n -> async(Dispatchers.Default) { client.call(stringEndpoint()) { query("n", n.toString()) } } }.awaitAll().map { it.getOrThrow() }
        }
    }

    @Test
    fun `a budget burst above the per-host limit puts that many calls in flight at once`() {
        UpstreamClient(baseUrl, "agent") { policy = cleartext(); budget(windows) { laneShares = emptyMap() } }.use { client ->
            val lookups = eightCalls(client)

            assertEquals(List(8) { Lookup.Found("ok") }, lookups)
            assertEquals(8, gate.maxHeld)
            assertEquals(8, gate.arrivals)
        }
    }

    @Test
    fun `control - an unsized transport holds back the calls beyond the per-host limit`() {
        val transport = fleet.track(LoopbackTransports.trusted("agent", cleartext()))
        UpstreamClient(transport, baseUrl) { policy = cleartext(); budget(windows) { laneShares = emptyMap() } }.use { client ->
            val lookups = eightCalls(client)

            assertEquals(List(8) { Lookup.Found("ok") }, lookups)
            assertEquals(5, gate.maxHeld)                    // the `Trusted` per-host default
            assertEquals(8, gate.arrivals)                   // the other three arrived once the gate let the first five go
        }
    }
}
