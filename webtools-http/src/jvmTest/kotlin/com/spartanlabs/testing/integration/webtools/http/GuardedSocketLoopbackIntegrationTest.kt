package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.inet
import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.webtools.http.GuardRelaxation
import com.spartanlabs.webtools.http.GuardedSocketFactory
import com.spartanlabs.webtools.http.PolicyRefusedIo
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Level 3 - `GuardedSocket` against a real loopback `ServerSocket` bound to `127.0.0.1` explicitly: under the loopback
 * relaxation a connect succeeds and the server accepts it; under `None` the connect is refused and no connection is made;
 * under the relaxation a non-loopback target and an unresolved target are still refused before any connect. Both `connect`
 * overloads are exercised.
 */
class GuardedSocketLoopbackIntegrationTest {

    private val loopback: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    private fun <T> withServer(block: (ServerSocket) -> T): T = ServerSocket(0, 50, loopback).use(block)

    private val overloads: List<Pair<String, (Socket, InetSocketAddress) -> Unit>> = listOf(
        "connect(SocketAddress, Int)" to { socket, endpoint -> socket.connect(endpoint, 2000) },
        "connect(SocketAddress)" to { socket, endpoint -> socket.connect(endpoint) },
    )

    @Test
    fun `under the loopback relaxation a connect reaches the server`() = withServer { server ->
        server.soTimeout = 2000
        val factory = GuardedSocketFactory(untrustedGuard(GuardRelaxation.LoopbackForTests))
        overloads.forEach { (name, connect) ->
            factory.createSocket().use { socket ->
                connect(socket, InetSocketAddress(loopback, server.localPort))
                assertTrue(socket.isConnected, name)
                server.accept().use { accepted -> assertTrue(accepted.isConnected, name) }
            }
        }
    }

    @Test
    fun `under None the connect is refused and no connection is made`() = withServer { server ->
        server.soTimeout = 300
        val factory = GuardedSocketFactory(untrustedGuard())
        overloads.forEach { (name, connect) ->
            factory.createSocket().use { socket ->
                val marker = assertFailsWith<PolicyRefusedIo>(name) { connect(socket, InetSocketAddress(loopback, server.localPort)) }
                assertEquals("loopback", marker.refusal.rangeClass, name)
            }
            assertFailsWith<SocketTimeoutException>(name) { server.accept() }
        }
    }

    @Test
    fun `under the relaxation a non-loopback and an unresolved target are still refused before any connect`() {
        val factory = GuardedSocketFactory(untrustedGuard(GuardRelaxation.LoopbackForTests))
        overloads.forEach { (name, connect) ->
            factory.createSocket().use { socket ->
                assertEquals("private-use", assertFailsWith<PolicyRefusedIo>(name) { connect(socket, InetSocketAddress(inet("10.0.0.5"), 80)) }.refusal.rangeClass)
            }
            factory.createSocket().use { socket ->
                assertEquals("unresolved", assertFailsWith<PolicyRefusedIo>(name) { connect(socket, InetSocketAddress.createUnresolved("host.example", 80)) }.refusal.rangeClass)
            }
        }
    }
}
