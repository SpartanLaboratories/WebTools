package com.spartanlabs.testing.nonfunctional.webtools

import com.spartanlabs.testing.support.webtools.FakeConnection
import com.spartanlabs.webtools.HandshakeCoordinator
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals

// Level 4c - non-functional properties of the handshake, exercised against the coordinator
// (no socket): bounded resource use under a retransmit storm, and the security property that
// a reply is only ever addressed to the datagram's real source.
@Tag("nonfunctional")
class HandshakeNonFunctionalTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val origin = InetSocketAddress(loopback, 40001)

    private var connectionsCreated = 0
    private val replyTargets = mutableListOf<InetSocketAddress>()

    private fun newCoordinator() = HandshakeCoordinator(
        newConnection = { name, _, ports ->
            connectionsCreated++
            FakeConnection(name, ports.sendPort, ports.receivePort)
        },
        reply = { _, target ->
            replyTargets += target
            Result.success(Unit)
        },
        onRegistered = {},
    )

    @Test
    fun `a retransmit storm from one origin allocates exactly one connection`() {
        val coordinator = newCoordinator()

        repeat(STORM_SIZE) { coordinator.handle(origin, listOf("Iam", "stormclient")) }

        assertEquals(1, coordinator.size, "$STORM_SIZE retransmits must register one client")
        assertEquals(1, connectionsCreated, "$STORM_SIZE retransmits must mint one dedicated connection")
        assertEquals(STORM_SIZE, replyTargets.size, "every retransmit is still answered")
    }

    @Test
    fun `the reply is only ever addressed to the datagram origin, never a payload-claimed address`() {
        val coordinator = newCoordinator()

        coordinator.handle(origin, listOf("Iam", "spoofer", "8.8.8.8", "1.1.1.1"))
        coordinator.handle(origin, listOf("Iam", "spoofer", "203.0.113.9"))

        assertEquals(listOf(origin, origin), replyTargets, "a claimed address must never become a reply target")
    }

    private companion object {
        const val STORM_SIZE = 1_000
    }
}
