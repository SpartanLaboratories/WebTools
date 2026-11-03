package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import org.junit.jupiter.api.parallel.Isolated
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Level 3 - the JVM-wide HTTP proxy settings (`http.proxyHost`, `http.proxyPort`, `http.nonProxyHosts=""`, a second server as the proxy)
 * against one and the same target URL: a `trusted` transport sends its request to the proxy (the recorded target is absolute, `http://...`) and
 * the origin sees nothing; an `untrusted` transport ignores the setting and reaches the origin. The class sets process-wide properties and is
 * isolated; its first statement records the previous values, which `finally` restores or clears.
 */
@Isolated
class ProxySettingsIntegrationTest {

    private val properties = listOf("http.proxyHost", "http.proxyPort", "http.nonProxyHosts")
    private val previous = properties.associateWith { System.getProperty(it) }
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
            previous.forEach { (name, value) -> if (value == null) System.clearProperty(name) else System.setProperty(name, value) }
        }
    }

    @Test
    fun `trusted honours the JVM-wide HTTP proxy and untrusted ignores it`() {
        val target = fleet.server()
        val proxy = fleet.server()
        System.setProperty("http.proxyHost", "127.0.0.1")
        System.setProperty("http.proxyPort", proxy.port.toString())
        System.setProperty("http.nonProxyHosts", "")   // the default list holds 127.* and would hide the effect
        val url = target.webtoolsUrl("/p")

        proxy.enqueue(MockResponse(body = "via proxy"))
        val trusted = fleet.track(LoopbackTransports.trusted("agent"))
        assertEquals("via proxy", fleet.fetch(trusted, fleet.get(url)).getOrThrow().body().decodeToString())
        assertEquals(1, proxy.requestCount)
        assertEquals(0, target.requestCount)
        assertTrue(assertNotNull(proxy.takeRequest(1, TimeUnit.SECONDS)).target.startsWith("http://"), "the proxy did not see an absolute target")

        target.enqueue(MockResponse(body = "direct"))
        val untrusted = fleet.track(LoopbackTransports.untrusted("agent"))
        assertEquals("direct", fleet.fetch(untrusted, fleet.get(url)).getOrThrow().body().decodeToString())
        assertEquals(1, proxy.requestCount)
        assertEquals(1, target.requestCount)
    }
}
