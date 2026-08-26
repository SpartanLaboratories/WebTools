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
import kotlin.test.Test
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

    // The server binds fixed ports (9998/9999) in its constructor, so we deliberately
    // create only a single instance for the whole test class to avoid port conflicts
    // between test methods.
    private val server = MultiConnectionUDPServer()

    @Test
    @Order(1)
    fun `pushToAll does not throw when there are no connections`() {
        log.info("Verifying pushToAll is a no-op with zero connections")
        assertTrue(runCatching { server.pushToAll("no one is listening") }.isSuccess)
    }

    @Test
    @Order(2)
    fun `an Iam handshake registers a new connection and gets a TXRXON reply`() {
        log.info("Starting Iam handshake test")
        val localAddress = resolveLocalAddress()

        // Listen on the server's common send port (9999) to catch the handshake reply.
        val clientListenSocket = DatagramSocket(9999)
        val clientSendSocket = DatagramSocket()

        try {
            val handshake = "Iam testclient $localAddress"
            val outBytes = handshake.toByteArray(Charsets.UTF_8)
            clientSendSocket.send(DatagramPacket(outBytes, outBytes.size, localAddress, 9998))

            val inBuffer = ByteArray(1024)
            val inPacket = DatagramPacket(inBuffer, inBuffer.size)
            clientListenSocket.soTimeout = 5000
            clientListenSocket.receive(inPacket)

            val reply = String(inPacket.data, 0, inPacket.length, Charsets.UTF_8).trim()
            log.debug("Received handshake reply: {}", reply)

            assertTrue(reply.contains("TXRXON"), "Expected a TXRXON reply, got: $reply")
        } finally {
            clientListenSocket.close()
            clientSendSocket.close()
        }
    }

    @Test
    @Order(3)
    fun `stop terminates connections and closes the common listen socket`() {
        log.info("Verifying stop() shuts the server down cleanly")
        assertTrue(runCatching { server.stop() }.isSuccess, "stop() should not throw")

        // After stopping, the server's common listen socket is closed, so a fresh
        // handshake attempt should get no TXRXON reply at all.
        val loopback = InetAddress.getLoopbackAddress()
        val clientSendSocket = DatagramSocket()
        val clientListenSocket = DatagramSocket(9999)

        try {
            val handshake = "Iam clientAfterStop $loopback"
            val outBytes = handshake.toByteArray(Charsets.UTF_8)
            clientSendSocket.send(DatagramPacket(outBytes, outBytes.size, loopback, 9998))

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