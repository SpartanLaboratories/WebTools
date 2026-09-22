package com.spartanlabs.testing.e2e.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.DeliveryMode
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import com.spartanlabs.webtools.udp.ReliableWindowFullException
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Level 4b - the OD-1 server-wide reliable conveniences, [MultiConnectionUDPServer.startReliable]
 * and [MultiConnectionUDPServer.pushToAllReliable], over real sockets with a real server subclass
 * and real clients (Issue #14 Stage 3, gap closed 2026-09-20 per §12 D1 of the plan). A genuinely
 * different harness from [MultiConnectionUDPReliableE2ETest]: a multi-client roster, one of them
 * deliberately silent, to reach the non-short-circuiting fold neither per-connection test can.
 */
@Tag("e2e")
class MultiConnectionUDPReliableBroadcastE2ETest {

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
    fun `startReliable binds every currently-registered connection, and only those`() {
        val server = ReliableServer()
        val firstClient = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        val secondClient = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        var thirdClient: MultiConnectionUDPClient? = null
        try {
            assertTrue(firstClient.handshake("broadcast1").isSuccess)
            assertTrue(secondClient.handshake("broadcast2").isSuccess)
            assertTrue(await(2_000L) { server.connections.size == 2 })

            val received = ConcurrentLinkedQueue<String>()
            assertTrue(server.startReliable { bytes -> received += String(bytes, Charsets.UTF_8) }.isSuccess)

            assertTrue(firstClient.channel(DeliveryMode.RELIABLE_ORDERED).send("from-first").isSuccess)
            assertTrue(secondClient.channel(DeliveryMode.RELIABLE_ORDERED).send("from-second").isSuccess)

            assertTrue(
                await(5_000L) { received.size >= 2 },
                "only ${received.size} of 2 reliable payload(s) reached the bound handler",
            )
            assertEquals(setOf("from-first", "from-second"), received.toSet())

            // A third client that handshakes AFTER the one startReliable() call must not be bound -
            // "currently-registered" is a snapshot at call time, matching start/startBytes.
            val fresh = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
            thirdClient = fresh
            assertTrue(fresh.handshake("broadcast3").isSuccess)
            assertTrue(await(2_000L) { server.connections.size == 3 })

            assertTrue(fresh.channel(DeliveryMode.RELIABLE_ORDERED).send("from-third").isSuccess)
            Thread.sleep(500) // give an errant delivery a chance to arrive before asserting absence
            assertFalse(received.contains("from-third"), "a late-handshaking client must not be bound by an earlier startReliable")
            assertEquals(2, received.size)
        } finally {
            firstClient.stop()
            secondClient.stop()
            thirdClient?.stop()
            server.stop()
        }
    }

    @Test
    fun `pushToAllReliable reaches every connected client reliably and in order`() {
        val server = ReliableServer()
        val firstClient = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        val secondClient = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(firstClient.handshake("bcast1").isSuccess)
            assertTrue(secondClient.handshake("bcast2").isSuccess)
            assertTrue(await(2_000L) { server.connections.size == 2 })

            val firstReliable = ConcurrentLinkedQueue<String>()
            val secondReliable = ConcurrentLinkedQueue<String>()
            val firstUnreliable = ConcurrentLinkedQueue<String>()
            val secondUnreliable = ConcurrentLinkedQueue<String>()
            assertTrue(firstClient.channel(DeliveryMode.RELIABLE_ORDERED).actuateBytes { firstReliable += String(it, Charsets.UTF_8) }.isSuccess)
            assertTrue(secondClient.channel(DeliveryMode.RELIABLE_ORDERED).actuateBytes { secondReliable += String(it, Charsets.UTF_8) }.isSuccess)
            assertTrue(firstClient.channel(DeliveryMode.UNRELIABLE).actuateBytes { firstUnreliable += String(it, Charsets.UTF_8) }.isSuccess)
            assertTrue(secondClient.channel(DeliveryMode.UNRELIABLE).actuateBytes { secondUnreliable += String(it, Charsets.UTF_8) }.isSuccess)

            val expected = (0 until BROADCAST_COUNT).map { "broadcast-$it" }
            expected.forEach { payload ->
                assertTrue(server.pushToAllReliable(payload.toByteArray(Charsets.UTF_8)).isSuccess)
            }

            assertTrue(await(5_000L) { firstReliable.size >= expected.size && secondReliable.size >= expected.size })

            assertEquals(expected, firstReliable.toList())
            assertEquals(expected, secondReliable.toList())
            expected.forEach { payload ->
                assertFalse(firstUnreliable.contains(payload), "a reliable broadcast must never land on the unreliable handler")
                assertFalse(secondUnreliable.contains(payload), "a reliable broadcast must never land on the unreliable handler")
            }
        } finally {
            firstClient.stop()
            secondClient.stop()
            server.stop()
        }
    }

    @Test
    fun `one peer's full reliable window does not stop pushToAllReliable reaching the others`() {
        val server = ReliableServer()
        // (a) The peer to be stalled handshakes FIRST - Registrations.snapshot() is oldest-first,
        // so this is the registration broadcastReliable's fold visits first.
        val stalled = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        val liveFirst = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        val liveSecond = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(stalled.handshake("stalled").isSuccess)
            assertTrue(await(2_000L) { server.connections.size == 1 })

            // (b) The two live peers handshake second and bind their reliable handlers.
            assertTrue(liveFirst.handshake("live1").isSuccess)
            assertTrue(liveSecond.handshake("live2").isSuccess)
            assertTrue(await(2_000L) { server.connections.size == 3 })

            val firstReceived = ConcurrentLinkedQueue<String>()
            val secondReceived = ConcurrentLinkedQueue<String>()
            assertTrue(liveFirst.channel(DeliveryMode.RELIABLE_ORDERED).actuateBytes { firstReceived += String(it, Charsets.UTF_8) }.isSuccess)
            assertTrue(liveSecond.channel(DeliveryMode.RELIABLE_ORDERED).actuateBytes { secondReceived += String(it, Charsets.UTF_8) }.isSuccess)

            // (c) Stop the stalled client without terminating it server-side: MultiConnectionUDPClient.stop()
            // sends the server nothing, so its registration survives, silently, and never acks again.
            stalled.stop()

            // (d) Fill only the stalled peer's window - windowSize (ReliableChannelEngine.windowSize)
            // is 256 - directly on its own server-side connection, never via the broadcast.
            val stalledConnection = server.connectionsByName.getValue("stalled")
            repeat(WINDOW_SIZE) { i ->
                assertTrue(
                    stalledConnection.channel(DeliveryMode.RELIABLE_ORDERED).send(byteArrayOf(i.toByte())).isSuccess,
                    "fill send #$i into the stalled peer's window must be accepted",
                )
            }

            // (e) One pushToAllReliable call: it must report the stalled peer's window-full failure
            // AND still have delivered to both live peers - the non-short-circuiting fold.
            val payload = "broadcast-despite-stall".toByteArray(Charsets.UTF_8)
            val result = server.pushToAllReliable(payload)

            assertTrue(result.isFailure, "the fold must surface the stalled peer's failure")
            val failure = assertIs<ReliableWindowFullException>(result.exceptionOrNull())
            assertEquals(WINDOW_SIZE, failure.inFlight, "must report the stalled peer's real window size")

            assertTrue(await(5_000L) { firstReceived.size == 1 && secondReceived.size == 1 })
            assertEquals(listOf("broadcast-despite-stall"), firstReceived.toList())
            assertEquals(listOf("broadcast-despite-stall"), secondReceived.toList())
        } finally {
            stalled.stop()
            liveFirst.stop()
            liveSecond.stop()
            server.stop()
        }
    }

    private companion object {
        const val BROADCAST_COUNT = 20
        const val WINDOW_SIZE = 256
    }
}
