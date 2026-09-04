package com.spartanlabs.testing.component.webtools

import com.spartanlabs.testing.support.webtools.FakeConnection
import com.spartanlabs.webtools.Registration
import com.spartanlabs.webtools.Registrations
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

// Level 2 - the registration collection in isolation; FakeConnection keeps it socket-free.
@Tag("component")
class RegistrationsTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private fun registration(port: Int) =
        Registration(FakeConnection("client-$port", port, port + 1, loopback), InetSocketAddress(loopback, port))

    @Test
    fun `add grows the size`() {
        val registrations = Registrations()
        assertEquals(0, registrations.size)

        registrations.add(registration(1000))
        registrations.add(registration(2000))

        assertEquals(2, registrations.size)
    }

    @Test
    fun `findByOrigin returns the entry with that origin`() {
        val registrations = Registrations()
        val first = registration(1000)
        val second = registration(2000)
        registrations.add(first)
        registrations.add(second)

        assertSame(second, registrations.findByOrigin(InetSocketAddress(loopback, 2000)))
    }

    @Test
    fun `findByOrigin matches by value, not object identity`() {
        val registrations = Registrations()
        registrations.add(registration(1000))

        // A freshly constructed but equal InetSocketAddress still matches.
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

        assertEquals(listOf(1, 2), snapshot.map { it.origin.port }, "order preserved, later add not visible")
    }
}
