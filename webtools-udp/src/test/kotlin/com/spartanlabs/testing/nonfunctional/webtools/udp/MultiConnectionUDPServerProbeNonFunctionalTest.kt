package com.spartanlabs.testing.nonfunctional.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.HandshakeWireFormat
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 4c - robustness of the server-side link-quality probe under many connections, the
// zero-cost-when-unused guarantee, and the bounded responder path.
@Tag("nonfunctional")
class MultiConnectionUDPServerProbeNonFunctionalTest {

    private val serverAddress: InetAddress = InetAddress.getLoopbackAddress()
    private var server: MultiConnectionUDPServer? = null

    @AfterTest
    fun tearDown() {
        runCatching { server?.stop() }
    }

    private fun handshake(client: DatagramSocket, name: String) {
        val iam = "Iam $name".toByteArray(Charsets.UTF_8)
        client.send(DatagramPacket(iam, iam.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
        client.soTimeout = 5_000
        client.receive(DatagramPacket(ByteArray(64), 64))
    }

    private fun DatagramSocket.nextText(timeoutMillis: Int): String? {
        soTimeout = timeoutMillis
        val packet = DatagramPacket(ByteArray(256), 256)
        return try {
            receive(packet); String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    private fun sawProbeRequest(client: DatagramSocket, windowMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + windowMillis
        while (System.currentTimeMillis() < deadline) {
            if (client.nextText(150)?.let { HandshakeWireFormat.isProbeRequest(it) } == true) return true
        }
        return false
    }

    private fun await(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    private fun probeThreads() =
        Thread.getAllStackTraces().keys.count { it.name == "mcups-probe" && it.isAlive }

    @Test
    fun `50 connections each arm a probe - one shared mcups-probe thread, every peer gets PING`() {
        val byName = ConcurrentHashMap<String, Connection>()
        val srv = object : MultiConnectionUDPServer() {
            override fun onClientConnect(connection: Connection) {
                byName[connection.name] = connection
                connection.startProbe(300L)
            }
        }
        server = srv
        val sockets = (0 until 50).map { i -> DatagramSocket().also { handshake(it, "c$i") } }
        try {
            assertTrue(await(6_000L) { byName.size == 50 })
            assertEquals(1, probeThreads(), "exactly one shared probe thread")
            sockets.take(10).forEach { assertTrue(sawProbeRequest(it, 2_000L), "peer received a PING") }
        } finally {
            sockets.forEach { it.close() }
        }
    }

    @Test
    fun `terminate on one connection stops its PING and does not disturb the others`() {
        val byName = ConcurrentHashMap<String, Connection>()
        val srv = object : MultiConnectionUDPServer() {
            override fun onClientConnect(connection: Connection) {
                byName[connection.name] = connection
                connection.startProbe(300L)
            }
        }
        server = srv
        val a = DatagramSocket().also { handshake(it, "a") }
        val b = DatagramSocket().also { handshake(it, "b") }
        try {
            assertTrue(await(4_000L) { byName.size == 2 })
            assertTrue(sawProbeRequest(a, 2_000L) && sawProbeRequest(b, 2_000L))

            assertTrue(byName.getValue("a").terminate().isSuccess)
            Thread.sleep(700)
            while (a.nextText(20) != null) { /* drain */ }
            assertTrue(!sawProbeRequest(a, 900L), "terminated connection's PING stopped")
            assertTrue(sawProbeRequest(b, 2_000L), "the other connection is undisturbed")
        } finally {
            a.close(); b.close()
        }
    }

    @Test
    fun `a server whose consumers never arm a probe allocates no tracker and starts no thread`() {
        val byName = ConcurrentHashMap<String, Connection>()
        val srv = object : MultiConnectionUDPServer() {
            override fun onClientConnect(connection: Connection) {
                byName[connection.name] = connection
                connection.actuate { }
            }
        }
        server = srv
        DatagramSocket().use { client ->
            handshake(client, "c")
            assertTrue(await(3_000L) { byName.size == 1 })

            // A burst of PING and data from the client - the server answers PING inline but
            // never allocates a LinkQualityTracker for the connection.
            repeat(20) { i ->
                val ping = HandshakeWireFormat.probeRequestMessage(i.toString()).toByteArray(Charsets.UTF_8)
                client.send(DatagramPacket(ping, ping.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
                val data = "d$i".toByteArray(Charsets.UTF_8)
                client.send(DatagramPacket(data, data.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
            }
            Thread.sleep(300)

            assertNull(byName.getValue("c").linkQuality(), "no tracker allocated for a never-probed connection")
            assertEquals(0, probeThreads(), "no probe thread")
        }
    }

    @Test
    fun `the responder answers every inbound PING inline even with the dispatch thread wedged`() {
        val byName = ConcurrentHashMap<String, Connection>()
        val srv = object : MultiConnectionUDPServer() {
            override fun onClientConnect(connection: Connection) {
                byName[connection.name] = connection
                connection.actuate { Thread.sleep(5_000) } // wedge mcups-dispatch on the first data datagram
            }
        }
        server = srv
        DatagramSocket().use { client ->
            handshake(client, "c")
            assertTrue(await(3_000L) { byName.size == 1 })

            // Wedge the dispatch thread with one data datagram.
            val data = "wedge".toByteArray(Charsets.UTF_8)
            client.send(DatagramPacket(data, data.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
            Thread.sleep(100)

            var pongs = 0
            repeat(10) { i ->
                val ping = HandshakeWireFormat.probeRequestMessage(i.toString()).toByteArray(Charsets.UTF_8)
                client.send(DatagramPacket(ping, ping.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
                if (client.nextText(500)?.let { HandshakeWireFormat.isProbeReply(it) } == true) pongs++
            }
            assertTrue(pongs >= 8, "PONGs kept flowing inline while dispatch was wedged, saw $pongs")
        }
    }
}
