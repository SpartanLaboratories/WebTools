package com.spartanlabs.testing.gating.webtools

import com.spartanlabs.webtools.HandshakeProtocol
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `consecutive connections get non-overlapping ports`() {
        val first = HandshakeProtocol.portPairFor(0)
        val second = HandshakeProtocol.portPairFor(1)
        assertEquals(
            emptySet(),
            setOf(first.sendPort, first.receivePort) intersect setOf(second.sendPort, second.receivePort),
        )
    }

    @Test
    fun `the reply body has the TXRXON shape`() {
        assertEquals("TXRXON 9997 9996", HandshakeProtocol.txrxonReply(HandshakeProtocol.portPairFor(0)))
    }
}
