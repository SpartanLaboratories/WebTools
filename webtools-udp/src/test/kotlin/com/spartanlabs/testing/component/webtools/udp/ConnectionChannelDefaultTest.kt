package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.FakeConnection
import com.spartanlabs.webtools.udp.DeliveryMode
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 2 - Connection.channel(mode)'s DEFAULT BODY in isolation, over FakeConnection (which
// deliberately does not override channel - see FakeConnection's KDoc). Locks the two guarantees
// every existing Connection implementation gets for free: a working UNRELIABLE handle over
// push/actuateBytes, and a failing RELIABLE_ORDERED handle.
@Tag("component")
class ConnectionChannelDefaultTest {

    @Test
    fun `channel UNRELIABLE send bytes reaches push ByteArray verbatim`() {
        val connection = FakeConnection("c")
        val payload = byteArrayOf(1, 2, 3)

        assertTrue(connection.channel(DeliveryMode.UNRELIABLE).send(payload).isSuccess)

        assertEquals(listOf(payload), connection.pushedBytes)
    }

    @Test
    fun `channel UNRELIABLE actuateBytes reaches actuateBytes - assert on lastOnBytes, not the shared counter`() {
        val connection = FakeConnection("c")
        val handler: (ByteArray) -> Unit = {}

        assertTrue(connection.channel(DeliveryMode.UNRELIABLE).actuateBytes(handler).isSuccess)

        assertEquals(handler, connection.lastOnBytes)
    }

    @Test
    fun `channel UNRELIABLE actuate delivers the trimmed UTF-8 string - observationally identical to Connection actuate`() {
        val connection = FakeConnection("c")
        val received = mutableListOf<String>()

        connection.channel(DeliveryMode.UNRELIABLE).actuate { received += it }
        // Drive the bound bytes handler as the transport would, with an untrimmed payload.
        connection.lastOnBytes!!.invoke("  hello  ".toByteArray(Charsets.UTF_8))

        assertEquals(listOf("hello"), received)
    }

    @Test
    fun `channel RELIABLE_ORDERED send and actuateBytes fail with UnsupportedOperationException`() {
        val connection = FakeConnection("c")
        val reliable = connection.channel(DeliveryMode.RELIABLE_ORDERED)

        val sendResult = reliable.send(byteArrayOf(1))
        val actuateResult = reliable.actuateBytes {}

        assertTrue(sendResult.isFailure)
        assertIs<UnsupportedOperationException>(sendResult.exceptionOrNull())
        assertTrue(actuateResult.isFailure)
        assertIs<UnsupportedOperationException>(actuateResult.exceptionOrNull())
    }

    @Test
    fun `channel RELIABLE_ORDERED reports the RELIABLE_ORDERED mode`() {
        val connection = FakeConnection("c")

        assertEquals(DeliveryMode.RELIABLE_ORDERED, connection.channel(DeliveryMode.RELIABLE_ORDERED).mode)
    }
}
