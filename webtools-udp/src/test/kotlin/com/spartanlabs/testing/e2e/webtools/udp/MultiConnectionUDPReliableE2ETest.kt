package com.spartanlabs.testing.e2e.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.DeliveryMode
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 4b - the headline reliable-channel scenario: a real [MultiConnectionUDPServer] paired
 * with a real [MultiConnectionUDPClient] over loopback, interleaving a reliable-ordered event
 * stream with a concurrent unreliable snapshot stream, proving both planes are independent and
 * the reliable one is exactly-once-in-order end to end (Issue #14 Stage 3).
 */
@Tag("e2e")
@Suppress("DEPRECATION") // exercises the still-working, now-deprecated push/actuate/send/start primitives on purpose
class MultiConnectionUDPReliableE2ETest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private class ReliableServer : MultiConnectionUDPServer() {
        val connections = CopyOnWriteArrayList<Connection>()
        val connectionsByName = ConcurrentHashMap<String, Connection>()
        override fun onClientConnect(connection: Connection) {
            connections += connection
            connectionsByName[connection.name] = connection
        }
    }

    private fun await(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    @Test
    fun `200 reliable events interleaved with unreliable snapshots - reliable arrives exactly once in order`() {
        val server = ReliableServer()
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(client.handshake("player1").isSuccess)
            assertTrue(await(2_000L) { server.connections.size == 1 })
            val serverConnection = server.connectionsByName.getValue("player1")

            val reliableReceivedOnServer = ConcurrentLinkedQueue<String>()
            val unreliableReceivedOnServer = ConcurrentLinkedQueue<String>()
            assertTrue(serverConnection.channel(DeliveryMode.RELIABLE_ORDERED).actuate(reliableReceivedOnServer::add).isSuccess)
            assertTrue(serverConnection.channel(DeliveryMode.UNRELIABLE).actuate(unreliableReceivedOnServer::add).isSuccess)

            val reliable = client.channel(DeliveryMode.RELIABLE_ORDERED)
            val unreliable = client.channel(DeliveryMode.UNRELIABLE)

            val expectedReliable = (0 until EVENT_COUNT).map { "event-$it" }
            // A payload that would have collided with the pre-2.0 text classifier - must still
            // arrive intact on the reliable plane too.
            val trapPayloads = listOf("KA", "PING 1")
            val allReliable = expectedReliable + trapPayloads

            allReliable.forEachIndexed { i, event ->
                assertTrue(reliable.send(event).isSuccess, "reliable send #$i failed")
                if (i % 3 == 0) unreliable.send("snapshot-$i")
            }
            // A steady tail of unreliable snapshots after the reliable burst too.
            repeat(20) { unreliable.send("tail-snapshot-$it") }

            assertTrue(
                await(10_000L) { reliableReceivedOnServer.size >= allReliable.size },
                "only ${reliableReceivedOnServer.size} of ${allReliable.size} reliable events arrived",
            )

            assertEquals(allReliable, reliableReceivedOnServer.toList(), "reliable delivery must be exactly-once, in order")
            allReliable.forEach { event ->
                assertTrue(event !in unreliableReceivedOnServer, "a reliable payload must never reorder into the unreliable stream")
            }
        } finally {
            client.stop()
            server.stop()
        }
    }

    @Test
    fun `terminate mid-stream discards un-acked reliable data and a subsequent send fails`() {
        val server = ReliableServer()
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(client.handshake("player2").isSuccess)
            assertTrue(await(2_000L) { server.connections.size == 1 })
            val serverConnection = server.connectionsByName.getValue("player2")

            assertTrue(client.channel(DeliveryMode.RELIABLE_ORDERED).send("before-terminate").isSuccess)

            assertTrue(serverConnection.terminate().isSuccess)

            val result = serverConnection.channel(DeliveryMode.RELIABLE_ORDERED).send("after-terminate")
            assertTrue(result.isFailure, "a send on a terminated connection's reliable channel must fail")
        } finally {
            client.stop()
            server.stop()
        }
    }

    @Test
    fun `a same-name reconnect gets a fresh reliable channel`() {
        val server = ReliableServer()
        val firstClient = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        var secondClient: MultiConnectionUDPClient? = null
        try {
            assertTrue(firstClient.handshake("reconnector").isSuccess)
            assertTrue(await(2_000L) { server.connections.size == 1 })
            assertTrue(firstClient.channel(DeliveryMode.RELIABLE_ORDERED).send("seq-0-on-first").isSuccess)

            val fresh = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
            secondClient = fresh
            assertTrue(fresh.handshake("reconnector").isSuccess) // same name - supersedes the first
            assertTrue(await(2_000L) { server.connections.size == 2 }, "the supersede did not register a second connection")

            val received = ConcurrentLinkedQueue<String>()
            val newServerConnection = server.connections.last()
            assertTrue(newServerConnection.channel(DeliveryMode.RELIABLE_ORDERED).actuate(received::add).isSuccess)

            // A fresh engine expects seq 0 again - this must deliver immediately, not buffer as
            // out-of-order against the superseded connection's advanced sequence state.
            assertTrue(fresh.channel(DeliveryMode.RELIABLE_ORDERED).send("seq-0-on-second").isSuccess)
            assertTrue(await(3_000L) { received.contains("seq-0-on-second") })
        } finally {
            firstClient.stop()
            secondClient?.stop()
            server.stop()
        }
    }

    @Test
    fun `stop on both sides leaves no live mcup retransmit thread`() {
        val server = ReliableServer()
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        assertTrue(client.handshake("player3").isSuccess)
        assertTrue(await(2_000L) { server.connections.size == 1 })
        val serverConnection = server.connections.single()

        assertTrue(client.channel(DeliveryMode.RELIABLE_ORDERED).send("open-the-channel").isSuccess)
        assertTrue(serverConnection.channel(DeliveryMode.RELIABLE_ORDERED).send("open-the-server-channel").isSuccess)
        Thread.sleep(200)

        client.stop()
        server.stop()
        Thread.sleep(300)

        assertNull(Thread.getAllStackTraces().keys.firstOrNull { it.name == "mcupc-retransmit" && it.isAlive })
        assertNull(Thread.getAllStackTraces().keys.firstOrNull { it.name == "mcups-retransmit" && it.isAlive })
    }

    private companion object {
        const val EVENT_COUNT = 200
    }
}
