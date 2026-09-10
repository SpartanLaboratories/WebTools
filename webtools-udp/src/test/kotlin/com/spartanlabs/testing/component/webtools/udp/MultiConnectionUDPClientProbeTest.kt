package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakePeriodicSchedule
import com.spartanlabs.webtools.udp.HandshakeWireFormat
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 2 - the client's link-quality probe tick in isolation, driven through the injected
// PeriodicSchedule seam (FakePeriodicSchedule) with no real timer. A real client socket is
// bound so a tick's PING can be observed at a fake peer, and a PONG can be fed back over a
// real loopback listen (the known compromise noted in the plan - the client has no
// injectable send seam).
@Tag("component")
class MultiConnectionUDPClientProbeTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    private fun fakePeer(): DatagramSocket = DatagramSocket().also { opened += it }

    private fun newClient(peerPort: Int, probe: FakePeriodicSchedule): MultiConnectionUDPClient =
        MultiConnectionUDPClient(
            loopback,
            peerPort,
            MultiConnectionUDPServer.DEFAULT_RECEIVE_BUFFER_BYTES,
            FakePeriodicSchedule(),
            probe,
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

    /** Receives one datagram and echoes [transform] of its trimmed text straight back to the sender. */
    private fun DatagramSocket.answerOnce(timeoutMillis: Int, transform: (String) -> String): Boolean {
        soTimeout = timeoutMillis
        val packet = DatagramPacket(ByteArray(256), 256)
        return try {
            receive(packet)
            val reply = transform(String(packet.data, 0, packet.length, Charsets.UTF_8).trim()).toByteArray(Charsets.UTF_8)
            send(DatagramPacket(reply, reply.size, packet.socketAddress))
            true
        } catch (_: SocketTimeoutException) {
            false
        }
    }

    @Test
    fun `startProbe records exactly one schedule for the server endpoint at the given interval`() {
        val peer = fakePeer()
        val probe = FakePeriodicSchedule()
        val client = newClient(peer.localPort, probe)

        assertTrue(client.startProbe(300L).isSuccess)

        val call = probe.scheduleCalls.single()
        assertEquals(InetSocketAddress(loopback, peer.localPort), call.key)
        assertEquals(300L, call.intervalMillis)
    }

    @Test
    fun `no-arg startProbe schedules at the default probe interval`() {
        val peer = fakePeer()
        val probe = FakePeriodicSchedule()
        val client = newClient(peer.localPort, probe)

        assertTrue(client.startProbe().isSuccess)

        assertEquals(
            HandshakeWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS,
            probe.scheduleCalls.single().intervalMillis,
        )
    }

    @Test
    fun `each tick sends exactly one PING with a strictly increasing token`() {
        val peer = fakePeer()
        val probe = FakePeriodicSchedule()
        val client = newClient(peer.localPort, probe)
        assertTrue(client.startProbe(300L).isSuccess)
        val endpoint = InetSocketAddress(loopback, peer.localPort)

        probe.tick(endpoint)
        val first = peer.nextText(500)
        assertNull(peer.nextText(200), "the tick sends exactly one PING")
        probe.tick(endpoint)
        val second = peer.nextText(500)

        assertTrue(first != null && HandshakeWireFormat.isProbeRequest(first), "was $first")
        assertTrue(second != null && HandshakeWireFormat.isProbeRequest(second), "was $second")
        val t1 = HandshakeWireFormat.probeToken(first!!).toLong()
        val t2 = HandshakeWireFormat.probeToken(second!!).toLong()
        assertTrue(t2 > t1, "tokens must strictly increase: $t1 then $t2")
    }

    @Test
    fun `a matching PONG populates linkQuality and a garbage token does not`() {
        val peer = fakePeer()
        val probe = FakePeriodicSchedule()
        val client = newClient(peer.localPort, probe)
        assertTrue(client.start { }.isSuccess)
        assertTrue(client.startProbe(300L).isSuccess)
        val endpoint = InetSocketAddress(loopback, peer.localPort)

        // Garbage reply first: neither throws nor populates.
        probe.tick(endpoint)
        assertTrue(peer.answerOnce(500) { "PONG not-a-number" })
        Thread.sleep(150)
        assertNull(client.linkQuality(), "a garbage token does not populate")

        // Now a correct echo.
        probe.tick(endpoint)
        assertTrue(peer.answerOnce(500) { ping -> HandshakeWireFormat.probeReplyMessage(HandshakeWireFormat.probeToken(ping)) })

        val deadline = System.currentTimeMillis() + 2_000
        while (client.linkQuality() == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        val snap = client.linkQuality()
        assertTrue(snap != null, "a matching PONG populates linkQuality")
        assertTrue(snap!!.rttMillis in 0.0..2_000.0, "a plausible small RTT, was ${snap.rttMillis}")
        assertEquals(0.0, snap.packetLossRatio, 0.001)
    }

    @Test
    fun `a repeat startProbe re-arms the same key - last call wins`() {
        val peer = fakePeer()
        val probe = FakePeriodicSchedule()
        val client = newClient(peer.localPort, probe)
        val endpoint = InetSocketAddress(loopback, peer.localPort)

        assertTrue(client.startProbe(300L).isSuccess)
        assertTrue(client.startProbe(500L).isSuccess)

        assertEquals(listOf(300L, 500L), probe.scheduleCalls.map { it.intervalMillis })
        assertTrue(probe.scheduleCalls.all { it.key == endpoint })
        assertEquals(500L, probe.scheduled.getValue(endpoint).intervalMillis)
    }

    @Test
    fun `stopProbe cancels the server endpoint and stop shuts the probe scheduler`() {
        val peer = fakePeer()
        val probe = FakePeriodicSchedule()
        val client = newClient(peer.localPort, probe)
        val endpoint = InetSocketAddress(loopback, peer.localPort)
        assertTrue(client.startProbe(300L).isSuccess)

        assertTrue(client.stopProbe().isSuccess)
        assertEquals(listOf(endpoint), probe.cancels)

        assertTrue(client.stop().isSuccess)
        assertEquals(1, probe.shutdownCalls)
    }

    @Test
    fun `startProbe after stop fails and records no new live schedule`() {
        val peer = fakePeer()
        val probe = FakePeriodicSchedule(failAfterShutdown = true)
        val client = newClient(peer.localPort, probe)
        assertTrue(client.stop().isSuccess)

        val result = client.startProbe(300L)

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertTrue(probe.scheduled.isEmpty(), "no live schedule after stop")
    }
}
