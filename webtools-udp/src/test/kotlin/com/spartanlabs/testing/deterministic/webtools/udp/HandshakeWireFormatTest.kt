package com.spartanlabs.testing.deterministic.webtools.udp

import com.spartanlabs.webtools.udp.HandshakeWireFormat
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

    // --- probe helpers ---

    @Test
    fun `probeRequestMessage and probeReplyMessage build the verb plus token`() {
        assertEquals("PING 42", HandshakeWireFormat.probeRequestMessage("42"))
        assertEquals("PONG 42", HandshakeWireFormat.probeReplyMessage("42"))
    }

    @Test
    fun `isProbeRequest truth table`() {
        assertTrue(HandshakeWireFormat.isProbeRequest("PING"))
        assertTrue(HandshakeWireFormat.isProbeRequest("PING 42"))
        assertFalse(HandshakeWireFormat.isProbeRequest("PINGX"))
        assertFalse(HandshakeWireFormat.isProbeRequest("hello"))
        assertFalse(HandshakeWireFormat.isProbeRequest("PONG 42"))
    }

    @Test
    fun `isProbeReply truth table`() {
        assertTrue(HandshakeWireFormat.isProbeReply("PONG"))
        assertTrue(HandshakeWireFormat.isProbeReply("PONG 42"))
        assertFalse(HandshakeWireFormat.isProbeReply("PONGX"))
        assertFalse(HandshakeWireFormat.isProbeReply("hello"))
        assertFalse(HandshakeWireFormat.isProbeReply("PING 42"))
    }

    @Test
    fun `probeToken truth table`() {
        assertEquals("42", HandshakeWireFormat.probeToken("PING 42"))
        // Leading / trailing / multi-space around the token are all trimmed away.
        assertEquals("42", HandshakeWireFormat.probeToken("PONG  42 "))
        assertEquals("", HandshakeWireFormat.probeToken("PING"))
        // Edge case: the token text is itself "PONG". removePrefix strips the leading
        // "PING", the second removePrefix does not match (the remainder starts with a
        // space), and trim() yields the bare word - locked in as current behaviour.
        assertEquals("PONG", HandshakeWireFormat.probeToken("PING PONG"))
    }

    @Test
    fun `probeToken round-trips the token built by probeRequestMessage`() {
        for (token in listOf("0", "42", "4711", "9007199254740993")) {
            assertEquals(token, HandshakeWireFormat.probeToken(HandshakeWireFormat.probeRequestMessage(token)))
        }
    }
}
