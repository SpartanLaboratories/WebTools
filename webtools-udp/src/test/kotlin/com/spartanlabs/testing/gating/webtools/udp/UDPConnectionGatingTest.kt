package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakeClientChannel
import com.spartanlabs.webtools.udp.DeliveryMode
import com.spartanlabs.webtools.udp.TransportWireFormat
import com.spartanlabs.webtools.udp.UDPConnection
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Level 1 - fast, socket-free smoke that UDPConnection.push forwards bytes to the
// channel and propagates the Result. The exhaustive matrix lives at Level 2.
@Tag("gating")
@Suppress("DEPRECATION") // exercises the still-working, now-deprecated push/actuate/send/start primitives on purpose
class UDPConnectionGatingTest {

    private val peer = InetSocketAddress(InetAddress.getLoopbackAddress(), 41300)

    @Test
    fun `push frames the UTF-8 bytes as an 0x90 datagram to channel send with peer and propagates success`() {
        val channel = FakeClientChannel()

        assertTrue(UDPConnection("c", peer, channel).push("hello").isSuccess)

        val (bytes, to) = channel.sentBytes.single()
        assertContentEquals(TransportWireFormat.unreliableDatagram("hello".toByteArray(Charsets.UTF_8)), bytes)
        assertEquals(peer, to)
    }

    @Test
    fun `push propagates a channel send failure`() {
        val channel = FakeClientChannel(sendResult = Result.failure(RuntimeException("down")))

        assertTrue(UDPConnection("c", peer, channel).push("hello").isFailure)
    }

    @Test
    fun `push bytes frames the exact payload as an 0x90 datagram to channel send with peer and propagates success`() {
        val channel = FakeClientChannel()
        val payload = byteArrayOf(0x01, 0x02, 0x03)

        assertTrue(UDPConnection("c", peer, channel).push(payload).isSuccess)

        val (bytes, to) = channel.sentBytes.single()
        assertContentEquals(TransportWireFormat.unreliableDatagram(payload), bytes)
        assertEquals(peer, to)
    }

    @Test
    fun `push bytes propagates a channel send failure`() {
        val channel = FakeClientChannel(sendResult = Result.failure(RuntimeException("down")))

        assertTrue(UDPConnection("c", peer, channel).push(byteArrayOf(1, 2)).isFailure)
    }

    @Test
    fun `push bytes frames a whitespace-bounded payload verbatim - no trim on send`() {
        val channel = FakeClientChannel()
        val payload = byteArrayOf(0x20, 0x41, 0x20)

        UDPConnection("c", peer, channel).push(payload)

        assertContentEquals(TransportWireFormat.unreliableDatagram(payload), channel.sentBytes.single().first)
    }

    @Test
    fun `channel UNRELIABLE and channel RELIABLE_ORDERED return non-null handles with the right mode`() {
        val connection = UDPConnection("c", peer, FakeClientChannel())

        val unreliable = connection.channel(DeliveryMode.UNRELIABLE)
        val reliable = connection.channel(DeliveryMode.RELIABLE_ORDERED)

        assertNotNull(unreliable)
        assertNotNull(reliable)
        assertEquals(DeliveryMode.UNRELIABLE, unreliable.mode)
        assertEquals(DeliveryMode.RELIABLE_ORDERED, reliable.mode)
    }

    @Test
    fun `the same channel call twice returns the same instance - the by lazy contract`() {
        val connection = UDPConnection("c", peer, FakeClientChannel())

        assertSame(connection.channel(DeliveryMode.UNRELIABLE), connection.channel(DeliveryMode.UNRELIABLE))
        assertSame(connection.channel(DeliveryMode.RELIABLE_ORDERED), connection.channel(DeliveryMode.RELIABLE_ORDERED))
    }
}
