package com.spartanlabs.testing.component.webtools.udp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.spartanlabs.testing.support.webtools.udp.FakePeriodicSchedule
import com.spartanlabs.testing.support.webtools.udp.captureLogsOf
import com.spartanlabs.testing.support.webtools.udp.hasEventAt
import com.spartanlabs.testing.support.webtools.udp.hasWarnContaining
import com.spartanlabs.webtools.udp.DatagramType
import com.spartanlabs.webtools.udp.DeliveryMode
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import com.spartanlabs.webtools.udp.ReliableWireFormat
import com.spartanlabs.webtools.udp.TransportWireFormat
import com.spartanlabs.webtools.udp.UdpChannel
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Component set - the client listener's origin screen, branch by branch, over a real socket. `peer`
// is the socket the client targets (its server endpoint) and `foreign` a second loopback socket it
// does not target. Each test sends one datagram from `foreign`, waits for the listener's own
// unexpected-origin DEBUG line, asserts that nothing else happened, then sends the same kind of
// datagram from `peer` and asserts that it is handled as before.
@Tag("component")
class MultiConnectionUDPClientOriginScreenTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    private fun fakePeer(): DatagramSocket = DatagramSocket().also { opened += it }

    private fun newClient(
        peerPort: Int,
        probe: FakePeriodicSchedule = FakePeriodicSchedule(),
        retransmit: FakePeriodicSchedule = FakePeriodicSchedule(),
    ): MultiConnectionUDPClient =
        MultiConnectionUDPClient(
            loopback,
            peerPort,
            MultiConnectionUDPServer.DEFAULT_RECEIVE_BUFFER_BYTES,
            FakePeriodicSchedule(),
            probe,
            retransmit,
            UdpChannel.DEFAULT_MAX_RELIABLE_MESSAGE_BYTES,
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

    private fun DatagramSocket.sendTo(client: MultiConnectionUDPClient, bytes: ByteArray) {
        send(DatagramPacket(bytes, bytes.size, loopback, client.localPort))
    }

    private fun List<ILoggingEvent>.originDrops(): Int =
        count { it.level == Level.DEBUG && it.formattedMessage.contains(ORIGIN_DROP) }

    /** Polls until the listener has logged [expected] origin drops, or the 2 s deadline passes. */
    private fun List<ILoggingEvent>.awaitOriginDrops(expected: Int) {
        val deadline = System.currentTimeMillis() + 2_000
        while (originDrops() != expected && System.currentTimeMillis() < deadline) Thread.sleep(20)
    }

    private fun List<ILoggingEvent>.assertOriginDrops(expected: Int) =
        assertEquals(expected, originDrops(), "the listener must log one unexpected-origin drop per foreign datagram")

    private fun awaitTrue(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 2_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
    }

    @Test
    fun `an off-origin 0x80 keepalive is dropped at DEBUG naming both origins - the server's keepalive still reaches the keepalive drop`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        assertTrue(client.channel(DeliveryMode.UNRELIABLE).actuateBytes { }.isSuccess)
        val foreign = fakePeer()

        captureLogsOf(MultiConnectionUDPClient::class.java, Level.TRACE) { events ->
            foreign.sendTo(client, TransportWireFormat.keepaliveDatagram())
            events.awaitOriginDrops(1)

            events.assertOriginDrops(1)
            val drop = events.single { it.level == Level.DEBUG && it.formattedMessage.contains(ORIGIN_DROP) }
            assertTrue(
                drop.formattedMessage.contains(":" + foreign.localPort) &&
                    drop.formattedMessage.contains(":" + peer.localPort),
                "the drop must name both the actual sender and the server endpoint, was: ${drop.formattedMessage}",
            )
            assertTrue(events.none { it.formattedMessage.contains("Dropped keepalive from server") })

            peer.sendTo(client, TransportWireFormat.keepaliveDatagram())
            awaitTrue { events.hasEventAt(Level.TRACE, "Dropped keepalive from server") }
            assertTrue(events.hasEventAt(Level.TRACE, "Dropped keepalive from server"))
        }
    }

    @Test
    fun `an off-origin 0x81 PROBE_PING draws no 0x82 reply - the server's PING is still answered`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        assertTrue(client.channel(DeliveryMode.UNRELIABLE).actuateBytes { }.isSuccess)
        val foreign = fakePeer()

        captureLogsOf(MultiConnectionUDPClient::class.java, Level.DEBUG) { events ->
            foreign.sendTo(client, TransportWireFormat.probePingDatagram(7))
            events.awaitOriginDrops(1)

            assertNull(peer.nextBytes(300), "a stranger's PING must draw no PONG to the server")
            events.assertOriginDrops(1)

            peer.sendTo(client, TransportWireFormat.probePingDatagram(8))
            assertContentEquals(TransportWireFormat.probePongDatagram(8), peer.nextBytes(2_000))
        }
    }

    @Test
    fun `an off-origin 0x82 PROBE_PONG completes no probe - the server's PONG still does`() {
        val peer = fakePeer()
        val probe = FakePeriodicSchedule()
        val client = newClient(peer.localPort, probe = probe)
        assertTrue(client.channel(DeliveryMode.UNRELIABLE).actuateBytes { }.isSuccess)
        assertTrue(client.startProbe(5_000L).isSuccess)
        probe.tick(InetSocketAddress(loopback, peer.localPort))
        val ping = assertNotNull(peer.nextBytes(2_000), "the probe tick must put a PING on the wire")
        val seq = assertNotNull(TransportWireFormat.probeSequenceOf(ping))
        val foreign = fakePeer()

        captureLogsOf(MultiConnectionUDPClient::class.java, Level.DEBUG) { events ->
            foreign.sendTo(client, TransportWireFormat.probePongDatagram(seq))
            events.awaitOriginDrops(1)

            assertNull(client.linkQuality(), "a stranger's PONG must complete no probe")
            events.assertOriginDrops(1)

            peer.sendTo(client, TransportWireFormat.probePongDatagram(seq))
            awaitTrue { client.linkQuality() != null }
            assertTrue(client.linkQuality() != null, "the server's PONG must complete the probe")
        }
    }

    @Test
    fun `an off-origin 0x90 is not delivered - the server's 0x90 still is`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val received = CopyOnWriteArrayList<ByteArray>()
        assertTrue(client.channel(DeliveryMode.UNRELIABLE).actuateBytes { received += it }.isSuccess)
        val foreign = fakePeer()

        captureLogsOf(MultiConnectionUDPClient::class.java, Level.DEBUG) { events ->
            foreign.sendTo(client, TransportWireFormat.unreliableDatagram(byteArrayOf(1)))
            events.awaitOriginDrops(1)

            assertTrue(received.isEmpty(), "a stranger's 0x90 must not be delivered, was ${received.map { it.toList() }}")
            events.assertOriginDrops(1)

            peer.sendTo(client, TransportWireFormat.unreliableDatagram(byteArrayOf(2)))
            awaitTrue { received.isNotEmpty() }
        }
        assertEquals(listOf(listOf<Byte>(2)), received.map { it.toList() })
    }

    @Test
    fun `an off-origin 0xA1 mints no reliable engine - the server's 0xA1 still does`() {
        val peer = fakePeer()
        val retransmit = FakePeriodicSchedule()
        val client = newClient(peer.localPort, retransmit = retransmit)
        assertTrue(client.channel(DeliveryMode.RELIABLE_ORDERED).actuateBytes { }.isSuccess)
        val foreign = fakePeer()

        captureLogsOf(MultiConnectionUDPClient::class.java, Level.DEBUG) { events ->
            foreign.sendTo(client, ReliableWireFormat.reliableAckDatagram(ack = 0, ackBitfield = 0))
            events.awaitOriginDrops(1)

            assertTrue(retransmit.scheduleCalls.isEmpty(), "a stranger's 0xA1 must not mint the engine")
            events.assertOriginDrops(1)

            peer.sendTo(client, ReliableWireFormat.reliableAckDatagram(ack = 0, ackBitfield = 0))
            awaitTrue { retransmit.scheduleCalls.isNotEmpty() }
            assertTrue(retransmit.scheduleCalls.isNotEmpty(), "the server's 0xA1 must mint the engine")
        }
    }

    @Test
    fun `an off-origin unframed datagram is dropped at DEBUG without the unframed WARN - the server's still draws it`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        assertTrue(client.channel(DeliveryMode.UNRELIABLE).actuateBytes { }.isSuccess)
        val foreign = fakePeer()

        captureLogsOf(MultiConnectionUDPClient::class.java, Level.DEBUG) { events ->
            foreign.sendTo(client, "REGISTERED 2".toByteArray())
            events.awaitOriginDrops(1)

            assertTrue(
                events.none { it.level.isGreaterOrEqual(Level.WARN) },
                "a stranger's datagram must draw no WARN, was ${events.filter { it.level.isGreaterOrEqual(Level.WARN) }.map { it.formattedMessage }}",
            )
            events.assertOriginDrops(1)

            peer.sendTo(client, "REGISTERED 2".toByteArray())
            awaitTrue { events.hasWarnContaining("Unframed/unknown datagram from server") }
            assertTrue(events.hasWarnContaining("Unframed/unknown datagram from server"))
        }
    }

    @Test
    fun `an off-origin reserved-tag datagram is dropped at the origin screen without the unhandled-type line - the server's still draws it`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        assertTrue(client.channel(DeliveryMode.UNRELIABLE).actuateBytes { }.isSuccess)
        val foreign = fakePeer()
        val reserved = byteArrayOf(0xA2.toByte())

        captureLogsOf(MultiConnectionUDPClient::class.java, Level.DEBUG) { events ->
            foreign.sendTo(client, reserved)
            events.awaitOriginDrops(1)

            assertTrue(events.none { it.formattedMessage.contains("Unhandled datagram type") })
            events.assertOriginDrops(1)

            peer.sendTo(client, reserved)
            awaitTrue { events.hasEventAt(Level.DEBUG, "Unhandled datagram type 0xa2 from server") }
            assertTrue(events.hasEventAt(Level.DEBUG, "Unhandled datagram type 0xa2 from server"))
        }
    }

    @Test
    fun `an off-origin malformed 0x81 draws no malformed-probe WARN - the server's still draws it`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        assertTrue(client.channel(DeliveryMode.UNRELIABLE).actuateBytes { }.isSuccess)
        val foreign = fakePeer()
        val malformed = byteArrayOf(DatagramType.PROBE_PING.tag, 1, 2)

        captureLogsOf(MultiConnectionUDPClient::class.java, Level.DEBUG) { events ->
            foreign.sendTo(client, malformed)
            events.awaitOriginDrops(1)

            assertTrue(events.none { it.formattedMessage.contains("Malformed 0x81") })
            events.assertOriginDrops(1)

            peer.sendTo(client, malformed)
            awaitTrue { events.hasWarnContaining("Malformed 0x81 probe from server") }
            assertTrue(events.hasWarnContaining("Malformed 0x81 probe from server"))
        }
    }

    @Test
    fun `an off-origin malformed 0x90 draws no malformed-datagram WARN - the server's still draws it`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val received = CopyOnWriteArrayList<ByteArray>()
        assertTrue(client.channel(DeliveryMode.UNRELIABLE).actuateBytes { received += it }.isSuccess)
        val foreign = fakePeer()
        val malformed = byteArrayOf(DatagramType.UNRELIABLE.tag)

        captureLogsOf(MultiConnectionUDPClient::class.java, Level.DEBUG) { events ->
            foreign.sendTo(client, malformed)
            events.awaitOriginDrops(1)

            assertTrue(events.none { it.formattedMessage.contains("Malformed 0x90") })
            events.assertOriginDrops(1)

            peer.sendTo(client, malformed)
            awaitTrue { events.hasWarnContaining("Malformed 0x90 datagram from server") }
            assertTrue(events.hasWarnContaining("Malformed 0x90 datagram from server"))
        }
        assertTrue(received.isEmpty(), "a malformed 0x90 is never delivered")
    }

    @Test
    fun `an off-origin empty datagram draws no WARN - the server's still draws the unframed WARN`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        assertTrue(client.channel(DeliveryMode.UNRELIABLE).actuateBytes { }.isSuccess)
        val foreign = fakePeer()

        captureLogsOf(MultiConnectionUDPClient::class.java, Level.DEBUG) { events ->
            foreign.sendTo(client, ByteArray(0))
            events.awaitOriginDrops(1)

            assertTrue(
                events.none { it.level.isGreaterOrEqual(Level.WARN) },
                "a stranger's empty datagram must draw no WARN, was ${events.filter { it.level.isGreaterOrEqual(Level.WARN) }.map { it.formattedMessage }}",
            )
            events.assertOriginDrops(1)

            peer.sendTo(client, ByteArray(0))
            awaitTrue { events.hasWarnContaining("Unframed/unknown datagram from server (byte0=null)") }
            assertTrue(events.hasWarnContaining("Unframed/unknown datagram from server (byte0=null)"))
        }
    }

    private companion object {
        const val ORIGIN_DROP = "Datagram from unexpected origin"
    }
}
