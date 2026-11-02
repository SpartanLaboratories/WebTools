package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.webtools.http.MAX_RESPONSE_BYTES_MESSAGE
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.USER_AGENT_MESSAGE
import com.spartanlabs.webtools.http.isValidUserAgent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Level 2 - construction: `isValidUserAgent` accepts plain and typical agents and refuses empty, blank, space-padded, tab, newline,
 * non-ASCII and DEL; each refusal through the factory is an `IllegalArgumentException` with the constant message that never echoes the
 * offered text; `maxResponseBytes` at `Int.MAX_VALUE - 8` is accepted and one above refused with its constant message; the factory is the
 * two-argument form; `toString()` prints the policy kind and the closed flag and never the user agent; the `INFO` create line holds the
 * policy kind and the settings numbers and no user agent.
 */
class OkHttpTransportConstructionComponentTest {

    @Test
    fun `isValidUserAgent accepts visible ASCII without padding and refuses the rest`() {
        listOf("a", "webtools/1.0 (+https://x.test)").forEach { assertTrue(isValidUserAgent(it), it) }
        listOf("", " ", "  ", " a", "a ", "a\tb", "a\nb", "é", "a\u007F").forEach { assertFalse(isValidUserAgent(it), "<$it>") }
    }

    @Test
    fun `a refused user agent is an IllegalArgumentException with the constant message and no echo`() {
        listOf("", " ", " SECRET-UA", "SECRET-UA ", "SECRET\tUA", "SECRET\nUA", "SECRET-UA-é", "SECRET-UA\u007F").forEach { offered ->
            val failure = assertFailsWith<IllegalArgumentException>("<$offered>") { OkHttpTransport(offered, OutboundUrlPolicy.untrusted()) }
            assertEquals(USER_AGENT_MESSAGE, failure.message)
            assertFalse("SECRET" in failure.message!!)
        }
    }

    @Test
    fun `maxResponseBytes at the byte-array limit is accepted and one above refused`() {
        val atLimit = OutboundUrlPolicy.untrusted { settings { maxResponseBytes = Int.MAX_VALUE - 8L } }
        OkHttpTransport("agent", atLimit).close()
        val above = OutboundUrlPolicy.untrusted { settings { maxResponseBytes = Int.MAX_VALUE - 7L } }
        val failure = assertFailsWith<IllegalArgumentException> { OkHttpTransport("agent", above) }
        assertEquals(MAX_RESPONSE_BYTES_MESSAGE, failure.message)
    }

    @Test
    fun `toString prints the policy kind and the closed flag and never the user agent`() {
        val transport = OkHttpTransport("SECRET-AGENT/1.0", OutboundUrlPolicy.untrusted())
        assertEquals("OkHttpTransport(policy=Untrusted, closed=false)", transport.toString())
        transport.close()
        assertEquals("OkHttpTransport(policy=Untrusted, closed=true)", transport.toString())
        val trusted = OkHttpTransport("agent", OutboundUrlPolicy.trusted())
        assertEquals("OkHttpTransport(policy=Trusted, closed=false)", trusted.toString())
        trusted.close()
    }

    @Test
    fun `the INFO create line holds the policy kind and the settings numbers and no user agent`() {
        val (transport, events) = captureTransportLogs(Level.INFO) { OkHttpTransport("SECRET-AGENT/1.0", OutboundUrlPolicy.untrusted()) }
        transport.close()
        val created = events.filter { it.formattedMessage.startsWith("created") }
        assertEquals(1, created.size, events.map { it.formattedMessage }.toString())
        assertEquals(Level.INFO, created.single().level)
        assertEquals("com.spartanlabs.webtools.http.OkHttpTransport", created.single().loggerName)
        val text = created.single().formattedMessage
        assertTrue("policy=Untrusted" in text, text)
        assertTrue("maxRedirects=3" in text, text)
        assertTrue("maxResponseBytes=2097152" in text, text)
        assertFalse("SECRET" in text, text)
    }
}
