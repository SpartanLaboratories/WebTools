package com.spartanlabs.testing.e2e.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.DisconnectReason
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 4b - real client + real server subclass over loopback, idle detection enabled.
@Tag("e2e")
class MultiConnectionUDPLivenessE2ETest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private class LivenessServer : MultiConnectionUDPServer(DEFAULT_RECEIVE_BUFFER_BYTES, 300L) {
        val connectionsByName = ConcurrentHashMap<String, Connection>()
        val disconnects = CopyOnWriteArrayList<Pair<String, DisconnectReason>>()

        override fun onClientConnect(connection: Connection) {
            connectionsByName[connection.name] = connection
            connection.actuate { }
        }

        override fun onClientDisconnect(connection: Connection, reason: DisconnectReason) {
            disconnects += connection.name to reason
            if (reason == DisconnectReason.TIMEOUT) connection.terminate()
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

    @Test
    fun `a client that stops triggers TIMEOUT then, when the hook terminates, TERMINATED`() {
        val server = LivenessServer()
        try {
            val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
            assertTrue(client.handshake("alice").isSuccess)
            assertTrue(await(2000) { server.connectionsByName.containsKey("alice") })
            client.start { }
            client.stop()

            assertTrue(await(4000) { server.disconnects.size >= 2 }, "TIMEOUT then TERMINATED")
            assertEquals(
                listOf("alice" to DisconnectReason.TIMEOUT, "alice" to DisconnectReason.TERMINATED),
                server.disconnects.toList(),
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a keepalive loop holds the connection alive, and stopping it then triggers TIMEOUT`() {
        val server = LivenessServer()
        try {
            val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
            assertTrue(client.handshake("ka").isSuccess)
            assertTrue(await(2000) { server.connectionsByName.containsKey("ka") })
            client.start { }

            repeat(15) {
                client.sendKeepAlive()
                Thread.sleep(100)
            }
            assertTrue(server.disconnects.isEmpty(), "kept alive across many sweep intervals")

            assertTrue(await(4000) { server.disconnects.any { it.second == DisconnectReason.TIMEOUT } })
            client.stop()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `after a TIMEOUT a fresh client under the same name is seen as SUPERSEDED then onClientConnect`() {
        val server = object : MultiConnectionUDPServer(DEFAULT_RECEIVE_BUFFER_BYTES, 300L) {
            val events = ConcurrentLinkedQueue<String>()
            override fun onClientConnect(connection: Connection) {
                events += "connect:${connection.name}"
                connection.actuate { }
            }
            override fun onClientDisconnect(connection: Connection, reason: DisconnectReason) {
                events += "$reason:${connection.name}"
            }
        }
        try {
            val first = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
            assertTrue(first.handshake("hero").isSuccess)
            first.start { }
            assertTrue(await(4000) { server.events.any { it == "TIMEOUT:hero" } })
            first.stop()

            val second = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
            assertTrue(second.handshake("hero").isSuccess)
            assertTrue(await(3000) { server.events.count { it.endsWith("connect:hero") } == 2 || server.events.contains("connect:hero") })
            assertTrue(server.events.contains("SUPERSEDED:hero"), "events were ${server.events}")
            second.stop()
        } finally {
            server.stop()
        }
    }
}
