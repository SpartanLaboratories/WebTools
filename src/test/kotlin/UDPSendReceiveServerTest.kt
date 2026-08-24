package com.spartanlabs.webtools

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class UDPSendReceiveServerTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val serversUnderTest = mutableListOf<UDPSendReceiveServer>()

    /** Finds a currently-free UDP port by briefly binding to port 0. */
    private fun freePort(): Int = DatagramSocket(0).use { it.localPort }

    private fun newServer(sendPort: Int, listenPort: Int): UDPSendReceiveServer =
        UDPSendReceiveServer(loopback, sendPort, listenPort).also { serversUnderTest.add(it) }

    @AfterEach
    fun tearDown() {
        serversUnderTest.forEach {
            try {
                it.close()
            } catch (_: Exception) {
                // already closed / best-effort cleanup
            }
        }
        serversUnderTest.clear()
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `send delivers a string message to the listening peer`() {
        val portA = freePort()
        val portB = freePort()

        val serverA = newServer(sendPort = portB, listenPort = portA)
        val serverB = newServer(sendPort = portA, listenPort = portB)

        val latch = CountDownLatch(1)
        val receivedMessage = AtomicReference<String>()
        val receivedSender = AtomicReference<InetAddress>()

        serverB.startListening { message, senderAddress ->
            receivedMessage.set(message)
            receivedSender.set(senderAddress)
            latch.countDown()
        }

        serverA.send("hello world")

        assertTrue(latch.await(4, TimeUnit.SECONDS), "Expected message was not received in time")
        assertEquals("hello world", receivedMessage.get())
        assertEquals(loopback, receivedSender.get())
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `send delivers a raw byte array message`() {
        val portA = freePort()
        val portB = freePort()

        val serverA = newServer(sendPort = portB, listenPort = portA)
        val serverB = newServer(sendPort = portA, listenPort = portB)

        val latch = CountDownLatch(1)
        val receivedMessage = AtomicReference<String>()

        serverB.startListening { message, _ ->
            receivedMessage.set(message)
            latch.countDown()
        }

        serverA.send("raw bytes".toByteArray(Charsets.UTF_8))

        assertTrue(latch.await(4, TimeUnit.SECONDS), "Expected message was not received in time")
        assertEquals("raw bytes", receivedMessage.get())
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `multiple messages are each delivered in order`() {
        val portA = freePort()
        val portB = freePort()

        val serverA = newServer(sendPort = portB, listenPort = portA)
        val serverB = newServer(sendPort = portA, listenPort = portB)

        val messagesToSend = listOf("one", "two", "three")
        val latch = CountDownLatch(messagesToSend.size)
        val received = mutableListOf<String>()

        serverB.startListening { message, _ ->
            synchronized(received) { received.add(message) }
            latch.countDown()
        }

        messagesToSend.forEach { serverA.send(it) }

        assertTrue(latch.await(4, TimeUnit.SECONDS), "Not all messages were received in time")
        assertEquals(messagesToSend, received)
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `stopListening halts further message processing`() {
        val portA = freePort()
        val portB = freePort()

        val serverA = newServer(sendPort = portB, listenPort = portA)
        val serverB = newServer(sendPort = portA, listenPort = portB)

        val firstMessageLatch = CountDownLatch(1)
        val receivedCount = java.util.concurrent.atomic.AtomicInteger(0)

        serverB.startListening { _, _ ->
            receivedCount.incrementAndGet()
            firstMessageLatch.countDown()
        }

        serverA.send("first")
        assertTrue(firstMessageLatch.await(4, TimeUnit.SECONDS), "First message was not received in time")

        serverB.stopListening()
        // Give the listener thread a moment to actually exit after the socket closes.
        Thread.sleep(200)

        // Sending after stopListening should not throw, and should not be processed by serverB.
        serverA.send("second")
        Thread.sleep(300)

        assertEquals(1, receivedCount.get(), "No messages should be processed after stopListening")
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `close stops listening and releases both sockets`() {
        val portA = freePort()
        val portB = freePort()

        val serverA = newServer(sendPort = portB, listenPort = portA)
        val serverB = newServer(sendPort = portA, listenPort = portB)

        val latch = CountDownLatch(1)
        serverB.startListening { _, _ -> latch.countDown() }

        serverB.close()

        // After close, sending to the now-closed listen port should not be received.
        serverA.send("should not arrive")
        assertFalse(latch.await(1, TimeUnit.SECONDS), "Message should not be received after close()")

        // Sending from the closed server should now fail because its send socket is closed.
        try {
            serverB.send("anything")
            throw AssertionError("Expected an exception when sending from a closed server")
        } catch (e: java.io.IOException) {
            // expected: the underlying DatagramSocket is closed
        }
    }
}