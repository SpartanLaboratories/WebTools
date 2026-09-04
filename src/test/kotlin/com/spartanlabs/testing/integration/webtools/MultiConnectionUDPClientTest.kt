package com.spartanlabs.testing.integration.webtools

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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 3 - real sockets, real threads: MultiConnectionUDPClient exercised against a fake
// peer DatagramSocket standing in for the server side of the handshake and session.
@Tag("integration")
class MultiConnectionUDPClientTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val opened = mutableListOf<DatagramSocket>()
    private val clients = mutableListOf<MultiConnectionUDPClient>()

    private fun fakePeer(): DatagramSocket = DatagramSocket().also { opened += it }

    private fun newClient(peerPort: Int): MultiConnectionUDPClient =
        MultiConnectionUDPClient(loopback, peerPort).also { clients += it }

    private fun DatagramSocket.receiveText(timeoutMillis: Int = RECEIVE_TIMEOUT_MILLIS): Pair<InetSocketAddress, String> {
        soTimeout = timeoutMillis
        val packet = DatagramPacket(ByteArray(RECEIVE_BUFFER_BYTES), RECEIVE_BUFFER_BYTES)
        receive(packet)
        val origin = InetSocketAddress(packet.address, packet.port)
        return origin to String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
    }

    private fun DatagramSocket.sendTo(target: InetSocketAddress, text: String) {
        val out = text.toByteArray(Charsets.UTF_8)
        send(DatagramPacket(out, out.size, target.address, target.port))
    }

    /** Handshakes [client] against [peer], replying REGISTERED, returning the client's origin. */
    private fun handshakeSucceeds(client: MultiConnectionUDPClient, peer: DatagramSocket): InetSocketAddress {
        var origin: InetSocketAddress? = null
        val thread = Thread {
            val (from, _) = peer.receiveText()
            origin = from
            peer.sendTo(from, "REGISTERED")
        }.apply { start() }
        assertTrue(client.handshake("alice").isSuccess)
        thread.join(RECEIVE_TIMEOUT_MILLIS.toLong())
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
    fun `constructing binds an ephemeral local port`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        assertTrue(client.localPort in 1..65535)
    }

    @Test
    fun `handshake against a peer that replies REGISTERED succeeds`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        handshakeSucceeds(client, peer)
    }

    @Test
    fun `handshake against a peer that replies something else fails with the expected message`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val thread = Thread {
            val (from, _) = peer.receiveText()
            peer.sendTo(from, "NOPE")
        }.apply { start() }
        val result = client.handshake("bob")
        thread.join(RECEIVE_TIMEOUT_MILLIS.toLong())
        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "Expected 'REGISTERED' but got 'NOPE'")
    }

    @Test
    fun `handshake against a peer that never replies fails on timeout`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val result = client.handshake("carol", timeoutMillis = SHORT_TIMEOUT_MILLIS)
        assertTrue(result.isFailure)
        assertIs<java.net.SocketTimeoutException>(result.exceptionOrNull())
    }

    @Test
    fun `after start an app datagram from the peer is delivered to onMessage asynchronously`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshakeSucceeds(client, peer)
        val received = ConcurrentLinkedQueue<String>()
        assertTrue(client.start { received += it }.isSuccess)

        peer.sendTo(origin, "hello")
        awaitQueueContains(received, "hello")
    }

    @Test
    fun `a bare KA from the peer is consumed silently and a following real message still arrives`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshakeSucceeds(client, peer)
        val received = ConcurrentLinkedQueue<String>()
        assertTrue(client.start { received += it }.isSuccess)

        peer.sendTo(origin, "KA")
        peer.sendTo(origin, "still-here")
        awaitQueueContains(received, "still-here")
        assertFalse(received.contains("KA"))
    }

    @Test
    fun `send puts the exact encoded bytes on the wire to the server address and port`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        assertTrue(client.send("hello-server").isSuccess)
        val (_, text) = peer.receiveText()
        assertEquals("hello-server", text)
    }

    @Test
    fun `sendKeepAlive puts the exact KA bytes on the wire`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        assertTrue(client.sendKeepAlive().isSuccess)
        val (_, text) = peer.receiveText()
        assertEquals("KA", text)
    }

    @Test
    fun `a send from one thread succeeds while the listener thread is concurrently blocked in receive`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        handshakeSucceeds(client, peer)
        assertTrue(client.start { }.isSuccess)

        // The listener thread is now blocked in socket.receive(); a concurrent send must still work.
        assertTrue(client.send("concurrent-send").isSuccess)
        val (_, text) = peer.receiveText()
        assertEquals("concurrent-send", text)
    }

    @Test
    fun `a throwing onMessage does not kill the dispatch thread or the listener`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshakeSucceeds(client, peer)
        val received = ConcurrentLinkedQueue<String>()
        assertTrue(
            client.start { message ->
                if (message == "boom") throw IllegalStateException("boom")
                received += message
            }.isSuccess,
        )

        peer.sendTo(origin, "boom")
        peer.sendTo(origin, "after-throw")
        awaitQueueContains(received, "after-throw")
    }

    @Test
    fun `several messages sent back-to-back are delivered in order`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        val origin = handshakeSucceeds(client, peer)
        val received = ConcurrentLinkedQueue<Int>()
        val done = CountDownLatch(BURST)
        assertTrue(
            client.start { message ->
                received += message.toInt()
                done.countDown()
            }.isSuccess,
        )

        repeat(BURST) { i -> peer.sendTo(origin, i.toString()) }
        assertTrue(done.await(5, TimeUnit.SECONDS), "all $BURST messages delivered")
        assertEquals((0 until BURST).toList(), received.toList())
    }

    @Test
    fun `stop joins the listener closes the socket and a send afterward fails`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        handshakeSucceeds(client, peer)
        assertTrue(client.start { }.isSuccess)
        val boundPort = client.localPort

        assertTrue(client.stop().isSuccess)
        assertTrue(client.send("after-stop").isFailure)

        // The port is released.
        DatagramSocket(boundPort).use { /* bind succeeds */ }
    }

    @Test
    fun `calling stop twice returns success both times`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        handshakeSucceeds(client, peer)
        assertTrue(client.start { }.isSuccess)

        assertTrue(client.stop().isSuccess)
        assertTrue(client.stop().isSuccess)
    }

    @Test
    fun `a send racing a concurrent stop does not throw and fails cleanly if the socket is already closing`() {
        val peer = fakePeer()
        val client = newClient(peer.localPort)
        handshakeSucceeds(client, peer)
        assertTrue(client.start { }.isSuccess)

        var stopResult: Result<Unit>? = null
        val stopper = Thread { stopResult = client.stop() }.apply { start() }
        // Hammer send() while stop() is concurrently closing the socket; every call must
        // return a Result rather than throw, regardless of whether it wins the race. Collecting
        // every result (instead of discarding them) is itself the assertion that none of the
        // 200 calls let an exception escape - a throw here would fail the test via .map itself.
        val racingResults = (1..200).map { client.send("racing-send") }
        stopper.join(RECEIVE_TIMEOUT_MILLIS.toLong())

        assertEquals(200, racingResults.size)
        assertTrue(stopResult?.isSuccess == true)
        // stop()'s join() waits up to a second before close() even runs, so the 200 racing
        // sends above almost always finish first and win the race - asserting one of them
        // failed would be flaky. Once stopper.join() above has returned, though, stop() has
        // unconditionally run to completion and the socket is closed for certain, so this
        // send() deterministically proves the "fails cleanly once closing" half of the
        // contract without depending on race timing.
        assertTrue(client.send("after-stop").isFailure)
    }

    private fun <T> awaitQueueContains(queue: ConcurrentLinkedQueue<T>, value: T, timeoutMillis: Long = 5000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!queue.contains(value) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertTrue(queue.contains(value), "expected $value to be delivered within ${timeoutMillis}ms, saw $queue")
    }

    private companion object {
        const val RECEIVE_BUFFER_BYTES = 1024
        const val RECEIVE_TIMEOUT_MILLIS = 5000
        const val SHORT_TIMEOUT_MILLIS = 200
        const val BURST = 50
    }
}
