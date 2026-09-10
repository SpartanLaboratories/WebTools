package com.spartanlabs.testing.nonfunctional.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 4c - robustness of the server-side scheduled keepalive under many connections and
// hostile senders, plus the zero-cost-when-unused guarantee.
@Tag("nonfunctional")
class MultiConnectionUDPServerKeepAliveNonFunctionalTest {

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

    private fun sawKeepAlive(client: DatagramSocket, windowMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + windowMillis
        while (System.currentTimeMillis() < deadline) {
            if (client.nextText(150) == "KA") return true
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

    @Test
    fun `50 connections each arm a keepalive - one shared mcups-keepalive thread, every peer gets KA`() {
        val byName = ConcurrentHashMap<String, Connection>()
        val srv = object : MultiConnectionUDPServer() {
            override fun onClientConnect(connection: Connection) {
                byName[connection.name] = connection
                connection.startKeepAlive(300L)
            }
        }
        server = srv
        val sockets = (0 until 50).map { i -> DatagramSocket().also { handshake(it, "c$i") } }
        try {
            assertTrue(await(6_000L) { byName.size == 50 })

            val keepAliveThreads = Thread.getAllStackTraces().keys.count { it.name == "mcups-keepalive" && it.isAlive }
            assertEquals(1, keepAliveThreads, "exactly one shared keepalive thread")

            sockets.take(10).forEach { assertTrue(sawKeepAlive(it, 2_000L), "peer received a KA") }
        } finally {
            sockets.forEach { it.close() }
        }
    }

    @Test
    fun `terminate on one connection stops its KA and does not disturb the others`() {
        val byName = ConcurrentHashMap<String, Connection>()
        val srv = object : MultiConnectionUDPServer() {
            override fun onClientConnect(connection: Connection) {
                byName[connection.name] = connection
                connection.startKeepAlive(300L)
            }
        }
        server = srv
        val a = DatagramSocket().also { handshake(it, "a") }
        val b = DatagramSocket().also { handshake(it, "b") }
        try {
            assertTrue(await(3_000L) { byName.size == 2 })
            assertTrue(sawKeepAlive(a, 1_500L))

            assertTrue(byName.getValue("a").terminate().isSuccess)
            Thread.sleep(400)
            while (a.nextText(20) != null) { /* drain */ }
            assertTrue(!sawKeepAlive(a, 900L), "terminated connection's KA stopped")
            assertTrue(sawKeepAlive(b, 1_500L), "the other connection is undisturbed")
        } finally {
            a.close(); b.close()
        }
    }

    @Test
    fun `an intermittently failing send never cancels the repeating keepalive task`() {
        // The server here can't inject a failing sender, so drive the repeating-task robustness
        // through PeriodicScheduler directly: a tick that throws every other call keeps firing.
        val scheduler = com.spartanlabs.webtools.udp.PeriodicScheduler("nf-keepalive")
        val key = java.net.InetSocketAddress(serverAddress, 57000)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        try {
            scheduler.schedule(key, 1_000L) {
                if (calls.incrementAndGet() % 2 == 0) error("intermittent send failure")
            }
            assertTrue(await(3_000L) { calls.get() >= 4 }, "task kept firing despite failures, saw ${calls.get()}")
        } finally {
            scheduler.shutdown()
        }
    }

    @Test
    fun `a server whose consumers never arm a keepalive starts no thread under a KA-data burst`() {
        val srv = object : MultiConnectionUDPServer() {
            override fun onClientConnect(connection: Connection) { connection.actuate { } }
        }
        server = srv
        DatagramSocket().use { client ->
            handshake(client, "d")
            repeat(200) {
                val ka = "KA".toByteArray(Charsets.UTF_8)
                client.send(DatagramPacket(ka, ka.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
                val data = "x".toByteArray(Charsets.UTF_8)
                client.send(DatagramPacket(data, data.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
            }
            Thread.sleep(500)
        }
        assertNull(Thread.getAllStackTraces().keys.firstOrNull { it.name == "mcups-keepalive" })
    }
}
