package com.spartanlabs.testing.e2e.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Level 4b - the framed-transport acceptance test for Issue #14 Stage 1: a real client and a
 * real server subclass over loopback, interleaving application data (including payloads that
 * were an Issue #8 footgun for the old text classifier) with a concurrent keepalive/probe
 * stream on both sides, proving every payload arrives byte-exact, in send order, and no
 * control frame ever leaks to a bound handler.
 */
@Tag("e2e")
@Suppress("DEPRECATION") // exercises the still-working, now-deprecated push/actuate/send/start primitives on purpose
class MultiConnectionUDPFramingE2ETest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private class TestServer : MultiConnectionUDPServer() {
        val connectionsByName = ConcurrentHashMap<String, Connection>()
        val inbound = ConcurrentLinkedQueue<ByteArray>()

        override fun onClientConnect(connection: Connection) {
            connectionsByName[connection.name] = connection
            connection.actuateBytes { bytes -> inbound += bytes }
        }
    }

    private fun awaitSize(queue: ConcurrentLinkedQueue<*>, size: Int, timeoutMillis: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (queue.size < size && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue(queue.size >= size, "expected at least $size, saw ${queue.size}")
    }

    @Test
    fun `interleaved data survives a concurrent keepalive-probe stream byte-exact and in order, with no control leakage`() {
        val server = TestServer()
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(client.handshake("alice").isSuccess)
            Thread.sleep(SETTLE_MILLIS)
            val connection = server.connectionsByName.getValue("alice")

            val clientInbound = ConcurrentLinkedQueue<ByteArray>()
            assertTrue(client.startBytes { bytes -> clientInbound += bytes }.isSuccess)

            // Arm keepalive + probe on both sides at a fast, overlapping cadence with the data burst.
            assertTrue(client.startKeepAlive(50L).isSuccess)
            assertTrue(client.startProbe(250L).isSuccess)
            assertTrue(connection.startKeepAlive(50L).isSuccess)
            assertTrue(connection.startProbe(250L).isSuccess)

            // A payload that trims to "KA" (0x4B, 0x41) was the Issue #8 footgun; a blob starting
            // 0x00 and one starting 0x81 exercise the old "reserved lead byte" advice being gone.
            val payloads = listOf(
                "text".toByteArray(Charsets.UTF_8),
                byteArrayOf(0x4B, 0x41),
                ByteArray(4096) { if (it == 0) 0x00 else (it % 256).toByte() },
                ByteArray(64) { if (it == 0) 0x81.toByte() else it.toByte() },
            )

            payloads.forEach { assertTrue(client.send(it).isSuccess) }
            awaitSize(server.inbound, payloads.size)
            assertEquals(payloads.map { it.toList() }, server.inbound.toList().map { it.toList() })
            assertTrue(server.inbound.none { it.isEmpty() }, "no control frame leaked into the bytes handler")

            payloads.forEach { assertTrue(connection.push(it).isSuccess) }
            awaitSize(clientInbound, payloads.size)
            assertEquals(payloads.map { it.toList() }, clientInbound.toList().map { it.toList() })
            assertTrue(clientInbound.none { it.isEmpty() }, "no control frame leaked into the bytes handler")
        } finally {
            assertTrue(client.stop().isSuccess)
            assertTrue(server.stop().isSuccess)
        }

        Thread.sleep(SETTLE_MILLIS)
        assertTrue(
            Thread.getAllStackTraces().keys.none {
                (it.name == "mcupc-keepalive" || it.name == "mcupc-probe" || it.name == "mcupc-listener") && it.isAlive
            },
            "no residual client thread after stop()",
        )
        assertTrue(
            Thread.getAllStackTraces().keys.none {
                (it.name == "mcups-keepalive" || it.name == "mcups-probe") && it.isAlive
            },
            "no residual server thread after stop()",
        )
    }

    private companion object {
        const val SETTLE_MILLIS = 300L
    }
}
