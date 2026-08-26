import com.spartanlabs.webtools.MultiConnectionUDPServer
import com.spartanlabs.webtools.resolveLocalAddress
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.test.Test
import kotlin.test.assertTrue

// PER_CLASS lifecycle so JUnit reuses a single test instance (and therefore a single
// MultiConnectionUDPServer bound to the fixed 9998/9999 ports) across all test methods.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiConnectionUDPServerTest {

    private val log = LoggerFactory.getLogger(MultiConnectionUDPServerTest::class.java)

    // The server binds fixed ports (9998/9999) in its constructor, so we deliberately
    // create only a single instance for the whole test class to avoid port conflicts
    // between test methods.
    private val server = MultiConnectionUDPServer()

    @Test
    fun `pushToAll does not throw when there are no connections`() {
        log.info("Verifying pushToAll is a no-op with zero connections")
        assertTrue(runCatching { server.pushToAll("no one is listening") }.isSuccess)
    }

    @Test
    fun `an Iam handshake registers a new connection and gets a TXRXON reply`() {
        log.info("Starting Iam handshake test")
        val loopback = resolveLocalAddress()

        // Listen on the server's common send port (9999) to catch the handshake reply.
        val clientListenSocket = DatagramSocket(9999)
        val clientSendSocket = DatagramSocket()

        try {
            val handshake = "Iam testclient $loopback"
            val outBytes = handshake.toByteArray(Charsets.UTF_8)
            clientSendSocket.send(DatagramPacket(outBytes, outBytes.size, loopback, 9998))

            val inBuffer = ByteArray(1024)
            val inPacket = DatagramPacket(inBuffer, inBuffer.size)
            clientListenSocket.soTimeout = 5000
            clientListenSocket.receive(inPacket)

            val reply = String(inPacket.data, 0, inPacket.length, Charsets.UTF_8).trim()
            log.debug("Received handshake reply: {}", reply)

            assertTrue(reply.contains("TXRXON"), "Expected a TXRXON reply, got: $reply")
        } finally {
            clientListenSocket.close()
            clientSendSocket.close()
        }
    }
}
