package com.spartanlabs.testing.integration.webtools

import com.spartanlabs.webtools.UDPConnection
import org.junit.jupiter.api.Tag
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 3 - exchanges real datagrams between two UDPConnection instances over loopback.
@Tag("integration")
class UDPConnectionTest {

    private val log = LoggerFactory.getLogger(UDPConnectionTest::class.java)
    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    @Test
    fun `two connections can exchange a message`() {
        log.info("Starting UDPConnection exchange test")
        // Connection A sends on 41300 and listens on 41301
        val connectionA = UDPConnection("A", loopback, 41300, 41301)
        // Connection B sends on 41301 (A's listen port) and listens on 41300 (A's send port)
        val connectionB = UDPConnection("B", loopback, 41301, 41300)

        val latch = CountDownLatch(1)
        var received: String? = null

        assertTrue(
            connectionB.actuate { message ->
                log.debug("Connection B received: {}", message)
                received = message
                latch.countDown()
            }.isSuccess,
            "Expected connection B to actuate"
        )

        assertTrue(connectionA.push("ping from A").isSuccess, "Expected the push to succeed")

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Expected message to be received within timeout")
        assertEquals("ping from A", received)

        assertTrue(connectionA.terminate().isSuccess, "Expected connection A to terminate cleanly")
        assertTrue(connectionB.terminate().isSuccess, "Expected connection B to terminate cleanly")
    }

    @Test
    fun `connection exposes the name and address it was constructed with`() {
        val connection = UDPConnection("named-connection", loopback, 41302, 41303)
        assertEquals("named-connection", connection.name)
        assertEquals(loopback, connection.address)
        assertTrue(connection.terminate().isSuccess)
    }
}
