package com.spartanlabs.testing.e2e.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertTrue

// Level 4b - real MultiConnectionUDPClient + real MultiConnectionUDPServer subclass over
// loopback, exercising the opt-in link-quality probe (both directions), an induced-loss
// scenario via a relay that drops every Nth client->server datagram, and non-interference
// with application traffic.
@Tag("e2e")
class MultiConnectionUDPProbeE2ETest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private class Srv : MultiConnectionUDPServer() {
        val byName = ConcurrentHashMap<String, Connection>()
        val received = ConcurrentLinkedQueue<String>()
        var armProbeOnConnect = false
        override fun onClientConnect(connection: Connection) {
            byName[connection.name] = connection
            connection.actuate { received += it }
            if (armProbeOnConnect) connection.startProbe(200L)
        }
    }

    private fun await(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    @Test
    fun `the client probe reports a sub-50ms rtt and zero loss, and the start handler never sees PING or PONG`() {
        val server = Srv().apply { armProbeOnConnect = true }
        try {
            val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
            assertTrue(client.handshake("alice").isSuccess)
            assertTrue(await(2_000L) { server.byName.containsKey("alice") })
            val seen = ConcurrentLinkedQueue<String>()
            client.start { seen += it }
            assertTrue(client.startProbe(200L).isSuccess)

            assertTrue(await(3_000L) { (client.linkQuality()?.probesDelivered ?: 0) >= 3 }, "probesDelivered climbs")
            val snap = client.linkQuality()!!
            assertTrue(snap.rttMillis in 0.0..50.0, "sub-50ms loopback RTT, was ${snap.rttMillis}")
            assertTrue(snap.packetLossRatio == 0.0, "zero loss, was ${snap.packetLossRatio}")

            // Symmetric server-side probe.
            val connection = server.byName.getValue("alice")
            assertTrue(await(3_000L) { connection.linkQuality() != null }, "server-side linkQuality populates")
            assertTrue(connection.linkQuality()!!.rttMillis in 0.0..50.0)

            assertTrue(seen.none { it == "KA" || it.startsWith("PING") || it.startsWith("PONG") },
                "probe tokens never reach the application handler")

            assertTrue(client.stop().isSuccess)
        } finally {
            assertTrue(server.stop().isSuccess)
        }
        Thread.sleep(400)
        assertTrue(Thread.getAllStackTraces().keys.none { it.name == "mcupc-probe" && it.isAlive })
        assertTrue(Thread.getAllStackTraces().keys.none { it.name == "mcups-probe" && it.isAlive })
    }

    @Test
    fun `application traffic is unaffected while the probe runs in both directions`() {
        val server = Srv().apply { armProbeOnConnect = true }
        try {
            val client = MultiConnectionUDPClient(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
            assertTrue(client.handshake("bob").isSuccess)
            assertTrue(await(2_000L) { server.byName.containsKey("bob") })
            val clientSeen = ConcurrentLinkedQueue<String>()
            // Server payloads lead with a 0x00 format tag; record the decoded text with the
            // leading tag (and any whitespace) stripped so the assertion can match on "s<i>".
            client.startBytes { bytes ->
                clientSeen += String(bytes, Charsets.UTF_8).dropWhile { c -> c.code <= 0x20 }
            }
            assertTrue(client.startProbe(200L).isSuccess)

            repeat(10) { i ->
                assertTrue(client.send("c$i").isSuccess)
                assertTrue(server.pushToAll(byteArrayOf(0x00) + "s$i".toByteArray(Charsets.UTF_8)).isSuccess)
                Thread.sleep(60)
            }

            assertTrue(await(2_000L) { server.received.count { it.startsWith("c") } >= 10 }, "server got all client payloads")
            assertTrue(await(2_000L) { clientSeen.count { it.startsWith("s") } >= 10 }, "client got all server payloads")

            assertTrue(client.stop().isSuccess)
        } finally {
            assertTrue(server.stop().isSuccess)
        }
    }

    @Test
    fun `an induced drop of every Nth client-to-server datagram pushes the loss ratio up, then it recovers`() {
        val server = Srv()
        val relay = Relay(loopback, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
        try {
            relay.start()
            val client = MultiConnectionUDPClient(loopback, relay.localPort)
            assertTrue(client.handshake("carl").isSuccess)
            assertTrue(await(2_000L) { server.byName.containsKey("carl") })
            client.start { }
            assertTrue(client.startProbe(200L).isSuccess)
            assertTrue(await(3_000L) { (client.linkQuality()?.probesDelivered ?: 0) >= 2 })

            // Drop every 2nd client->server datagram: ~50% of PINGs are lost.
            relay.dropEveryNth.set(2)
            assertTrue(
                await(8_000L) { (client.linkQuality()?.packetLossRatio ?: 0.0) >= 0.2 },
                "loss climbs under the induced drop, was ${client.linkQuality()?.packetLossRatio}",
            )

            // Stop dropping: loss recovers toward 0 as fresh probes resolve.
            relay.dropEveryNth.set(0)
            assertTrue(
                await(12_000L) { (client.linkQuality()?.packetLossRatio ?: 1.0) <= 0.05 },
                "loss recovers once drops stop, was ${client.linkQuality()?.packetLossRatio}",
            )

            assertTrue(client.stop().isSuccess)
        } finally {
            relay.stop()
            assertTrue(server.stop().isSuccess)
        }
    }

    /**
     * A minimal UDP relay: clients send to [localPort], it forwards to the real server and
     * relays replies back. [dropEveryNth] > 0 drops every Nth client->server datagram.
     */
    private class Relay(private val serverAddress: InetAddress, private val serverPort: Int) {
        private val socket = DatagramSocket()
        val localPort: Int get() = socket.localPort
        val dropEveryNth = AtomicInteger(0)
        private val clientAddr = AtomicReference<InetSocketAddress?>()
        private val forwarded = AtomicInteger(0)
        @Volatile private var running = true
        private val thread = Thread {
            socket.soTimeout = 100
            while (running) {
                val packet = DatagramPacket(ByteArray(65_507), 65_507)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: Exception) {
                    break
                }
                val from = InetSocketAddress(packet.address, packet.port)
                val fromServer = packet.address == serverAddress && packet.port == serverPort
                if (fromServer) {
                    clientAddr.get()?.let { c ->
                        runCatching { socket.send(DatagramPacket(packet.data, packet.length, c.address, c.port)) }
                    }
                } else {
                    clientAddr.set(from)
                    val n = dropEveryNth.get()
                    val count = forwarded.incrementAndGet()
                    if (n > 0 && count % n == 0) continue // drop this client->server datagram
                    runCatching {
                        socket.send(DatagramPacket(packet.data, packet.length, serverAddress, serverPort))
                    }
                }
            }
        }.apply { isDaemon = true }

        fun start() = thread.start()
        fun stop() {
            running = false
            thread.join(1_000)
            socket.close()
        }
    }
}
