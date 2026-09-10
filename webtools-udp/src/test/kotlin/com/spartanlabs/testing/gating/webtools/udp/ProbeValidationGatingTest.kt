package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakeClientChannel
import com.spartanlabs.webtools.udp.LinkQuality
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Level 1 - a fast pre-commit gate over the opt-in probe's validation paths: a non-positive
// interval fails and arms nothing; linkQuality() is null before any probe; the UDPConnection
// delegates to its channel.
@Tag("gating")
class ProbeValidationGatingTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    @AfterTest
    fun cleanup() {
        clients.forEach { runCatching { it.stop() } }
        opened.forEach { runCatching { it.close() } }
    }

    private fun DatagramSocket.sawNothing(timeoutMillis: Int): Boolean {
        soTimeout = timeoutMillis
        return try {
            receive(DatagramPacket(ByteArray(64), 64)); false
        } catch (_: SocketTimeoutException) {
            true
        }
    }

    @Test
    fun `startProbe with a non-positive interval fails, arms nothing, and leaves linkQuality null`() {
        val peer = DatagramSocket().also { opened += it }
        val client = MultiConnectionUDPClient(loopback, peer.localPort, MultiConnectionUDPServer.DEFAULT_RECEIVE_BUFFER_BYTES)
            .also { clients += it }

        assertNull(client.linkQuality(), "null before any probe")
        listOf(0L, -1L).forEach { bad ->
            val result = client.startProbe(bad)
            assertTrue(result.isFailure, "interval $bad must fail")
            assertIs<IllegalArgumentException>(result.exceptionOrNull())
        }
        assertTrue(peer.sawNothing(300), "no PING went out")
        assertNull(client.linkQuality())
        assertTrue(client.stop().isSuccess)
    }

    @Test
    fun `a UDPConnection startProbe surfaces the channel failure and linkQuality returns the channel value`() {
        val snapshot = LinkQuality(12.0, 3.0, 0.0, 5, 5)
        val channel = FakeClientChannel(
            scheduleProbeResult = Result.failure(IllegalArgumentException("bad interval")),
            linkQuality = snapshot,
        )
        val connection = UDPConnection("c", InetSocketAddress(loopback, 41777), channel)

        val result = connection.startProbe(0)
        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
        assertEquals(listOf(InetSocketAddress(loopback, 41777) to 0L), channel.probeSchedules)
        assertSame(snapshot, connection.linkQuality())
    }
}
