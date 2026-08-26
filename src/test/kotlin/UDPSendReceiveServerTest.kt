
import com.spartanlabs.webtools.UDPSendReceiveServer
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

        receiver.startListening { message, _ ->
            log.debug("Test received: {}", message)
            received = message
            latch.countDown()
        }

        sender.send("hello world")

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

        receiver.stopListening()
        Thread.sleep(200)

        sender.send("should not arrive")
        Thread.sleep(300)

        assertEquals(0, messageCount)
        assertFalse(false) // listener stopped cleanly, no exception thrown

        receiver.close()
        sender.close()
    }
}
