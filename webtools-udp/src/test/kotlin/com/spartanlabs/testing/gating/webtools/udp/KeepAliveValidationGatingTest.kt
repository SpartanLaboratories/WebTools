package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakeClientChannel
import com.spartanlabs.webtools.udp.HandshakeWireFormat
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.UDPConnection
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 1 - the startKeepAlive interval guard. The client binds only an ephemeral port
// (no common-port lock needed); the UDPConnection path is socket-free over a FakeClientChannel.
@Tag("gating")
class KeepAliveValidationGatingTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    @AfterTest
    fun cleanup() {
        clients.forEach { runCatching { it.stop() } }
        opened.forEach { runCatching { it.close() } }
    }

    @Test
    fun `client startKeepAlive rejects a non-positive interval and arms nothing`() {
        val peer = DatagramSocket().also { opened += it }
        val client = MultiConnectionUDPClient(loopback, peer.localPort).also { clients += it }

        listOf(0L, -1L).forEach { bad ->
            val result = client.startKeepAlive(bad)
            assertTrue(result.isFailure, "interval $bad must fail")
            assertIs<IllegalArgumentException>(result.exceptionOrNull())
        }

        // Nothing is armed - no KA reaches the fake peer in a short window.
        peer.soTimeout = 400
        assertFailsWith<SocketTimeoutException> {
            peer.receive(DatagramPacket(ByteArray(64), 64))
        }
        assertTrue(client.stop().isSuccess)
    }

    @Test
    fun `UDPConnection startKeepAlive surfaces the channel's IllegalArgumentException`() {
        val channel = FakeClientChannel(
            scheduleKeepAliveResult = Result.failure(IllegalArgumentException("intervalMillis must be > 0, was 0")),
        )
        val connection = UDPConnection("c", InetSocketAddress(loopback, 41300), channel)

        val result = connection.startKeepAlive(0L)

        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
        assertEquals(listOf(InetSocketAddress(loopback, 41300) to 0L), channel.keepAliveSchedules)
    }

    @Test
    fun `Connection default startKeepAlive is unsupported`() {
        val plain = object : com.spartanlabs.webtools.udp.Connection {
            override val name = "x"
            override val peer = InetSocketAddress(loopback, 1)
            override fun actuate(onMessage: (message: String) -> Unit) = Result.success(Unit)
            override fun terminate() = Result.success(Unit)
            override fun push(message: String) = Result.success(Unit)
            override fun keepAlive() = Result.success(Unit)
        }
        assertTrue(plain.startKeepAlive().isFailure)
        assertTrue(plain.startKeepAlive(HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS).isFailure)
        assertTrue(plain.stopKeepAlive().isSuccess)
    }
}
