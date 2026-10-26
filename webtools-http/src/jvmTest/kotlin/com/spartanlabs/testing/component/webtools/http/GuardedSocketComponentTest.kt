package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.inet
import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.webtools.http.GuardedSocket
import com.spartanlabs.webtools.http.POLICY_REFUSED_IO_MESSAGE
import com.spartanlabs.webtools.http.PolicyRefusedIo
import com.spartanlabs.webtools.http.RefusalReason
import java.lang.reflect.Modifier
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.SocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Level 2 - `GuardedSocket` of an `Untrusted` guard, both `connect` overloads: a denied address, an unresolved target and
 * a `SocketAddress` that is not an `InetSocketAddress` each throw `PolicyRefusedIo` (never a `ConnectException`) with the
 * constant message before any I/O, leaving the socket unconnected and open; IPv6 and mapped addresses are judged; the
 * class declares exactly one public method, `connect`. Allowed targets reach `super.connect` only in the Level 3 tests.
 */
class GuardedSocketComponentTest {

    private val guard = untrustedGuard()

    private class NotInet : SocketAddress()

    private fun <T> withSocket(block: (GuardedSocket) -> T): T = GuardedSocket(guard).use(block)

    private fun refusal(connect: (GuardedSocket) -> Unit): PolicyRefusedIo = withSocket { socket ->
        val failure = assertFailsWith<PolicyRefusedIo> { connect(socket) }
        assertFalse(ConnectException::class.java.isInstance(failure))
        assertEquals(POLICY_REFUSED_IO_MESSAGE, failure.message)
        assertFalse(socket.isConnected)
        assertFalse(socket.isClosed)
        failure
    }

    private val overloads: List<Pair<String, (GuardedSocket, SocketAddress) -> Unit>> = listOf(
        "connect(SocketAddress, Int)" to { socket, endpoint -> socket.connect(endpoint, 1000) },
        "connect(SocketAddress)" to { socket, endpoint -> socket.connect(endpoint) },
    )

    @Test
    fun `a denied address is refused with its class`() {
        overloads.forEach { (name, connect) ->
            val marker = refusal { connect(it, InetSocketAddress(inet("10.0.0.5"), 80)) }
            assertEquals(RefusalReason.DeniedAddress, marker.refusal.reason, name)
            assertEquals("private-use", marker.refusal.rangeClass, name)
        }
    }

    @Test
    fun `an unresolved target is refused as unresolved`() {
        overloads.forEach { (name, connect) ->
            val marker = refusal { connect(it, InetSocketAddress.createUnresolved("host.example", 80)) }
            assertEquals("unresolved", marker.refusal.rangeClass, name)
        }
    }

    @Test
    fun `a SocketAddress that is not an InetSocketAddress is refused as unresolved`() {
        overloads.forEach { (name, connect) ->
            assertEquals("unresolved", refusal { connect(it, NotInet()) }.refusal.rangeClass, name)
        }
    }

    @Test
    fun `IPv6 and mapped addresses are judged`() {
        assertEquals("not-global-unicast", refusal { it.connect(InetSocketAddress(inet("fd00::1"), 80), 1000) }.refusal.rangeClass)
        assertEquals("loopback", refusal { it.connect(InetSocketAddress(inet("::ffff:127.0.0.1"), 80), 1000) }.refusal.rangeClass)
    }

    @Test
    fun `the class declares exactly one public method`() {
        val public = GuardedSocket::class.java.declaredMethods.filter { Modifier.isPublic(it.modifiers) }.map { it.name }.toSet()
        assertEquals(setOf("connect"), public)
    }
}
