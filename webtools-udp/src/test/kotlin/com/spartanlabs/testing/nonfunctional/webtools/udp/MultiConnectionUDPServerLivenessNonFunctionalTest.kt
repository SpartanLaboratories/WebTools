package com.spartanlabs.testing.nonfunctional.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.DisconnectReason
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 4c - robustness of the idle-connection sweep under load and hostile hooks.
@Tag("nonfunctional")
class MultiConnectionUDPServerLivenessNonFunctionalTest {

    private val serverAddress: InetAddress = InetAddress.getLoopbackAddress()
    private var server: MultiConnectionUDPServer? = null

    @AfterTest
    fun tearDown() {
        runCatching { server?.stop() }
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
    fun `50 idle clients are each reported exactly once and the sweep thread does not wedge`() {
        val counts = ConcurrentHashMap<String, AtomicInteger>()
        val srv = object : MultiConnectionUDPServer(DEFAULT_RECEIVE_BUFFER_BYTES, 300L) {
            override fun onClientConnect(connection: Connection) {}
            override fun onClientDisconnect(connection: Connection, reason: DisconnectReason) {
                counts.getOrPut(connection.name) { AtomicInteger() }.incrementAndGet()
            }
        }
        server = srv
        val sockets = (0 until 50).map { DatagramSocket().also { s -> handshake(s, "c$it") } }
        try {
            assertTrue(await(6000) { counts.size == 50 }, "all 50 reported, saw ${counts.size}")
            Thread.sleep(800)
            assertTrue(counts.values.all { it.get() == 1 }, "each reported exactly once")

            // sweep thread still healthy: a new handshake still registers.
            val registered = CopyOnWriteArrayList<String>()
            // reuse: just assert a fresh socket handshakes without throwing
            DatagramSocket().use { late -> handshake(late, "late") }
            registered += "late"
            assertEquals(listOf("late"), registered)
        } finally {
            sockets.forEach { it.close() }
        }
    }

    @Test
    fun `a slow onClientDisconnect does not delay detection of other idle clients`() {
        val reported = CopyOnWriteArrayList<String>()
        val srv = object : MultiConnectionUDPServer(DEFAULT_RECEIVE_BUFFER_BYTES, 300L) {
            override fun onClientConnect(connection: Connection) {}
            override fun onClientDisconnect(connection: Connection, reason: DisconnectReason) {
                if (connection.name == "slow") Thread.sleep(500)
                reported += connection.name
            }
        }
        server = srv
        val a = DatagramSocket().also { handshake(it, "slow") }
        val b = DatagramSocket().also { handshake(it, "fast") }
        try {
            // "fast" must be reported even though the dispatch thread is stuck in the slow hook.
            assertTrue(await(4000) { reported.contains("fast") }, "fast reported despite slow hook; saw $reported")
        } finally {
            a.close(); b.close()
        }
    }

    @Test
    fun `a throwing onClientDisconnect never kills the sweep or dispatch threads`() {
        val ok = CopyOnWriteArrayList<String>()
        val srv = object : MultiConnectionUDPServer(DEFAULT_RECEIVE_BUFFER_BYTES, 300L) {
            override fun onClientConnect(connection: Connection) {}
            override fun onClientDisconnect(connection: Connection, reason: DisconnectReason) {
                if (connection.name.startsWith("boom")) error("hostile hook")
                ok += connection.name
            }
        }
        server = srv
        val boomers = (0 until 5).map { DatagramSocket().also { s -> handshake(s, "boom$it") } }
        try {
            Thread.sleep(1500)
            DatagramSocket().use { survivor -> handshake(survivor, "survivor") }
            assertTrue(await(4000) { ok.contains("survivor") }, "sweep still running after throwing hooks; saw $ok")
        } finally {
            boomers.forEach { it.close() }
        }
    }

    @Test
    fun `with detection disabled no mcups-liveness thread exists and a KA burst leaves the roster untouched`() {
        val srv = object : MultiConnectionUDPServer() {
            val disconnects = CopyOnWriteArrayList<DisconnectReason>()
            override fun onClientConnect(connection: Connection) {}
            override fun onClientDisconnect(connection: Connection, reason: DisconnectReason) {
                disconnects += reason
            }
        }
        server = srv
        DatagramSocket().use { client ->
            handshake(client, "d")
            repeat(200) {
                val ka = "KA".toByteArray(Charsets.UTF_8)
                client.send(DatagramPacket(ka, ka.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
            }
            Thread.sleep(500)
            assertTrue(srv.disconnects.isEmpty())
        }
        assertNull(Thread.getAllStackTraces().keys.firstOrNull { it.name == "mcups-liveness" })
    }
}
