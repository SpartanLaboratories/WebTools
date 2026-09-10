package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.webtools.udp.HandshakeProtocol
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
        assertEquals("alice", HandshakeProtocol.parseHandshake(listOf("Iam", "alice")).getOrThrow().name)
    }

    @Test
    fun `a well-formed Iam with a credential parses to name and credential`() {
        val parsed = HandshakeProtocol.parseHandshake(listOf("Iam", "alice", "tok")).getOrThrow()
        assertEquals("alice", parsed.name)
        assertEquals("tok", parsed.credential)
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

    @Test
    fun `isProbeRequest matches a bare or tokened PING`() {
        assertTrue(HandshakeProtocol.isProbeRequest("PING"))
        assertTrue(HandshakeProtocol.isProbeRequest("PING 42"))
        assertFalse(HandshakeProtocol.isProbeRequest("PONG 42"))
    }

    @Test
    fun `isProbeReply matches a bare or tokened PONG`() {
        assertTrue(HandshakeProtocol.isProbeReply("PONG"))
        assertTrue(HandshakeProtocol.isProbeReply("PONG 42"))
        assertFalse(HandshakeProtocol.isProbeReply("PING 42"))
    }
}
