package com.spartanlabs.testing.component.webtools

import com.spartanlabs.testing.support.webtools.FakeConnection
import com.spartanlabs.webtools.Registration
import com.spartanlabs.webtools.Registrations
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

// Level 2 - the registration collection in isolation; FakeConnection keeps it socket-free.
@Tag("component")
class RegistrationsTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private fun registration(port: Int) =
        Registration(FakeConnection("client-$port", InetSocketAddress(loopback, port)))

    @Test
    fun `add grows the size`() {
        val registrations = Registrations()
        assertEquals(0, registrations.size)

        registrations.add(registration(1000))
        registrations.add(registration(2000))

        assertEquals(2, registrations.size)
    }

    @Test
    fun `origin reads through to the connection peer`() {
        val entry = registration(1234)
        assertEquals(InetSocketAddress(loopback, 1234), entry.origin)
    }

    @Test
    fun `onMessage defaults null and is settable`() {
        val entry = registration(1000)
        assertNull(entry.onMessage)
        val handler: (String) -> Unit = {}
        entry.onMessage = handler
        assertNotNull(entry.onMessage)
    }

    @Test
    fun `findByOrigin matches by value, not object identity`() {
        val registrations = Registrations()
        registrations.add(registration(1000))

        assertSame(
            registrations.snapshot().single(),
            registrations.findByOrigin(InetSocketAddress(loopback, 1000)),
        )
    }

    @Test
    fun `findByOrigin returns null for an unregistered origin`() {
        val registrations = Registrations()
        registrations.add(registration(1000))

        assertNull(registrations.findByOrigin(InetSocketAddress(loopback, 9999)))
    }

    @Test
    fun `snapshot preserves insertion order and is a detached copy`() {
        val registrations = Registrations()
        registrations.add(registration(1))
        registrations.add(registration(2))

        val snapshot = registrations.snapshot()
        registrations.add(registration(3))

        assertEquals(listOf(1, 2), snapshot.map { it.origin.port })
    }
}
