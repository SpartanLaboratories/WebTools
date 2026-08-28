package com.spartanlabs.webtools

import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
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
import kotlin.test.assertTrue

// PER_CLASS lifecycle so JUnit reuses a single test instance (and therefore a single
// MultiConnectionUDPServer bound to the fixed 9998/9999 ports) across all test methods.
// Tests are explicitly ordered because `stop()` closes the shared server's socket and
// must run last, after the tests that rely on it still being open.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MultiConnectionUDPServerTest {

    private val log = LoggerFactory.getLogger(MultiConnectionUDPServerTest::class.java)

    // Connections handed to onClientConnect(), in the order they were registered.
    // Backed by a CopyOnWriteArrayList since it's written from the server's listener
    // thread and read from the test thread.
    private val connectedClients = CopyOnWriteArrayList<UDPConnection>()

    // MultiConnectionUDPServer is abstract, so tests supply their own onClientConnect
    // implementation - here it just records the connection for later assertions.
    // The server binds fixed ports (9998/9999) in its constructor, so we deliberately
    // create only a single instance for the whole test class to avoid port conflicts
    // between test methods.
    private val server = object : MultiConnectionUDPServer() {
        override fun onClientConnect(connection: UDPConnection) {
            log.debug("Test recorded onClientConnect for '{}'", connection.name)
            connectedClients.add(connection)
        }
    }

    @Test
    @Order(1)
    fun `pushToAll succeeds when there are no connections`() {
        log.info("Verifying pushToAll is a no-op with zero connections")
        assertTrue(server.pushToAll("no one is listening").isSuccess)
    }

    @Test
    @Order(2)
    fun `an Iam handshake registers a new connection and gets a TXRXON reply`() {
        log.info("Starting Iam handshake test")
        // resolveLocalAddress() now reports failure rather than silently substituting
        // loopback, so the test recovers explicitly instead of masking a routing problem.
        val localAddress = resolveLocalAddress().getOrDefault(InetAddress.getLoopbackAddress())

        // Listen on the server's common send port (9999) to catch the handshake reply.
        val clientListenSocket = DatagramSocket(MultiConnectionUDPServer.COMMON_SEND_PORT)
        val clientSendSocket = DatagramSocket()

        try {
            val handshake = "Iam testclient $localAddress"
            val outBytes = handshake.toByteArray(Charsets.UTF_8)
            clientSendSocket.send(DatagramPacket(outBytes, outBytes.size, localAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))

            val inBuffer = ByteArray(1024)
            val inPacket = DatagramPacket(inBuffer, inBuffer.size)
            clientListenSocket.soTimeout = 5000
            clientListenSocket.receive(inPacket)

            val reply = String(inPacket.data, 0, inPacket.length, Charsets.UTF_8).trim()
            log.debug("Received handshake reply: {}", reply)

            assertTrue(reply.contains("TXRXON"), "Expected a TXRXON reply, got: $reply")

            // onClientConnect() is invoked from the listener thread right after the reply
            // is sent, so give it a brief moment to run before asserting on it.
            Thread.sleep(200)
            assertEquals(1, connectedClients.size, "Expected onClientConnect to fire exactly once")
            assertEquals("testclient", connectedClients[0].name)
            assertEquals(localAddress, connectedClients[0].address)
        } finally {
            clientListenSocket.close()
            clientSendSocket.close()
        }
    }

    @Test
    @Order(3)
    fun `stop terminates connections and closes the common listen socket`() {
        log.info("Verifying stop() shuts the server down cleanly")
        assertTrue(server.stop().isSuccess, "stop() should report success")

        // After stopping, the server's common listen socket is closed, so a fresh
        // handshake attempt should get no TXRXON reply at all.
        val loopback = InetAddress.getLoopbackAddress()
        val clientSendSocket = DatagramSocket()
        val clientListenSocket = DatagramSocket(MultiConnectionUDPServer.COMMON_SEND_PORT)

        try {
            val handshake = "Iam clientAfterStop $loopback"
            val outBytes = handshake.toByteArray(Charsets.UTF_8)
            clientSendSocket.send(DatagramPacket(outBytes, outBytes.size, loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT))

            clientListenSocket.soTimeout = 500
            assertFailsWith<SocketTimeoutException>("Expected no reply once the server has stopped") {
                clientListenSocket.receive(DatagramPacket(ByteArray(1024), 1024))
            }
            log.debug("Confirmed no reply was received after stop()")
        } finally {
            clientSendSocket.close()
            clientListenSocket.close()
        }
    }
}