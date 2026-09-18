package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.webtools.udp.HandshakeRefusedException
import com.spartanlabs.webtools.udp.IncompatibleProtocolException
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 3 - a real MultiConnectionUDPClient socket against a fake server DatagramSocket that
// answers the handshake with a caller-chosen reply, exercising the REGISTERED <version>
// cross-major failure modes end to end (design §3.2 / plan §3.2).
@Tag("integration")
class MultiConnectionUDPProtocolVersionTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    @AfterTest
    fun cleanup() {
        clients.forEach { runCatching { it.stop() } }
        opened.forEach { runCatching { it.close() } }
    }

    private fun fakeServer(): DatagramSocket = DatagramSocket().also { opened += it }

    /** Answers exactly one inbound `Iam` with [reply], on a background thread. */
    private fun answerOnce(server: DatagramSocket, reply: String) {
        Thread {
            server.soTimeout = 5_000
            val packet = DatagramPacket(ByteArray(256), 256)
            runCatching {
                server.receive(packet)
                val bytes = reply.toByteArray(Charsets.UTF_8)
                server.send(DatagramPacket(bytes, bytes.size, packet.address, packet.port))
            }
        }.apply { isDaemon = true; start() }
    }

    private fun newClient(serverPort: Int) =
        MultiConnectionUDPClient(loopback, serverPort).also { clients += it }

    @Test
    fun `a bare REGISTERED from a pre-2 0 server fails with IncompatibleProtocolException remoteVersion 1`() {
        val server = fakeServer()
        answerOnce(server, "REGISTERED")
        val client = newClient(server.localPort)

        val result = client.handshake("alice")

        assertTrue(result.isFailure)
        val cause = assertIs<IncompatibleProtocolException>(result.exceptionOrNull())
        assertEquals(1, cause.remoteVersion)
        assertEquals(2, cause.localVersion)
    }

    @Test
    fun `REGISTERED 2 from a same-major server succeeds`() {
        val server = fakeServer()
        answerOnce(server, "REGISTERED 2")
        val client = newClient(server.localPort)

        assertTrue(client.handshake("alice").isSuccess)
    }

    @Test
    fun `REGISTERED 3 from a future-major server fails with IncompatibleProtocolException remoteVersion 3`() {
        val server = fakeServer()
        answerOnce(server, "REGISTERED 3")
        val client = newClient(server.localPort)

        val result = client.handshake("alice")

        assertTrue(result.isFailure)
        val cause = assertIs<IncompatibleProtocolException>(result.exceptionOrNull())
        assertEquals(3, cause.remoteVersion)
        assertEquals(2, cause.localVersion)
    }

    @Test
    fun `REFUSED full still fails with HandshakeRefusedException - refusal wins over the version check`() {
        val server = fakeServer()
        answerOnce(server, "REFUSED full")
        val client = newClient(server.localPort)

        val result = client.handshake("alice")

        assertTrue(result.isFailure)
        val cause = assertIs<HandshakeRefusedException>(result.exceptionOrNull())
        assertEquals("full", cause.reason)
    }
}
