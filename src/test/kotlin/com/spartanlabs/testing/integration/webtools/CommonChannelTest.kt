package com.spartanlabs.testing.integration.webtools

import com.spartanlabs.webtools.CommonChannel
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 3 - CommonChannel over real loopback UDP sockets.
@Tag("integration")
class CommonChannelTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    @Test
    fun `localPort reflects the bound port`() {
        val channel = CommonChannel(0)
        try {
            assertTrue(channel.localPort in 1..65535)
        } finally {
            channel.closeResult()
        }
    }

    @Test
    fun `send then receive round-trips text and the real source address`() {
        val receiver = CommonChannel(0)
        val sender = CommonChannel(0)
        try {
            val to = InetSocketAddress(loopback, receiver.localPort)
            assertTrue(sender.send("hello".toByteArray(), to).isSuccess)

            val inbound = receiver.receive(ByteArray(1024)).getOrThrow()
            assertEquals("hello", inbound.text)
            assertEquals(sender.localPort, inbound.origin.port)
        } finally {
            receiver.closeResult()
            sender.closeResult()
        }
    }

    @Test
    fun `receive after close yields a SocketException failure`() {
        val channel = CommonChannel(0)
        channel.closeResult()

        val result = channel.receive(ByteArray(64))

        assertTrue(result.isFailure)
        assertIs<SocketException>(result.exceptionOrNull())
    }

    @Test
    fun `a send on one thread while another is blocked in receive both succeed on the same socket`() {
        val channel = CommonChannel(0)
        val peer = java.net.DatagramSocket(0)
        try {
            val peerAddr = InetSocketAddress(loopback, peer.localPort)
            // Peer echoes whatever it gets straight back to the channel's port.
            val echo = Thread {
                val p = java.net.DatagramPacket(ByteArray(64), 64)
                peer.receive(p)
                peer.send(java.net.DatagramPacket(p.data, p.length, p.socketAddress))
            }.apply { start() }

            // Thread B sends on the channel while thread A (this one) is about to block in receive.
            val sender = Thread {
                Thread.sleep(150)
                assertTrue(channel.send("ping".toByteArray(), peerAddr).isSuccess)
            }.apply { start() }

            val inbound = channel.receive(ByteArray(1024)).getOrThrow()
            sender.join()
            echo.join()
            assertEquals("ping", inbound.text)
        } finally {
            channel.closeResult()
            peer.close()
        }
    }
}
