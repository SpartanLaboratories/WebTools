package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.UrlCorpus
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlParseException
import com.spartanlabs.webtools.http.UrlProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

/**
 * Level 4a - the IPv6 table of `research/url-parsing.md` F6: forms, RFC 5952 output, the tie and
 * single-zero rules, mapped addresses, NAT64 and IPv4-compatible addresses staying IPv6, zones and
 * malformed forms rejected, and `[1:2:3:4:5:6:7::8]` rejected rather than crashing.
 */
class UrlIpv6DeterministicTest {

    private fun hostOf(literal: String): String =
        Url.parse("http://$literal/").getOrElse { fail("$literal was rejected: ${it.message}") }.host

    private fun textOf(literal: String): String =
        Url.parse("http://$literal/").getOrElse { fail("$literal was rejected: ${it.message}") }.toString()

    private fun rejected(literal: String) {
        val exception = Url.parse("http://$literal/").exceptionOrNull()
        assertIs<UrlParseException>(exception, "$literal was accepted")
        assertEquals(UrlProblem.Host, exception.problem, literal)
    }

    @Test
    fun `loopback and unspecified forms collapse to RFC 5952`() {
        listOf("[::1]", "[0:0:0:0:0:0:0:1]", "[0000:0000:0000:0000:0000:0000:0000:0001]").forEach {
            assertEquals("::1", hostOf(it), it)
            assertEquals("http://[::1]/", textOf(it), it)
        }
        listOf("[::]", "[0::]", "[::0:0]", "[0:0:0:0:0:0:0:0]").forEach {
            assertEquals("::", hostOf(it), it)
            assertEquals("http://[::]/", textOf(it), it)
        }
    }

    @Test
    fun `hex is lower-case, the longest zero run wins and the first run wins a tie`() {
        assertEquals("2001:db8::1", hostOf("[2001:DB8::1]"))
        assertEquals("2001:db8::1:0:0:1", hostOf("[2001:db8:0:0:1:0:0:1]"))
        assertEquals("2001:0:0:1::1", hostOf("[2001:0:0:1:0:0:0:1]"))
    }

    @Test
    fun `a single zero group is not compressed`() {
        assertEquals("1:2:3:4:5:6:7:0", hostOf("[1:2:3:4:5:6:7::]"))
        assertEquals("http://[1:2:3:4:5:6:7:0]/", textOf("[1:2:3:4:5:6:7::]"))
    }

    @Test
    fun `an IPv4-mapped address prints as dotted IPv4 without brackets`() {
        listOf("[::ffff:127.0.0.1]", "[::ffff:7f00:1]", "[::FFFF:7F00:1]").forEach {
            assertEquals("127.0.0.1", hostOf(it), it)
            assertEquals("http://127.0.0.1/", textOf(it), it)
        }
        assertEquals("0.0.0.0", hostOf("[::ffff:0:0]"))
        assertEquals("169.254.169.254", hostOf("[::ffff:a9fe:a9fe]"))
    }

    @Test
    fun `IPv4-compatible and NAT64 addresses stay IPv6`() {
        assertEquals("::102:304", hostOf("[::1.2.3.4]"))
        assertEquals("http://[::102:304]/", textOf("[::1.2.3.4]"))
        assertEquals("64:ff9b::102:304", hostOf("[64:ff9b::1.2.3.4]"))
        assertEquals("1:2:3:4:5:6:102:304", hostOf("[1:2:3:4:5:6:1.2.3.4]"))
    }

    @Test
    fun `a port after the literal is read`() {
        assertEquals(80, Url.parse("http://[::1]:/").getOrThrow().port)
        assertEquals(8080, Url.parse("http://[::1]:8080/").getOrThrow().port)
        assertEquals("http://[::1]:8080/", Url.parse("http://[::1]:8080/").getOrThrow().toString())
    }

    @Test
    fun `zones and malformed literals are rejected`() {
        listOf(
            "[fe80::1%25eth0]", "[fe80::1%eth0]", "[::1", "[]", "[:::1]", "[1::2::3]", "[g::1]", "[12345::]", "[1.2.3.4]",
            "[::1.2.3.256]", "[::01.2.3.4]", "[::ffff:1.2.3]", "[1:2:3:4:5:6:7:8:9]", "[::1]x", "[v1.x]", "[1:2:3:4:5:6:7]",
            "[1.2.3.4::]", "[::ffff:1.2.3.4:5]",
        ).forEach { rejected(it) }
    }

    @Test
    fun `the literal that crashes HttpUrl is rejected`() {
        rejected("[1:2:3:4:5:6:7::8]")
        UrlCorpus.crash.forEach { input ->
            val exception = Url.parse(input).exceptionOrNull()
            assertIs<UrlParseException>(exception, "$input was accepted")
            assertEquals(UrlProblem.Host, exception.problem, input)
        }
    }

    @Test
    fun `an unbracketed IPv6 text is read as a host and a port`() {
        val exception = Url.parse("http://::1/").exceptionOrNull()
        assertIs<UrlParseException>(exception)
        assertEquals(UrlProblem.Port, exception.problem)
    }
}
