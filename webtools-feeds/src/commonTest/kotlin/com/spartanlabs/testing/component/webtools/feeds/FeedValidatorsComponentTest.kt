package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.webtools.feeds.FeedValidators
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Level 2 - `FeedValidators`: `None` holds neither; equality and hash are on both values; `toString` prints presence and
 * never a value; a value from storage is held to the same rules as one from a response.
 */
class FeedValidatorsComponentTest {

    @Test
    fun `None holds neither validator and equals a pair of nulls`() {
        assertNull(FeedValidators.None.etag)
        assertNull(FeedValidators.None.lastModified)
        assertEquals(FeedValidators(null, null), FeedValidators.None)
        assertEquals(FeedValidators(null, null).hashCode(), FeedValidators.None.hashCode())
    }

    @Test
    fun `equality and hashCode are on both values`() {
        val a = FeedValidators("\"x\"", "Fri, 06 Nov 2026 04:25:27 GMT")
        val b = FeedValidators("\"x\"", "Fri, 06 Nov 2026 04:25:27 GMT")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, FeedValidators("\"y\"", "Fri, 06 Nov 2026 04:25:27 GMT"))
        assertNotEquals(a, FeedValidators("\"x\"", null))
        assertNotEquals(a, FeedValidators(null, "Fri, 06 Nov 2026 04:25:27 GMT"))
        assertNotEquals<Any>(a, "\"x\"")
    }

    @Test
    fun `toString prints presence and never a value`() {
        assertEquals("FeedValidators(etag=present, lastModified=absent)", FeedValidators("\"TAG-SENTINEL\"", null).toString())
        assertEquals("FeedValidators(etag=absent, lastModified=present)", FeedValidators(null, "Fri, 06 Nov 2026 04:25:27 GMT").toString())
        assertEquals("FeedValidators(etag=absent, lastModified=absent)", FeedValidators.None.toString())
        val text = FeedValidators("\"TAG-SENTINEL\"", "Fri, 06 Nov 2026 04:25:27 GMT").toString()
        assertFalse(text.contains("SENTINEL"))
        assertFalse(text.contains("2026"))
    }

    @Test
    fun `a value from storage is held to the rules of a value from a response`() {
        val kept = FeedValidators("W/\"x\"", "Fri, 6 Nov 2026 04:25:27 GMT")
        assertEquals("W/\"x\"", kept.etag)
        assertEquals("Fri, 6 Nov 2026 04:25:27 GMT", kept.lastModified)
        assertNull(FeedValidators("\"" + "a".repeat(255) + "\"", null).etag, "257 characters")
        assertNull(FeedValidators("\"café\"", null).etag, "non-ASCII")
        assertNull(FeedValidators("*", null).etag, "the star")
    }
}
