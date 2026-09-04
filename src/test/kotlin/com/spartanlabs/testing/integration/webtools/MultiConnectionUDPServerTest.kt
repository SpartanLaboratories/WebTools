package com.spartanlabs.testing.integration.webtools

import com.spartanlabs.webtools.Connection
import com.spartanlabs.webtools.MultiConnectionUDPServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// PER_CLASS lifecycle so JUnit reuses a single MultiConnectionUDPServer bound to the fixed
// common port across all methods. Ordered because stop() closes the shared socket last.
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MultiConnectionUDPServerTest {

    private val log = LoggerFactory.getLogger(MultiConnectionUDPServerTest::class.java)
    private val serverAddress: InetAddress = InetAddress.getLoopbackAddress()

    private val connectedClients = CopyOnWriteArrayList<Connection>()

    // Per-connection-name inbound message queues, populated by the shared handler bound via start().
    private val inbound = ConcurrentHashMap<String, LinkedBlockingQueue<String>>()

    private val server = object : MultiConnectionUDPServer() {
        override fun onClientConnect(connection: Connection) {
            connectedClients.add(connection)
            inbound.getOrPut(connection.name) { LinkedBlockingQueue() }
            connection.actuate { message -> inbound.getValue(connection.name).add(message) }
        }
    }

    private fun DatagramSocket.sendToServer(payload: String) {
        val out = payload.toByteArray(Charsets.UTF_8)
        send(DatagramPacket(out, out.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
    }

    private fun DatagramSocket.receiveText(): String {
        soTimeout = REPLY_TIMEOUT_MILLIS
        val p = DatagramPacket(ByteArray(RECEIVE_BUFFER_BYTES), RECEIVE_BUFFER_BYTES)
        receive(p)
        return String(p.data, 0, p.length, Charsets.UTF_8).trim()
    }

    private fun handshakeFrom(client: DatagramSocket, payload: String): String {
        client.sendToServer(payload)
        return client.receiveText()
    }

    private fun assertNoReplyTo(payload: String) {
        DatagramSocket().use { client ->
            client.sendToServer(payload)
            client.soTimeout = NO_REPLY_TIMEOUT_MILLIS
            assertFailsWith<SocketTimeoutException>("payload \"$payload\" must get no reply") {
                client.receive(DatagramPacket(ByteArray(64), 64))
            }
        }
    }

    private fun connectionNamed(name: String): Connection =
        connectedClients.first { it.name == name }

    @Test
    @Order(1)
    fun `pushToAll succeeds when there are no connections`() {
        assertTrue(server.pushToAll("no one is listening").isSuccess)
    }

    @Test
    @Order(2)
    fun `an Iam handshake registers a connection and replies with the bare token REGISTERED`() {
        val before = connectedClients.size
        DatagramSocket().use { client ->
            val reply = handshakeFrom(client, "Iam alpha")
            assertEquals("REGISTERED", reply)
            assertFalse(reply.contains("/"))
            assertFalse(reply.any { it.isDigit() })

            Thread.sleep(POST_HANDSHAKE_SETTLE_MILLIS)
            assertEquals(before + 1, connectedClients.size)
            val registered = connectionNamed("alpha")
            assertEquals(InetSocketAddress(serverAddress, client.localPort), registered.peer)
        }
    }

    @Test
    @Order(3)
    fun `the client-claimed address token in the payload is ignored`() {
        DatagramSocket().use { client ->
            assertEquals("REGISTERED", handshakeFrom(client, "Iam beta 203.0.113.7"))
            Thread.sleep(POST_HANDSHAKE_SETTLE_MILLIS)
            assertEquals(
                InetSocketAddress(serverAddress, client.localPort),
                connectionNamed("beta").peer,
            )
        }
    }

    @Test
    @Order(4)
    fun `a retransmitted Iam from the same origin repeats REGISTERED and does not re-register`() {
        val before = connectedClients.size
        DatagramSocket().use { client ->
            assertEquals("REGISTERED", handshakeFrom(client, "Iam gamma"))
            Thread.sleep(POST_HANDSHAKE_SETTLE_MILLIS)
            assertEquals(before + 1, connectedClients.size)

            assertEquals("REGISTERED", handshakeFrom(client, "Iam gamma"))
            Thread.sleep(POST_HANDSHAKE_SETTLE_MILLIS)
            assertEquals(before + 1, connectedClients.size)
        }
    }

    @Test
    @Order(5)
    fun `an actuated client's app datagram is delivered to its handler and push rides the one mapping`() {
        DatagramSocket().use { client ->
            handshakeFrom(client, "Iam delta")
            Thread.sleep(POST_HANDSHAKE_SETTLE_MILLIS)

            client.sendToServer("hello-from-delta")
            assertEquals("hello-from-delta", inbound.getValue("delta").poll(5, TimeUnit.SECONDS))

            assertTrue(connectionNamed("delta").push("hello-from-server").isSuccess)
            assertEquals("hello-from-server", client.receiveText())
        }
    }

    @Test
    @Order(6)
    fun `keepAlive puts a KA on the wire and an inbound KA is consumed without reaching the handler`() {
        DatagramSocket().use { client ->
            handshakeFrom(client, "Iam epsilon")
            Thread.sleep(POST_HANDSHAKE_SETTLE_MILLIS)

            assertTrue(connectionNamed("epsilon").keepAlive().isSuccess)
            assertEquals("KA", client.receiveText())

            client.sendToServer("KA")
            client.sendToServer("real-message")
            assertEquals("real-message", inbound.getValue("epsilon").poll(5, TimeUnit.SECONDS))
            assertNull(inbound.getValue("epsilon").poll(200, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    @Order(7)
    fun `pushToAll reaches every registered client socket`() {
        DatagramSocket().use { a ->
            DatagramSocket().use { b ->
                handshakeFrom(a, "Iam zeta1")
                handshakeFrom(b, "Iam zeta2")
                Thread.sleep(POST_HANDSHAKE_SETTLE_MILLIS)

                assertTrue(server.pushToAll("broadcast-1").isSuccess)
                assertEquals("broadcast-1", a.receiveText())
                assertEquals("broadcast-1", b.receiveText())
            }
        }
    }

    @Test
    @Order(8)
    fun `a datagram from client A is delivered only to A's handler`() {
        DatagramSocket().use { a ->
            DatagramSocket().use { b ->
                handshakeFrom(a, "Iam theta1")
                handshakeFrom(b, "Iam theta2")
                Thread.sleep(POST_HANDSHAKE_SETTLE_MILLIS)

                a.sendToServer("only-for-a")
                assertEquals("only-for-a", inbound.getValue("theta1").poll(5, TimeUnit.SECONDS))
                assertNull(inbound.getValue("theta2").poll(200, TimeUnit.MILLISECONDS))
            }
        }
    }

    @Test
    @Order(9)
    fun `data from an unregistered socket is silently dropped and the listener survives`() {
        DatagramSocket().use { stranger ->
            stranger.sendToServer("i-never-said-Iam")
        }
        Thread.sleep(POST_HANDSHAKE_SETTLE_MILLIS)
        DatagramSocket().use { recovered ->
            assertEquals("REGISTERED", handshakeFrom(recovered, "Iam afterstranger"))
        }
    }

    @Test
    @Order(10)
    fun `the server can push to a client that has sent nothing since its Iam`() {
        DatagramSocket().use { client ->
            handshakeFrom(client, "Iam quiet")
            Thread.sleep(POST_HANDSHAKE_SETTLE_MILLIS)

            assertTrue(connectionNamed("quiet").push("unsolicited").isSuccess)
            assertEquals("unsolicited", client.receiveText())
        }
    }

    @Test
    @Order(11)
    fun `malformed empty unknown-verb and oversized datagrams do not wedge the listener`() {
        listOf("", "   ", "HELLO world", "Iam").forEach(::assertNoReplyTo)
        DatagramSocket().use { client ->
            val oversized = ("Iam " + "z".repeat(4096)).toByteArray(Charsets.UTF_8)
            client.send(
                DatagramPacket(oversized, oversized.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT),
            )
        }
        Thread.sleep(POST_HANDSHAKE_SETTLE_MILLIS)
        DatagramSocket().use { recovered ->
            assertEquals("REGISTERED", handshakeFrom(recovered, "Iam afterjunk"))
        }
    }

    @Test
    @Order(20)
    fun `stop terminates connections closes the socket and shuts the executor`() {
        assertTrue(server.stop().isSuccess)
        assertNoReplyTo("Iam clientAfterStop")
    }

    @AfterAll
    fun releaseCommonPort() {
        runCatching { server.stop() }
    }

    private companion object {
        const val RECEIVE_BUFFER_BYTES = 1024
        const val REPLY_TIMEOUT_MILLIS = 5000
        const val NO_REPLY_TIMEOUT_MILLIS = 500
        const val POST_HANDSHAKE_SETTLE_MILLIS = 200L
    }
}
