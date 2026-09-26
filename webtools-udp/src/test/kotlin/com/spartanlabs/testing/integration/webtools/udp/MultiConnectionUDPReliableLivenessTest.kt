package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.DeliveryMode
import com.spartanlabs.webtools.udp.DisconnectReason
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

// Level 3 - the concrete consequence of the §1.3(b) delayed-ack guard: an idle-but-open
// reliable channel must not masquerade as liveness. Without the ackPending guard, both sides'
// retransmit ticks would exchange a standalone ack forever once any reliable datagram had ever
// been seen, permanently refreshing Registration.lastInboundAt and silently defeating Issue #10
// idle detection - see design §1.3(b) / §3.6.
@Tag("integration")
@Suppress("DEPRECATION") // exercises the still-working, now-deprecated push/actuate/send/start primitives on purpose
class MultiConnectionUDPReliableLivenessTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private var server: MultiConnectionUDPServer? = null
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    @AfterTest
    fun cleanup() {
        clients.forEach { runCatching { it.stop() } }
        runCatching { server?.stop() }
    }

    private class LivenessServer(idle: Long) : MultiConnectionUDPServer(DEFAULT_RECEIVE_BUFFER_BYTES, idle) {
        val connections = CopyOnWriteArrayList<Connection>()
        val disconnects = ConcurrentLinkedQueue<Pair<String, DisconnectReason>>()
        override fun onClientConnect(connection: Connection) {
            connections += connection
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
    fun `a reliable channel open but idle still lets onClientDisconnect TIMEOUT fire`() {
        val srv = LivenessServer(idle = 500L)
        server = srv
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT).also { clients += it }
        assertTrue(client.handshake("dana").isSuccess)
        assertTrue(await(2_000L) { srv.connections.size == 1 })
        val serverConnection = srv.connections.single()

        // Open and exchange once on the reliable channel BOTH ways, so each side's engine has
        // seen at least one inbound 0xA0/0xA1 - the exact precondition the pre-fix "ack !=
        // SENTINEL" check would have latched on forever.
        val clientReceived = ConcurrentLinkedQueue<String>()
        val serverReceived = ConcurrentLinkedQueue<String>()
        assertTrue(client.channel(DeliveryMode.RELIABLE_ORDERED).actuate(clientReceived::add).isSuccess)
        assertTrue(serverConnection.channel(DeliveryMode.RELIABLE_ORDERED).actuate(serverReceived::add).isSuccess)
        assertTrue(client.channel(DeliveryMode.RELIABLE_ORDERED).send("hello-once").isSuccess)
        assertTrue(await(3_000L) { serverReceived.contains("hello-once") })
        assertTrue(serverConnection.channel(DeliveryMode.RELIABLE_ORDERED).send("hi-once").isSuccess)
        assertTrue(await(3_000L) { clientReceived.contains("hi-once") })

        // Now both channels are idle and fully acked. With the ackPending guard, no further
        // datagram of any kind should flow, so the server's idle sweep must still fire TIMEOUT.
        assertTrue(
            await(4_000L) { srv.disconnects.any { it == "dana" to DisconnectReason.TIMEOUT } },
            "TIMEOUT never fired - an idle reliable channel is masquerading as liveness",
        )
    }

    @Test
    fun `a connection retransmitting into a black hole still trips the idle sweep`() {
        val srv = LivenessServer(idle = 500L)
        server = srv
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT).also { clients += it }
        assertTrue(client.handshake("erin").isSuccess)
        assertTrue(await(2_000L) { srv.connections.size == 1 })
        val serverConnection = srv.connections.single()

        // The client vanishes (socket closed) before ever acking - a black hole from the
        // server's point of view. The server's reliable engine will keep retransmitting into
        // the void, but that is OUTBOUND traffic and must not count as this connection being alive.
        client.stop()

        assertTrue(serverConnection.channel(DeliveryMode.RELIABLE_ORDERED).send("into-the-void").isSuccess)

        assertTrue(
            await(4_000L) { srv.disconnects.any { it == "erin" to DisconnectReason.TIMEOUT } },
            "TIMEOUT never fired - the server's own retransmit attempts must not masquerade as liveness",
        )
    }
}
