package com.spartanlabs.testing.component.webtools.udp

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.udp.FakeConnection
import com.spartanlabs.testing.support.webtools.udp.FakePeriodicSchedule
import com.spartanlabs.testing.support.webtools.udp.captureLogsOf
import com.spartanlabs.testing.support.webtools.udp.hasEventAt
import com.spartanlabs.testing.support.webtools.udp.hasWarnContaining
import com.spartanlabs.webtools.udp.Admission
import com.spartanlabs.webtools.udp.HandshakeCoordinator
import com.spartanlabs.webtools.udp.TransportWireFormat
import com.spartanlabs.webtools.udp.UdpChannel
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

// Level 2 - not pure: exercises HandshakeCoordinator.classify end to end (the Issue #8 fix).
// A 0x90 payload of any bytes reaches the bound handler verbatim - including a payload that
// would have collided with the old text classifier - and a malformed/reserved/unframed
// datagram is dropped with the right WARN, never delivered.
@Tag("component")
class FramingComponentTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val origin = InetSocketAddress(loopback, 42001)

    private fun newCoordinator() = HandshakeCoordinator(
        newConnection = { name, peer, _ -> FakeConnection(name, peer) },
        sender = { _, _ -> Result.success(Unit) },
        onRegistered = {},
        admit = { _, _, _ -> Admission.Admitted },
        dispatch = { it() },
        onDisconnect = { _, _ -> },
        idleTimeoutMillis = 0L,
        keepAliveSchedule = FakePeriodicSchedule(),
        probeSchedule = FakePeriodicSchedule(),
        retransmitSchedule = FakePeriodicSchedule(),
        reliableMaxMessageBytes = UdpChannel.DEFAULT_MAX_RELIABLE_MESSAGE_BYTES,
    )

    @Test
    fun `a 0x90 payload that trims to KA reaches the bytes handler verbatim - the Issue 8 fix`() {
        val coordinator = newCoordinator()
        coordinator.accept(origin, "Iam alice")
        val received = mutableListOf<ByteArray>()
        coordinator.bindBytes(origin, received::add)
        val payload = " KA ".toByteArray(Charsets.UTF_8)
        val framed = TransportWireFormat.unreliableDatagram(payload)

        assertTrue(coordinator.accept(origin, framed, String(framed, Charsets.UTF_8).trim()).isSuccess)

        assertContentEquals(payload, received.single())
    }

    @Test
    fun `a 0x90 payload leading with 0x00 reaches the bytes handler verbatim`() {
        val coordinator = newCoordinator()
        coordinator.accept(origin, "Iam alice")
        val received = mutableListOf<ByteArray>()
        coordinator.bindBytes(origin, received::add)
        val payload = byteArrayOf(0x00, 1, 2, 3)
        val framed = TransportWireFormat.unreliableDatagram(payload)

        assertTrue(coordinator.accept(origin, framed, String(framed, Charsets.UTF_8).trim()).isSuccess)

        assertContentEquals(payload, received.single())
    }

    @Test
    fun `a 0x90 payload leading with 0x81 reaches the bytes handler verbatim - not classified as a probe`() {
        val coordinator = newCoordinator()
        coordinator.accept(origin, "Iam alice")
        val received = mutableListOf<ByteArray>()
        coordinator.bindBytes(origin, received::add)
        val payload = byteArrayOf(0x81.toByte(), 9, 9)
        val framed = TransportWireFormat.unreliableDatagram(payload)

        assertTrue(coordinator.accept(origin, framed, String(framed, Charsets.UTF_8).trim()).isSuccess)

        assertContentEquals(payload, received.single())
    }

    @Test
    fun `a 1-byte 0x90 datagram is dropped with a WARN`() {
        val coordinator = newCoordinator()
        coordinator.accept(origin, "Iam alice")
        val received = mutableListOf<ByteArray>()
        coordinator.bindBytes(origin, received::add)
        val malformed = byteArrayOf(0x90.toByte())

        captureLogsOf(HandshakeCoordinator::class.java) { events ->
            assertTrue(coordinator.accept(origin, malformed, "").isSuccess)
            assertTrue(events.hasWarnContaining("Malformed 0x90"))
        }
        assertTrue(received.isEmpty())
    }

    @Test
    fun `a reserved 0xA2 inbound is dropped with a WARN`() {
        // 0xA0/0xA1 went live as RELIABLE_DATA/RELIABLE_ACK in Stage 3 - 0xA2 is the first tag
        // still reserved (design doc's future reliable-channel-control range, 0xA2-0xAF).
        val coordinator = newCoordinator()
        coordinator.accept(origin, "Iam alice")
        val received = mutableListOf<ByteArray>()
        coordinator.bindBytes(origin, received::add)
        val reserved = byteArrayOf(0xA2.toByte(), 1, 2)

        captureLogsOf(HandshakeCoordinator::class.java) { events ->
            assertTrue(coordinator.accept(origin, reserved, "").isSuccess)
            assertTrue(events.hasWarnContaining("Unhandled datagram type"))
        }
        assertTrue(received.isEmpty())
    }

    @Test
    fun `a channel-0x01 0x90 from a registered origin is dropped with the unsupported-channel WARN, never delivered`() {
        val coordinator = newCoordinator()
        coordinator.accept(origin, "Iam alice")
        val receivedBytes = mutableListOf<ByteArray>()
        coordinator.bindBytes(origin, receivedBytes::add)
        val framed = TransportWireFormat.unreliableDatagram(byteArrayOf(1), channel = 0x01)

        captureLogsOf(HandshakeCoordinator::class.java) { events ->
            assertTrue(coordinator.accept(origin, framed, String(framed, Charsets.UTF_8).trim()).isSuccess)
            assertTrue(events.hasWarnContaining("Unreliable datagram on unsupported channel 0x1 from"))
        }
        assertTrue(receivedBytes.isEmpty(), "must not reach the bound bytes handler")

        val receivedText = mutableListOf<String>()
        coordinator.bind(origin, receivedText::add) // rebinds - bind() nulls the bytes handler
        val secondFramed = TransportWireFormat.unreliableDatagram(byteArrayOf(2), channel = 0x01)
        assertTrue(coordinator.accept(origin, secondFramed, String(secondFramed, Charsets.UTF_8).trim()).isSuccess)
        assertTrue(receivedText.isEmpty(), "must not reach the bound text handler either")
    }

    @Test
    fun `a channel-0xFF 0x90 logs the masked hex byte, never the sign-extended int`() {
        val coordinator = newCoordinator()
        coordinator.accept(origin, "Iam alice")
        val framed = TransportWireFormat.unreliableDatagram(byteArrayOf(1), channel = 0xFF.toByte())

        captureLogsOf(HandshakeCoordinator::class.java) { events ->
            assertTrue(coordinator.accept(origin, framed, "").isSuccess)
            assertTrue(events.hasWarnContaining("on unsupported channel 0xff from"))
            assertTrue(events.none { it.formattedMessage.contains("ffffffff") }, "must mask to a single byte, not sign-extend")
        }
    }

    @Test
    fun `a channel-0x01 0x90 from an unregistered origin keeps the DEBUG drop - origin screening wins`() {
        val coordinator = newCoordinator()
        val framed = TransportWireFormat.unreliableDatagram(byteArrayOf(1), channel = 0x01)

        captureLogsOf(HandshakeCoordinator::class.java, Level.DEBUG) { events ->
            assertTrue(coordinator.accept(origin, framed, "").isSuccess)
            assertTrue(events.none { it.formattedMessage.contains("on unsupported channel") }, "origin screening must run first")
            assertTrue(events.hasEventAt(Level.DEBUG, "Dropped datagram from unregistered"))
        }
    }

    @Test
    fun `a channel-0x00 frame after a dropped channel-0x01 frame is still delivered`() {
        val coordinator = newCoordinator()
        coordinator.accept(origin, "Iam alice")
        val received = mutableListOf<ByteArray>()
        coordinator.bindBytes(origin, received::add)

        val dropped = TransportWireFormat.unreliableDatagram(byteArrayOf(1), channel = 0x01)
        assertTrue(coordinator.accept(origin, dropped, "").isSuccess)
        val delivered = TransportWireFormat.unreliableDatagram(byteArrayOf(2))
        assertTrue(coordinator.accept(origin, delivered, "").isSuccess)

        assertContentEquals(byteArrayOf(2), received.single())
    }

    @Test
    fun `an unframed hello from a registered origin is dropped with the sender-may-be-pre-2 0 WARN`() {
        val coordinator = newCoordinator()
        coordinator.accept(origin, "Iam alice")
        val received = mutableListOf<String>()
        coordinator.bind(origin, received::add)

        captureLogsOf(HandshakeCoordinator::class.java) { events ->
            assertTrue(coordinator.accept(origin, "hello").isSuccess)
            assertTrue(events.hasWarnContaining("sender may be pre-2.0"))
        }
        assertTrue(received.isEmpty())
    }
}
