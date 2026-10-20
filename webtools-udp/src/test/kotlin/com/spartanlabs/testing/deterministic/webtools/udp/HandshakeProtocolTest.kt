package com.spartanlabs.testing.deterministic.webtools.udp

import com.spartanlabs.webtools.udp.Handshake
import com.spartanlabs.webtools.udp.HandshakeDefect
import com.spartanlabs.webtools.udp.HandshakeParse
import com.spartanlabs.webtools.udp.HandshakeProtocol
import com.spartanlabs.webtools.udp.HandshakeWireFormat
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 4a - exhaustive input -> output mapping for every pure function in HandshakeProtocol.
@Tag("deterministic")
class HandshakeProtocolTest {

    private fun parse(vararg tokens: String): HandshakeParse = HandshakeProtocol.parseHandshake(tokens.toList())

    private fun malformed(defect: HandshakeDefect) = HandshakeParse.Malformed(defect)

    // --- parseHandshake: valid ---

    @Test
    fun `parseHandshake returns name and empty credential for a clean Iam`() {
        assertEquals(HandshakeParse.Valid(Handshake("bob", "")), parse("Iam", "bob"))
    }

    @Test
    fun `parseHandshake returns name and the opaque credential token`() {
        assertEquals(HandshakeParse.Valid(Handshake("bob", "tok")), parse("Iam", "bob", "tok"))
    }

    @Test
    fun `parseHandshake keeps only name and credential when extra tokens follow`() {
        assertEquals(HandshakeParse.Valid(Handshake("bob", "tok")), parse("Iam", "bob", "tok", "x", "y"))
        assertEquals(2, HandshakeProtocol.extraTokenCount(listOf("Iam", "bob", "tok", "x", "y")))
    }

    @Test
    fun `parseHandshake ignores an empty token past the credential slot`() {
        assertEquals(HandshakeParse.Valid(Handshake("bob", "tok")), parse("Iam", "bob", "tok", "", "x"))
        assertEquals(HandshakeParse.Valid(Handshake("bob", "tok")), parse("Iam", "bob", "tok", ""))
        assertEquals(2, HandshakeProtocol.extraTokenCount(listOf("Iam", "bob", "tok", "", "x")))
    }

    // --- parseHandshake: malformed ---

    @Test
    fun `parseHandshake reports NAME_MISSING for a verb-only line`() {
        assertEquals(malformed(HandshakeDefect.NAME_MISSING), parse("Iam"))
    }

    @Test
    fun `parseHandshake reports NAME_EMPTY for an empty name slot`() {
        assertEquals(malformed(HandshakeDefect.NAME_EMPTY), parse("Iam", ""))
        assertEquals(malformed(HandshakeDefect.NAME_EMPTY), parse("Iam", "", "x"))
    }

    @Test
    fun `parseHandshake reports CREDENTIAL_EMPTY for a present-but-empty credential slot`() {
        assertEquals(malformed(HandshakeDefect.CREDENTIAL_EMPTY), parse("Iam", "bob", ""))
        assertEquals(malformed(HandshakeDefect.CREDENTIAL_EMPTY), parse("Iam", "bob", "", "tok"))
    }

    @Test
    fun `parseHandshake checks the name before the credential`() {
        assertEquals(malformed(HandshakeDefect.NAME_EMPTY), parse("Iam", "", ""))
        assertEquals(malformed(HandshakeDefect.NAME_EMPTY), parse("Iam", "", "", "z"))
    }

    // --- parseHandshake: not a handshake ---

    @Test
    fun `parseHandshake reports NotAHandshake for anything whose first token is not exactly Iam`() {
        assertEquals(HandshakeParse.NotAHandshake, HandshakeProtocol.parseHandshake(emptyList()))
        assertEquals(HandshakeParse.NotAHandshake, parse("Nope", "bob"))
        assertEquals(HandshakeParse.NotAHandshake, parse("iam", "bob"))
        assertEquals(HandshakeParse.NotAHandshake, parse(""))
        assertEquals(HandshakeParse.NotAHandshake, parse("HELLO"))
        assertEquals(HandshakeParse.NotAHandshake, parse("Iam\tbob"))
    }

    // --- parseHandshake: totality and the known #53 gap ---

    @Test
    fun `parseHandshake is total - every input yields one of the three outcomes`() {
        val inputs: List<List<String>> = listOf(
            emptyList(), listOf(""), listOf("Iam"), listOf("Iam", ""), listOf("Iam", "", ""),
            listOf("Iam", "bob"), listOf("Iam", "bob", ""), listOf("Iam", "bob", "tok", "", ""),
            listOf("iam"), listOf("Iam\tbob"), listOf("Iam", "\u0000", "\r\n"),
        )
        inputs.forEach { assertIs<HandshakeParse>(HandshakeProtocol.parseHandshake(it), "input: $it") }
    }

    // Known gap, tracked as #53: whitespace other than U+0020 inside a token is not rejected.
    // When #53 lands this test flips to a Malformed defect.
    @Test
    fun `parseHandshake accepts non-space whitespace inside a name - known gap tracked as 53`() {
        assertEquals(HandshakeParse.Valid(Handshake("bo\tb", "")), parse("Iam", "bo\tb"))
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

    // --- reply tokens and datagrams ---

    @Test
    fun `REGISTERED_REPLY is the exact verb literal`() {
        assertEquals("REGISTERED", HandshakeProtocol.REGISTERED_REPLY)
    }

    @Test
    fun `REGISTERED_DATAGRAM decodes to the versioned accepted reply`() {
        assertEquals("REGISTERED 2", String(HandshakeProtocol.REGISTERED_DATAGRAM, Charsets.UTF_8))
    }

    @Test
    fun `MALFORMED_REFUSAL_DATAGRAM is the 17-byte REFUSED malformed reply`() {
        val reply = String(HandshakeProtocol.MALFORMED_REFUSAL_DATAGRAM, Charsets.UTF_8)
        assertEquals("REFUSED malformed", reply)
        assertEquals(17, HandshakeProtocol.MALFORMED_REFUSAL_DATAGRAM.size)
        assertTrue(HandshakeWireFormat.isRefused(reply))
        assertEquals("malformed", HandshakeWireFormat.refusalReason(reply))
    }

    @Test
    fun `HandshakeDefect labels are the fixed log vocabulary`() {
        assertEquals(
            listOf("name missing", "name empty", "credential empty"),
            HandshakeDefect.entries.map { it.label },
        )
    }

    // --- isHandshake ---

    @Test
    fun `isHandshake truth table`() {
        assertTrue(HandshakeProtocol.isHandshake(listOf("Iam", "x")))
        assertTrue(HandshakeProtocol.isHandshake(listOf("Iam")))
        assertTrue(HandshakeProtocol.isHandshake(listOf("Iam", "")))
        assertFalse(HandshakeProtocol.isHandshake(listOf("iam", "x")))
        assertFalse(HandshakeProtocol.isHandshake(listOf("")))
        assertFalse(HandshakeProtocol.isHandshake(listOf("HELLO")))
        assertFalse(HandshakeProtocol.isHandshake(listOf("KA")))
        assertFalse(HandshakeProtocol.isHandshake(listOf("Iam\tbob")))
        assertFalse(HandshakeProtocol.isHandshake(emptyList()))
    }
}
