package com.spartanlabs.testing.integration.webtools

import com.spartanlabs.webtools.MultiConnectionUDPServer
import com.spartanlabs.webtools.UDPConnection
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// PER_CLASS lifecycle so JUnit reuses a single test instance (and therefore a single
// MultiConnectionUDPServer bound to the fixed common port) across all test methods.
// Tests are explicitly ordered because `stop()` closes the shared server's socket and
// must run last, after the tests that rely on it still being open.
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MultiConnectionUDPServerTest {

    private val log = LoggerFactory.getLogger(MultiConnectionUDPServerTest::class.java)

    // The handshake reply is now addressed to the datagram's source, so a client only needs
    // one socket - the one it sends the `Iam` from also receives the `TXRXON`. Sending over
    // loopback means the server observes this exact address as the packet source.
    private val serverAddress: InetAddress = InetAddress.getLoopbackAddress()

    // Connections handed to onClientConnect(), in the order they were registered.
    // Backed by a CopyOnWriteArrayList since it's written from the server's listener
    // thread and read from the test thread.
    private val connectedClients = CopyOnWriteArrayList<UDPConnection>()

    // MultiConnectionUDPServer is abstract, so tests supply their own onClientConnect
    // implementation - here it just records the connection for later assertions.
    // The server binds a fixed common port in its constructor, so we deliberately create
    // only a single instance for the whole test class to avoid port conflicts between
    // test methods.
    private val server = object : MultiConnectionUDPServer() {
        override fun onClientConnect(connection: UDPConnection) {
            log.debug("Test recorded onClientConnect for '{}'", connection.name)
            connectedClients.add(connection)
        }
    }

    /**
     * Sends [payload] to the server's common port from [client] and returns the reply text.
     * [client] is left open and its receive timeout set, so callers can keep reading from it
     * (e.g. to catch a later [MultiConnectionUDPServer.pushToAll] broadcast).
     */
    private fun handshakeFrom(client: DatagramSocket, payload: String): String {
        val out = payload.toByteArray(Charsets.UTF_8)
        client.send(DatagramPacket(out, out.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
        client.soTimeout = REPLY_TIMEOUT_MILLIS
        val reply = DatagramPacket(ByteArray(RECEIVE_BUFFER_BYTES), RECEIVE_BUFFER_BYTES)
        client.receive(reply)
        return String(reply.data, 0, reply.length, Charsets.UTF_8).trim()
    }

    @Test
    @Order(1)
    fun `pushToAll succeeds when there are no connections`() {
        log.info("Verifying pushToAll is a no-op with zero connections")
        assertTrue(server.pushToAll("no one is listening").isSuccess)
    }

    @Test
    @Order(2)
    fun `an Iam handshake registers a connection and replies with a bare TXRXON to the datagram source`() {
        log.info("Starting Iam handshake test")
        val before = connectedClients.size

        DatagramSocket().use { client ->
            val reply = handshakeFrom(client, "Iam testclient")
            log.debug("Received handshake reply: {}", reply)

            assertTrue(reply.startsWith("$HANDSHAKE_REPLY_VERB "), "Expected a bare TXRXON reply, got: $reply")
            assertEquals(3, reply.split(' ').size, "Reply must be '$HANDSHAKE_REPLY_VERB <sendPort> <receivePort>'")
            assertFalse(reply.contains("/"), "Reply must not carry an address prefix any more: $reply")

            // onClientConnect() is invoked from the listener thread right after the reply
            // is sent, so give it a brief moment to run before asserting on it.
            Thread.sleep(POST_HANDSHAKE_SETTLE_MILLIS)
            assertEquals(before + 1, connectedClients.size, "Expected exactly one new connection")
            val registered = connectedClients.last()
            assertEquals("testclient", registered.name)
            // The address is learned from the datagram, so a loopback handshake yields loopback.
            assertEquals(serverAddress, registered.address)
        }
    }

    @Test
    @Order(3)
    fun `the client-claimed address token in the payload is ignored`() {
        log.info("Verifying the server trusts the datagram source, not the payload")
        val before = connectedClients.size

        DatagramSocket().use { client ->
            // 203.0.113.0/24 is TEST-NET-3: a syntactically valid address that is never us.
            val reply = handshakeFrom(client, "Iam liarclient 203.0.113.7")
            assertTrue(reply.startsWith("$HANDSHAKE_REPLY_VERB "), "Expected a TXRXON reply, got: $reply")

            Thread.sleep(POST_HANDSHAKE_SETTLE_MILLIS)
            assertEquals(before + 1, connectedClients.size)
            val registered = connectedClients.last()
            assertEquals("liarclient", registered.name)
            assertEquals(serverAddress, registered.address, "Address must come from the datagram, not the payload")
            assertNotEquals(InetAddress.getByName("203.0.113.7"), registered.address)
        }
    }

    @Test
    @Order(4)
    fun `pushToAll delivers to each client's learned handshake origin`() {
        log.info("Verifying pushToAll reaches the socket the handshake came from")

        DatagramSocket().use { client ->
            handshakeFrom(client, "Iam pushclient")

            assertTrue(server.pushToAll("broadcast-1").isSuccess)

            val received = DatagramPacket(ByteArray(RECEIVE_BUFFER_BYTES), RECEIVE_BUFFER_BYTES)
            client.receive(received) // receive timeout already set by handshakeFrom
            assertEquals("broadcast-1", String(received.data, 0, received.length, Charsets.UTF_8).trim())
        }
    }

    @Test
    @Order(5)
    fun `a malformed Iam with no name is rejected without wedging the listener`() {
        log.info("Verifying a nameless Iam neither registers nor kills the server")
        val before = connectedClients.size

        DatagramSocket().use { client ->
            val out = "Iam".toByteArray(Charsets.UTF_8)
            client.send(DatagramPacket(out, out.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
            client.soTimeout = NO_REPLY_TIMEOUT_MILLIS
            assertFailsWith<SocketTimeoutException>("A malformed handshake must not get a reply") {
                client.receive(DatagramPacket(ByteArray(64), 64))
            }
        }
        Thread.sleep(POST_HANDSHAKE_SETTLE_MILLIS)
        assertEquals(before, connectedClients.size, "A malformed handshake must not register a connection")

        // The listener survived: a well-formed handshake right after still gets a reply.
        DatagramSocket().use { recovered ->
            assertTrue(handshakeFrom(recovered, "Iam recoverclient").startsWith("$HANDSHAKE_REPLY_VERB "))
        }
    }

    @Test
    @Order(6)
    fun `stop terminates connections and closes the common socket`() {
        log.info("Verifying stop() shuts the server down cleanly")
        assertTrue(server.stop().isSuccess, "stop() should report success")

        // After stopping, the common socket is closed, so a fresh handshake gets no reply.
        DatagramSocket().use { client ->
            val out = "Iam clientAfterStop".toByteArray(Charsets.UTF_8)
            client.send(DatagramPacket(out, out.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
            client.soTimeout = NO_REPLY_TIMEOUT_MILLIS
            assertFailsWith<SocketTimeoutException>("Expected no reply once the server has stopped") {
                client.receive(DatagramPacket(ByteArray(1024), 1024))
            }
            log.debug("Confirmed no reply was received after stop()")
        }
    }

    private companion object {
        const val HANDSHAKE_REPLY_VERB = "TXRXON"
        const val RECEIVE_BUFFER_BYTES = 1024

        /** How long to wait for a handshake reply that should arrive. */
        const val REPLY_TIMEOUT_MILLIS = 5000

        /** How long to wait before concluding no reply is coming. */
        const val NO_REPLY_TIMEOUT_MILLIS = 500

        /** Grace period for the listener thread's post-reply `onClientConnect` call to run. */
        const val POST_HANDSHAKE_SETTLE_MILLIS = 200L
    }
}
