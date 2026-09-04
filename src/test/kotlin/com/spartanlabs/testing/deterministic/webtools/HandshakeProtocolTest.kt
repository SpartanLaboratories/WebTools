package com.spartanlabs.testing.deterministic.webtools

import com.spartanlabs.webtools.HandshakeProtocol
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
    fun `parseHandshake returns the name for a clean Iam`() {
        assertEquals("bob", HandshakeProtocol.parseHandshake(listOf("Iam", "bob")).getOrThrow())
    }

    @Test
    fun `parseHandshake returns only the name when extra tokens follow`() {
        assertEquals("bob", HandshakeProtocol.parseHandshake(listOf("Iam", "bob", "1.2.3.4", "x")).getOrThrow())
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
    fun `extraTokenCount is zero for a clean handshake`() {
        assertEquals(0, HandshakeProtocol.extraTokenCount(listOf("Iam", "bob")))
    }

    @Test
    fun `extraTokenCount counts every token past the name`() {
        assertEquals(2, HandshakeProtocol.extraTokenCount(listOf("Iam", "bob", "x", "y")))
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
}
