package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 3 - a real MultiConnectionUDPClient socket against a fake peer DatagramSocket,
// exercising the opt-in scheduled keepalive end to end on the client side.
@Tag("integration")
class MultiConnectionUDPClientKeepAliveTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    private fun fakePeer(): DatagramSocket = DatagramSocket().also { opened += it }

    private fun newClient(peerPort: Int): MultiConnectionUDPClient =
        MultiConnectionUDPClient(loopback, peerPort).also { clients += it }

    @AfterTest
    fun cleanup() {
        clients.forEach { runCatching { it.stop() } }
        opened.forEach { runCatching { it.close() } }
    }

    private fun DatagramSocket.nextText(timeoutMillis: Int): Pair<InetSocketAddress, String>? {
        soTimeout = timeoutMillis
        val packet = DatagramPacket(ByteArray(256), 256)
        return try {
            receive(packet)
            InetSocketAddress(packet.address, packet.port) to
                String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    /** Counts `KA` datagrams from [client] arriving at [peer] over [windowMillis]. */
    private fun countKeepAlives(peer: DatagramSocket, windowMillis: Long): Int {
        val deadline = System.currentTimeMillis() + windowMillis
        var count = 0
        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(1)
            val msg = peer.nextText(remaining) ?: break
            if (msg.second == "KA") count++
        }
        return count
    }

    private fun handshake(client: MultiConnectionUDPClient, peer: DatagramSocket): InetSocketAddress {
        var origin: InetSocketAddress? = null
        val t = Thread {
            val (from, _) = peer.nextText(5_000)!!
            origin = from
            val reg = "REGISTERED".toByteArray()
            peer.send(DatagramPacket(reg, reg.size, from.address, from.port))
        }.apply { start() }
        assertTrue(client.handshake("alice").isSuccess)
        t.join(5_000)
        return origin!!
    }

    @Test
    fun `after startKeepAlive the peer receives KA datagrams roughly every interval while idle`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        handshake(client, peer)

        assertTrue(client.startKeepAlive(300L).isSuccess)

        // ~300 ms interval, poll 250 ms: expect at least 2 KA in a 1.4 s idle window.
        assertTrue(countKeepAlives(peer, 1_400L) >= 2)
    }

    @Test
    fun `frequent application sends suppress the scheduled KA`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        handshake(client, peer)
        assertTrue(client.startKeepAlive(300L).isSuccess)

        val deadline = System.currentTimeMillis() + 1_500L
        var kaSeen = 0
        var dataSeen = 0
        while (System.currentTimeMillis() < deadline) {
            client.send("x")
            Thread.sleep(100)
            while (true) {
                val msg = peer.nextText(20) ?: break
                if (msg.second == "KA") kaSeen++ else dataSeen++
            }
        }
        assertTrue(dataSeen > 0, "data was received")
        assertTrue(kaSeen == 0, "no KA while the client is busy, saw $kaSeen")
    }

    @Test
    fun `stopKeepAlive halts the KA stream and a later startKeepAlive resumes it`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        handshake(client, peer)
        assertTrue(client.startKeepAlive(300L).isSuccess)
        assertTrue(countKeepAlives(peer, 1_200L) >= 1)

        assertTrue(client.stopKeepAlive().isSuccess)
        Thread.sleep(400)
        while (peer.nextText(20) != null) { /* drain */ }
        assertTrue(countKeepAlives(peer, 900L) == 0, "no KA after stopKeepAlive")

        assertTrue(client.startKeepAlive(300L).isSuccess)
        assertTrue(countKeepAlives(peer, 1_200L) >= 1, "KA resumed")
    }

    @Test
    fun `stop halts the KA stream and leaves no mcupc-keepalive thread`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        handshake(client, peer)
        assertTrue(client.startKeepAlive(300L).isSuccess)
        assertTrue(countKeepAlives(peer, 1_200L) >= 1)

        assertTrue(client.stop().isSuccess)
        Thread.sleep(400)
        while (peer.nextText(20) != null) { /* drain */ }
        assertTrue(countKeepAlives(peer, 900L) == 0)
        assertTrue(Thread.getAllStackTraces().keys.none { it.name == "mcupc-keepalive" && it.isAlive })
    }

    @Test
    fun `repeated startKeepAlive with a different interval - last wins, single stream`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        handshake(client, peer)

        assertTrue(client.startKeepAlive(2_000L).isSuccess)
        assertTrue(client.startKeepAlive(300L).isSuccess)

        // The faster cadence proves the re-arm took; a single stream means no doubled KA burst.
        val count = countKeepAlives(peer, 1_400L)
        assertTrue(count in 2..8, "expected the 300ms cadence, saw $count")
    }

    @Test
    fun `startKeepAlive after stop fails and starts no thread`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        handshake(client, peer)
        assertTrue(client.stop().isSuccess)

        val result = client.startKeepAlive(300L)

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertTrue(Thread.getAllStackTraces().keys.none { it.name == "mcupc-keepalive" && it.isAlive })
    }
}
