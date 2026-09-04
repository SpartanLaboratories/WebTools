package com.spartanlabs.testing.gating.webtools

import com.spartanlabs.testing.support.webtools.FakeConnection
import com.spartanlabs.webtools.HandshakeCoordinator
import com.spartanlabs.webtools.Registration
import com.spartanlabs.webtools.Registrations
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

// Level 1 - a fast pre-commit gate over the two new stateful units (the handshake state
// machine and its registration store). Socket-free, sub-millisecond. The full behaviour
// matrix is at Level 2 (HandshakeCoordinatorTest, RegistrationsTest).
@Tag("gating")
class HandshakeCoordinatorGatingTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val origin = InetSocketAddress(loopback, 40001)

    private fun coordinator(replies: MutableList<InetSocketAddress>) = HandshakeCoordinator(
        newConnection = { name, _, ports -> FakeConnection(name, ports.sendPort, ports.receivePort) },
        reply = { _, target -> replies += target; Result.success(Unit) },
        onRegistered = {},
    )

    @Test
    fun `a first Iam registers the client and sends one reply`() {
        val replies = mutableListOf<InetSocketAddress>()
        val coordinator = coordinator(replies)

        coordinator.handle(origin, listOf("Iam", "alice"))

        assertEquals(1, coordinator.size)
        assertEquals(listOf(origin), replies)
    }

    @Test
    fun `a retransmit from the same origin does not register a second connection`() {
        val coordinator = coordinator(mutableListOf())

        coordinator.handle(origin, listOf("Iam", "alice"))
        coordinator.handle(origin, listOf("Iam", "alice"))

        assertEquals(1, coordinator.size)
    }

    @Test
    fun `Registrations findByOrigin round-trips`() {
        val registrations = Registrations()
        val entry = Registration(FakeConnection("c", 1, 2), origin)
        registrations.add(entry)

        assertSame(entry, registrations.findByOrigin(InetSocketAddress(loopback, 40001)))
    }
}
