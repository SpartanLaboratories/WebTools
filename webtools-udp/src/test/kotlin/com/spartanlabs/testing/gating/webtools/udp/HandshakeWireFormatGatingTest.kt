package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.webtools.udp.HandshakeWireFormat
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
    fun `handshakeMessage builds Iam plus name, with an optional credential`() {
        assertEquals("Iam alice", HandshakeWireFormat.handshakeMessage("alice"))
        assertEquals("Iam alice", HandshakeWireFormat.handshakeMessage("alice", ""))
        assertEquals("Iam alice tok", HandshakeWireFormat.handshakeMessage("alice", "tok"))
    }

    @Test
    fun `the REFUSED reply token and its helpers`() {
        assertEquals("REFUSED", HandshakeWireFormat.REFUSED_REPLY)
        assertTrue(HandshakeWireFormat.isRefused("REFUSED"))
        assertTrue(HandshakeWireFormat.isRefused("REFUSED over capacity"))
        assertFalse(HandshakeWireFormat.isRefused("REGISTERED"))
        assertFalse(HandshakeWireFormat.isRefused("REFUSEDX"))
        assertEquals("over capacity", HandshakeWireFormat.refusalReason("REFUSED over capacity"))
        assertEquals("", HandshakeWireFormat.refusalReason("REFUSED"))
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

    @Test
    fun `the default keepalive interval is 20 seconds`() {
        assertEquals(20_000L, HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS)
    }

    @Test
    fun `the probe verbs and default interval are the exact literals`() {
        assertEquals("PING", HandshakeWireFormat.PROBE_REQUEST_VERB)
        assertEquals("PONG", HandshakeWireFormat.PROBE_REPLY_VERB)
        assertEquals(1_000L, HandshakeWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS)
    }

    @Test
    fun `probe request and reply helpers round-trip the token`() {
        assertEquals("PING 42", HandshakeWireFormat.probeRequestMessage("42"))
        assertEquals("PONG 42", HandshakeWireFormat.probeReplyMessage("42"))

        assertTrue(HandshakeWireFormat.isProbeRequest("PING"))
        assertTrue(HandshakeWireFormat.isProbeRequest(HandshakeWireFormat.probeRequestMessage("42")))
        assertTrue(HandshakeWireFormat.isProbeReply("PONG"))
        assertTrue(HandshakeWireFormat.isProbeReply(HandshakeWireFormat.probeReplyMessage("42")))
        assertFalse(HandshakeWireFormat.isProbeRequest("PINGX"))
        assertFalse(HandshakeWireFormat.isProbeReply("hello"))

        assertEquals("42", HandshakeWireFormat.probeToken("PING 42"))
        assertEquals("42", HandshakeWireFormat.probeToken("PONG 42"))
        assertEquals("", HandshakeWireFormat.probeToken("PING"))
        assertEquals("", HandshakeWireFormat.probeToken("PONG"))
    }
}
