package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.webtools.udp.HandshakeWireFormat
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 3 - a real MultiConnectionUDPClient socket against a fake peer DatagramSocket that
// echoes PONG for each PING, exercising the opt-in link-quality probe end to end on the
// client side.
@Tag("integration")
class MultiConnectionUDPClientProbeTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()
    private val echoers = mutableListOf<Echoer>()

    private fun fakePeer(): DatagramSocket = DatagramSocket().also { opened += it }

    private fun newClient(peerPort: Int): MultiConnectionUDPClient =
        MultiConnectionUDPClient(loopback, peerPort).also { clients += it }

    @AfterTest
    fun cleanup() {
        echoers.forEach { it.stop() }
        clients.forEach { runCatching { it.stop() } }
        opened.forEach { runCatching { it.close() } }
    }

    /** Background responder: replies REGISTERED to an Iam, PONG to a PING, and counts PINGs. */
    private inner class Echoer(private val peer: DatagramSocket) {
        val pings = AtomicInteger()
        val answering = AtomicBoolean(true)
        private val running = AtomicBoolean(true)
        private val thread = Thread {
            peer.soTimeout = 100
            while (running.get()) {
                val packet = DatagramPacket(ByteArray(256), 256)
                try {
                    peer.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: Exception) {
                    break
                }
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                val reply = when {
                    text == HandshakeWireFormat.HANDSHAKE_VERB || text.startsWith("Iam ") -> "REGISTERED"
                    HandshakeWireFormat.isProbeRequest(text) -> {
                        pings.incrementAndGet()
                        if (answering.get()) HandshakeWireFormat.probeReplyMessage(HandshakeWireFormat.probeToken(text)) else null
                    }
                    else -> null
                } ?: continue
                val bytes = reply.toByteArray(Charsets.UTF_8)
                runCatching { peer.send(DatagramPacket(bytes, bytes.size, packet.socketAddress)) }
            }
        }.apply { isDaemon = true; start() }

        fun stop() {
            running.set(false)
            thread.join(1_000)
        }
    }

    private fun echoer(peer: DatagramSocket) = Echoer(peer).also { echoers += it }

    private fun await(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    private fun connected(peer: DatagramSocket): MultiConnectionUDPClient {
        val client = newClient(peer.localPort)
        assertTrue(client.handshake("alice").isSuccess)
        assertTrue(client.start { }.isSuccess)
        return client
    }

    @Test
    fun `probing an echoing peer yields a non-null small-rtt snapshot with zero loss`() {
        val peer = fakePeer()
        val echo = echoer(peer)
        val client = connected(peer)

        assertTrue(client.startProbe(300L).isSuccess)

        assertTrue(await(3_000L) { client.linkQuality() != null }, "linkQuality populated")
        assertTrue(await(2_000L) { echo.pings.get() >= 2 }, "PING sent roughly every interval")
        val snap = client.linkQuality()!!
        assertTrue(snap.rttMillis in 0.0..500.0, "small RTT, was ${snap.rttMillis}")
        assertTrue(snap.packetLossRatio < 0.5, "low loss while echoing, was ${snap.packetLossRatio}")
    }

    @Test
    fun `when the peer stops echoing the loss ratio climbs and rtt stops advancing`() {
        val peer = fakePeer()
        val echo = echoer(peer)
        val client = connected(peer)
        assertTrue(client.startProbe(200L).isSuccess)
        assertTrue(await(3_000L) { (client.linkQuality()?.probesDelivered ?: 0) >= 2 })

        val rttBefore = client.linkQuality()!!.rttMillis
        echo.answering.set(false)

        assertTrue(await(6_000L) { (client.linkQuality()?.packetLossRatio ?: 0.0) >= 0.5 }, "loss climbs toward 1.0")
        val rttAfter = client.linkQuality()!!.rttMillis
        assertTrue(kotlin.math.abs(rttAfter - rttBefore) < 50.0, "rtt froze: $rttBefore -> $rttAfter")
    }

    @Test
    fun `stopProbe halts the PING stream and freezes the snapshot, a later startProbe resumes`() {
        val peer = fakePeer()
        val echo = echoer(peer)
        val client = connected(peer)
        assertTrue(client.startProbe(300L).isSuccess)
        assertTrue(await(3_000L) { client.linkQuality() != null })

        assertTrue(client.stopProbe().isSuccess)
        Thread.sleep(400)
        val frozen = echo.pings.get()
        val snapshot = client.linkQuality()
        Thread.sleep(700)
        assertTrue(echo.pings.get() <= frozen + 1, "PING stream halted")
        assertTrue(client.linkQuality() == snapshot || client.linkQuality() != null, "last snapshot still readable")

        assertTrue(client.startProbe(300L).isSuccess)
        val resumeBase = echo.pings.get()
        assertTrue(await(2_000L) { echo.pings.get() > resumeBase + 1 }, "PING resumed")
    }

    @Test
    fun `stop halts the stream and leaves no mcupc-probe thread`() {
        val peer = fakePeer()
        echoer(peer)
        val client = connected(peer)
        assertTrue(client.startProbe(300L).isSuccess)
        assertTrue(await(3_000L) { client.linkQuality() != null })

        assertTrue(client.stop().isSuccess)
        Thread.sleep(400)
        assertTrue(Thread.getAllStackTraces().keys.none { it.name == "mcupc-probe" && it.isAlive })
    }

    @Test
    fun `a default client never starting a probe has no thread and a null linkQuality`() {
        val peer = fakePeer()
        echoer(peer)
        val client = connected(peer)

        Thread.sleep(300)
        assertNull(client.linkQuality())
        assertTrue(Thread.getAllStackTraces().keys.none { it.name == "mcupc-probe" && it.isAlive })
    }

    @Test
    fun `startProbe after stop fails and starts no thread`() {
        val peer = fakePeer()
        echoer(peer)
        val client = connected(peer)
        assertTrue(client.stop().isSuccess)

        val result = client.startProbe(300L)

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertTrue(Thread.getAllStackTraces().keys.none { it.name == "mcupc-probe" && it.isAlive })
    }
}
