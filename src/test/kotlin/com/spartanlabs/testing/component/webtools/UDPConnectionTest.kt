package com.spartanlabs.testing.component.webtools

import com.spartanlabs.testing.support.webtools.FakeClientChannel
import com.spartanlabs.webtools.HandshakeProtocol
import com.spartanlabs.webtools.UDPConnection
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 2 - the socket-free UDPConnection handle in isolation, over a FakeClientChannel.
@Tag("component")
class UDPConnectionTest {

    private val peer = InetSocketAddress(InetAddress.getLoopbackAddress(), 41300)

    private fun connection(channel: FakeClientChannel) = UDPConnection("c", peer, channel)

    @Test
    fun `push encodes UTF-8 and calls channel send to peer`() {
        val channel = FakeClientChannel()

        assertTrue(connection(channel).push("hello").isSuccess)

        assertEquals(listOf(FakeClientChannel.Sent("hello", peer)), channel.sent)
    }

    @Test
    fun `push propagates a channel send failure`() {
        val channel = FakeClientChannel(sendResult = Result.failure(RuntimeException("down")))

        assertTrue(connection(channel).push("hello").isFailure)
    }

    @Test
    fun `keepAlive sends the KA bytes to peer`() {
        val channel = FakeClientChannel()

        assertTrue(connection(channel).keepAlive().isSuccess)

        assertEquals(listOf(FakeClientChannel.Sent(HandshakeProtocol.KEEPALIVE_TOKEN, peer)), channel.sent)
    }

    @Test
    fun `keepAlive propagates a channel send failure`() {
        val channel = FakeClientChannel(sendResult = Result.failure(RuntimeException("down")))

        assertTrue(connection(channel).keepAlive().isFailure)
    }

    @Test
    fun `actuate binds the handler for peer`() {
        val channel = FakeClientChannel()
        val handler: (String) -> Unit = {}

        assertTrue(connection(channel).actuate(handler).isSuccess)

        assertEquals(handler, channel.bound[peer])
    }

    @Test
    fun `terminate unbinds peer`() {
        val channel = FakeClientChannel()
        val connection = connection(channel)
        connection.actuate {}

        assertTrue(connection.terminate().isSuccess)

        assertEquals(listOf(peer), channel.unbound)
    }

    @Test
    fun `name and peer are exposed as constructed`() {
        val connection = connection(FakeClientChannel())
        assertEquals("c", connection.name)
        assertEquals(peer, connection.peer)
    }
}
