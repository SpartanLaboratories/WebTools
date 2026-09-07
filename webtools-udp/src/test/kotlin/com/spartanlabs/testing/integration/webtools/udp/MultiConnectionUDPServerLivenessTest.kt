package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.DisconnectReason
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 3 - a real MultiConnectionUDPServer with idle detection enabled, its own instance
// (not the shared server of MultiConnectionUDPServerTest). Runs under the module's
// commonUdpPortLock via the integrationTest task.
@Tag("integration")
class MultiConnectionUDPServerLivenessTest {

    private val serverAddress: InetAddress = InetAddress.getLoopbackAddress()
    private var server: MultiConnectionUDPServer? = null

    @AfterTest
    fun tearDown() {
        runCatching { server?.stop() }
    }

    private class LivenessServer(idle: Long) : MultiConnectionUDPServer(DEFAULT_RECEIVE_BUFFER_BYTES, idle) {
        val connections = CopyOnWriteArrayList<Connection>()
        val disconnects = ConcurrentLinkedQueue<Pair<String, DisconnectReason>>()
        val disconnectThreads = ConcurrentLinkedQueue<String>()

        override fun onClientConnect(connection: Connection) {
            connections += connection
        }

        override fun onClientDisconnect(connection: Connection, reason: DisconnectReason) {
            disconnectThreads += Thread.currentThread().name
            disconnects += connection.name to reason
        }
    }

    private fun handshake(client: DatagramSocket, name: String) {
        val iam = "Iam $name".toByteArray(Charsets.UTF_8)
        client.send(DatagramPacket(iam, iam.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
        client.soTimeout = 5000
        client.receive(DatagramPacket(ByteArray(64), 64))
    }

    private fun await(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    @Test
    fun `a silent client is reported TIMEOUT once, on the dispatch thread, and stays addressable`() {
        val srv = LivenessServer(300L)
        server = srv
        DatagramSocket().use { client ->
            handshake(client, "silent")
            assertTrue(await(2000) { srv.connections.size == 1 })

            assertTrue(await(3000) { srv.disconnects.isNotEmpty() }, "TIMEOUT fired")
            Thread.sleep(700)
            assertEquals(listOf("silent" to DisconnectReason.TIMEOUT), srv.disconnects.toList())
            assertTrue(srv.disconnectThreads.all { it == "mcups-dispatch" })

            // notify-only: still in the roster and still a pushToAll target.
            assertEquals(1, srv.connections.size)
            assertTrue(srv.pushToAll("still-here").isSuccess)
            client.soTimeout = 2000
            val packet = DatagramPacket(ByteArray(64), 64)
            client.receive(packet)
            assertEquals("still-here", String(packet.data, 0, packet.length, Charsets.UTF_8).trim())
        }
    }

    @Test
    fun `a client sending KA on a 100ms cadence is never reported`() {
        val srv = LivenessServer(300L)
        server = srv
        DatagramSocket().use { client ->
            handshake(client, "chatty")
            assertTrue(await(2000) { srv.connections.size == 1 })

            repeat(20) {
                val ka = "KA".toByteArray(Charsets.UTF_8)
                client.send(DatagramPacket(ka, ka.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
                Thread.sleep(100)
            }
            assertTrue(srv.disconnects.isEmpty(), "a live client must not be reported")
        }
    }

    @Test
    fun `the default server creates no mcups-liveness thread and never reports a silent client`() {
        val inbound = LinkedBlockingQueue<String>()
        val srv = object : MultiConnectionUDPServer() {
            val disconnects = ConcurrentLinkedQueue<DisconnectReason>()
            override fun onClientConnect(connection: Connection) = connection.actuate { inbound.add(it) }.let {}
            override fun onClientDisconnect(connection: Connection, reason: DisconnectReason) {
                disconnects += reason
            }
        }
        server = srv
        DatagramSocket().use { client ->
            handshake(client, "def")
            assertNull(inbound.poll(500, TimeUnit.MILLISECONDS))
            Thread.sleep(1500)
            assertTrue(srv.disconnects.isEmpty())
        }
        assertNull(Thread.getAllStackTraces().keys.firstOrNull { it.name == "mcups-liveness" })
    }

    @Test
    fun `stop on a liveness-enabled server leaves no live mcups-liveness thread and fires no TERMINATED storm`() {
        val srv = LivenessServer(300L)
        server = srv
        DatagramSocket().use { client -> handshake(client, "gone") }
        assertTrue(await(2000) { srv.connections.size == 1 })

        assertTrue(srv.stop().isSuccess)
        Thread.sleep(300)

        assertTrue(srv.disconnects.none { it.second == DisconnectReason.TERMINATED })
        val live = Thread.getAllStackTraces().keys.firstOrNull { it.name == "mcups-liveness" && it.isAlive }
        assertNull(live)
    }
}
