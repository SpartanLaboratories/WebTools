package com.spartanlabs.testing.gating.webtools

import com.spartanlabs.testing.support.webtools.FakeClientChannel
import com.spartanlabs.webtools.UDPConnection
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 1 - fast, socket-free smoke that UDPConnection.push forwards bytes to the
// channel and propagates the Result. The exhaustive matrix lives at Level 2.
@Tag("gating")
class UDPConnectionGatingTest {

    private val peer = InetSocketAddress(InetAddress.getLoopbackAddress(), 41300)

    @Test
    fun `push forwards the UTF-8 bytes to channel send with peer and propagates success`() {
        val channel = FakeClientChannel()

        assertTrue(UDPConnection("c", peer, channel).push("hello").isSuccess)

        assertEquals(listOf(FakeClientChannel.Sent("hello", peer)), channel.sent)
    }

    @Test
    fun `push propagates a channel send failure`() {
        val channel = FakeClientChannel(sendResult = Result.failure(RuntimeException("down")))

        assertTrue(UDPConnection("c", peer, channel).push("hello").isFailure)
    }
}
