package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.webtools.http.GuardRelaxation
import com.spartanlabs.webtools.http.GuardedSocketFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.parallel.Isolated
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Level 3 - a JVM-wide SOCKS setting (`socksProxyHost`, `socksProxyPort`, `socksNonProxyHosts=""`) against a fake SOCKS
 * listener on loopback that counts and closes every accepted socket. **Control:** the default factory's socket reaches the
 * listener, so the path is reachable on this JDK. **The claim:** a `GuardedSocket` (which extends `Socket(Proxy.NO_PROXY)`)
 * under the loopback relaxation connects straight to the target and the listener's count does not move. A real
 * `OkHttpClient` with `proxy(Proxy.NO_PROXY)` and the default socket factory behaves as the control: the JVM-wide setting is
 * applied at the socket layer, which the client-level proxy choice does not reach; the same counter asserts it. The class
 * sets process-wide properties and is isolated; each property is restored (or cleared) in `finally`.
 */
@Isolated
class GuardedSocketSocksIntegrationTest {

    private val loopback: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val properties = listOf("socksProxyHost", "socksProxyPort", "socksNonProxyHosts")

    // Accepts until it times out, so a stray connection from an earlier step never counts as the next step's.
    private fun drain(server: ServerSocket) {
        val previous = server.soTimeout
        server.soTimeout = 300
        try {
            while (true) server.accept().close()
        } catch (_: SocketTimeoutException) {
        } finally {
            server.soTimeout = previous
        }
    }

    @Test
    fun `a guarded socket bypasses a JVM-wide SOCKS setting that the default socket honours`() {
        val listener = ServerSocket(0, 50, loopback)
        val target = ServerSocket(0, 50, loopback)
        val hits = AtomicInteger()
        val firstHit = CountDownLatch(1)
        val acceptor = Thread {
            try {
                while (true) {
                    listener.accept().close()
                    hits.incrementAndGet()
                    firstHit.countDown()
                }
            } catch (_: IOException) {
                // the listener was closed
            }
        }.apply { isDaemon = true; start() }
        val previous = properties.associateWith { System.getProperty(it) }
        val client = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(2, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
        try {
            System.setProperty("socksProxyHost", "127.0.0.1")
            System.setProperty("socksProxyPort", listener.localPort.toString())
            System.setProperty("socksNonProxyHosts", "")   // the default list holds 127.* and would hide the effect
            val endpoint = InetSocketAddress(loopback, target.localPort)

            // Control: the default factory's socket consults the JVM-wide SOCKS setting and reaches the fake listener.
            SocketFactory.getDefault().createSocket().use { plain ->
                try {
                    plain.connect(endpoint, 2000)
                } catch (_: IOException) {
                    // the listener closed the SOCKS handshake; the connect fails, which is expected
                }
            }
            assertTrue(firstHit.await(2, TimeUnit.SECONDS), "control: the default factory's socket did not reach the SOCKS listener on this JDK")
            val afterControl = hits.get()
            assertTrue(afterControl >= 1)
            drain(target)

            // The claim: a GuardedSocket connects straight to the target and the listener's count does not move.
            target.soTimeout = 2000
            GuardedSocketFactory(untrustedGuard(GuardRelaxation.LoopbackForTests)).createSocket().use { guarded ->
                guarded.connect(endpoint, 2000)
                target.accept().use { accepted -> assertTrue(accepted.isConnected) }
            }
            assertEquals(afterControl, hits.get(), "the guarded socket reached the SOCKS listener")

            // An OkHttpClient with proxy(NO_PROXY) and the default factory behaves as the control.
            try {
                client.newCall(Request.Builder().url("http://127.0.0.1:${target.localPort}/").build()).execute().close()
            } catch (_: IOException) {
                // the SOCKS handshake fails; the call fails, which is expected
            }
            assertTrue(hits.get() > afterControl, "OkHttp's default socket with proxy(NO_PROXY) did not reach the SOCKS listener")
        } finally {
            previous.forEach { (name, value) -> if (value == null) System.clearProperty(name) else System.setProperty(name, value) }
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            listener.close()
            target.close()
            acceptor.join(2000)
        }
    }
}
