package com.spartanlabs.testing.gating.webtools

import com.spartanlabs.webtools.HandshakeWireFormat
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Level 1 - a fast pre-commit smoke over the published wire-format subset. No I/O, sub-millisecond.
// The exhaustive input->output mapping lives in the Level 4a deterministic suite.
@Tag("gating")
class HandshakeWireFormatGatingTest {

    @Test
    fun `the published tokens are the exact literals`() {
        assertEquals("Iam", HandshakeWireFormat.HANDSHAKE_VERB)
        assertEquals("REGISTERED", HandshakeWireFormat.REGISTERED_REPLY)
        assertEquals("KA", HandshakeWireFormat.KEEPALIVE_TOKEN)
    }

    @Test
    fun `handshakeMessage builds Iam plus name`() {
        assertEquals("Iam alice", HandshakeWireFormat.handshakeMessage("alice"))
    }

    @Test
    fun `isRegistered matches only the bare REGISTERED token`() {
        assertTrue(HandshakeWireFormat.isRegistered("REGISTERED"))
        assertFalse(HandshakeWireFormat.isRegistered("registered"))
    }

    @Test
    fun `isKeepAlive matches only the bare KA token`() {
        assertTrue(HandshakeWireFormat.isKeepAlive("KA"))
        assertFalse(HandshakeWireFormat.isKeepAlive("Iam x"))
    }
}
