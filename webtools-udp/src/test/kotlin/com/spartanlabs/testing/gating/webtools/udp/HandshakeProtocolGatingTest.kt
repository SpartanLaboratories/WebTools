package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.webtools.udp.HandshakeDefect
import com.spartanlabs.webtools.udp.HandshakeParse
import com.spartanlabs.webtools.udp.HandshakeProtocol
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 1 - a fast pre-commit smoke over the handshake rules. No I/O, sub-millisecond.
// The exhaustive input->output mapping lives in the Level 4a deterministic suite.
@Tag("gating")
class HandshakeProtocolGatingTest {

    @Test
    fun `a well-formed Iam parses to its name`() {
        val parsed = assertIs<HandshakeParse.Valid>(HandshakeProtocol.parseHandshake(listOf("Iam", "alice")))
        assertEquals("alice", parsed.handshake.name)
    }

    @Test
    fun `a well-formed Iam with a credential parses to name and credential`() {
        val parsed = assertIs<HandshakeParse.Valid>(HandshakeProtocol.parseHandshake(listOf("Iam", "alice", "tok")))
        assertEquals("alice", parsed.handshake.name)
        assertEquals("tok", parsed.handshake.credential)
    }

    @Test
    fun `a nameless Iam is malformed with the name-missing defect`() {
        assertEquals(
            HandshakeParse.Malformed(HandshakeDefect.NAME_MISSING),
            HandshakeProtocol.parseHandshake(listOf("Iam")),
        )
    }

    @Test
    fun `an Iam with an empty name slot is malformed with the name-empty defect`() {
        assertEquals(
            HandshakeParse.Malformed(HandshakeDefect.NAME_EMPTY),
            HandshakeProtocol.parseHandshake(listOf("Iam", "", "x")),
        )
    }

    @Test
    fun `the handshake reply verb is REGISTERED and the datagram is versioned`() {
        assertEquals("REGISTERED", HandshakeProtocol.REGISTERED_REPLY)
        assertEquals("REGISTERED 2", String(HandshakeProtocol.REGISTERED_DATAGRAM, Charsets.UTF_8))
    }

    @Test
    fun `the malformed refusal datagram is REFUSED malformed`() {
        assertEquals("REFUSED malformed", String(HandshakeProtocol.MALFORMED_REFUSAL_DATAGRAM, Charsets.UTF_8))
    }

    @Test
    fun `isHandshake matches only an Iam-led token list`() {
        assertTrue(HandshakeProtocol.isHandshake(listOf("Iam", "alice")))
        assertFalse(HandshakeProtocol.isHandshake(listOf("HELLO", "there")))
        assertFalse(HandshakeProtocol.isHandshake(emptyList()))
    }
}
