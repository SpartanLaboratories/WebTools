package com.spartanlabs.testing.nonfunctional.webtools.udp

import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

// Level 4c - robustness of MultiConnectionUDPClient's scheduled keepalive: thread hygiene
// across many arm/disarm cycles, independence from the dispatch thread, the overshoot bound,
// and races with stop().
@Tag("nonfunctional")
class MultiConnectionUDPClientKeepAliveNonFunctionalTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    private fun fakePeer(): DatagramSocket = DatagramSocket().also { opened += it }
    private fun newClient(port: Int) = MultiConnectionUDPClient(loopback, port).also { clients += it }

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

    private fun keepAliveThreadCount() =
        Thread.getAllStackTraces().keys.count { it.name == "mcupc-keepalive" && it.isAlive }

    @Test
    fun `200 arm-disarm cycles leave at most one keepalive thread and a final arm still delivers KA`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        handshake(client, peer)

        repeat(200) {
            assertTrue(client.startKeepAlive(300L).isSuccess)
            assertTrue(client.stopKeepAlive().isSuccess)
        }
        assertTrue(keepAliveThreadCount() <= 1, "cycles leaked keepalive threads")

        while (peer.nextText(20) != null) { /* drain */ }
        assertTrue(client.startKeepAlive(300L).isSuccess)
        val deadline = System.currentTimeMillis() + 1_500L
        var ka = false
        while (!ka && System.currentTimeMillis() < deadline) {
            ka = peer.nextText(200)?.second == "KA"
        }
        assertTrue(ka, "a final arm still delivers KA")
    }

    @Test
    fun `a wedged start handler does not delay the KA - the keepalive thread is independent of dispatch`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshake(client, peer)
        client.start { Thread.sleep(2_000) } // every dispatched message wedges the dispatch thread
        assertTrue(client.startKeepAlive(300L).isSuccess)

        // Wedge the dispatch thread.
        val poke = "poke".toByteArray()
        peer.send(DatagramPacket(poke, poke.size, origin.address, origin.port))

        val deadline = System.currentTimeMillis() + 1_500L
        var ka = false
        while (!ka && System.currentTimeMillis() < deadline) {
            ka = peer.nextText(200)?.second == "KA"
        }
        assertTrue(ka, "KA still flowed while the dispatch thread was wedged")
    }

    @Test
    fun `the KA overshoot stays within interval plus one poll`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        handshake(client, peer)
        while (peer.nextText(20) != null) { /* drain */ }

        val interval = 300L
        val poll = 250L // KeepAlive.pollIntervalMillis(300) floors at 250
        val slack = 400L
        assertTrue(client.startKeepAlive(interval).isSuccess)
        val armedAt = System.currentTimeMillis()

        // Wait for the first KA; time from the arm (the last outbound was the handshake, older).
        var firstKaAt = 0L
        val deadline = armedAt + interval + poll + slack + 1_000L
        while (firstKaAt == 0L && System.currentTimeMillis() < deadline) {
            if (peer.nextText(100)?.second == "KA") firstKaAt = System.currentTimeMillis()
        }
        assertTrue(firstKaAt != 0L, "a KA arrived")
        assertTrue(firstKaAt - armedAt <= interval + poll + slack, "overshoot was ${firstKaAt - armedAt} ms")
    }

    @Test
    fun `a scheduled KA racing stop never throws out of either call`() {
        repeat(20) {
            val peer = fakePeer()
            val client = newClient(peer.localPort)
            handshake(client, peer)
            client.startKeepAlive(1L) // fastest cadence, poll floors at 250 ms
            Thread.sleep(260)
            // stop() while a tick is very likely mid-flight - must return a Result, not throw.
            assertTrue(client.stop().isSuccess)
        }
    }
}
