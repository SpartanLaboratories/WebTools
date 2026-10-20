package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.webtools.udp.Admission
import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Level 3 - the admit() screening hook over a real socket. Each test binds its own server on
// the common port and stops it in @AfterTest; the module's commonUdpPortLock serialises the
// integration task so the fixed port is never contended across Gradle workers.
@Tag("integration")
class MultiConnectionUDPServerAdmissionTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private var server: MultiConnectionUDPServer? = null

    @AfterTest
    fun tearDown() {
        runCatching { server?.stop() }
        server = null
    }

    private class GuardedServer : MultiConnectionUDPServer() {
        val connected = CopyOnWriteArrayList<String>()
        val seenCredentials = ConcurrentLinkedQueue<String>()

        override fun admit(name: String, peer: InetSocketAddress, credential: String): Admission {
            seenCredentials += credential
            return when {
                name == "banned" -> Admission.Refused("banned")
                name == "guarded" && credential != "s3cret" -> Admission.Refused("invalid credential")
                else -> Admission.Admitted
            }
        }

        override fun onClientConnect(connection: Connection) {
            connected += connection.name
        }
    }

    private fun DatagramSocket.exchange(payload: String): String {
        val out = payload.toByteArray(Charsets.UTF_8)
        send(DatagramPacket(out, out.size, loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
        soTimeout = REPLY_TIMEOUT_MILLIS
        val p = DatagramPacket(ByteArray(256), 256)
        receive(p)
        return String(p.data, 0, p.length, Charsets.UTF_8).trim()
    }

    @Test
    fun `a refused name gets REFUSED, never registers, and its later datagrams are dropped`() {
        val guarded = GuardedServer().also { server = it }
        DatagramSocket().use { client ->
            assertEquals("REFUSED banned", client.exchange("Iam banned"))
            Thread.sleep(SETTLE_MILLIS)
            assertFalse(guarded.connected.contains("banned"))

            // A follow-up datagram from the unregistered socket draws no reply.
            val out = "hello".toByteArray()
            client.send(DatagramPacket(out, out.size, loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
            client.soTimeout = NO_REPLY_TIMEOUT_MILLIS
            assertFailsWith<SocketTimeoutException> { client.receive(DatagramPacket(ByteArray(64), 64)) }
        }
    }

    @Test
    fun `a good credential registers, a wrong one is refused`() {
        val guarded = GuardedServer().also { server = it }
        DatagramSocket().use { ok ->
            assertEquals("REGISTERED 2", ok.exchange("Iam guarded s3cret"))
            Thread.sleep(SETTLE_MILLIS)
            assertTrue(guarded.connected.contains("guarded"))
        }
        DatagramSocket().use { bad ->
            assertEquals("REFUSED invalid credential", bad.exchange("Iam guarded wrong"))
        }
    }

    @Test
    fun `the default branch sees an empty credential for a bare Iam`() {
        val guarded = GuardedServer().also { server = it }
        DatagramSocket().use { client ->
            assertEquals("REGISTERED 2", client.exchange("Iam alice"))
        }
        assertTrue(guarded.seenCredentials.contains(""))
    }

    @Test
    fun `every malformed Iam shape gets REFUSED malformed and admit is never consulted`() {
        val guarded = GuardedServer().also { server = it }
        listOf("Iam", "Iam  x", "Iam bob  tok").forEach { line ->
            DatagramSocket().use { client ->
                assertEquals("REFUSED malformed", client.exchange(line), line)
            }
        }
        Thread.sleep(SETTLE_MILLIS)
        assertTrue(guarded.seenCredentials.isEmpty())
        assertTrue(guarded.connected.isEmpty())
    }

    @Test
    fun `a malformed Iam from a registered socket is refused and leaves it registered`() {
        val guarded = GuardedServer().also { server = it }
        DatagramSocket().use { client ->
            assertEquals("REGISTERED 2", client.exchange("Iam alice"))
            assertEquals("REFUSED malformed", client.exchange("Iam  x"))
            assertEquals("REFUSED malformed", client.exchange("Iam"))
            assertEquals("REFUSED malformed", client.exchange("Iam alice  tok"))
            assertEquals("REGISTERED 2", client.exchange("Iam alice"))
            Thread.sleep(SETTLE_MILLIS)
            assertEquals(listOf("alice"), guarded.connected.toList())
            assertEquals(1, guarded.seenCredentials.size, "the retransmit is not re-screened")
        }
    }

    @Test
    fun `a trailing space on the wire is trimmed so Iam bob with a trailing space registers bob`() {
        val guarded = GuardedServer().also { server = it }
        DatagramSocket().use { client ->
            assertEquals("REGISTERED 2", client.exchange("Iam bob "))
            Thread.sleep(SETTLE_MILLIS)
            assertEquals(listOf("bob"), guarded.connected.toList())
            assertEquals(listOf(""), guarded.seenCredentials.toList())
        }
    }

    @Test
    fun `an empty token past the credential slot is ignored and the credential reaches admit`() {
        val guarded = GuardedServer().also { server = it }
        DatagramSocket().use { client ->
            assertEquals("REGISTERED 2", client.exchange("Iam bob tok  x"))
            assertEquals(listOf("tok"), guarded.seenCredentials.toList())
        }
    }

    @Test
    fun `a socket refused once can retry successfully on the same socket`() {
        GuardedServer().also { server = it }
        DatagramSocket().use { client ->
            assertEquals("REFUSED banned", client.exchange("Iam banned"))
            assertEquals("REGISTERED 2", client.exchange("Iam guarded s3cret"))
        }
    }

    @Test
    fun `a socket refused as malformed can retry a well-formed Iam on the same socket`() {
        val guarded = GuardedServer().also { server = it }
        DatagramSocket().use { client ->
            assertEquals("REFUSED malformed", client.exchange("Iam  x"))
            assertEquals("REGISTERED 2", client.exchange("Iam bob"))
            Thread.sleep(SETTLE_MILLIS)
            assertEquals(listOf("bob"), guarded.connected.toList())
        }
    }

    @Test
    fun `a default server with no admit override registers any name and credential`() {
        val plain = object : MultiConnectionUDPServer() {
            val connected = CopyOnWriteArrayList<String>()
            override fun onClientConnect(connection: Connection) { connected += connection.name }
        }
        server = plain
        DatagramSocket().use { client ->
            assertEquals("REGISTERED 2", client.exchange("Iam whoever any-credential-here"))
            Thread.sleep(SETTLE_MILLIS)
            assertContentEquals(listOf("whoever"), plain.connected)
        }
    }

    private companion object {
        const val REPLY_TIMEOUT_MILLIS = 5000
        const val NO_REPLY_TIMEOUT_MILLIS = 500
        const val SETTLE_MILLIS = 200L
    }
}
