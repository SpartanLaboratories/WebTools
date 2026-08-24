import com.spartanlabs.webtools.UDPSendReceiveServer
import org.junit.jupiter.api.Timeout
import java.net.BindException
import java.net.InetAddress
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Tests for [com.spartanlabs.webtools.UDPSendReceiveServer].
 *
 * These are integration-style tests: they open real UDP sockets on loopback
 * and exercise actual send/receive round trips rather than mocking
 * [java.net.DatagramSocket]. Each test uses its own port pair to avoid
 * clashing with other tests, and all servers created via [track] are closed
 * automatically in [tearDown].
 */
class UDPSendReceiveServerTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val openServers = CopyOnWriteArrayList<UDPSendReceiveServer>()
    private val portCounter = AtomicInteger(41000)

    /** Returns a fresh port number for this test run, to avoid cross-test collisions. */
    private fun nextPort(): Int = portCounter.incrementAndGet()

    /** Creates a server and registers it for cleanup in [tearDown]. */
    private fun track(server: UDPSendReceiveServer): UDPSendReceiveServer {
        openServers.add(server)
        return server
    }

    @AfterTest
    fun tearDown() {
        openServers.forEach { server ->
            try {
                server.close()
            } catch (_: Exception) {
                // already closed / best-effort cleanup
            }
        }
        openServers.clear()
    }

    @Test
    @Timeout(5)
    fun `sends and receives a string message round trip`() {
        val portA = nextPort()
        val portB = nextPort()

        // serverA listens on portA, sends to whatever listens on portB
        val serverA = track(UDPSendReceiveServer(targetAddress = loopback, sendPort = portB, listenPort = portA))
        // serverB listens on portB, sends to whatever listens on portA
        val serverB = track(UDPSendReceiveServer(targetAddress = loopback, sendPort = portA, listenPort = portB))

        val latch = CountDownLatch(1)
        var receivedMessage: String? = null
        var receivedSender: InetAddress? = null

        serverB.startListening { message, senderAddress ->
            receivedMessage = message
            receivedSender = senderAddress
            latch.countDown()
        }

        serverA.send("hello world", loopback)

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Expected message was not received in time")
        assertEquals("hello world", receivedMessage)
        assertEquals(loopback, receivedSender)
    }

    @Test
    @Timeout(5)
    fun `sends and receives a raw byte array message`() {
        val portA = nextPort()
        val portB = nextPort()

        val serverA = track(UDPSendReceiveServer(targetAddress = loopback, sendPort = portB, listenPort = portA))
        val serverB = track(UDPSendReceiveServer(targetAddress = loopback, sendPort = portA, listenPort = portB))

        val latch = CountDownLatch(1)
        var received: String? = null

        serverB.startListening { message, _ ->
            received = message
            latch.countDown()
        }

        val payload = "raw-bytes-payload".toByteArray(Charsets.UTF_8)
        serverA.send(payload, loopback)

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Expected message was not received in time")
        assertEquals("raw-bytes-payload", received)
    }

    @Test
    @Timeout(5)
    fun `receives multiple messages in sequence`() {
        val portA = nextPort()
        val portB = nextPort()

        val serverA = track(UDPSendReceiveServer(targetAddress = loopback, sendPort = portB, listenPort = portA))
        val serverB = track(UDPSendReceiveServer(targetAddress = loopback, sendPort = portA, listenPort = portB))

        val messageCount = 5
        val latch = CountDownLatch(messageCount)
        val receivedMessages = CopyOnWriteArrayList<String>()

        serverB.startListening { message, _ ->
            receivedMessages.add(message)
            latch.countDown()
        }

        repeat(messageCount) { i -> serverA.send("message-$i", loopback) }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Not all messages were received in time")
        assertEquals(messageCount, receivedMessages.size)
        (0 until messageCount).forEach { i ->
            assertContains(receivedMessages, "message-$i")
        }
    }

    @Test
    @Timeout(5)
    fun `stopListening halts further message delivery`() {
        val portA = nextPort()
        val portB = nextPort()

        val serverA = track(UDPSendReceiveServer(targetAddress = loopback, sendPort = portB, listenPort = portA))
        val serverB = track(UDPSendReceiveServer(targetAddress = loopback, sendPort = portA, listenPort = portB))

        val firstMessageLatch = CountDownLatch(1)
        val unexpectedSecondMessageLatch = CountDownLatch(1)

        serverB.startListening { message, _ ->
            if (message == "first") firstMessageLatch.countDown()
            if (message == "second") unexpectedSecondMessageLatch.countDown()
        }

        serverA.send("first", loopback)
        assertTrue(firstMessageLatch.await(3, TimeUnit.SECONDS), "First message was not received")

        serverB.stopListening()
        // give the listener loop a moment to actually exit its blocking receive
        Thread.sleep(200)

        serverA.send("second", loopback)

        assertFalse(
            unexpectedSecondMessageLatch.await(1, TimeUnit.SECONDS),
            "Message should not have been delivered after stopListening()"
        )
    }

    @Test
    @Timeout(5)
    fun `close stops listening and releases the listen port`() {
        val portA = nextPort()
        val portB = nextPort()

        val serverA = UDPSendReceiveServer(targetAddress = loopback, sendPort = portB, listenPort = portA)
        openServers.add(serverA)

        serverA.startListening { _, _ -> /* no-op */ }
        serverA.close()

        // Port should be free again once closed; binding a new socket to it should succeed.
        val replacement = track(UDPSendReceiveServer(targetAddress = loopback, sendPort = portB, listenPort = portA))
        assertNotNull(replacement)
    }

    @Test
    @Timeout(5)
    fun `send after close throws`() {
        val portA = nextPort()
        val portB = nextPort()

        val server = UDPSendReceiveServer(targetAddress = loopback, sendPort = portB, listenPort = portA)
        server.close()

        assertFailsWith<SocketException> {
            server.send("should fail", loopback)
        }
    }

    @Test
    @Timeout(5)
    fun `binding to an already-used listen port throws`() {
        val busyPort = nextPort()
        val otherPort = nextPort()

        track(UDPSendReceiveServer(targetAddress = loopback, sendPort = otherPort, listenPort = busyPort))

        assertFailsWith<BindException> {
            UDPSendReceiveServer(targetAddress = loopback, sendPort = otherPort, listenPort = busyPort)
        }
    }

    @Test
    @Timeout(5)
    fun `resolveLocalAddress returns a non-null usable address`() {
        val address = UDPSendReceiveServer.Companion.resolveLocalAddress()
        assertNotNull(address)
    }

    @Test
    @Timeout(5)
    fun `default targetAddress is used implicitly by companion resolver`() {
        // Confirms the default parameter resolves without requiring an explicit targetAddress.
        val portA = nextPort()
        val portB = nextPort()
        val server = track(UDPSendReceiveServer(sendPort = portB, listenPort = portA))
        assertNotNull(server)
    }
}