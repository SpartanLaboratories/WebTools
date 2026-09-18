package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.webtools.udp.HandshakeWireFormat
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 1 - a fast pre-commit smoke over the published handshake-bootstrap subset. No I/O, sub-millisecond.
// The exhaustive input->output mapping lives in the Level 4a deterministic suite.
@Tag("gating")
class HandshakeWireFormatGatingTest {

    @Test
    fun `the published tokens are the exact literals`() {
        assertEquals("Iam", HandshakeWireFormat.HANDSHAKE_VERB)
        assertEquals("REGISTERED", HandshakeWireFormat.REGISTERED_REPLY)
        assertEquals("REFUSED", HandshakeWireFormat.REFUSED_REPLY)
    }

    @Test
    fun `handshakeMessage builds Iam plus name, with an optional credential`() {
        assertEquals("Iam alice", HandshakeWireFormat.handshakeMessage("alice"))
        assertEquals("Iam alice", HandshakeWireFormat.handshakeMessage("alice", ""))
        assertEquals("Iam alice tok", HandshakeWireFormat.handshakeMessage("alice", "tok"))
    }

    @Test
    fun `the REFUSED reply token and its helpers`() {
        assertTrue(HandshakeWireFormat.isRefused("REFUSED"))
        assertTrue(HandshakeWireFormat.isRefused("REFUSED over capacity"))
        assertFalse(HandshakeWireFormat.isRefused("REGISTERED 2"))
        assertFalse(HandshakeWireFormat.isRefused("REFUSEDX"))
        assertEquals("over capacity", HandshakeWireFormat.refusalReason("REFUSED over capacity"))
        assertEquals("", HandshakeWireFormat.refusalReason("REFUSED"))
    }

    @Test
    fun `registeredMessage is the versioned accepted reply`() {
        assertEquals("REGISTERED 2", HandshakeWireFormat.registeredMessage())
    }

    @Test
    fun `isRegistered is strict - only REGISTERED 2`() {
        assertTrue(HandshakeWireFormat.isRegistered("REGISTERED 2"))
        assertFalse(HandshakeWireFormat.isRegistered("REGISTERED"))
        assertFalse(HandshakeWireFormat.isRegistered("registered"))
    }

    @Test
    fun `registeredProtocolVersion reads the announced major`() {
        assertEquals(1, HandshakeWireFormat.registeredProtocolVersion("REGISTERED"))
        assertEquals(2, HandshakeWireFormat.registeredProtocolVersion("REGISTERED 2"))
        assertNull(HandshakeWireFormat.registeredProtocolVersion("REGISTERED x"))
    }
}
