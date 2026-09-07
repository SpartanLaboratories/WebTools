package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 3 - a real MultiConnectionUDPServer with its own instance, a real client DatagramSocket
// standing in for a peer, exercising Connection.startKeepAlive / stopKeepAlive end to end.
@Tag("integration")
class MultiConnectionUDPServerKeepAliveTest {

    private val serverAddress: InetAddress = InetAddress.getLoopbackAddress()
    private var server: MultiConnectionUDPServer? = null

    @AfterTest
    fun tearDown() {
        runCatching { server?.stop() }
    }

    private class KeepAliveServer(
        val onConnect: (Connection) -> Unit,
    ) : MultiConnectionUDPServer() {
        val connections = CopyOnWriteArrayList<Connection>()
        override fun onClientConnect(connection: Connection) {
            connections += connection
            onConnect(connection)
        }
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
            receive(packet)
            String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    private fun countKeepAlives(client: DatagramSocket, windowMillis: Long): Int {
        val deadline = System.currentTimeMillis() + windowMillis
        var count = 0
        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(1)
            val msg = client.nextText(remaining) ?: break
            if (msg == "KA") count++
        }
        return count
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
    fun `an idle client receives server-to-client KA once onClientConnect arms one`() {
        val srv = KeepAliveServer { it.startKeepAlive(300L) }
        server = srv
        DatagramSocket().use { client ->
            handshake(client, "c")
            assertTrue(await(2_000L) { srv.connections.size == 1 })

            assertTrue(countKeepAlives(client, 1_400L) >= 2)
        }
    }

    @Test
    fun `server push traffic suppresses the next KA`() {
        val srv = KeepAliveServer { it.startKeepAlive(300L) }
        server = srv
        DatagramSocket().use { client ->
            handshake(client, "c")
            assertTrue(await(2_000L) { srv.connections.size == 1 })
            val connection = srv.connections.single()

            val deadline = System.currentTimeMillis() + 1_500L
            var kaSeen = 0
            var dataSeen = 0
            while (System.currentTimeMillis() < deadline) {
                connection.push("tick")
                Thread.sleep(100)
                while (true) {
                    val msg = client.nextText(20) ?: break
                    if (msg == "KA") kaSeen++ else dataSeen++
                }
            }
            assertTrue(dataSeen > 0)
            assertTrue(kaSeen == 0, "no KA while the server keeps pushing, saw $kaSeen")
        }
    }

    @Test
    fun `stopKeepAlive halts the KA stream`() {
        val srv = KeepAliveServer { it.startKeepAlive(300L) }
        server = srv
        DatagramSocket().use { client ->
            handshake(client, "c")
            assertTrue(await(2_000L) { srv.connections.size == 1 })
            assertTrue(countKeepAlives(client, 1_200L) >= 1)

            assertTrue(srv.connections.single().stopKeepAlive().isSuccess)
            Thread.sleep(400)
            while (client.nextText(20) != null) { /* drain */ }
            assertTrue(countKeepAlives(client, 900L) == 0)
        }
    }

    @Test
    fun `terminate halts the KA stream and cancels the schedule`() {
        val srv = KeepAliveServer { it.startKeepAlive(300L) }
        server = srv
        DatagramSocket().use { client ->
            handshake(client, "c")
            assertTrue(await(2_000L) { srv.connections.size == 1 })
            assertTrue(countKeepAlives(client, 1_200L) >= 1)

            assertTrue(srv.connections.single().terminate().isSuccess)
            Thread.sleep(400)
            while (client.nextText(20) != null) { /* drain */ }
            assertTrue(countKeepAlives(client, 900L) == 0)
        }
    }

    @Test
    fun `a server whose consumers never arm a keepalive has no mcups-keepalive thread`() {
        val srv = KeepAliveServer { /* do not arm */ }
        server = srv
        DatagramSocket().use { client ->
            handshake(client, "c")
            assertTrue(await(2_000L) { srv.connections.size == 1 })
            Thread.sleep(500)
        }
        assertNull(Thread.getAllStackTraces().keys.firstOrNull { it.name == "mcups-keepalive" })
    }

    @Test
    fun `stop leaves no live mcups-keepalive thread`() {
        val srv = KeepAliveServer { it.startKeepAlive(300L) }
        server = srv
        DatagramSocket().use { client ->
            handshake(client, "c")
            assertTrue(await(2_000L) { srv.connections.size == 1 })
            assertTrue(countKeepAlives(client, 1_000L) >= 1)
        }

        assertTrue(srv.stop().isSuccess)
        Thread.sleep(300)
        assertNull(Thread.getAllStackTraces().keys.firstOrNull { it.name == "mcups-keepalive" && it.isAlive })
    }
}
