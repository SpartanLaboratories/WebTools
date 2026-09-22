package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.DeliveryMode
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 3 - MultiConnectionUDPServer.startReliable / pushToAllReliable over real sockets: a real
// server subclass and >= 2 real MultiConnectionUDPClients, no fakes. Closes the integration-tier
// gap those two conveniences had relative to their unreliable siblings start / pushToAll, which
// already have this tier's coverage in MultiConnectionUDPServerTest ("pushToAll reaches every
// registered client socket"). The non-short-circuiting fold under one peer's full window is
// deliberately not repeated here - that's already covered at e2e
// (MultiConnectionUDPReliableBroadcastE2ETest) - this file is happy-path only.
@Tag("integration")
class MultiConnectionUDPReliableBroadcastTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private class ReliableServer : MultiConnectionUDPServer() {
        val connections = CopyOnWriteArrayList<Connection>()
        override fun onClientConnect(connection: Connection) {
            connections += connection
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
    fun `startReliable binds every registered connection and a reliable send from each real client reaches the handler`() {
        val server = ReliableServer()
        val first = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        val second = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(first.handshake("intg1").isSuccess)
            assertTrue(second.handshake("intg2").isSuccess)
            assertTrue(await(2_000L) { server.connections.size == 2 })

            val received = ConcurrentLinkedQueue<String>()
            assertTrue(server.startReliable { bytes -> received += String(bytes, Charsets.UTF_8) }.isSuccess)

            assertTrue(first.channel(DeliveryMode.RELIABLE_ORDERED).send("from-first").isSuccess)
            assertTrue(second.channel(DeliveryMode.RELIABLE_ORDERED).send("from-second").isSuccess)

            assertTrue(
                await(5_000L) { received.size >= 2 },
                "only ${received.size} of 2 reliable payload(s) reached the bound handler",
            )
            assertEquals(setOf("from-first", "from-second"), received.toSet())
        } finally {
            first.stop()
            second.stop()
            server.stop()
        }
    }

    @Test
    fun `pushToAllReliable reaches every connected real client`() {
        val server = ReliableServer()
        val first = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        val second = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(first.handshake("bcast1").isSuccess)
            assertTrue(second.handshake("bcast2").isSuccess)
            assertTrue(await(2_000L) { server.connections.size == 2 })

            val firstReceived = ConcurrentLinkedQueue<String>()
            val secondReceived = ConcurrentLinkedQueue<String>()
            assertTrue(
                first.channel(DeliveryMode.RELIABLE_ORDERED).actuateBytes { firstReceived += String(it, Charsets.UTF_8) }.isSuccess,
            )
            assertTrue(
                second.channel(DeliveryMode.RELIABLE_ORDERED).actuateBytes { secondReceived += String(it, Charsets.UTF_8) }.isSuccess,
            )

            val payload = "broadcast-payload".toByteArray(Charsets.UTF_8)
            assertTrue(server.pushToAllReliable(payload).isSuccess)

            assertTrue(await(5_000L) { firstReceived.size == 1 && secondReceived.size == 1 })
            assertEquals("broadcast-payload", firstReceived.single())
            assertEquals("broadcast-payload", secondReceived.single())
        } finally {
            first.stop()
            second.stop()
            server.stop()
        }
    }
}
