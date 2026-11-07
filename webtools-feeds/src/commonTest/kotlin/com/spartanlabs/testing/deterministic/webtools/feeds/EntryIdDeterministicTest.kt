package com.spartanlabs.testing.deterministic.webtools.feeds

import com.spartanlabs.webtools.feeds.DIGEST_PREFIX
import com.spartanlabs.webtools.feeds.digestId
import com.spartanlabs.webtools.feeds.entryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Level 4a - the id rule: a guid is the id, trimmed at the ends with its case and inner text kept; the link as written is
 * the id when there is no guid; the digest is the id when neither fits; a candidate that is blank by Unicode rules counts
 * as absent.
 */
class EntryIdDeterministicTest {

    private fun id(guid: String? = null, link: String? = null, title: String? = null, maxIdChars: Int = 2_048): String =
        entryId(guid, link, title, null, null, null, null, maxIdChars)

    @Test
    fun `a guid is the id, trimmed at the ends, case kept, inner text kept, isPermaLink irrelevant`() {
        assertEquals("tag:x,2026:1", id(guid = "  tag:x,2026:1 \n", link = "http://l/x"))
        assertEquals("AbC", id(guid = "AbC"))
        assertEquals("a  b", id(guid = "a  b"))
        assertEquals("not a url at all", id(guid = "not a url at all", link = "http://l/x"))
    }

    @Test
    fun `the link as written is the id when there is no guid`() {
        assertEquals("http://l/x", id(guid = " ", link = " http://l/x "))
        assertEquals("/p/1", id(link = "/p/1"))
        assertEquals("http://l/x", id(guid = "", link = " http://l/x "))
        assertNotEquals(id(link = "http://x.test/p"), id(link = "https://x.test/p"))
        assertEquals("http://x.test/p", id(link = "http://x.test/p"))
        assertEquals("https://x.test/p", id(link = "https://x.test/p"))
    }

    @Test
    fun `the digest is the id when neither fits`() {
        val huge = "g".repeat(5_000_000)
        assertEquals("http://l/x", id(guid = huge, link = "http://l/x"))
        assertEquals(digestId("T", null, null, null, null), id(guid = huge, title = "T"))
        assertEquals("g".repeat(2_048), id(guid = "g".repeat(2_048)))
        assertTrue(id(guid = "g".repeat(2_049), title = "T").startsWith(DIGEST_PREFIX))
        assertEquals(digestId("T", null, null, null, null), id(link = "javascript:alert(1)", title = "T"))
        assertEquals(digestId("T", null, null, null, null), id(link = "mailto:a@b.test", title = "T"))
        assertTrue(id(link = "http://x.test/" + "a".repeat(3_000 - 14), title = "T").startsWith(DIGEST_PREFIX))
        assertTrue(id(link = "http://x.test/a b", title = "T").startsWith(DIGEST_PREFIX), "an inner space refuses the link")
        assertEquals("a".repeat(60), id(guid = "a".repeat(60), maxIdChars = 60))
        assertTrue(id(guid = "a".repeat(61), maxIdChars = 60).startsWith(DIGEST_PREFIX))
    }

    @Test
    fun `a candidate that is blank by Unicode rules counts as absent`() {
        assertEquals("http://l/x", id(guid = " ", link = "http://l/x"))
        assertEquals(digestId("T", null, null, null, null), id(guid = " ", link = " ", title = "T"))
        assertEquals(digestId(null, null, null, null, null), id(guid = "  　"))
    }
}
