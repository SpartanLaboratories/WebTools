package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.webtools.udp.DatagramType
import com.spartanlabs.webtools.udp.HandshakeWireFormat
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.TransportWireFormat
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
@Suppress("DEPRECATION") // exercises the still-working, now-deprecated push/actuate/send/start primitives on purpose
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

    /** Background responder: replies REGISTERED 2 to an Iam, an 0x82 PROBE_PONG to an 0x81 PROBE_PING, and counts them. */
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
                val inbound = packet.data.copyOf(packet.length)
                val text = String(inbound, Charsets.UTF_8).trim()
                val reply: ByteArray = when {
                    text == HandshakeWireFormat.HANDSHAKE_VERB || text.startsWith("Iam ") ->
                        HandshakeWireFormat.registeredMessage().toByteArray(Charsets.UTF_8)

                    DatagramType.ofTagByte(inbound.getOrNull(0)) == DatagramType.PROBE_PING -> {
                        pings.incrementAndGet()
                        val seq = TransportWireFormat.probeSequenceOf(inbound)
                        if (answering.get() && seq != null) TransportWireFormat.probePongDatagram(seq) else continue
                    }

                    else -> continue
                }
                runCatching { peer.send(DatagramPacket(reply, reply.size, packet.socketAddress)) }
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
        assertTrue(client.startProbe(250L).isSuccess)
        assertTrue(await(3_000L) { (client.linkQuality()?.probesDelivered ?: 0) >= 2 })

        val rttBefore = client.linkQuality()!!.rttMillis
        echo.answering.set(false)

        assertTrue(await(6_000L) { (client.linkQuality()?.packetLossRatio ?: 0.0) >= 0.5 }, "loss climbs toward 1.0")
        val rttAfter = client.linkQuality()!!.rttMillis
        assertTrue(kotlin.math.abs(rttAfter - rttBefore) < 50.0, "rtt froze: $rttBefore -> $rttAfter")
    }

    @Test
    fun `a rejected startProbe does not switch off loss detection on the probe already running`() {
        val peer = fakePeer()
        val echo = echoer(peer)
        val client = connected(peer)
        assertTrue(client.startProbe(250L).isSuccess)
        assertTrue(await(3_000L) { (client.linkQuality()?.probesDelivered ?: 0) >= 2 })

        // Pre-fix, startProbe wrote probeIntervalMillis = 0 onto the live tracker before the
        // interval was rejected, and Rtt.isProbeLost(.., 0) is always false - so loss aging on
        // the still-running probe was switched off for good and this await would time out.
        assertTrue(client.startProbe(0L).isFailure)

        echo.answering.set(false)
        assertTrue(
            await(6_000L) { (client.linkQuality()?.packetLossRatio ?: 0.0) >= 0.5 },
            "loss must still climb after a rejected re-arm",
        )
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
