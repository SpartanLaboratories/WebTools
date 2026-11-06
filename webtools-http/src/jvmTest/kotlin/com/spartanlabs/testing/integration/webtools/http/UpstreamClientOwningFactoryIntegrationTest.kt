package com.spartanlabs.testing.integration.webtools.http

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.stringEndpoint
import com.spartanlabs.webtools.http.CacheKey
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CacheStats
import com.spartanlabs.webtools.http.CaffeineResponseCache
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.UpstreamClient
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 3 - the JVM owning factory `UpstreamClient(baseUrl, userAgent) { }` against a loopback server, on real time under `runBlocking` within
 * 30 s: it builds a working client and `close()` closes what it built in the order cache, budget, transport (each component's own `closed` line,
 * then the client's); a cache passed in is not closed and stays usable; a `userAgent` the transport refuses closes the budget the factory had
 * already built and builds no transport; a refused configuration builds nothing at all.
 */
class UpstreamClientOwningFactoryIntegrationTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var server: MockWebServer
    private lateinit var baseUrl: Url
    private val windows: List<RateWindow> = listOf(RateWindow("minute", 60, 1.minutes))

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        server = fleet.server()
        baseUrl = server.webtoolsUrl("/v1/")
    }

    @AfterTest
    fun stop() = fleet.close()

    // A loopback server speaks `http`; a `Trusted` policy filters no address, so the real transport reaches it once cleartext is allowed.
    private fun cleartext(): OutboundUrlPolicy.Trusted = OutboundUrlPolicy.trusted { allowCleartext = true; pinnedHosts = setOf(baseUrl.host) }

    private fun List<ILoggingEvent>.lines(message: String): List<String> =
        filter { it.formattedMessage.startsWith(message) }.map { it.loggerName.substringAfterLast('.') }

    @Test
    fun `the owning factory builds a working client that closes what it built in the order cache, budget, transport`() {
        server.enqueue(MockResponse.Builder().code(200).body("Ada").build())
        val events = captureTransportLogs(Level.INFO) {
            val client = UpstreamClient(baseUrl, "agent") { policy = cleartext(); budget(windows) }
            try {
                val lookup = runBlocking { withTimeout(30.seconds) { client.call(stringEndpoint()) { path("players", "7") } } }.getOrThrow()
                assertEquals(Lookup.Found("Ada"), lookup)
                assertEquals(1, server.requestCount)
                assertTrue("owned=[cache, budget, transport]" in client.toString(), client.toString())
            } finally {
                client.close()
            }
        }.second

        assertEquals(listOf("CaffeineResponseCache", "WindowedCallBudget", "OkHttpTransport", "UpstreamClient"), events.lines("closed"))
        // The budget is built first (the transport's sizing needs its burst), then the transport, then the cache, then the client.
        assertEquals(listOf("WindowedCallBudget", "OkHttpTransport", "CaffeineResponseCache", "UpstreamClient"), events.lines("created"))
    }

    @Test
    fun `a cache passed in is not closed by the client and stays usable`() {
        val supplied = CaffeineResponseCache()
        try {
            val events = captureTransportLogs(Level.INFO) {
                val client = UpstreamClient(baseUrl, "agent") { policy = cleartext(); cache(supplied) }
                assertTrue("owned=[transport]" in client.toString(), client.toString())
                client.close()
            }.second

            assertEquals(listOf("OkHttpTransport", "UpstreamClient"), events.lines("closed"))
            assertEquals(CacheStats.Empty, supplied.stats())
            val stored = runBlocking {
                withTimeout(30.seconds) { supplied.getOrLoad(CacheKey<String>("k"), CachePolicy(1.minutes)) { Result.success(Lookup.Found("v")) } }
            }
            assertEquals(Lookup.Found("v"), stored.getOrThrow())
        } finally {
            supplied.close()
        }
    }

    @Test
    fun `a refused user agent closes the budget the factory had built`() {
        val events = captureTransportLogs(Level.INFO) {
            assertFailsWith<IllegalArgumentException> { UpstreamClient(baseUrl, " bad") { policy = cleartext(); budget(windows) } }
        }.second

        assertEquals(listOf("WindowedCallBudget"), events.lines("created"))
        assertEquals(listOf("WindowedCallBudget"), events.lines("closed"))
        assertFalse(events.any { it.loggerName.endsWith("OkHttpTransport") })
    }

    @Test
    fun `a refused configuration builds nothing`() {
        val withQuery = server.webtoolsUrl("/v1/?a=1")
        val events = captureTransportLogs(Level.INFO) {
            assertFailsWith<IllegalArgumentException> { UpstreamClient(withQuery, "agent") { policy = cleartext(); budget(windows) } }
        }.second

        assertTrue(events.lines("created").isEmpty(), events.map { it.loggerName + " " + it.formattedMessage }.toString())
    }
}
