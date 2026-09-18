package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakePeriodicSchedule
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import com.spartanlabs.webtools.udp.TransportWireFormat
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

// Level 2 - the client's send()/receiveLoop framing in isolation, over a real socket against
// a loopback peer DatagramSocket (no handshake, no dispatch to a server). Mirrors the framing
// coverage FramingComponentTest gives the server side.
@Tag("component")
class MultiConnectionUDPClientFramingTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    private fun fakePeer(): DatagramSocket = DatagramSocket().also { opened += it }

    private fun newClient(peerPort: Int): MultiConnectionUDPClient =
        MultiConnectionUDPClient(
            loopback,
            peerPort,
            MultiConnectionUDPServer.DEFAULT_RECEIVE_BUFFER_BYTES,
            FakePeriodicSchedule(),
            FakePeriodicSchedule(),
        ).also { clients += it }

    @AfterTest
    fun cleanup() {
        clients.forEach { runCatching { it.stop() } }
        opened.forEach { runCatching { it.close() } }
    }

    private fun DatagramSocket.nextBytes(timeoutMillis: Int): ByteArray? {
        soTimeout = timeoutMillis
        val packet = DatagramPacket(ByteArray(256), 256)
        return try {
            receive(packet)
            packet.data.copyOf(packet.length)
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    @Test
    fun `send puts an 0x90 00 frame on the wire`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)

        assertTrue(client.send("hi").isSuccess)

        assertContentEquals(TransportWireFormat.unreliableDatagram("hi".toByteArray()), peer.nextBytes(500))
    }

    @Test
    fun `an inbound 0x90 00 hi frame reaches the start text handler as hi`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val received = mutableListOf<String>()
        assertTrue(client.start(received::add).isSuccess)

        val framed = TransportWireFormat.unreliableDatagram("hi".toByteArray())
        peer.send(DatagramPacket(framed, framed.size, loopback, client.localPort))

        val deadline = System.currentTimeMillis() + 2_000
        while (received.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertContentEquals(listOf("hi"), received)
    }

    @Test
    fun `an inbound 0x90 00 frame carrying raw bytes reaches the startBytes handler exactly`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val received = mutableListOf<ByteArray>()
        assertTrue(client.startBytes(received::add).isSuccess)

        val payload = byteArrayOf(0x00, 0x4B, 0x41, -0x01)
        val framed = TransportWireFormat.unreliableDatagram(payload)
        peer.send(DatagramPacket(framed, framed.size, loopback, client.localPort))

        val deadline = System.currentTimeMillis() + 2_000
        while (received.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertContentEquals(payload, received.single())
    }

    @Test
    fun `an inbound 0x80 keepalive is dropped, never reaching the handler`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val received = mutableListOf<String>()
        assertTrue(client.start(received::add).isSuccess)

        val keepalive = TransportWireFormat.keepaliveDatagram()
        peer.send(DatagramPacket(keepalive, keepalive.size, loopback, client.localPort))
        Thread.sleep(300)

        assertTrue(received.isEmpty())
    }

    @Test
    fun `an unframed datagram after start is dropped, never reaching the handler`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val received = mutableListOf<String>()
        assertTrue(client.start(received::add).isSuccess)

        val unframed = "hi".toByteArray(Charsets.UTF_8)
        peer.send(DatagramPacket(unframed, unframed.size, loopback, client.localPort))
        Thread.sleep(300)

        assertTrue(received.isEmpty())
    }
}
