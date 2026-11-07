package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.webtools.feeds.FeedLimits
import com.spartanlabs.webtools.feeds.msgLimitRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Level 2 - `FeedLimits`: the ten defaults and `Defaults`; a factory with no lambda equals them; each member tightens to
 * its floor and raises back to its default; a value above its default or below its floor is refused with a constant
 * message that names the property; the copy form starts from the given value and never modifies it; equality, hash
 * and `toString` are on all ten members.
 */
class FeedLimitsComponentTest {

    // name, floor, default, a setter and a getter for each member, in declaration order.
    private class Member(
        val name: String, val floor: Int, val default: Int,
        val set: FeedLimits.Builder.(Int) -> Unit, val get: FeedLimits.() -> Int,
    )

    private val members = listOf(
        Member("maxEntries", 1, 1_000, { maxEntries = it }, { maxEntries }),
        Member("maxTextChars", 16, 262_144, { maxTextChars = it }, { maxTextChars }),
        Member("maxIdChars", 50, 2_048, { maxIdChars = it }, { maxIdChars }),
        Member("maxLinkChars", 32, 8_000, { maxLinkChars = it }, { maxLinkChars }),
        Member("maxCategoriesPerEntry", 0, 32, { maxCategoriesPerEntry = it }, { maxCategoriesPerEntry }),
        Member("maxEnclosuresPerEntry", 0, 8, { maxEnclosuresPerEntry = it }, { maxEnclosuresPerEntry }),
        Member("maxDepth", 4, 256, { maxDepth = it }, { maxDepth }),
        Member("maxAttributesPerElement", 4, 100, { maxAttributesPerElement = it }, { maxAttributesPerElement }),
        Member("maxElements", 16, 100_000, { maxElements = it }, { maxElements }),
        Member("maxNamespaceUris", 2, 64, { maxNamespaceUris = it }, { maxNamespaceUris }),
    )

    @Test
    fun `the defaults are the documented numbers and Defaults holds them`() {
        assertEquals(1_000, FeedLimits.DEFAULT_MAX_ENTRIES)
        assertEquals(262_144, FeedLimits.DEFAULT_MAX_TEXT_CHARS)
        assertEquals(2_048, FeedLimits.DEFAULT_MAX_ID_CHARS)
        assertEquals(8_000, FeedLimits.DEFAULT_MAX_LINK_CHARS)
        assertEquals(32, FeedLimits.DEFAULT_MAX_CATEGORIES_PER_ENTRY)
        assertEquals(8, FeedLimits.DEFAULT_MAX_ENCLOSURES_PER_ENTRY)
        assertEquals(256, FeedLimits.DEFAULT_MAX_DEPTH)
        assertEquals(100, FeedLimits.DEFAULT_MAX_ATTRIBUTES_PER_ELEMENT)
        assertEquals(100_000, FeedLimits.DEFAULT_MAX_ELEMENTS)
        assertEquals(64, FeedLimits.DEFAULT_MAX_NAMESPACE_URIS)
        for (m in members) assertEquals(m.default, FeedLimits.Defaults.(m.get)(), m.name)
    }

    @Test
    fun `a factory with no lambda equals the defaults`() {
        assertEquals(FeedLimits.Defaults, FeedLimits())
        assertEquals(FeedLimits.Defaults, FeedLimits {})
    }

    @Test
    fun `each member can be tightened to its floor and raised back to its default`() {
        for (m in members) {
            val tightened = FeedLimits { m.set(this, m.floor) }
            assertEquals(m.floor, tightened.(m.get)(), m.name)
            for (other in members) if (other !== m) assertEquals(other.default, tightened.(other.get)(), "${m.name} must not change ${other.name}")
            val raised = FeedLimits(tightened) { m.set(this, m.default) }
            assertEquals(FeedLimits.Defaults, raised, m.name)
        }
    }

    @Test
    fun `each member above its default is refused with a constant message that names the property and never a value`() {
        for (m in members) {
            val value = m.default + 1
            val e = assertFailsWith<IllegalArgumentException>(m.name) { FeedLimits { m.set(this, value) } }
            assertEquals(msgLimitRange(m.name, m.floor, m.default), e.message, m.name)
            assertTrue(e.message!!.contains(m.name), m.name)
            assertFalse(e.message!!.contains(value.toString()), "${m.name}: the message must not echo the value")
        }
    }

    @Test
    fun `each member below its floor is refused`() {
        for (m in members) {
            val e = assertFailsWith<IllegalArgumentException>(m.name) { FeedLimits { m.set(this, m.floor - 1) } }
            assertEquals(msgLimitRange(m.name, m.floor, m.default), e.message, m.name)
        }
        assertEquals(0, FeedLimits { maxCategoriesPerEntry = 0 }.maxCategoriesPerEntry)
        assertEquals(0, FeedLimits { maxEnclosuresPerEntry = 0 }.maxEnclosuresPerEntry)
    }

    @Test
    fun `the copy form starts from the given value, may raise a member to its default and never modifies the original`() {
        val original = FeedLimits { maxEntries = 10; maxDepth = 8 }
        val copy = FeedLimits(original) { maxDepth = FeedLimits.DEFAULT_MAX_DEPTH; maxTextChars = 1_024 }
        assertEquals(10, copy.maxEntries)
        assertEquals(FeedLimits.DEFAULT_MAX_DEPTH, copy.maxDepth)
        assertEquals(1_024, copy.maxTextChars)
        assertEquals(10, original.maxEntries)
        assertEquals(8, original.maxDepth)
        assertEquals(FeedLimits.DEFAULT_MAX_TEXT_CHARS, original.maxTextChars)
        assertFailsWith<IllegalArgumentException> { FeedLimits(original) { maxDepth = FeedLimits.DEFAULT_MAX_DEPTH + 1 } }
    }

    @Test
    fun `equality and hashCode are on all ten members`() {
        val a = FeedLimits { maxEntries = 10 }
        val b = FeedLimits { maxEntries = 10 }
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        for (m in members) {
            val differs = FeedLimits { m.set(this, m.floor) }
            assertNotEquals(FeedLimits.Defaults, differs, m.name)
            assertNotEquals(FeedLimits.Defaults.hashCode(), differs.hashCode(), m.name)
        }
        assertNotEquals<Any>(a, "FeedLimits")
    }

    @Test
    fun `toString prints the ten numbers`() {
        assertEquals(
            "FeedLimits(maxEntries=1000, maxTextChars=262144, maxIdChars=2048, maxLinkChars=8000, maxCategoriesPerEntry=32, " +
                "maxEnclosuresPerEntry=8, maxDepth=256, maxAttributesPerElement=100, maxElements=100000, maxNamespaceUris=64)",
            FeedLimits.Defaults.toString(),
        )
        assertTrue(FeedLimits { maxEntries = 7 }.toString().contains("maxEntries=7"))
    }
}
