package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakeConnection
import com.spartanlabs.webtools.udp.Registration
import com.spartanlabs.webtools.udp.Registrations
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
    fun `onBytes defaults null and is settable independently of onMessage`() {
        val entry = registration(1000)
        assertNull(entry.onBytes)

        val bytesHandler: (ByteArray) -> Unit = {}
        entry.onBytes = bytesHandler
        assertNotNull(entry.onBytes)
        // At the Registration level, setting onBytes does not touch onMessage - the
        // mutual-exclusion nulling lives in HandshakeCoordinator.bind / bindBytes.
        assertNull(entry.onMessage)

        val textHandler: (String) -> Unit = {}
        entry.onMessage = textHandler
        assertNotNull(entry.onMessage)
        assertNotNull(entry.onBytes)
    }

    @Test
    fun `lastInboundAt is seeded near nanoTime at construction and is independently mutable`() {
        val before = System.nanoTime()
        val entry = registration(1000)
        val after = System.nanoTime()

        assertTrue(entry.lastInboundAt in before..after, "seeded within the construction window")

        entry.lastInboundAt = 123L
        assertEquals(123L, entry.lastInboundAt)
    }

    @Test
    fun `lastOutboundAt is seeded near nanoTime at construction and is independently mutable`() {
        val before = System.nanoTime()
        val entry = registration(1000)
        val after = System.nanoTime()

        assertTrue(entry.lastOutboundAt in before..after, "seeded within the construction window")

        entry.lastOutboundAt = 456L
        assertEquals(456L, entry.lastOutboundAt)
        // independent of lastInboundAt
        assertTrue(entry.lastInboundAt != 456L)
    }

    @Test
    fun `timedOut defaults false and is settable`() {
        val entry = registration(1000)
        assertFalse(entry.timedOut)
        entry.timedOut = true
        assertTrue(entry.timedOut)
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
    fun `findByName returns the matching entry`() {
        val registrations = Registrations()
        registrations.add(registration(1000))

        assertSame(
            registrations.snapshot().single(),
            registrations.findByName("client-1000"),
        )
    }

    @Test
    fun `findByName returns null when no name matches`() {
        val registrations = Registrations()
        registrations.add(registration(1000))

        assertNull(registrations.findByName("nobody"))
    }

    @Test
    fun `removeByOrigin removes the matching entry and returns true`() {
        val registrations = Registrations()
        registrations.add(registration(1000))
        registrations.add(registration(2000))

        assertTrue(registrations.removeByOrigin(InetSocketAddress(loopback, 1000)))

        assertEquals(1, registrations.size)
        assertNull(registrations.findByOrigin(InetSocketAddress(loopback, 1000)))
        assertEquals(listOf(2000), registrations.snapshot().map { it.origin.port })
    }

    @Test
    fun `removeByOrigin returns false and leaves the list untouched when nothing matches`() {
        val registrations = Registrations()
        registrations.add(registration(1000))

        assertFalse(registrations.removeByOrigin(InetSocketAddress(loopback, 9999)))

        assertEquals(1, registrations.size)
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
