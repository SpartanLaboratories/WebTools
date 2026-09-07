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
import kotlin.test.assertTrue

// Level 4b - real MultiConnectionUDPClient + real MultiConnectionUDPServer subclass over
// loopback, exercising the opt-in scheduled keepalive as the only thing holding a session open.
@Tag("e2e")
class MultiConnectionUDPKeepAliveE2ETest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private class Srv(idle: Long) : MultiConnectionUDPServer(DEFAULT_RECEIVE_BUFFER_BYTES, idle) {
        val byName = ConcurrentHashMap<String, Connection>()
        val disconnects = CopyOnWriteArrayList<Pair<String, DisconnectReason>>()
        var armKeepAliveOnConnect = false
        override fun onClientConnect(connection: Connection) {
            byName[connection.name] = connection
            connection.actuate { }
            if (armKeepAliveOnConnect) connection.startKeepAlive(300L)
        }
        override fun onClientDisconnect(connection: Connection, reason: DisconnectReason) {
            disconnects += connection.name to reason
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
    fun `the scheduled keepalive alone keeps a client from timing out, and stopping it lets TIMEOUT fire`() {
        val server = Srv(idle = 1_200L)
        try {
            val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
            assertTrue(client.handshake("alice").isSuccess)
            assertTrue(await(2_000L) { server.byName.containsKey("alice") })
            client.start { }
            assertTrue(client.startKeepAlive(300L).isSuccess)

            // Several times the idle timeout with no application traffic and no hand-rolled timer.
            Thread.sleep(4_000L)
            assertTrue(server.disconnects.none { it.second == DisconnectReason.TIMEOUT }, "kept alive by the scheduler")
            assertTrue(server.pushToAll("still-reachable").isSuccess)

            // Kill the only thing keeping it alive - TIMEOUT must now fire.
            assertTrue(client.stopKeepAlive().isSuccess)
            assertTrue(
                await(4_000L) { server.disconnects.any { it.second == DisconnectReason.TIMEOUT } },
                "TIMEOUT fires once the keepalive stops",
            )
            client.stop()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a server-to-client scheduled keepalive never reaches the start callback but the client stays reachable`() {
        val server = Srv(idle = 0L).apply { armKeepAliveOnConnect = true }
        try {
            val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
            assertTrue(client.handshake("bob").isSuccess)
            assertTrue(await(2_000L) { server.byName.containsKey("bob") })
            val seen = ConcurrentLinkedQueue<String>()
            client.start { seen += it }

            Thread.sleep(1_500L)
            assertTrue(seen.none { it == "KA" }, "KA is dropped, never delivered")

            assertTrue(server.byName.getValue("bob").push("ping").isSuccess)
            assertTrue(await(2_000L) { seen.contains("ping") }, "client still reachable")

            assertTrue(server.byName.getValue("bob").terminate().isSuccess)
            client.stop()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `both sides arm a keepalive and a bidirectional idle session survives past both intervals`() {
        val server = Srv(idle = 1_200L).apply { armKeepAliveOnConnect = true }
        try {
            val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
            assertTrue(client.handshake("carol").isSuccess)
            assertTrue(await(2_000L) { server.byName.containsKey("carol") })
            client.start { }
            assertTrue(client.startKeepAlive(300L).isSuccess)

            Thread.sleep(4_000L)
            assertTrue(server.disconnects.isEmpty(), "no disconnect on an idle but kept-alive session")
            assertTrue(server.pushToAll("hi").isSuccess)

            assertTrue(client.stop().isSuccess)
        } finally {
            assertTrue(server.stop().isSuccess)
        }
        assertTrue(Thread.getAllStackTraces().keys.none { it.name == "mcupc-keepalive" && it.isAlive })
        assertTrue(Thread.getAllStackTraces().keys.none { it.name == "mcups-keepalive" && it.isAlive })
    }

    @Test
    fun `the one-shot sendKeepAlive primitive on a caller's own timer still works`() {
        val server = Srv(idle = 1_200L)
        val timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
            assertTrue(client.handshake("dave").isSuccess)
            assertTrue(await(2_000L) { server.byName.containsKey("dave") })
            client.start { }
            timer.scheduleWithFixedDelay({ client.sendKeepAlive() }, 0, 300, java.util.concurrent.TimeUnit.MILLISECONDS)

            Thread.sleep(4_000L)
            assertTrue(server.disconnects.none { it.second == DisconnectReason.TIMEOUT })
            client.stop()
        } finally {
            timer.shutdownNow()
            server.stop()
        }
    }
}
