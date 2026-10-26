package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.inet
import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.webtools.http.GuardedSocket
import com.spartanlabs.webtools.http.GuardedSocketFactory
import com.spartanlabs.webtools.http.NOT_A_GUARDED_ENTRY_POINT_MESSAGE
import java.net.SocketException
import javax.net.ssl.SSLSocketFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame

/**
 * Level 2 - `GuardedSocketFactory`: `createSocket()` returns a new, unconnected, open `GuardedSocket` each call; each of
 * the four abstract overloads throws `SocketException` with the constant message and creates no socket; the factory is
 * not an `SSLSocketFactory`; `toString()` is the class name.
 */
class GuardedSocketFactoryComponentTest {

    private val factory = GuardedSocketFactory(untrustedGuard())

    @Test
    fun `createSocket returns a new unconnected open GuardedSocket each call`() {
        val first = factory.createSocket()
        val second = factory.createSocket()
        try {
            assertIs<GuardedSocket>(first)
            assertIs<GuardedSocket>(second)
            assertNotSame(first, second)
            assertFalse(first.isConnected)
            assertFalse(first.isClosed)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `the four overloads refuse with the constant message`() {
        val host = inet("8.8.8.8")
        val calls: List<() -> Unit> = listOf(
            { factory.createSocket("host.example", 80) },
            { factory.createSocket("host.example", 80, host, 0) },
            { factory.createSocket(host, 80) },
            { factory.createSocket(host, 80, host, 0) },
        )
        calls.forEachIndexed { index, call ->
            val failure = kotlin.test.assertFailsWith<SocketException>("overload $index") { call() }
            assertEquals(NOT_A_GUARDED_ENTRY_POINT_MESSAGE, failure.message, "overload $index")
        }
    }

    @Test
    fun `the factory is not an SSLSocketFactory and prints its name`() {
        assertFalse(SSLSocketFactory::class.java.isInstance(factory))
        assertEquals("GuardedSocketFactory", factory.toString())
    }
}
