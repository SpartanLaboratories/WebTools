package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakeClientChannel
import com.spartanlabs.testing.support.webtools.udp.captureLogsOf
import com.spartanlabs.testing.support.webtools.udp.hasWarnContaining
import com.spartanlabs.webtools.udp.HandshakeWireFormat
import com.spartanlabs.webtools.udp.LinkQuality
import com.spartanlabs.webtools.udp.UDPConnection
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Level 2 - the socket-free UDPConnection link-quality probe delegation, over a FakeClientChannel.
@Tag("component")
class UDPConnectionProbeTest {

    private val peer = InetSocketAddress(InetAddress.getLoopbackAddress(), 41400)

    private fun connection(channel: FakeClientChannel) = UDPConnection("c", peer, channel)

    @Test
    fun `startProbe with an explicit interval calls scheduleProbe once and returns its Result`() {
        val channel = FakeClientChannel()

        assertTrue(connection(channel).startProbe(5_000L).isSuccess)

        assertEquals(listOf(peer to 5_000L), channel.probeSchedules)
    }

    @Test
    fun `startProbe with no arg passes the default interval`() {
        val channel = FakeClientChannel()

        connection(channel).startProbe()

        assertEquals(
            listOf(peer to HandshakeWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS),
            channel.probeSchedules,
        )
    }

    @Test
    fun `stopProbe calls cancelProbe for the peer`() {
        val channel = FakeClientChannel()

        assertTrue(connection(channel).stopProbe().isSuccess)

        assertEquals(listOf(peer), channel.probeCancels)
    }

    @Test
    fun `linkQuality returns the channel's snapshot for the peer`() {
        val snapshot = LinkQuality(18.0, 4.0, 0.1, 20, 18)
        val channel = FakeClientChannel(linkQuality = snapshot)

        assertSame(snapshot, connection(channel).linkQuality())
        assertEquals(listOf(peer), channel.linkQualityQueries)
    }

    @Test
    fun `a channel scheduleProbe failure is propagated and logged`() {
        val channel = FakeClientChannel(
            scheduleProbeResult = Result.failure(RuntimeException("nope")),
        )

        captureLogsOf(UDPConnection::class.java) { events ->
            assertTrue(connection(channel).startProbe(5_000L).isFailure)
            assertTrue(events.hasWarnContaining("could not start a link-quality probe"))
        }
    }
}
