package com.spartanlabs.testing.e2e.webtools

import com.spartanlabs.webtools.Connection
import com.spartanlabs.webtools.MultiConnectionUDPClient
import com.spartanlabs.webtools.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Level 4b - the actual acceptance test for issue #3: a real
 * [MultiConnectionUDPServer] subclass paired with real [MultiConnectionUDPClient]
 * instances over loopback, proving the public client and public server interoperate
 * end to end with neither side hand-rolling socket code.
 */
@Tag("e2e")
class MultiConnectionUDPClientServerE2ETest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private class TestServer : MultiConnectionUDPServer() {
        val connections = CopyOnWriteArrayList<Connection>()
        val connectionsByName = ConcurrentHashMap<String, Connection>()
        val handlerInbound = ConcurrentHashMap<String, ConcurrentLinkedQueue<String>>()

        override fun onClientConnect(connection: Connection) {
            connections += connection
            connectionsByName[connection.name] = connection
            handlerInbound.getOrPut(connection.name) { ConcurrentLinkedQueue() }
            connection.actuate { message -> handlerInbound.getValue(connection.name).add(message) }
        }
    }

    @Test
    fun `client and server interoperate end to end with no hand-rolled socket code`() {
        val server = TestServer()
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(client.handshake("alice").isSuccess)
            Thread.sleep(SETTLE_MILLIS)
            assertEquals(1, server.connections.size)

            val clientInbound = ConcurrentLinkedQueue<String>()
            assertTrue(client.start { message -> clientInbound += message }.isSuccess)

            // client -> server, delivered to the server's handler.
            assertTrue(client.send("hello-from-client").isSuccess)
            awaitQueueContains(server.handlerInbound.getValue("alice"), "hello-from-client")

            // server -> client, via Connection.push (per SS2.3, the single-client send path).
            assertTrue(server.connectionsByName.getValue("alice").push("hello-from-server").isSuccess)
            awaitQueueContains(clientInbound, "hello-from-server")

            // server.pushToAll reaches the connected client.
            assertTrue(server.pushToAll("broadcast").isSuccess)
            awaitQueueContains(clientInbound, "broadcast")

            // client-side keepalive is consumed by the server without reaching its handler.
            assertTrue(client.sendKeepAlive().isSuccess)
            Thread.sleep(SETTLE_MILLIS)
            assertTrue(server.handlerInbound.getValue("alice").none { it == "KA" })

            // server-side keepalive is consumed by the client without reaching onMessage.
            assertTrue(server.connectionsByName.getValue("alice").keepAlive().isSuccess)
            Thread.sleep(SETTLE_MILLIS)
            assertTrue(clientInbound.none { it == "KA" })

            // A burst of ordered messages each way arrives in order.
            val fromServerReceived = ConcurrentLinkedQueue<Int>()
            val fromServerDone = CountDownLatch(BURST)
            val fromClientReceived = ConcurrentLinkedQueue<Int>()
            val fromClientDone = CountDownLatch(BURST)

            val burstClient = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
            try {
                assertTrue(burstClient.handshake("burst").isSuccess)
                Thread.sleep(SETTLE_MILLIS)
                assertTrue(
                    burstClient.start { message ->
                        fromServerReceived += message.toInt()
                        fromServerDone.countDown()
                    }.isSuccess,
                )

                val serverConnection = server.connectionsByName.getValue("burst")
                serverConnection.actuate { message ->
                    fromClientReceived += message.toInt()
                    fromClientDone.countDown()
                }

                repeat(BURST) { i -> burstClient.send(i.toString()) }
                assertTrue(fromClientDone.await(5, TimeUnit.SECONDS), "all $BURST client->server messages delivered")
                assertEquals((0 until BURST).toList(), fromClientReceived.toList())

                repeat(BURST) { i -> serverConnection.push(i.toString()) }
                assertTrue(fromServerDone.await(5, TimeUnit.SECONDS), "all $BURST server->client messages delivered")
                assertEquals((0 until BURST).toList(), fromServerReceived.toList())
            } finally {
                burstClient.stop()
            }
        } finally {
            assertTrue(client.stop().isSuccess)
            assertTrue(server.stop().isSuccess)
        }

        // A fresh client/server pair can bind again afterward.
        val freshServer = TestServer()
        val freshClient = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(freshClient.handshake("fresh").isSuccess)
        } finally {
            freshClient.stop()
            freshServer.stop()
        }
    }

    private fun <T> awaitQueueContains(queue: ConcurrentLinkedQueue<T>, value: T, timeoutMillis: Long = 5000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!queue.contains(value) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertTrue(queue.contains(value), "expected $value within ${timeoutMillis}ms, saw $queue")
    }

    private companion object {
        const val SETTLE_MILLIS = 200L
        const val BURST = 50
    }
}
