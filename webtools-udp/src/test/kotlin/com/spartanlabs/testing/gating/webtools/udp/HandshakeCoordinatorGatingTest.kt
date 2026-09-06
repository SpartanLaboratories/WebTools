package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakeConnection
import com.spartanlabs.webtools.udp.HandshakeCoordinator
import com.spartanlabs.webtools.udp.HandshakeProtocol
import com.spartanlabs.webtools.udp.Registration
import com.spartanlabs.webtools.udp.Registrations
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Level 1 - a fast pre-commit gate over the two stateful units (the handshake state machine
// / inbound router and its registration store). Socket-free, sub-millisecond. The full
// behaviour matrix is at Level 2 (HandshakeCoordinatorTest, RegistrationsTest).
@Tag("gating")
class HandshakeCoordinatorGatingTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val origin = InetSocketAddress(loopback, 40001)

    private fun coordinator(replies: MutableList<Pair<String, InetSocketAddress>>) = HandshakeCoordinator(
        newConnection = { name, peer, _ -> FakeConnection(name, peer) },
        sender = { bytes, to -> replies += String(bytes, Charsets.UTF_8) to to; Result.success(Unit) },
        onRegistered = {},
        dispatch = { it() },
    )

    @Test
    fun `a first Iam registers the client and sends one REGISTERED reply to the origin`() {
        val replies = mutableListOf<Pair<String, InetSocketAddress>>()
        val coordinator = coordinator(replies)

        coordinator.accept(origin, "Iam alice")

        assertEquals(1, coordinator.size)
        assertEquals(listOf("REGISTERED" to origin), replies)
    }

    @Test
    fun `a retransmit from the same origin does not register a second connection`() {
        val coordinator = coordinator(mutableListOf())

        coordinator.accept(origin, "Iam alice")
        coordinator.accept(origin, "Iam alice")

        assertEquals(1, coordinator.size)
    }

    @Test
    fun `a new Iam under an existing name from a different origin supersedes, not accumulates`() {
        val coordinator = coordinator(mutableListOf())
        val otherOrigin = InetSocketAddress(loopback, 40002)

        coordinator.accept(origin, "Iam alice")
        coordinator.accept(otherOrigin, "Iam alice")

        assertEquals(1, coordinator.size)
    }

    @Test
    fun `a KA from a registered origin is dropped with no dispatch`() {
        val replies = mutableListOf<Pair<String, InetSocketAddress>>()
        val coordinator = coordinator(replies)
        coordinator.accept(origin, "Iam alice")
        replies.clear()

        assertTrue(coordinator.accept(origin, HandshakeProtocol.KEEPALIVE_TOKEN).isSuccess)
        assertTrue(replies.isEmpty())
    }

    @Test
    fun `a non-Iam datagram from an unregistered origin is dropped`() {
        val coordinator = coordinator(mutableListOf())

        assertTrue(coordinator.accept(origin, "hello").isSuccess)
        assertEquals(0, coordinator.size)
    }

    @Test
    fun `a bound bytes handler receives the exact undecoded application bytes`() {
        val coordinator = coordinator(mutableListOf())
        coordinator.accept(origin, "Iam alice")
        val received = mutableListOf<ByteArray>()
        coordinator.bindBytes(origin, received::add)
        // leading 0x00, an embedded ASCII-whitespace byte, and a trailing 0x0A - all of
        // which String(..).trim() would reshape on the text path.
        val payload = byteArrayOf(0x00, 0x20, 0x4B, 0x0A)

        coordinator.accept(origin, payload, String(payload, Charsets.UTF_8).trim())

        assertContentEquals(payload, received.single())
    }

    @Test
    fun `a payload trimming to KA is still dropped when a bytes handler is bound`() {
        val coordinator = coordinator(mutableListOf())
        coordinator.accept(origin, "Iam alice")
        val received = mutableListOf<ByteArray>()
        coordinator.bindBytes(origin, received::add)
        val payload = " KA ".toByteArray(Charsets.UTF_8)

        assertTrue(coordinator.accept(origin, payload, String(payload, Charsets.UTF_8).trim()).isSuccess)

        assertTrue(received.isEmpty())
    }

    @Test
    fun `Registrations findByOrigin round-trips`() {
        val registrations = Registrations()
        val entry = Registration(FakeConnection("c", origin))
        registrations.add(entry)

        assertSame(entry, registrations.findByOrigin(InetSocketAddress(loopback, 40001)))
    }
}
