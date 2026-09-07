package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.captureLogsOf
import com.spartanlabs.testing.support.webtools.udp.hasWarnContaining
import com.spartanlabs.webtools.udp.UDPSendReceiveServer
import org.junit.jupiter.api.Tag
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 3 - drives real UDP sockets over loopback through the send/receive server.
@Tag("integration")
class UDPSendReceiveServerTest {

    private val log = LoggerFactory.getLogger(UDPSendReceiveServerTest::class.java)
    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    @Test
    fun `sends and receives a message over loopback`() {
        log.info("Starting send/receive test")
        val receiverPort = 41231
        val senderPort = 41232

        val receiver = UDPSendReceiveServer(loopback, senderPort, receiverPort)
        val sender = UDPSendReceiveServer(loopback, receiverPort, senderPort)

        val latch = CountDownLatch(1)
        var received: String? = null

        assertTrue(
            receiver.startListening { message, _ ->
                log.debug("Test received: {}", message)
                received = message
                latch.countDown()
            }.isSuccess,
            "Expected the listener to start"
        )

        assertTrue(sender.send("hello world").isSuccess, "Expected the message to be sent")

        val completed = latch.await(5, TimeUnit.SECONDS)
        assertTrue(completed, "Expected message to be received within timeout")
        assertEquals("hello world", received)

        receiver.close()
        sender.close()
    }

    @Test
    fun `stopListening halts further message delivery`() {
        log.info("Starting stopListening test")
        val receiverPort = 41233
        val senderPort = 41234

        val receiver = UDPSendReceiveServer(loopback, senderPort, receiverPort)
        val sender = UDPSendReceiveServer(loopback, receiverPort, senderPort)

        var messageCount = 0
        receiver.startListening { _, _ -> messageCount++ }

        assertTrue(receiver.stopListening().isSuccess, "Expected the listener to stop cleanly")
        Thread.sleep(200)

        // The datagram still leaves the sender; it simply has nothing bound to arrive at.
        assertTrue(sender.send("should not arrive").isSuccess)
        Thread.sleep(300)

        assertEquals(0, messageCount)

        // shutDown() reports its outcome, unlike close(), which AutoCloseable pins to Unit.
        assertTrue(sender.shutDown().isSuccess, "Expected the sender to shut down cleanly")
        receiver.close()
    }

    // --- Issue #9: the receive buffer is no longer hardcoded to 1024 bytes.

    /** Starts [receiver] listening, sends [payload] from [sender], returns the first delivered message. */
    private fun roundTrip(receiver: UDPSendReceiveServer, sender: UDPSendReceiveServer, payload: String): String {
        val delivered = LinkedBlockingQueue<String>()
        assertTrue(receiver.startListening { message, _ -> delivered.add(message) }.isSuccess)
        assertTrue(sender.send(payload).isSuccess)
        return delivered.poll(5, TimeUnit.SECONDS) ?: error("no datagram delivered within 5s")
    }

    @Test
    fun `receives a datagram larger than 1024 bytes intact`() {
        val receiverPort = 41241
        val senderPort = 41242
        val receiver = UDPSendReceiveServer(loopback, senderPort, receiverPort)
        val sender = UDPSendReceiveServer(loopback, receiverPort, senderPort)
        try {
            // ~4 KiB of non-whitespace text: pre-#9 this class truncated it to 1024.
            val payload = "x".repeat(4096)
            assertEquals(payload, roundTrip(receiver, sender, payload))
        } finally {
            receiver.close()
            sender.close()
        }
    }

    @Test
    fun `receives a ~60 KiB datagram intact at the default buffer`() {
        val receiverPort = 41243
        val senderPort = 41244
        val receiver = UDPSendReceiveServer(loopback, senderPort, receiverPort)
        val sender = UDPSendReceiveServer(loopback, receiverPort, senderPort)
        try {
            val payload = "abcdefghij".repeat(6000) // 60_000 chars, under the 65507 ceiling
            assertEquals(payload, roundTrip(receiver, sender, payload))
        } finally {
            receiver.close()
            sender.close()
        }
    }

    @Test
    fun `a configured 512-byte buffer truncates a larger datagram and logs a warning`() {
        val receiverPort = 41245
        val senderPort = 41246
        val receiver = UDPSendReceiveServer(loopback, senderPort, receiverPort, receiveBufferBytes = 512)
        val sender = UDPSendReceiveServer(loopback, receiverPort, senderPort)
        try {
            captureLogsOf(UDPSendReceiveServer::class.java) { events ->
                val delivered = roundTrip(receiver, sender, "y".repeat(2048))
                assertTrue(delivered.toByteArray(Charsets.UTF_8).size <= 512, "message truncated to the buffer size")
                assertTrue(events.hasWarnContaining("may have been truncated"), "truncation WARN was logged")
            }
        } finally {
            receiver.close()
            sender.close()
        }
    }
}
