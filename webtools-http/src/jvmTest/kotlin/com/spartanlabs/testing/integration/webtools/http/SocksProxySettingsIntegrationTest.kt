package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import org.junit.jupiter.api.parallel.Isolated
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Level 3 - the JVM-wide SOCKS settings (`socksProxyHost`, `socksProxyPort`, `socksNonProxyHosts=""`) against a counting raw listener on
 * loopback that closes every accepted socket: an `untrusted` transport never touches the listener and reaches the origin; a `trusted`
 * transport hits it at least once (the control that the path is reachable on this JDK; its call fails, which is expected). The class sets
 * process-wide properties and is isolated; its first statement records the previous values, which `finally` restores or clears.
 */
@Isolated
class SocksProxySettingsIntegrationTest {

    private val properties = listOf("socksProxyHost", "socksProxyPort", "socksNonProxyHosts")
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
    fun `untrusted bypasses a JVM-wide SOCKS setting that trusted honours`() {
        val target = fleet.server()
        val listener = ServerSocket(0, 50, fleet.loopback)
        val hits = AtomicInteger()
        val acceptor = Thread {
            try {
                while (true) {
                    listener.accept().close()
                    hits.incrementAndGet()
                }
            } catch (_: IOException) {
                // the listener was closed
            }
        }.apply { isDaemon = true; start() }
        try {
            System.setProperty("socksProxyHost", "127.0.0.1")
            System.setProperty("socksProxyPort", listener.localPort.toString())
            System.setProperty("socksNonProxyHosts", "")   // the default list holds 127.* and would hide the effect

            target.enqueue(MockResponse(body = "direct"))
            val untrusted = fleet.track(LoopbackTransports.untrusted("agent"))
            assertEquals("direct", fleet.fetch(untrusted, fleet.get(target.webtoolsUrl("/"))).getOrThrow().body().decodeToString())
            assertEquals(0, hits.get(), "the untrusted transport reached the SOCKS listener")
            assertEquals(1, target.requestCount)

            val trusted = fleet.track(LoopbackTransports.trusted("agent"))
            fleet.fetch(trusted, fleet.get(target.webtoolsUrl("/")))   // fails: the listener closes the SOCKS handshake
            assertTrue(hits.get() >= 1, "control: the trusted transport's socket did not reach the SOCKS listener on this JDK")
        } finally {
            listener.close()
            acceptor.join(2000)
        }
    }
}
