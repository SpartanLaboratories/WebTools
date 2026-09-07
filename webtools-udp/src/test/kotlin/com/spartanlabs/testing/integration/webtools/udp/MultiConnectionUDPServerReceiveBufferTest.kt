package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.captureLogsOf
import com.spartanlabs.testing.support.webtools.udp.hasWarnContaining
import com.spartanlabs.webtools.udp.CommonChannel
import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Level 3 - a MultiConnectionUDPServer constructed with a lowered receiveBufferBytes.
// Separate from MultiConnectionUDPServerTest because that class shares one server bound
// to the common port for its whole lifecycle; this one needs a server with a custom
// buffer size. Both run under the module's commonUdpPortLock (the integrationTest task),
// and JUnit runs test classes sequentially, so only one binds port 9998 at a time.
@Tag("integration")
class MultiConnectionUDPServerReceiveBufferTest {

    private val serverAddress: InetAddress = InetAddress.getLoopbackAddress()
    private val inbound = LinkedBlockingQueue<ByteArray>()
    private var server: MultiConnectionUDPServer? = null

    @AfterTest
    fun tearDown() {
        runCatching { server?.stop() }
    }

    @Test
    fun `a configured receiveBufferBytes reaches CommonChannel - a larger datagram is truncated and warned`() {
        captureLogsOf(CommonChannel::class.java) { events ->
            server = object : MultiConnectionUDPServer(2048) {
                override fun onClientConnect(connection: Connection) {
                    connection.actuateBytes { inbound.add(it) }
                }
            }

            DatagramSocket().use { client ->
                handshake(client)
                Thread.sleep(POST_HANDSHAKE_SETTLE_MILLIS)

                val payload = ByteArray(4096) { ((it % 250) + 1).toByte() }
                client.send(DatagramPacket(payload, payload.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))

                val delivered = inbound.poll(5, TimeUnit.SECONDS)
                assertNotNull(delivered, "a (truncated) datagram was delivered")
                assertTrue(delivered.size <= 2048, "delivered payload truncated to the 2048-byte buffer")
            }

            val deadline = System.currentTimeMillis() + 2000
            while (!events.hasWarnContaining("may have been truncated") && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            assertTrue(events.hasWarnContaining("may have been truncated"), "truncation WARN was logged")
        }
    }

    private fun handshake(client: DatagramSocket) {
        val iam = "Iam probe".toByteArray(Charsets.UTF_8)
        client.send(DatagramPacket(iam, iam.size, serverAddress, MultiConnectionUDPServer.COMMON_LISTEN_PORT))
        client.soTimeout = 5000
        client.receive(DatagramPacket(ByteArray(64), 64)) // REGISTERED
    }

    private companion object {
        const val POST_HANDSHAKE_SETTLE_MILLIS = 200L
    }
}
