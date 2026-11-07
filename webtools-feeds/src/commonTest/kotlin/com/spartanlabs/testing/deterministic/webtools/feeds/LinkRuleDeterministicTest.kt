package com.spartanlabs.testing.deterministic.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.LinkVectors
import com.spartanlabs.webtools.feeds.narrowBase
import com.spartanlabs.webtools.feeds.resolveLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Level 4a - the link rule, `resolveLink` against the base `https://example.org/blog/index.xml?x=1#frag` with a cap of
 * 8,000 unless stated: the RFC 3986 section 5.4 tables; absolute links keep everything but the scheme's case; unsafe and
 * malformed links are null; a trailing control character is trimmed and an inner one refused; relative references
 * resolve against the base; the `xml:base` chain resolves in turn; the cap is exact and applies after resolution;
 * `allowRelative = false` refuses a relative value.
 */
class LinkRuleDeterministicTest {

    private val base = LinkVectors.MODULE_BASE
    private val cap = 8_000

    private fun resolve(raw: String?, base: String? = this.base, maxChars: Int = cap, allowRelative: Boolean = true): String? =
        resolveLink(raw, base, maxChars, allowRelative)

    @Test
    fun `RFC 3986 section 5-4-1 normal examples resolve against the base http a b c d p q`() {
        assertEquals(23, LinkVectors.normal.size)
        for ((reference, expected) in LinkVectors.normal) assertEquals(expected, resolve(reference, LinkVectors.RFC_BASE), "'$reference'")
        assertEquals(21, LinkVectors.normal.count { it.second != null })
    }

    @Test
    fun `RFC 3986 section 5-4-2 abnormal examples resolve`() {
        assertEquals(19, LinkVectors.abnormal.size)
        for ((reference, expected) in LinkVectors.abnormal) assertEquals(expected, resolve(reference, LinkVectors.RFC_BASE), "'$reference'")
        assertEquals(18, LinkVectors.abnormal.count { it.second != null })
    }

    @Test
    fun `absolute links keep everything but the scheme case`() {
        for ((input, expected) in LinkVectors.absoluteKept) {
            assertEquals(expected, resolve(input), "'$input'")
            assertEquals(expected, resolve(input, base = null), "'$input' without a base")
        }
        assertEquals("https://Example.ORG/A", resolve("  HTTPS://Example.ORG/A  "))
        assertEquals("http://[2001:db8::1]:8080/x", resolve("http://[2001:db8::1]:8080/x"))
        assertEquals("http://x.test:65535/", resolve("http://x.test:65535/"))
        assertNull(resolve("http://x.test:65536/"))
        assertNull(resolve("http://x.test:80a/"))
    }

    @Test
    fun `unsafe and malformed links are null`() {
        for (input in LinkVectors.refused) {
            assertNull(resolve(input), "'$input'")
            assertNull(resolve(input, base = null), "'$input' without a base")
        }
    }

    @Test
    fun `a trailing control character is trimmed and an inner one is null`() {
        assertEquals("http://x.test/", resolve("http://x.test/\u0000"))
        assertEquals("http://x.test/", resolve("\u0001 http://x.test/\t\n"))
        assertNull(resolve("http://x.test/a\u0000b"))
        assertNull(resolve("http://x.te\u0001st/"))
    }

    @Test
    fun `relative references against the base`() {
        assertEquals("https://cdn.example.net/x.js", resolve("//cdn.example.net/x.js"))
        assertNull(resolve("//cdn.example.net/x.js", base = null))
        assertEquals("https://example.org/post/1", resolve("/post/1"))
        assertEquals("https://example.org/blog/post/1?x#y", resolve("post/1?x#y"))
        assertEquals("https://example.org/blog/index.xml?x=1#only", resolve("#only"))
        assertEquals("https://example.org/up", resolve("../up", base = "https://example.org/blog/"))
        assertEquals("https://example.org/x", resolve("x", base = "https://example.org"))
        assertNull(resolve("x", base = "ftp://example.org/"))
        assertNull(resolve("x", base = "https://u@example.org/"))
        assertNull(resolve("x", base = "https://example.org/a b/"))
        assertNull(resolve("x", base = null))
        assertNull(resolve("/post/1", base = null))
    }

    @Test
    fun `the xml base chain resolves in turn`() {
        val document = "https://example.org/feeds/a.xml"
        val feedBase = narrowBase(document, "/blog/", cap)
        assertEquals("https://example.org/blog/", feedBase)
        val yearBase = narrowBase(feedBase, "2026/", cap)
        assertEquals("https://example.org/blog/2026/", yearBase)
        assertEquals("https://example.org/blog/2026/post-1.html#c", resolve("post-1.html#c", base = yearBase))
        assertEquals(document, narrowBase(document, null, cap))
        assertEquals(document, narrowBase(document, "  ", cap))
        assertNull(narrowBase(document, "ftp://other.test/", cap))
        assertNull(narrowBase(null, "2026/", cap))
        assertNull(resolve("post-1.html", base = narrowBase(document, "ftp://other.test/", cap)))
    }

    @Test
    fun `the cap is exact and applies after resolution, and allowRelative = false refuses a relative value`() {
        val prefix = "http://x.test/"
        assertEquals(prefix + "a".repeat(cap - prefix.length), resolve(prefix + "a".repeat(cap - prefix.length)))
        assertNull(resolve(prefix + "a".repeat(cap - prefix.length + 1)))
        assertNull(resolve("http://x.test/" + "a".repeat(5_000_000)))
        val relative = "a".repeat(cap - "https://example.org/blog/".length)
        assertEquals("https://example.org/blog/$relative", resolve(relative))
        assertNull(resolve(relative + "a"), "the cap applies to the resolved link")
        assertNull(resolve("7f3a9c2e-1b4d-4e8a-9c3d-0a1b2c3d4e5f", allowRelative = false))
        assertEquals("https://x.test/a", resolve("https://x.test/a", allowRelative = false))
        assertEquals("https://x.test/a", resolve("https://x.test/a", base = null, allowRelative = false))
    }
}
