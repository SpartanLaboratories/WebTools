package com.spartanlabs.testing.e2e.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Level 4b - end-to-end acceptance for issue #8: the raw binary datagram path over
 * a real [MultiConnectionUDPServer] subclass paired with real
 * [MultiConnectionUDPClient] instances over loopback. Kept separate from
 * [MultiConnectionUDPClientServerE2ETest] so each stays readable.
 */
@Tag("e2e")
class MultiConnectionUDPBinaryE2ETest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private class TestServer : MultiConnectionUDPServer() {
        val connectionsByName = ConcurrentHashMap<String, Connection>()
        val bytesInbound = ConcurrentHashMap<String, ConcurrentLinkedQueue<ByteArray>>()
        val textInbound = ConcurrentHashMap<String, ConcurrentLinkedQueue<String>>()
        val connectAsBytes = CopyOnWriteArrayList<String>()

        override fun onClientConnect(connection: Connection) {
            connectionsByName[connection.name] = connection
            bytesInbound.getOrPut(connection.name) { ConcurrentLinkedQueue() }
            textInbound.getOrPut(connection.name) { ConcurrentLinkedQueue() }
            // Fixture convention: an empty connectAsBytes means "every client is a bytes client"
            // (the common case); otherwise only the named clients get a bytes handler.
            if (connection.name in connectAsBytes || connectAsBytes.isEmpty()) {
                connection.actuateBytes { bytesInbound.getValue(connection.name).add(it) }
            } else {
                connection.actuate { textInbound.getValue(connection.name).add(it) }
            }
        }
    }

    private val allBytes = ByteArray(256) { it.toByte() }

    @Test
    fun `client to server binary payload arrives byte-identical at an actuateBytes handler`() {
        val server = TestServer()
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(client.handshake("alice").isSuccess)
            Thread.sleep(SETTLE_MILLIS)

            assertTrue(client.send(allBytes).isSuccess)
            awaitBytes(server.bytesInbound.getValue("alice"), allBytes)
        } finally {
            client.stop()
            server.stop()
        }
    }

    @Test
    fun `server to client via push and pushToAll arrives byte-identical at startBytes handler`() {
        val server = TestServer()
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(client.handshake("bob").isSuccess)
            Thread.sleep(SETTLE_MILLIS)
            val received = ConcurrentLinkedQueue<ByteArray>()
            assertTrue(client.startBytes { received += it }.isSuccess)

            val push = byteArrayOf(0x00, 0x11, 0x22, 0x0A)
            assertTrue(server.connectionsByName.getValue("bob").push(push).isSuccess)
            awaitBytes(received, push)

            val broadcast = byteArrayOf(-0x80, 0x01, 0x20)
            assertTrue(server.pushToAll(broadcast).isSuccess)
            awaitBytes(received, broadcast)
        } finally {
            client.stop()
            server.stop()
        }
    }

    @Test
    fun `a payload of exactly KA is swallowed end to end`() {
        val server = TestServer()
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(client.handshake("carol").isSuccess)
            Thread.sleep(SETTLE_MILLIS)

            assertTrue(client.send(byteArrayOf(0x4B, 0x41)).isSuccess)
            assertTrue(client.send("after".toByteArray()).isSuccess)
            awaitBytes(server.bytesInbound.getValue("carol"), "after".toByteArray())
            assertTrue(server.bytesInbound.getValue("carol").none { it.contentEquals(byteArrayOf(0x4B, 0x41)) })
        } finally {
            client.stop()
            server.stop()
        }
    }

    @Test
    fun `a burst of N ordered binary frames arrives in order`() {
        val server = TestServer()
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(client.handshake("dave").isSuccess)
            Thread.sleep(SETTLE_MILLIS)
            val seen = ConcurrentLinkedQueue<Int>()
            val done = CountDownLatch(BURST)
            // lead byte 0x00 keeps every frame out of the classifier
            server.connectionsByName.getValue("dave").actuateBytes { seen += it[1].toInt(); done.countDown() }
            Thread.sleep(SETTLE_MILLIS)

            repeat(BURST) { i -> assertTrue(client.send(byteArrayOf(0x00, i.toByte())).isSuccess) }
            assertTrue(done.await(5, TimeUnit.SECONDS), "all $BURST frames delivered")
            assertEquals((0 until BURST).toList(), seen.toList())
        } finally {
            client.stop()
            server.stop()
        }
    }

    @Test
    fun `mixed - one client on text and another on bytes each get their own view of pushToAll`() {
        val server = TestServer()
        server.connectAsBytes += "binclient"
        val textClient = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        val byteClient = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(textClient.handshake("txtclient").isSuccess)
            assertTrue(byteClient.handshake("binclient").isSuccess)
            Thread.sleep(SETTLE_MILLIS)

            val textSeen = ConcurrentLinkedQueue<String>()
            val bytesSeen = ConcurrentLinkedQueue<ByteArray>()
            assertTrue(textClient.start { textSeen += it }.isSuccess)
            assertTrue(byteClient.startBytes { bytesSeen += it }.isSuccess)

            val payload = "hello".toByteArray()
            assertTrue(server.pushToAll(payload).isSuccess)

            awaitBytes(bytesSeen, payload)
            val deadline = System.currentTimeMillis() + 5000
            while (!textSeen.contains("hello") && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertTrue(textSeen.contains("hello"))
        } finally {
            textClient.stop()
            byteClient.stop()
            server.stop()
        }
    }

    @Test
    fun `clean teardown - a fresh client-server pair rebinds afterward`() {
        val server = TestServer()
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        assertTrue(client.handshake("first").isSuccess)
        client.stop()
        server.stop()

        val freshServer = TestServer()
        val freshClient = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(freshClient.handshake("second").isSuccess)
        } finally {
            freshClient.stop()
            freshServer.stop()
        }
    }

    private fun awaitBytes(queue: ConcurrentLinkedQueue<ByteArray>, value: ByteArray, timeoutMillis: Long = 5000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (queue.none { it.contentEquals(value) } && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertContentEquals(value, queue.firstOrNull { it.contentEquals(value) } ?: ByteArray(0))
    }

    private companion object {
        const val SETTLE_MILLIS = 200L
        const val BURST = 30
    }
}
