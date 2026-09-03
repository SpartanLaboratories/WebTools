package com.spartanlabs.testing.integration.webtools

import com.spartanlabs.webtools.UDPSendReceiveServer
import org.junit.jupiter.api.Tag
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
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
}
