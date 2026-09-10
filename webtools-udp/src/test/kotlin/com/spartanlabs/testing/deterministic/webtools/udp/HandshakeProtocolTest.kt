package com.spartanlabs.testing.deterministic.webtools.udp

import com.spartanlabs.webtools.udp.Handshake
import com.spartanlabs.webtools.udp.HandshakeProtocol
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 4a - exhaustive input -> output mapping for every pure function in HandshakeProtocol.
@Tag("deterministic")
class HandshakeProtocolTest {

    // --- parseHandshake ---

    @Test
    fun `parseHandshake returns name and empty credential for a clean Iam`() {
        assertEquals(Handshake("bob", ""), HandshakeProtocol.parseHandshake(listOf("Iam", "bob")).getOrThrow())
    }

    @Test
    fun `parseHandshake returns name and the opaque credential token`() {
        assertEquals(
            Handshake("bob", "tok"),
            HandshakeProtocol.parseHandshake(listOf("Iam", "bob", "tok")).getOrThrow(),
        )
    }

    @Test
    fun `parseHandshake keeps only name and credential when extra tokens follow`() {
        assertEquals(
            Handshake("bob", "tok"),
            HandshakeProtocol.parseHandshake(listOf("Iam", "bob", "tok", "x", "y")).getOrThrow(),
        )
        assertEquals(2, HandshakeProtocol.extraTokenCount(listOf("Iam", "bob", "tok", "x", "y")))
    }

    @Test
    fun `parseHandshake rejects a verb-only line`() {
        assertTrue(HandshakeProtocol.parseHandshake(listOf("Iam")).isFailure)
    }

    @Test
    fun `parseHandshake rejects an empty token list`() {
        assertTrue(HandshakeProtocol.parseHandshake(emptyList()).isFailure)
    }

    @Test
    fun `parseHandshake rejects a non-Iam verb`() {
        assertTrue(HandshakeProtocol.parseHandshake(listOf("Nope", "bob")).isFailure)
    }

    @Test
    fun `parseHandshake failure carries an IllegalArgumentException`() {
        assertIs<IllegalArgumentException>(HandshakeProtocol.parseHandshake(listOf("Iam")).exceptionOrNull())
    }

    // --- extraTokenCount ---

    @Test
    fun `extraTokenCount truth table shifted by the credential slot`() {
        assertEquals(0, HandshakeProtocol.extraTokenCount(listOf("Iam", "bob")))
        assertEquals(0, HandshakeProtocol.extraTokenCount(listOf("Iam", "bob", "tok")))
        assertEquals(1, HandshakeProtocol.extraTokenCount(listOf("Iam", "bob", "tok", "z")))
    }

    @Test
    fun `extraTokenCount counts every token past the credential slot`() {
        assertEquals(2, HandshakeProtocol.extraTokenCount(listOf("Iam", "bob", "tok", "x", "y")))
    }

    @Test
    fun `extraTokenCount never goes negative`() {
        assertEquals(0, HandshakeProtocol.extraTokenCount(listOf("Iam")))
        assertEquals(0, HandshakeProtocol.extraTokenCount(emptyList()))
    }

    // --- reply / keepalive tokens ---

    @Test
    fun `REGISTERED_REPLY and KEEPALIVE_TOKEN are the exact literals`() {
        assertEquals("REGISTERED", HandshakeProtocol.REGISTERED_REPLY)
        assertEquals("KA", HandshakeProtocol.KEEPALIVE_TOKEN)
    }

    // --- isHandshake ---

    @Test
    fun `isHandshake truth table`() {
        assertTrue(HandshakeProtocol.isHandshake(listOf("Iam", "x")))
        assertTrue(HandshakeProtocol.isHandshake(listOf("Iam")))
        assertFalse(HandshakeProtocol.isHandshake(listOf("iam", "x")))
        assertFalse(HandshakeProtocol.isHandshake(listOf("")))
        assertFalse(HandshakeProtocol.isHandshake(listOf("HELLO")))
        assertFalse(HandshakeProtocol.isHandshake(listOf("KA")))
        assertFalse(HandshakeProtocol.isHandshake(emptyList()))
    }

    // --- isKeepAlive ---

    @Test
    fun `isKeepAlive truth table`() {
        assertTrue(HandshakeProtocol.isKeepAlive("KA"))
        assertFalse(HandshakeProtocol.isKeepAlive("ka"))
        assertFalse(HandshakeProtocol.isKeepAlive("KA x"))
        assertFalse(HandshakeProtocol.isKeepAlive(""))
        assertFalse(HandshakeProtocol.isKeepAlive("Iam x"))
    }

    // --- isProbeRequest / isProbeReply (delegate to HandshakeWireFormat) ---

    @Test
    fun `isProbeRequest truth table`() {
        assertTrue(HandshakeProtocol.isProbeRequest("PING"))
        assertTrue(HandshakeProtocol.isProbeRequest("PING 42"))
        assertFalse(HandshakeProtocol.isProbeRequest("ping"))
        assertFalse(HandshakeProtocol.isProbeRequest("PINGX"))
        assertFalse(HandshakeProtocol.isProbeRequest(""))
        assertFalse(HandshakeProtocol.isProbeRequest("PONG 42"))
    }

    @Test
    fun `isProbeReply truth table`() {
        assertTrue(HandshakeProtocol.isProbeReply("PONG"))
        assertTrue(HandshakeProtocol.isProbeReply("PONG 42"))
        assertFalse(HandshakeProtocol.isProbeReply("pong"))
        assertFalse(HandshakeProtocol.isProbeReply("PONGX"))
        assertFalse(HandshakeProtocol.isProbeReply(""))
        assertFalse(HandshakeProtocol.isProbeReply("PING 42"))
    }
}
