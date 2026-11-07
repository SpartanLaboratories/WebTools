package com.spartanlabs.testing.deterministic.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.DigestVectors
import com.spartanlabs.webtools.feeds.DIGEST_ID_CHARS
import com.spartanlabs.webtools.feeds.DIGEST_PREFIX
import com.spartanlabs.webtools.feeds.digestId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Level 4a - the entry digest: the fourteen pinned vectors give their ids; the documented equalities hold; every digest
 * id is 50 characters with the prefix and 32 lower-case hex digits.
 */
class EntryDigestDeterministicTest {

    private fun id(title: String? = null, summary: String? = null, content: String? = null, enclosureUrl: String? = null, published: Long? = null): String =
        digestId(title, summary, content, enclosureUrl, published)

    @Test
    fun `the fourteen vectors give their ids`() {
        assertEquals(14, DigestVectors.all.size)
        for (v in DigestVectors.all) {
            assertEquals(v.expectedId, digestId(v.title, v.summary, v.content, v.enclosureUrl, v.publishedMillis), v.name)
        }
    }

    @Test
    fun `the equalities hold`() {
        assertEquals(id(title = "Hello"), id(title = " \t\nHello\r\n "), "surrounding white space is irrelevant")
        assertEquals(id(title = "t".repeat(4_096)), id(title = "t".repeat(10_000)), "the field is cut at 4,096 units")
        assertEquals(id(published = 1_700_000_000_000L), id(published = 1_700_000_000_999L), "sub-second milliseconds are irrelevant")
        assertNotEquals(id(published = 1_700_000_000_000L), id(published = 1_700_000_001_000L), "a whole second matters")
        assertEquals(id(published = -1L), id(published = -1_000L), "the floor of -1 ms and -1000 ms is the same second")
        assertEquals(id(title = "Hello"), id(title = "Hello", summary = "body"), "a title beats a body")
        assertEquals(id(summary = "body"), id(content = "body"), "summary and content share a tag")
        assertNotEquals(id(title = "a", summary = "b"), id(title = "ab"), "a length prefix keeps the boundary")
        assertEquals(DigestVectors.NOTHING_ID, id(title = " ", summary = "\t", content = "\n"), "blank fields equal nothing")
    }

    @Test
    fun `every digest id is 50 characters with the prefix and 32 lower-case hex digits`() {
        val ids = DigestVectors.all.map { it.expectedId } + id(title = "x", published = 5L) + id(enclosureUrl = "https://cdn.test/a")
        for (digest in ids) {
            assertEquals(DIGEST_ID_CHARS, digest.length, digest)
            assertEquals(50, digest.length, digest)
            assertTrue(digest.startsWith(DIGEST_PREFIX), digest)
            assertTrue(digest.substring(DIGEST_PREFIX.length).all { it in '0'..'9' || it in 'a'..'f' }, digest)
        }
    }
}
