package com.spartanlabs.testing.deterministic.webtools.feeds

import com.spartanlabs.webtools.feeds.origin
import com.spartanlabs.webtools.feeds.originOfLink
import com.spartanlabs.webtools.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Level 4a - the origin a log line or a `toString` prints: a `Url` prints scheme, host and port; a URL with a path and a
 * query sentinel prints neither; `originOfLink` prints the origin of a canonical link and `invalid` otherwise.
 */
class UrlOriginDeterministicTest {

    private fun url(text: String): Url = Url.parse(text).getOrThrow()

    @Test
    fun `a URL prints scheme, host and port`() {
        assertEquals("https://example.org:443", url("https://example.org/").origin())
        assertEquals("http://example.org:80", url("http://example.org").origin())
        assertEquals("https://example.org:8443", url("https://example.org:8443/x").origin())
        assertEquals("http://[2001:db8::1]:8080", url("http://[2001:db8::1]:8080/x").origin())
        assertEquals("http://[::1]:80", url("http://[::1]/").origin())
    }

    @Test
    fun `a URL with a path and a query sentinel prints neither`() {
        val text = url("https://feeds.example.test/PATH-SENTINEL/feed.xml?token=QUERY-SENTINEL").origin()
        assertEquals("https://feeds.example.test:443", text)
        assertFalse(text.contains("SENTINEL"))
        assertFalse(text.contains("?"))
    }

    @Test
    fun `originOfLink prints the authority of a canonical link and invalid otherwise`() {
        assertEquals("https://example.org", originOfLink("https://example.org/PATH-SENTINEL?q=QUERY-SENTINEL#f"))
        assertEquals("https://example.org:8443", originOfLink("https://example.org:8443/x"))
        assertEquals("http://[2001:db8::1]:8080", originOfLink("http://[2001:db8::1]:8080/x"))
        assertEquals("https://example.org", originOfLink("https://example.org"))
        assertEquals("https://example.org", originOfLink("https://example.org?q"))
        assertEquals("https://example.org", originOfLink("https://example.org#f"))
        assertEquals("invalid", originOfLink("/relative/PATH-SENTINEL"))
        assertEquals("invalid", originOfLink("mailto:a@b.test"))
        assertEquals("invalid", originOfLink(""))
    }
}
