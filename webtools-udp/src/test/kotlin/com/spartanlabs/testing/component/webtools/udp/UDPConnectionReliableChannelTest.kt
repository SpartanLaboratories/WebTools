package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakeClientChannel
import com.spartanlabs.webtools.udp.DeliveryMode
import com.spartanlabs.webtools.udp.ReliableSendFailure
import com.spartanlabs.webtools.udp.ReliableWindowFullException
import com.spartanlabs.webtools.udp.TransportWireFormat
import com.spartanlabs.webtools.udp.UDPConnection
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 2 - UDPConnection.channel(RELIABLE_ORDERED) in isolation, over a FakeClientChannel:
// the channel handle is a thin, correct façade over ClientChannel.sendReliable/bindReliable.
@Tag("component")
class UDPConnectionReliableChannelTest {

    private val peer = InetSocketAddress(InetAddress.getLoopbackAddress(), 41300)

    @Test
    fun `channel RELIABLE_ORDERED send reaches ClientChannel sendReliable with the exact bytes and peer`() {
        val channel = FakeClientChannel()
        val connection = UDPConnection("c", peer, channel)
        val payload = byteArrayOf(1, 2, 3)

        assertTrue(connection.channel(DeliveryMode.RELIABLE_ORDERED).send(payload).isSuccess)

        val (bytes, to) = channel.reliableSent.single()
        assertContentEquals(payload, bytes)
        assertEquals(peer, to)
    }

    @Test
    fun `channel RELIABLE_ORDERED actuateBytes reaches bindReliable`() {
        val channel = FakeClientChannel()
        val connection = UDPConnection("c", peer, channel)
        val handler: (ByteArray) -> Unit = {}

        assertTrue(connection.channel(DeliveryMode.RELIABLE_ORDERED).actuateBytes(handler).isSuccess)

        assertEquals(handler, channel.boundReliable[peer])
    }

    @Test
    fun `a failing sendReliable propagates its Result failure unchanged - cause is ReliableSendFailure`() {
        val failure = ReliableWindowFullException(256)
        val channel = FakeClientChannel(sendReliableResult = Result.failure(failure))
        val connection = UDPConnection("c", peer, channel)

        val result = connection.channel(DeliveryMode.RELIABLE_ORDERED).send(byteArrayOf(1))

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
        assertIs<ReliableSendFailure>(result.exceptionOrNull())
    }

    @Test
    fun `channel UNRELIABLE send still produces an 0x90-framed datagram through push`() {
        val channel = FakeClientChannel()
        val connection = UDPConnection("c", peer, channel)
        val payload = byteArrayOf(4, 5, 6)

        assertTrue(connection.channel(DeliveryMode.UNRELIABLE).send(payload).isSuccess)

        val (bytes, to) = channel.sentBytes.single()
        assertContentEquals(TransportWireFormat.unreliableDatagram(payload), bytes)
        assertEquals(peer, to)
    }
}
