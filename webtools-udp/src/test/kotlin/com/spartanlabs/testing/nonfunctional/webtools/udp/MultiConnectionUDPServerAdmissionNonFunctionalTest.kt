package com.spartanlabs.testing.nonfunctional.webtools.udp

import com.spartanlabs.webtools.udp.Admission
import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.DisconnectReason
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import com.spartanlabs.webtools.udp.TransportWireFormat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 4c - robustness / security / timing properties of the admit() screening hook over a
// real socket: the credential arrives verbatim, a throwing admit never wedges the listener,
// a refusal burst leaks no state, a malformed-handshake burst - from strangers and from a
// registered socket - draws exactly one refusal per datagram and never reaches admit, a slow
// admit serialises handshakes (documented cost), and a multi-line refusal reason is
// collapsed to one wire line.
@Tag("nonfunctional")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiConnectionUDPServerAdmissionNonFunctionalTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private fun withServer(server: MultiConnectionUDPServer, block: () -> Unit) {
        try {
            block()
        } finally {
            runCatching { server.stop() }
        }
    }

    private fun DatagramSocket.send(payload: String) {
        val out = payload.toByteArray(Charsets.UTF_8)
        send(DatagramPacket(out, out.size, loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
    }

    private fun DatagramSocket.recv(timeoutMillis: Int = 5000): String {
        soTimeout = timeoutMillis
        val p = DatagramPacket(ByteArray(4096), 4096)
        receive(p)
        return String(p.data, 0, p.length, Charsets.UTF_8).trim()
    }

    @Test
    fun `a 1 KiB base64url credential arrives byte-identical at admit`() {
        val seen = ConcurrentLinkedQueue<String>()
        val server = object : MultiConnectionUDPServer() {
            override fun admit(name: String, peer: InetSocketAddress, credential: String): Admission {
                seen += credential
                return Admission.Admitted
            }
            override fun onClientConnect(connection: Connection) {}
        }
        val raw = ByteArray(1024).also { Random(1).nextBytes(it) }
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        withServer(server) {
            DatagramSocket().use { client ->
                client.send("Iam bigcred $token")
                assertEquals("REGISTERED 2", client.recv())
            }
        }
        assertEquals(token, seen.single())
    }

    @Test
    fun `a hundred throwing admits never kill the listener thread`() {
        var calls = 0
        val connected = CopyOnWriteArrayList<String>()
        val server = object : MultiConnectionUDPServer() {
            override fun admit(name: String, peer: InetSocketAddress, credential: String): Admission {
                calls++
                if (name.startsWith("boom")) error("admit blew up")
                return Admission.Admitted
            }
            override fun onClientConnect(connection: Connection) { connected += connection.name }
        }
        withServer(server) {
            repeat(100) { i ->
                DatagramSocket().use { client ->
                    client.send("Iam boom$i")
                    client.soTimeout = 200
                    runCatching { client.receive(DatagramPacket(ByteArray(64), 64)) }
                }
            }
            DatagramSocket().use { client ->
                client.send("Iam survivor")
                assertEquals("REGISTERED 2", client.recv())
            }
            Thread.sleep(200)
        }
        assertTrue(calls >= 101)
        assertEquals(listOf("survivor"), connected.toList())
    }

    @Test
    fun `a burst of refusals leaves no registrations and the server still accepts a good client`() {
        val connected = CopyOnWriteArrayList<String>()
        val server = object : MultiConnectionUDPServer() {
            override fun admit(name: String, peer: InetSocketAddress, credential: String): Admission =
                if (name == "good") Admission.Admitted else Admission.Refused("no")
            override fun onClientConnect(connection: Connection) { connected += connection.name }
        }
        withServer(server) {
            repeat(500) { i ->
                DatagramSocket().use { client ->
                    client.send("Iam junk$i")
                    runCatching { client.recv(300) }
                }
            }
            DatagramSocket().use { client ->
                client.send("Iam good")
                assertEquals("REGISTERED 2", client.recv())
            }
            Thread.sleep(200)
        }
        assertEquals(listOf("good"), connected.toList())
    }

    @Test
    fun `a burst of malformed handshakes is refused, never reaches admit, and the listener survives`() {
        val admitted = ConcurrentLinkedQueue<String>()
        val connected = CopyOnWriteArrayList<String>()
        val server = object : MultiConnectionUDPServer() {
            override fun admit(name: String, peer: InetSocketAddress, credential: String): Admission {
                admitted += name
                return Admission.Admitted
            }
            override fun onClientConnect(connection: Connection) { connected += connection.name }
        }
        val malformedLines = listOf("Iam", "Iam  x", "Iam bob  tok")
        withServer(server) {
            repeat(MALFORMED_BURST) { i ->
                DatagramSocket().use { client ->
                    client.send(malformedLines[i % malformedLines.size])
                    assertEquals("REFUSED malformed", client.recv(2000))
                }
            }
            DatagramSocket().use { client ->
                client.send("Iam good")
                assertEquals("REGISTERED 2", client.recv())
            }
            // onClientConnect runs after the REGISTERED 2 send; give it time to land before asserting.
            Thread.sleep(CONNECT_SETTLE_MILLIS)
        }
        assertEquals(listOf("good"), admitted.toList(), "only the well-formed line reached admit")
        assertEquals(listOf("good"), connected.toList())
    }

    @Test
    fun `a malformed burst that includes a registered socket draws one refusal per datagram and the peer stays registered and reachable`() {
        val admitted = ConcurrentLinkedQueue<String>()
        val connected = CopyOnWriteArrayList<Connection>()
        val disconnects = CopyOnWriteArrayList<DisconnectReason>()
        val server = object : MultiConnectionUDPServer() {
            override fun admit(name: String, peer: InetSocketAddress, credential: String): Admission {
                admitted += name
                return Admission.Admitted
            }
            override fun onClientConnect(connection: Connection) { connected += connection }
            override fun onClientDisconnect(connection: Connection, reason: DisconnectReason) { disconnects += reason }
        }
        val malformedLines = listOf("Iam", "Iam  x", "Iam bob  tok")
        withServer(server) {
            DatagramSocket().use { peer ->
                DatagramSocket().use { stranger ->
                    peer.send("Iam peer")
                    assertEquals("REGISTERED 2", peer.recv())
                    // onClientConnect runs after the REGISTERED 2 send; give it time to land before asserting.
                    Thread.sleep(CONNECT_SETTLE_MILLIS)

                    repeat(MALFORMED_BURST_WITH_PEER) { i ->
                        val line = malformedLines[i % malformedLines.size]
                        val sender = if (i % 2 == 0) peer else stranger
                        sender.send(line)
                        assertEquals("REFUSED malformed", sender.recv(2000), line)
                    }
                    // One reply per datagram: after the burst nothing further is queued on either socket.
                    assertNull(runCatching { peer.recv(300) }.getOrNull(), "no extra reply to the registered peer")
                    assertNull(runCatching { stranger.recv(300) }.getOrNull(), "no extra reply to the stranger")

                    assertEquals(listOf("peer"), admitted.toList(), "only the registering line reached admit")
                    assertEquals(1, connected.size, "the peer is still the only registration")
                    assertTrue(disconnects.isEmpty(), "no onClientDisconnect fired before stop")
                    // pushToAll addresses only peers still in the registry, so receiving it proves the
                    // registration survived the burst.
                    assertTrue(server.pushToAll("still-here").isSuccess)
                    peer.soTimeout = 5000
                    val packet = DatagramPacket(ByteArray(4096), 4096).also { peer.receive(it) }
                    val payload = TransportWireFormat.unreliablePayloadOf(packet.data.copyOf(packet.length))
                    assertEquals("still-here", payload?.toString(Charsets.UTF_8))
                }
            }
        }
    }

    @Test
    fun `a slow admit serialises handshakes`() {
        val server = object : MultiConnectionUDPServer() {
            override fun admit(name: String, peer: InetSocketAddress, credential: String): Admission {
                Thread.sleep(ADMIT_DELAY_MILLIS)
                return Admission.Admitted
            }
            override fun onClientConnect(connection: Connection) {}
        }
        withServer(server) {
            val started = System.nanoTime()
            repeat(HANDSHAKES) { i ->
                DatagramSocket().use { client ->
                    client.send("Iam slow$i")
                    assertEquals("REGISTERED 2", client.recv())
                }
            }
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000
            assertTrue(
                elapsedMillis >= (HANDSHAKES - 1) * ADMIT_DELAY_MILLIS,
                "expected >= ${(HANDSHAKES - 1) * ADMIT_DELAY_MILLIS} ms of serialised admits, took $elapsedMillis ms",
            )
        }
    }

    @Test
    fun `a multi-line refusal reason is collapsed to one wire line`() {
        val server = object : MultiConnectionUDPServer() {
            override fun admit(name: String, peer: InetSocketAddress, credential: String): Admission =
                Admission.Refused("line1\nline2  end")
            override fun onClientConnect(connection: Connection) {}
        }
        withServer(server) {
            DatagramSocket().use { client ->
                client.send("Iam anyone")
                assertEquals("REFUSED line1 line2 end", client.recv())
                client.soTimeout = 300
                assertNull(
                    runCatching { client.recv(300) }.getOrNull(),
                    "only one reply datagram is sent",
                )
            }
        }
    }

    private companion object {
        const val ADMIT_DELAY_MILLIS = 200L
        const val HANDSHAKES = 5
        const val MALFORMED_BURST = 600
        const val MALFORMED_BURST_WITH_PEER = 300
        const val CONNECT_SETTLE_MILLIS = 200L
    }
}
