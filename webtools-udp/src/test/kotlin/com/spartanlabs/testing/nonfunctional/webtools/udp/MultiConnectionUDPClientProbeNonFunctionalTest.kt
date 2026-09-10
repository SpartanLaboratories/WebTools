package com.spartanlabs.testing.nonfunctional.webtools.udp

import com.spartanlabs.webtools.udp.HandshakeWireFormat
import com.spartanlabs.webtools.udp.LinkQualityTracker
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

// Level 4c - robustness of MultiConnectionUDPClient's link-quality probe: thread hygiene
// across many arm/disarm cycles, independence from the dispatch thread, tracker concurrency,
// datagram size, and races with stop().
@Tag("nonfunctional")
class MultiConnectionUDPClientProbeNonFunctionalTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    private fun fakePeer(): DatagramSocket = DatagramSocket().also { opened += it }
    private fun newClient(port: Int) = MultiConnectionUDPClient(loopback, port).also { clients += it }

    @AfterTest
    fun cleanup() {
        clients.forEach { runCatching { it.stop() } }
        opened.forEach { runCatching { it.close() } }
    }

    private fun DatagramSocket.nextText(timeoutMillis: Int): Pair<Int, String>? {
        soTimeout = timeoutMillis
        val packet = DatagramPacket(ByteArray(256), 256)
        return try {
            receive(packet)
            packet.length to String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    private fun handshake(client: MultiConnectionUDPClient, peer: DatagramSocket): InetSocketAddress {
        var origin: InetSocketAddress? = null
        val t = Thread {
            peer.soTimeout = 5_000
            val packet = DatagramPacket(ByteArray(256), 256)
            peer.receive(packet)
            origin = InetSocketAddress(packet.address, packet.port)
            val reg = "REGISTERED".toByteArray()
            peer.send(DatagramPacket(reg, reg.size, packet.address, packet.port))
        }.apply { start() }
        assertTrue(client.handshake("alice").isSuccess)
        t.join(5_000)
        return origin!!
    }

    private fun probeThreadCount() =
        Thread.getAllStackTraces().keys.count { it.name == "mcupc-probe" && it.isAlive }

    @Test
    fun `200 arm-disarm cycles leave at most one probe thread and a final arm still delivers PING`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        handshake(client, peer)

        repeat(200) {
            assertTrue(client.startProbe(300L).isSuccess)
            assertTrue(client.stopProbe().isSuccess)
        }
        assertTrue(probeThreadCount() <= 1, "cycles leaked probe threads")

        while (peer.nextText(20) != null) { /* drain */ }
        assertTrue(client.startProbe(300L).isSuccess)
        val deadline = System.currentTimeMillis() + 1_500L
        var ping = false
        while (!ping && System.currentTimeMillis() < deadline) {
            ping = peer.nextText(200)?.let { HandshakeWireFormat.isProbeRequest(it.second) } ?: false
        }
        assertTrue(ping, "a final arm still delivers PING")
    }

    @Test
    fun `a wedged start handler does not delay the PING - the probe thread is independent of dispatch`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshake(client, peer)
        client.start { Thread.sleep(2_000) }
        assertTrue(client.startProbe(300L).isSuccess)

        val poke = "poke".toByteArray()
        peer.send(DatagramPacket(poke, poke.size, origin.address, origin.port))

        val deadline = System.currentTimeMillis() + 1_500L
        var ping = false
        while (!ping && System.currentTimeMillis() < deadline) {
            ping = peer.nextText(200)?.let { HandshakeWireFormat.isProbeRequest(it.second) } ?: false
        }
        assertTrue(ping, "PING still flowed while the dispatch thread was wedged")
    }

    @Test
    fun `a probe datagram stays well under 32 bytes`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        handshake(client, peer)
        while (peer.nextText(20) != null) { /* drain */ }
        assertTrue(client.startProbe(300L).isSuccess)

        val deadline = System.currentTimeMillis() + 1_500L
        var size = -1
        while (size < 0 && System.currentTimeMillis() < deadline) {
            peer.nextText(200)?.let { (len, text) -> if (HandshakeWireFormat.isProbeRequest(text)) size = len }
        }
        assertTrue(size in 1..32, "a probe datagram was $size bytes")
    }

    @Test
    fun `LinkQualityTracker survives 100k interleaved beginProbe completeProbe and snapshot`() {
        val tracker = LinkQualityTracker(windowSize = 128)
        tracker.probeIntervalMillis = 1_000L
        val pending = ConcurrentLinkedQueue<Long>()
        val stop = AtomicBoolean(false)
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()

        val begin = Thread {
            try {
                repeat(100_000) { pending += tracker.beginProbe() }
            } catch (t: Throwable) {
                failure.set(t)
            }
        }
        val complete = Thread {
            try {
                while (!stop.get() || pending.isNotEmpty()) {
                    pending.poll()?.let { tracker.completeProbe(it) }
                }
            } catch (t: Throwable) {
                failure.set(t)
            }
        }
        val read = Thread {
            try {
                while (!stop.get()) {
                    tracker.snapshot()?.let { snap ->
                        assertTrue(snap.rttMillis.isFinite() && snap.rttMillis >= 0.0, "rtt ${snap.rttMillis}")
                        assertTrue(snap.packetLossRatio in 0.0..1.0, "loss ${snap.packetLossRatio}")
                    }
                }
            } catch (t: Throwable) {
                failure.set(t)
            }
        }

        listOf(begin, complete, read).forEach { it.start() }
        begin.join()
        stop.set(true)
        complete.join()
        read.join()

        failure.get()?.let { throw AssertionError("a worker thread failed", it) }
        val snap = tracker.snapshot()
        assertTrue(snap != null && snap.rttMillis.isFinite() && snap.rttMillis >= 0.0)
    }

    @Test
    fun `a scheduled PING racing stop never throws out of either call`() {
        repeat(20) {
            val peer = fakePeer()
            val client = newClient(peer.localPort)
            handshake(client, peer)
            client.startProbe(1L)
            Thread.sleep(260)
            assertTrue(client.stop().isSuccess)
        }
    }
}
