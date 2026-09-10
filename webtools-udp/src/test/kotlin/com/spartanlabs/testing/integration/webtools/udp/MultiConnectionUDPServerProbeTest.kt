package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 3 - a real MultiConnectionUDPServer against a real MultiConnectionUDPClient (whose
// listener auto-answers PING), exercising Connection.startProbe / stopProbe end to end and
// the symmetric client-probes-server direction.
@Tag("integration")
class MultiConnectionUDPServerProbeTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private var server: MultiConnectionUDPServer? = null
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    @AfterTest
    fun tearDown() {
        clients.forEach { runCatching { it.stop() } }
        runCatching { server?.stop() }
    }

    private class ProbeServer(val onConnect: (Connection) -> Unit) : MultiConnectionUDPServer() {
        val connections = CopyOnWriteArrayList<Connection>()
        override fun onClientConnect(connection: Connection) {
            connections += connection
            connection.actuate { }
            onConnect(connection)
        }
    }

    private fun await(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    private fun connectedClient(): MultiConnectionUDPClient {
        val client = MultiConnectionUDPClient(loopback).also { clients += it }
        assertTrue(client.handshake("c").isSuccess)
        assertTrue(client.start { }.isSuccess)
        return client
    }

    @Test
    fun `onClientConnect starting a probe drives connection linkQuality to a small-rtt snapshot`() {
        val srv = ProbeServer { it.startProbe(300L) }
        server = srv
        connectedClient()
        assertTrue(await(3_000L) { srv.connections.size == 1 })
        val connection = srv.connections.single()

        assertTrue(await(4_000L) { connection.linkQuality() != null }, "linkQuality populated")
        val snap = connection.linkQuality()!!
        assertTrue(snap.rttMillis in 0.0..500.0, "small RTT, was ${snap.rttMillis}")
        assertTrue(snap.packetLossRatio < 0.5, "low loss, was ${snap.packetLossRatio}")
    }

    @Test
    fun `stopProbe then terminate each stop the probe cleanly`() {
        val srv = ProbeServer { it.startProbe(300L) }
        server = srv
        connectedClient()
        assertTrue(await(3_000L) { srv.connections.size == 1 })
        val connection = srv.connections.single()
        assertTrue(await(4_000L) { connection.linkQuality() != null })

        assertTrue(connection.stopProbe().isSuccess)
        val frozen = connection.linkQuality()
        Thread.sleep(700)
        assertTrue(connection.linkQuality()?.probesSent == frozen?.probesSent, "probe stream halted by stopProbe")

        assertTrue(connection.startProbe(300L).isSuccess)
        assertTrue(await(3_000L) { (connection.linkQuality()?.probesSent ?: 0) > (frozen?.probesSent ?: 0) })
        assertTrue(connection.terminate().isSuccess)
    }

    @Test
    fun `the server answers client PINGs even with no server-side probe armed`() {
        val srv = ProbeServer { /* no server-side probe */ }
        server = srv
        val client = connectedClient()
        assertTrue(await(3_000L) { srv.connections.size == 1 })

        assertTrue(client.startProbe(300L).isSuccess)

        assertTrue(await(4_000L) { client.linkQuality() != null }, "client linkQuality populated")
        val snap = client.linkQuality()!!
        assertTrue(snap.rttMillis in 0.0..500.0, "small RTT, was ${snap.rttMillis}")
        assertTrue(snap.packetLossRatio < 0.5)
    }

    @Test
    fun `a default server has no mcups-probe thread and stop leaves none`() {
        val srv = ProbeServer { /* never arm */ }
        server = srv
        connectedClient()
        assertTrue(await(3_000L) { srv.connections.size == 1 })
        Thread.sleep(400)
        assertNull(Thread.getAllStackTraces().keys.firstOrNull { it.name == "mcups-probe" && it.isAlive })

        assertTrue(srv.stop().isSuccess)
        Thread.sleep(300)
        assertNull(Thread.getAllStackTraces().keys.firstOrNull { it.name == "mcups-probe" && it.isAlive })
    }
}
