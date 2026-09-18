package com.spartanlabs.testing.deterministic.webtools.udp

import com.spartanlabs.webtools.udp.HandshakeWireFormat
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 4a - exhaustive input -> output mapping for every pure function in HandshakeWireFormat.
@Tag("deterministic")
class HandshakeWireFormatTest {

    // --- handshakeMessage ---

    @Test
    fun `handshakeMessage prefixes an ordinary name with the handshake verb`() {
        assertEquals("Iam bob", HandshakeWireFormat.handshakeMessage("bob"))
    }

    @Test
    fun `handshakeMessage credential truth table`() {
        assertEquals("Iam bob", HandshakeWireFormat.handshakeMessage("bob"))
        assertEquals("Iam bob", HandshakeWireFormat.handshakeMessage("bob", ""))
        assertEquals("Iam bob tok", HandshakeWireFormat.handshakeMessage("bob", "tok"))
        val longToken = "A".repeat(200)
        assertEquals("Iam bob $longToken", HandshakeWireFormat.handshakeMessage("bob", longToken))
    }

    // --- REFUSED_REPLY ---

    @Test
    fun `REFUSED_REPLY is the exact literal`() {
        assertEquals("REFUSED", HandshakeWireFormat.REFUSED_REPLY)
    }

    // --- isRefused ---

    @Test
    fun `isRefused truth table`() {
        assertTrue(HandshakeWireFormat.isRefused("REFUSED"))
        assertTrue(HandshakeWireFormat.isRefused("REFUSED x"))
        assertTrue(HandshakeWireFormat.isRefused("REFUSED a b c"))
        assertFalse(HandshakeWireFormat.isRefused("REFUSEDX"))
        assertFalse(HandshakeWireFormat.isRefused("refused"))
        assertFalse(HandshakeWireFormat.isRefused(""))
        assertFalse(HandshakeWireFormat.isRefused("REGISTERED"))
        assertFalse(HandshakeWireFormat.isRefused(" REFUSED"))
    }

    // --- refusalReason ---

    @Test
    fun `refusalReason truth table`() {
        assertEquals("", HandshakeWireFormat.refusalReason("REFUSED"))
        assertEquals("full", HandshakeWireFormat.refusalReason("REFUSED full"))
        assertEquals("over   capacity", HandshakeWireFormat.refusalReason("REFUSED  over   capacity "))
    }

    // --- refusedMessage ---

    @Test
    fun `refusedMessage collapses whitespace and drops a blank reason`() {
        assertEquals("REFUSED", HandshakeWireFormat.refusedMessage(""))
        assertEquals("REFUSED", HandshakeWireFormat.refusedMessage("   "))
        assertEquals("REFUSED over capacity", HandshakeWireFormat.refusedMessage("over capacity"))
        assertEquals("REFUSED line1 line2 end", HandshakeWireFormat.refusedMessage("line1\nline2\t end"))
    }

    @Test
    fun `refusalReason round-trips a clean reason through refusedMessage`() {
        val reason = "invalid credential for guarded"
        assertEquals(reason, HandshakeWireFormat.refusalReason(HandshakeWireFormat.refusedMessage(reason)))
    }

    // --- registeredMessage ---

    @Test
    fun `registeredMessage is the exact accepted reply for this major`() {
        assertEquals("REGISTERED 2", HandshakeWireFormat.registeredMessage())
    }

    // --- registeredProtocolVersion ---

    @Test
    fun `registeredProtocolVersion truth table`() {
        assertEquals(1, HandshakeWireFormat.registeredProtocolVersion("REGISTERED"))
        assertEquals(2, HandshakeWireFormat.registeredProtocolVersion("REGISTERED 2"))
        assertEquals(3, HandshakeWireFormat.registeredProtocolVersion("REGISTERED 3"))
        // Strict (OD-4): exactly one canonical decimal token.
        assertNull(HandshakeWireFormat.registeredProtocolVersion("REGISTERED 02"))
        assertNull(HandshakeWireFormat.registeredProtocolVersion("REGISTERED 2 3"))
        assertNull(HandshakeWireFormat.registeredProtocolVersion("REGISTERED x"))
        assertNull(HandshakeWireFormat.registeredProtocolVersion("REGISTERED "))
        assertNull(HandshakeWireFormat.registeredProtocolVersion("registered"))
        assertNull(HandshakeWireFormat.registeredProtocolVersion(""))
        assertNull(HandshakeWireFormat.registeredProtocolVersion("REFUSED full"))
    }

    // --- isRegistered ---

    @Test
    fun `isRegistered is strict - only this build's exact REGISTERED 2`() {
        assertTrue(HandshakeWireFormat.isRegistered("REGISTERED 2"))
        assertFalse(HandshakeWireFormat.isRegistered("REGISTERED"))
        assertFalse(HandshakeWireFormat.isRegistered("REGISTERED 3"))
        assertFalse(HandshakeWireFormat.isRegistered("REGISTERED 02"))
        assertFalse(HandshakeWireFormat.isRegistered("registered"))
        assertFalse(HandshakeWireFormat.isRegistered(""))
        assertFalse(HandshakeWireFormat.isRegistered("REGISTERED extra"))
    }

    @Test
    fun `isRegistered round-trips registeredMessage`() {
        assertTrue(HandshakeWireFormat.isRegistered(HandshakeWireFormat.registeredMessage()))
    }
}
