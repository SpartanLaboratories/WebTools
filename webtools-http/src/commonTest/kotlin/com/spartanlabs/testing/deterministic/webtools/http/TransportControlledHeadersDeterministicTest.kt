package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.TransportControlledHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Level 4a - the table of request headers the transport controls: all twelve names are refused in every case mix, the rule text uses
 * the list's spelling and never the caller's, the first controlled entry in request order is the one named, near-misses are admitted,
 * an empty header set gives `null`, and the list holds exactly twelve distinct names.
 */
class TransportControlledHeadersDeterministicTest {

    private fun rule(configure: HttpHeaders.Builder.() -> Unit): String? = TransportControlledHeaders.ruleFor(HttpHeaders(configure))

    @Test
    fun `all twelve names are refused in every case mix and reported in the list's spelling`() {
        val mixed = mapOf(
            "user-agent" to "User-Agent", "ACCEPT-ENCODING" to "Accept-Encoding", "host" to "Host", "cOnNeCtIoN" to "Connection",
            "content-length" to "Content-Length", "transfer-encoding" to "Transfer-Encoding", "keep-alive" to "Keep-Alive",
            "proxy-connection" to "Proxy-Connection", "te" to "TE", "TRAILER" to "Trailer", "upgrade" to "Upgrade", "expect" to "Expect",
        )
        assertEquals(12, mixed.size)
        mixed.forEach { (written, canonical) ->
            assertEquals("header $canonical is controlled by the transport", rule { add(written, "v") }, written)
        }
    }

    @Test
    fun `the first controlled entry in request order is named`() {
        assertEquals("header TE is controlled by the transport", rule { add("X-A", "1"); add("te", "trailers"); add("Host", "h") })
        assertEquals("header Host is controlled by the transport", rule { add("Host", "h"); add("te", "trailers") })
    }

    @Test
    fun `near-misses are admitted`() {
        listOf("Hosts", "X-Host", "Content-Encoding", "Via", "Range", "Authorization", "X-Content-Length", "Accept", "Referer", "Origin")
            .forEach { name -> assertNull(rule { add(name, "v") }, name) }
    }

    @Test
    fun `an empty header set is allowed`() {
        assertNull(rule { })
        assertNull(TransportControlledHeaders.ruleFor(HttpHeaders()))
    }

    @Test
    fun `the list holds exactly twelve distinct names`() {
        assertEquals(12, TransportControlledHeaders.names.size)
        assertEquals(12, TransportControlledHeaders.names.distinct().size)
        assertEquals(
            listOf(
                "User-Agent", "Accept-Encoding", "Host", "Connection", "Content-Length", "Transfer-Encoding",
                "Keep-Alive", "Proxy-Connection", "TE", "Trailer", "Upgrade", "Expect",
            ),
            TransportControlledHeaders.names,
        )
    }
}
