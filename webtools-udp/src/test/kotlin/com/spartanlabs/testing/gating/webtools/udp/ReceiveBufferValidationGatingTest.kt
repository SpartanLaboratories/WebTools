package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import com.spartanlabs.webtools.udp.UDPSendReceiveServer
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertFailsWith

// Level 1 - the receiveBufferBytes constructor guard on all three receive types.
// The guard runs in an init block that precedes every socket bind (see the issue-9
// plan SS2.5), so every rejection case here binds nothing: no ephemeral port, and -
// crucially for the server - never the fixed common port 9998. That keeps this class
// socket-free and outside the module's commonUdpPortLock.
@Tag("gating")
class ReceiveBufferValidationGatingTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    // A concrete server subclass whose construction we can drive with a chosen buffer size.
    private class SizedServer(receiveBufferBytes: Int) : MultiConnectionUDPServer(receiveBufferBytes) {
        override fun onClientConnect(connection: com.spartanlabs.webtools.udp.Connection) = Unit
    }

    private val rejected = listOf(Int.MIN_VALUE, -1, 0, 511, 65508, Int.MAX_VALUE)

    @Test
    fun `MultiConnectionUDPServer rejects an out-of-range buffer before binding the common port`() {
        rejected.forEach { size ->
            assertFailsWith<IllegalArgumentException>("size $size must be rejected") { SizedServer(size) }
        }
    }

    @Test
    fun `MultiConnectionUDPClient rejects an out-of-range buffer before binding a socket`() {
        rejected.forEach { size ->
            assertFailsWith<IllegalArgumentException>("size $size must be rejected") {
                MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT, size)
            }
        }
    }

    @Test
    fun `UDPSendReceiveServer rejects an out-of-range buffer before binding a socket`() {
        rejected.forEach { size ->
            assertFailsWith<IllegalArgumentException>("size $size must be rejected") {
                UDPSendReceiveServer(loopback, 41411, 41412, size)
            }
        }
    }

    @Test
    fun `the client and send-receive server accept the documented bounds and a mid value`() {
        listOf(
            MultiConnectionUDPServer.MIN_RECEIVE_BUFFER_BYTES,
            1200,
            MultiConnectionUDPServer.MAX_UDP_PAYLOAD_BYTES,
        ).forEach { size ->
            MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT, size).stop()
            UDPSendReceiveServer(loopback, 41413, 41414, size).close()
        }
    }
}
