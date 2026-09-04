package com.spartanlabs.testing.gating.webtools

import com.spartanlabs.webtools.HandshakeProtocol
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Level 1 - a fast pre-commit smoke over the handshake rules. No I/O, sub-millisecond.
// The exhaustive input->output mapping lives in the Level 4a deterministic suite.
@Tag("gating")
class HandshakeProtocolGatingTest {

    @Test
    fun `a well-formed Iam parses to its name`() {
        assertEquals("alice", HandshakeProtocol.parseHandshake(listOf("Iam", "alice")).getOrThrow())
    }

    @Test
    fun `a nameless Iam is rejected`() {
        assertTrue(HandshakeProtocol.parseHandshake(listOf("Iam")).isFailure)
    }

    @Test
    fun `the handshake reply is the single token REGISTERED`() {
        assertEquals("REGISTERED", HandshakeProtocol.REGISTERED_REPLY)
    }

    @Test
    fun `isHandshake matches only an Iam-led token list`() {
        assertTrue(HandshakeProtocol.isHandshake(listOf("Iam", "alice")))
        assertFalse(HandshakeProtocol.isHandshake(listOf("HELLO", "there")))
        assertFalse(HandshakeProtocol.isHandshake(emptyList()))
    }

    @Test
    fun `isKeepAlive matches only the bare KA token`() {
        assertTrue(HandshakeProtocol.isKeepAlive("KA"))
        assertFalse(HandshakeProtocol.isKeepAlive("Iam x"))
    }
}
