package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.webtools.udp.DatagramType
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import com.spartanlabs.webtools.udp.TransportWireFormat
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertTrue

// Level 3 - a real MultiConnectionUDPClient probing a raw peer DatagramSocket, counting the
// 0x81 PROBE_PINGs that actually arrive over a fixed window (Issue #34): the probe must fire
// once per configured interval, not once per poll-divided quarter-interval.
@Tag("integration")
class ProbeCadenceIntegrationTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    @Test
    fun `startProbe at the documented 1s default fires roughly once per second, not once per 250ms`() {
        val peer = DatagramSocket()
        val client = MultiConnectionUDPClient(loopback, peer.localPort, MultiConnectionUDPServer.DEFAULT_RECEIVE_BUFFER_BYTES)
        try {
            assertTrue(client.startProbe(TransportWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS).isSuccess)

            val deadline = System.currentTimeMillis() + 4_300L
            var count = 0
            peer.soTimeout = 200
            val buffer = ByteArray(64)
            val packet = DatagramPacket(buffer, buffer.size)
            while (System.currentTimeMillis() < deadline) {
                // Reset the length every iteration: receive() shrinks the packet to the
                // bytes actually read, which would cap every later read at that size.
                packet.setLength(buffer.size)
                try {
                    peer.receive(packet)
                    // Count 0x81 PROBE_PING only - never "any datagram" - so the assertion
                    // stays honest if this setup ever grows a handshake or a keepalive.
                    if (packet.length > 0 && buffer[0] == DatagramType.PROBE_PING.tag) count++
                } catch (_: SocketTimeoutException) {
                    // no PING in this poll slice; keep waiting for the deadline
                }
            }

            assertTrue(
                count in 3..5,
                "expected ~4 PINGs in 4.3s at a 1s interval (the old poll-divided bug would " +
                    "have sent ~17), saw $count",
            )
        } finally {
            client.stop()
            peer.close()
        }
    }
}
