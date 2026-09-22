package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakePeriodicSchedule
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import com.spartanlabs.webtools.udp.TransportWireFormat
import com.spartanlabs.webtools.udp.UdpChannel
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 2 - the client's idle-aware keepalive tick in isolation, driven through the
// injected PeriodicSchedule seam (FakePeriodicSchedule) with no real timer and no
// wall-clock scheduling. The client-side mirror of HandshakeCoordinatorTest's keepalive
// coverage. A real client socket is bound (a documented construction side effect) so a
// tick's sendKeepAlive() can be observed as a datagram at a fake peer.
@Tag("component")
@Suppress("DEPRECATION") // exercises the still-working, now-deprecated push/actuate/send/start primitives on purpose
class MultiConnectionUDPClientKeepAliveTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    private fun fakePeer(): DatagramSocket = DatagramSocket().also { opened += it }

    private fun newClient(
        peerPort: Int,
        fake: FakePeriodicSchedule,
        probe: FakePeriodicSchedule = FakePeriodicSchedule(),
    ): MultiConnectionUDPClient =
        MultiConnectionUDPClient(
            loopback,
            peerPort,
            MultiConnectionUDPServer.DEFAULT_RECEIVE_BUFFER_BYTES,
            fake,
            probe,
            FakePeriodicSchedule(),
            UdpChannel.DEFAULT_MAX_RELIABLE_MESSAGE_BYTES,
        ).also { clients += it }

    @AfterTest
    fun cleanup() {
        clients.forEach { runCatching { it.stop() } }
        opened.forEach { runCatching { it.close() } }
    }

    private fun DatagramSocket.nextText(timeoutMillis: Int): String? {
        soTimeout = timeoutMillis
        val packet = DatagramPacket(ByteArray(256), 256)
        return try {
            receive(packet)
            String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
        } catch (_: SocketTimeoutException) {
            null
        }
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
    fun `startKeepAlive records exactly one schedule for the server endpoint at the given interval`() {
        val peer = fakePeer()
        val fake = FakePeriodicSchedule()
        val client = newClient(peer.localPort, fake)

        assertTrue(client.startKeepAlive(300L).isSuccess)

        val call = fake.scheduleCalls.single()
        assertEquals(InetSocketAddress(loopback, peer.localPort), call.key)
        assertEquals(300L, call.intervalMillis)
    }

    @Test
    fun `no-arg startKeepAlive schedules at the recommended default interval`() {
        val peer = fakePeer()
        val fake = FakePeriodicSchedule()
        val client = newClient(peer.localPort, fake)

        assertTrue(client.startKeepAlive().isSuccess)

        assertEquals(
            TransportWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS,
            fake.scheduleCalls.single().intervalMillis,
        )
    }

    @Test
    fun `a tick with the last outbound forced past the interval sends exactly one 0x80 keepalive`() {
        val peer = fakePeer()
        val fake = FakePeriodicSchedule()
        val client = newClient(peer.localPort, fake)
        // 1 ms interval: by the time the tick runs the client has been output-idle
        // for far longer, so KeepAlive.isDue is true.
        assertTrue(client.startKeepAlive(1L).isSuccess)
        Thread.sleep(15)

        fake.tick(InetSocketAddress(loopback, peer.localPort))

        assertContentEquals(TransportWireFormat.keepaliveDatagram(), peer.nextBytes(500))
        assertNull(peer.nextText(200), "the tick sends exactly one keepalive")
    }

    @Test
    fun `a tick with a fresh last outbound sends no keepalive`() {
        val peer = fakePeer()
        val fake = FakePeriodicSchedule()
        val client = newClient(peer.localPort, fake)
        assertTrue(client.startKeepAlive(60_000L).isSuccess)

        // An application send stamps lastOutboundAtNanos; drain that datagram (framed as 0x90 + channel).
        assertTrue(client.send("hello").isSuccess)
        assertContentEquals(TransportWireFormat.unreliableDatagram("hello".toByteArray()), peer.nextBytes(500))

        fake.tick(InetSocketAddress(loopback, peer.localPort))

        assertNull(peer.nextText(200), "idle-aware: no keepalive while output is fresh")
    }

    @Test
    fun `a repeat startKeepAlive re-arms the same key - last call wins`() {
        val peer = fakePeer()
        val fake = FakePeriodicSchedule()
        val client = newClient(peer.localPort, fake)
        val endpoint = InetSocketAddress(loopback, peer.localPort)

        assertTrue(client.startKeepAlive(300L).isSuccess)
        assertTrue(client.startKeepAlive(500L).isSuccess)

        assertEquals(listOf(300L, 500L), fake.scheduleCalls.map { it.intervalMillis })
        assertTrue(fake.scheduleCalls.all { it.key == endpoint })
        assertEquals(500L, fake.scheduled.getValue(endpoint).intervalMillis)
    }

    @Test
    fun `stopKeepAlive cancels the server endpoint and stop shuts the scheduler`() {
        val peer = fakePeer()
        val fake = FakePeriodicSchedule()
        val client = newClient(peer.localPort, fake)
        val endpoint = InetSocketAddress(loopback, peer.localPort)
        assertTrue(client.startKeepAlive(300L).isSuccess)

        assertTrue(client.stopKeepAlive().isSuccess)
        assertEquals(listOf(endpoint), fake.cancels)

        assertTrue(client.stop().isSuccess)
        assertEquals(1, fake.shutdownCalls)
    }

    @Test
    fun `startKeepAlive after stop fails and records no new live schedule`() {
        val peer = fakePeer()
        val fake = FakePeriodicSchedule(failAfterShutdown = true)
        val client = newClient(peer.localPort, fake)
        assertTrue(client.stop().isSuccess)

        val result = client.startKeepAlive(300L)

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertTrue(fake.scheduled.isEmpty(), "no live schedule after stop")
    }
}
