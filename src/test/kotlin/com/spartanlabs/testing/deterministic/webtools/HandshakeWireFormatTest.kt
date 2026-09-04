package com.spartanlabs.testing.deterministic.webtools

import com.spartanlabs.webtools.HandshakeWireFormat
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Level 4a - exhaustive input -> output mapping for every pure function in HandshakeWireFormat.
@Tag("deterministic")
class HandshakeWireFormatTest {

    // --- handshakeMessage ---

    @Test
    fun `handshakeMessage prefixes an ordinary name with the handshake verb`() {
        assertEquals("Iam bob", HandshakeWireFormat.handshakeMessage("bob"))
    }

    // --- isRegistered ---

    @Test
    fun `isRegistered truth table`() {
        assertTrue(HandshakeWireFormat.isRegistered("REGISTERED"))
        assertFalse(HandshakeWireFormat.isRegistered("registered"))
        assertFalse(HandshakeWireFormat.isRegistered(""))
        assertFalse(HandshakeWireFormat.isRegistered("REGISTERED extra"))
    }

    // --- isKeepAlive ---

    @Test
    fun `isKeepAlive truth table`() {
        assertTrue(HandshakeWireFormat.isKeepAlive("KA"))
        assertFalse(HandshakeWireFormat.isKeepAlive("ka"))
        assertFalse(HandshakeWireFormat.isKeepAlive("KA x"))
        assertFalse(HandshakeWireFormat.isKeepAlive(""))
        assertFalse(HandshakeWireFormat.isKeepAlive("Iam x"))
    }
}
