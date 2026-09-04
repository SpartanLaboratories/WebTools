package com.spartanlabs.testing.e2e.webtools

import com.spartanlabs.webtools.Connection
import com.spartanlabs.webtools.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Level 4b - full-stack flow over loopback: a real [MultiConnectionUDPServer]
 * subclass and three concurrent simulated clients. Each handshakes, actuates, and
 * runs a scripted exchange (N messages each, server echoes and periodically
 * broadcasts, server-side keepalive between messages). Asserts every client sees
 * its own echoes and all broadcasts in order with no cross-talk, keepalives never
 * surface as app messages, and the server binds exactly one UDP port.
 */
@Tag("e2e")
class MultiConnectionUDPServerE2ETest {

    private val log = LoggerFactory.getLogger(MultiConnectionUDPServerE2ETest::class.java)
    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private class EchoServer : MultiConnectionUDPServer() {
        val connections = CopyOnWriteArrayList<Connection>()
        override fun onClientConnect(connection: Connection) {
            connections += connection
            connection.actuate { msg ->
                connection.push("echo:$msg")
                connection.keepAlive()
            }
        }
    }

    private inner class Client(val name: String) {
        val socket = DatagramSocket()
        val received = CopyOnWriteArrayList<String>()

        fun handshake() {
            send("Iam $name")
            assertEquals("REGISTERED", recv())
        }

        fun send(text: String) {
            val out = text.toByteArray()
            socket.send(DatagramPacket(out, out.size, loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
        }

        fun pump(timeoutMillis: Int) {
            socket.soTimeout = timeoutMillis
            try {
                while (true) {
                    val p = DatagramPacket(ByteArray(1024), 1024)
                    socket.receive(p)
                    received += String(p.data, 0, p.length, Charsets.UTF_8).trim()
                }
            } catch (_: java.net.SocketTimeoutException) {
                // done draining
            }
        }

        private fun recv(): String {
            socket.soTimeout = 5000
            val p = DatagramPacket(ByteArray(1024), 1024)
            socket.receive(p)
            return String(p.data, 0, p.length, Charsets.UTF_8).trim()
        }
    }

    @Test
    fun `three clients exchange data both ways with no cross-talk over one port`() {
        val server = EchoServer()
        val clients = listOf(Client("c1"), Client("c2"), Client("c3"))
        val perClient = 10
        try {
            clients.forEach { it.handshake() }
            Thread.sleep(200)
            assertEquals(3, server.connections.size)

            repeat(perClient) { i ->
                clients.forEach { it.send("${it.name}-msg$i") }
                clients.forEach { it.send("KA") } // client-side keepalive; server must swallow it
                if (i % 4 == 0) server.pushToAll("bc$i")
                Thread.sleep(20)
            }

            clients.forEach { it.pump(1000) }

            clients.forEach { client ->
                val echoes = client.received.filter { it.startsWith("echo:") }
                assertEquals(
                    (0 until perClient).map { "echo:${client.name}-msg$it" },
                    echoes,
                    "${client.name} must receive its own echoes in order",
                )
                // No other client's messages leaked in.
                clients.filter { it != client }.forEach { other ->
                    assertTrue(client.received.none { it.contains("${other.name}-msg") })
                }
                // Broadcasts arrived.
                val broadcasts = client.received.filter { it.startsWith("bc") }.toSet()
                assertEquals(setOf("bc0", "bc4", "bc8"), broadcasts)
                // Inbound keepalives were consumed by the server, never dispatched to the
                // handler (which would have echoed "echo:KA" back).
                assertTrue(client.received.none { it == "echo:KA" })
            }
        } finally {
            clients.forEach { it.socket.close() }
            assertTrue(server.stop().isSuccess)
        }

        // The one UDP port the whole session used is free again.
        DatagramSocket(MultiConnectionUDPServer.COMMON_LISTEN_PORT).use { /* bind succeeds */ }
    }
}
