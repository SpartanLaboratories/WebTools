package com.spartanlabs.testing.nonfunctional.webtools

import com.spartanlabs.webtools.MultiConnectionUDPClient
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 4c - non-functional properties of MultiConnectionUDPClient's listener-thread /
// dispatch-executor concurrency model, mirroring HandshakeNonFunctionalTest's server-side
// coverage now that the client has the same concurrency shape.
@Tag("nonfunctional")
class MultiConnectionUDPClientNonFunctionalTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    private fun fakePeer(): DatagramSocket = DatagramSocket().also { opened += it }

    private fun newClient(peerPort: Int): MultiConnectionUDPClient =
        MultiConnectionUDPClient(loopback, peerPort).also { clients += it }

    private fun DatagramSocket.receiveOrigin(): InetSocketAddress {
        soTimeout = 5000
        val packet = DatagramPacket(ByteArray(64), 64)
        receive(packet)
        return InetSocketAddress(packet.address, packet.port)
    }

    private fun DatagramSocket.sendTo(target: InetSocketAddress, text: String) {
        val out = text.toByteArray(Charsets.UTF_8)
        send(DatagramPacket(out, out.size, target.address, target.port))
    }

    private fun handshakeSucceeds(client: MultiConnectionUDPClient, peer: DatagramSocket): InetSocketAddress {
        var origin: InetSocketAddress? = null
        val thread = Thread {
            origin = peer.receiveOrigin()
            peer.sendTo(origin!!, "REGISTERED")
        }.apply { start() }
        assertTrue(client.handshake("alice").isSuccess)
        thread.join(5000)
        return origin!!
    }

    @AfterTest
    fun cleanup() {
        clients.forEach { runCatching { it.stop() } }
        clients.clear()
        opened.forEach { runCatching { it.close() } }
        opened.clear()
    }

    @Test
    fun `per-session message order is preserved under a burst`() {
        // The single-threaded dispatch executor guarantees onMessage sees a burst of
        // server-sent messages in send order. Accepted trade-off (same as the server side):
        // a slow onMessage delays delivery of only this client's own subsequent messages.
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshakeSucceeds(client, peer)
        val seen = ConcurrentLinkedQueue<Int>()
        val done = CountDownLatch(BURST)
        assertTrue(
            client.start { message ->
                seen += message.toInt()
                done.countDown()
            }.isSuccess,
        )

        repeat(BURST) { i -> peer.sendTo(origin, i.toString()) }
        assertTrue(done.await(10, TimeUnit.SECONDS), "all $BURST messages delivered")
        assertEquals((0 until BURST).toList(), seen.toList())
    }

    @Test
    fun `a KA storm from the peer creates no calls to onMessage and does not wedge the listener`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshakeSucceeds(client, peer)
        val dispatched = ConcurrentLinkedQueue<String>()
        val realMessageSeen = CountDownLatch(1)
        assertTrue(
            client.start { message ->
                dispatched += message
                if (message == "real-message") realMessageSeen.countDown()
            }.isSuccess,
        )

        repeat(STORM_SIZE) { peer.sendTo(origin, "KA") }
        peer.sendTo(origin, "real-message")

        assertTrue(realMessageSeen.await(10, TimeUnit.SECONDS), "listener not wedged by the KA storm")
        assertEquals(listOf("real-message"), dispatched.toList())
    }

    @Test
    fun `a handler that always throws never kills the dispatch thread across many messages`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshakeSucceeds(client, peer)
        val checkSeen = CountDownLatch(1)
        assertTrue(
            client.start { message ->
                if (message == "check") {
                    checkSeen.countDown()
                } else {
                    throw IllegalStateException("always throws")
                }
            }.isSuccess,
        )

        repeat(THROWING_BURST) { i -> peer.sendTo(origin, "throw-$i") }
        peer.sendTo(origin, "check")

        assertTrue(checkSeen.await(10, TimeUnit.SECONDS), "dispatch thread survived $THROWING_BURST throwing messages")
    }

    private companion object {
        const val BURST = 500
        const val STORM_SIZE = 100
        const val THROWING_BURST = 50
    }
}
