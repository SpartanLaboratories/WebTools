package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakeClientChannel
import com.spartanlabs.webtools.udp.UDPConnection
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
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

    @Test
    fun `push bytes forwards the exact bytes to channel send with peer and propagates success`() {
        val channel = FakeClientChannel()
        val payload = byteArrayOf(0x01, 0x02, 0x03)

        assertTrue(UDPConnection("c", peer, channel).push(payload).isSuccess)

        val (bytes, to) = channel.sentBytes.single()
        assertContentEquals(payload, bytes)
        assertEquals(peer, to)
    }

    @Test
    fun `push bytes propagates a channel send failure`() {
        val channel = FakeClientChannel(sendResult = Result.failure(RuntimeException("down")))

        assertTrue(UDPConnection("c", peer, channel).push(byteArrayOf(1, 2)).isFailure)
    }

    @Test
    fun `push bytes forwards a whitespace-bounded payload verbatim - no trim on send`() {
        val channel = FakeClientChannel()
        val payload = byteArrayOf(0x20, 0x41, 0x20)

        UDPConnection("c", peer, channel).push(payload)

        assertContentEquals(payload, channel.sentBytes.single().first)
    }
}
