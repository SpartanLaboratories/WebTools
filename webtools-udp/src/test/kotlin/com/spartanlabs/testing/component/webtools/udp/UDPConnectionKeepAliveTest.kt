package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakeClientChannel
import com.spartanlabs.testing.support.webtools.udp.captureLogsOf
import com.spartanlabs.testing.support.webtools.udp.hasWarnContaining
import com.spartanlabs.webtools.udp.HandshakeWireFormat
import com.spartanlabs.webtools.udp.UDPConnection
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 2 - the socket-free UDPConnection keepalive delegation, over a FakeClientChannel.
@Tag("component")
class UDPConnectionKeepAliveTest {

    private val peer = InetSocketAddress(InetAddress.getLoopbackAddress(), 41300)

    private fun connection(channel: FakeClientChannel) = UDPConnection("c", peer, channel)

    @Test
    fun `startKeepAlive with an explicit interval calls scheduleKeepAlive once and returns its Result`() {
        val channel = FakeClientChannel()

        assertTrue(connection(channel).startKeepAlive(5_000L).isSuccess)

        assertEquals(listOf(peer to 5_000L), channel.keepAliveSchedules)
    }

    @Test
    fun `startKeepAlive with no arg passes the default interval`() {
        val channel = FakeClientChannel()

        connection(channel).startKeepAlive()

        assertEquals(
            listOf(peer to HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS),
            channel.keepAliveSchedules,
        )
    }

    @Test
    fun `stopKeepAlive calls cancelKeepAlive for the peer`() {
        val channel = FakeClientChannel()

        assertTrue(connection(channel).stopKeepAlive().isSuccess)

        assertEquals(listOf(peer), channel.keepAliveCancels)
    }

    @Test
    fun `a channel scheduleKeepAlive failure is propagated and logged`() {
        val channel = FakeClientChannel(
            scheduleKeepAliveResult = Result.failure(RuntimeException("nope")),
        )

        captureLogsOf(UDPConnection::class.java) { events ->
            assertTrue(connection(channel).startKeepAlive(5_000L).isFailure)
            assertTrue(events.hasWarnContaining("could not start a scheduled keepalive"))
        }
    }
}
