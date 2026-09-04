package com.spartanlabs.testing.deterministic.webtools

import com.spartanlabs.webtools.HandshakeProtocol
import com.spartanlabs.webtools.HandshakeProtocol.PortPair
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    // --- portPairFor ---

    @Test
    fun `portPairFor maps the first indices to the documented pairs`() {
        assertEquals(PortPair(9997, 9996), HandshakeProtocol.portPairFor(0))
        assertEquals(PortPair(9995, 9994), HandshakeProtocol.portPairFor(1))
        assertEquals(PortPair(9993, 9992), HandshakeProtocol.portPairFor(2))
    }

    @Test
    fun `portPairFor send is always one above receive`() {
        repeat(50) { index ->
            val pair = HandshakeProtocol.portPairFor(index)
            assertEquals(pair.receivePort + 1, pair.sendPort, "at index $index")
        }
    }

    @Test
    fun `portPairFor never reuses a port across the first 100 indices`() {
        val seen = mutableSetOf<Int>()
        repeat(100) { index ->
            val pair = HandshakeProtocol.portPairFor(index)
            assertTrue(seen.add(pair.sendPort), "sendPort ${pair.sendPort} reused at index $index")
            assertTrue(seen.add(pair.receivePort), "receivePort ${pair.receivePort} reused at index $index")
        }
    }

    @Test
    fun `portPairFor rejects a negative index`() {
        assertFailsWith<IllegalArgumentException> { HandshakeProtocol.portPairFor(-1) }
    }

    // --- txrxonReply ---

    @Test
    fun `txrxonReply renders TXRXON send receive`() {
        assertEquals("TXRXON 5000 5001", HandshakeProtocol.txrxonReply(PortPair(5000, 5001)))
    }

    @Test
    fun `the loose txrxonReply overload matches the PortPair form`() {
        assertEquals(
            HandshakeProtocol.txrxonReply(PortPair(1, 2)),
            HandshakeProtocol.txrxonReply(1, 2),
        )
    }
}
