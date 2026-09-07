package com.spartanlabs.testing.e2e.webtools.udp

import com.spartanlabs.webtools.udp.Admission
import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.HandshakeRefusedException
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 4b - a real MultiConnectionUDPClient against a real MultiConnectionUDPServer subclass
// over loopback, proving the admit() refusal path surfaces as a typed HandshakeRefusedException
// on the client and that a refused newcomer never disturbs an established connection.
@Tag("e2e")
class MultiConnectionUDPHandshakeRefusalE2ETest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private class CredentialServer(
        private val decide: (name: String, credential: String, size: Int) -> Admission,
    ) : MultiConnectionUDPServer() {
        val connections = CopyOnWriteArrayList<Connection>()
        val byName = ConcurrentHashMap<String, Connection>()
        val inbound = ConcurrentHashMap<String, ConcurrentLinkedQueue<String>>()

        override fun admit(name: String, peer: InetSocketAddress, credential: String): Admission =
            decide(name, credential, connections.size)

        override fun onClientConnect(connection: Connection) {
            connections += connection
            byName[connection.name] = connection
            inbound.getOrPut(connection.name) { ConcurrentLinkedQueue() }
            connection.actuate { msg -> inbound.getValue(connection.name).add(msg) }
        }
    }

    private fun <T> await(queue: ConcurrentLinkedQueue<T>, value: T, timeoutMillis: Long = 5000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!queue.contains(value) && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue(queue.contains(value), "expected $value within ${timeoutMillis}ms, saw $queue")
    }

    @Test
    fun `a good credential connects and the session works`() {
        val server = CredentialServer { _, credential, _ ->
            if (credential == "good") Admission.Admitted else Admission.Refused("invalid credential")
        }
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(client.handshake("alice", credential = "good").isSuccess)
            Thread.sleep(SETTLE_MILLIS)
            assertEquals(1, server.connections.size)

            val clientInbound = ConcurrentLinkedQueue<String>()
            assertTrue(client.start { clientInbound += it }.isSuccess)

            assertTrue(client.send("ping").isSuccess)
            await(server.inbound.getValue("alice"), "ping")
            assertTrue(server.byName.getValue("alice").push("pong").isSuccess)
            await(clientInbound, "pong")
        } finally {
            client.stop()
            server.stop()
        }
    }

    @Test
    fun `a bad credential fails with a typed HandshakeRefusedException and registers nothing`() {
        val server = CredentialServer { _, credential, _ ->
            if (credential == "good") Admission.Admitted else Admission.Refused("invalid credential")
        }
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            val outcome = client.handshake("mallory", credential = "bad")
            assertTrue(outcome.isFailure)
            val cause = outcome.exceptionOrNull()
            assertIs<HandshakeRefusedException>(cause)
            assertEquals("invalid credential", cause.reason)
            Thread.sleep(SETTLE_MILLIS)
            assertTrue(server.connections.none { it.name == "mallory" })
        } finally {
            assertTrue(client.stop().isSuccess)
            server.stop()
        }
    }

    @Test
    fun `a capacity refusal fails the second client and leaves the first untouched`() {
        val server = CredentialServer { _, _, size ->
            if (size >= 1) Admission.Refused("server full") else Admission.Admitted
        }
        val first = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        val second = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(first.handshake("first").isSuccess)
            Thread.sleep(SETTLE_MILLIS)

            val firstInbound = ConcurrentLinkedQueue<String>()
            assertTrue(first.start { firstInbound += it }.isSuccess)

            val outcome = second.handshake("second")
            assertIs<HandshakeRefusedException>(outcome.exceptionOrNull())
            assertEquals("server full", (outcome.exceptionOrNull() as HandshakeRefusedException).reason)

            assertTrue(server.byName.getValue("first").push("still-here").isSuccess)
            await(firstInbound, "still-here")
        } finally {
            first.stop()
            second.stop()
            server.stop()
        }
    }

    @Test
    fun `against a default server a bare handshake still succeeds`() {
        val server = object : MultiConnectionUDPServer() {
            val connections = CopyOnWriteArrayList<Connection>()
            override fun onClientConnect(connection: Connection) { connections += connection }
        }
        val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(client.handshake("bob").isSuccess)
            Thread.sleep(SETTLE_MILLIS)
            assertEquals(1, server.connections.size)
        } finally {
            client.stop()
            server.stop()
        }
    }

    @Test
    fun `a refused newcomer under an existing name does not evict the incumbent`() {
        val server = CredentialServer { _, credential, _ ->
            if (credential == "good") Admission.Admitted else Admission.Refused("invalid credential")
        }
        val incumbent = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        val spoofer = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            assertTrue(incumbent.handshake("alice", credential = "good").isSuccess)
            Thread.sleep(SETTLE_MILLIS)
            val incumbentInbound = ConcurrentLinkedQueue<String>()
            assertTrue(incumbent.start { incumbentInbound += it }.isSuccess)

            assertIs<HandshakeRefusedException>(
                spoofer.handshake("alice", credential = "bad").exceptionOrNull(),
            )
            Thread.sleep(SETTLE_MILLIS)

            assertTrue(server.pushToAll("after-spoof").isSuccess)
            await(incumbentInbound, "after-spoof")
            assertTrue(server.byName.getValue("alice").push("direct").isSuccess)
            await(incumbentInbound, "direct")
        } finally {
            incumbent.stop()
            spoofer.stop()
            server.stop()
        }
    }

    private companion object {
        const val SETTLE_MILLIS = 200L
    }
}
