package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.FeedModelFixtures
import com.spartanlabs.webtools.feeds.FeedEnclosure
import com.spartanlabs.webtools.feeds.FeedEntry
import com.spartanlabs.webtools.feeds.MSG_ENTRY_ID
import com.spartanlabs.webtools.feeds.MSG_LINK
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Level 2 - `FeedEntry`: the id is required and a blank one refused with a constant message; the optional fields default
 * to null and empty lists; a canonical link is accepted and each other form refused; lists are copied; equality is on all
 * ten fields and hash agrees; `toString` prints counts and presence, the link as an origin, and no text; the factory
 * applies no limit and no fallback.
 */
class FeedEntryComponentTest {

    @Test
    fun `the id is required and a blank one is refused with a constant message`() {
        for (blank in listOf("", " ", " ", " \t\n")) {
            val e = assertFailsWith<IllegalArgumentException>("'$blank'") { FeedEntry(blank) }
            assertEquals(MSG_ENTRY_ID, e.message)
        }
        assertEquals("x", FeedEntry("x").id)
    }

    @Test
    fun `the optional fields default to null and empty lists`() {
        val entry = FeedEntry("id")
        assertNull(entry.title)
        assertNull(entry.link)
        assertNull(entry.summary)
        assertNull(entry.content)
        assertNull(entry.author)
        assertNull(entry.publishedAtEpochMillis)
        assertNull(entry.updatedAtEpochMillis)
        assertEquals(emptyList(), entry.categories)
        assertEquals(emptyList(), entry.enclosures)
    }

    @Test
    fun `a canonical link is accepted and each other form is refused`() {
        assertEquals("https://a.test/x#frag", FeedEntry("id") { link = "https://a.test/x#frag" }.link)
        val refused = listOf(
            "/relative/x", "javascript:alert(1)", "https://a.test/x y", "https://u:p@a.test/x", "HTTPS://a.test/x",
            "https://a.test\\x", "https:///x", "https://a.test/x\u0001",
        )
        for (bad in refused) {
            val e = assertFailsWith<IllegalArgumentException>(bad) { FeedEntry("id") { link = bad } }
            assertEquals(MSG_LINK, e.message, bad)
        }
    }

    @Test
    fun `lists are copied when the entry is built`() {
        val categories = mutableListOf("a")
        val enclosures = mutableListOf(FeedEnclosure("https://cdn.test/a"))
        val entry = FeedEntry("id") { this.categories = categories; this.enclosures = enclosures }
        categories.add("b")
        enclosures.add(FeedEnclosure("https://cdn.test/b"))
        assertEquals(listOf("a"), entry.categories)
        assertEquals(1, entry.enclosures.size)
    }

    @Test
    fun `equality is on all ten fields`() {
        val a = FeedModelFixtures.entry("x")
        assertEquals(a, FeedModelFixtures.entry("x"))
        val variants: List<FeedEntry.Builder.() -> Unit> = listOf(
            { title = "other" }, { link = "https://other.test/" }, { summary = "other" }, { content = "other" }, { author = "other" },
            { publishedAtEpochMillis = 1L }, { updatedAtEpochMillis = 1L }, { categories = listOf("other") }, { enclosures = emptyList() },
        )
        for ((i, change) in variants.withIndex()) {
            val changed = FeedEntry("x") {
                title = a.title; link = a.link; summary = a.summary; content = a.content; author = a.author
                publishedAtEpochMillis = a.publishedAtEpochMillis; updatedAtEpochMillis = a.updatedAtEpochMillis
                categories = a.categories; enclosures = a.enclosures
                change()
            }
            assertNotEquals(a, changed, "variant $i")
        }
        assertNotEquals(a, FeedModelFixtures.entry("y"))
        assertNotEquals<Any>(a, "x")
    }

    @Test
    fun `hashCode agrees with equality`() {
        assertEquals(FeedModelFixtures.entry("x").hashCode(), FeedModelFixtures.entry("x").hashCode())
        assertEquals(FeedEntry("id").hashCode(), FeedEntry("id").hashCode())
        assertNotEquals(FeedModelFixtures.entry("x").hashCode(), FeedModelFixtures.entry("y").hashCode())
    }

    @Test
    fun `toString prints counts and presence, the link as an origin, and no text`() {
        val text = FeedModelFixtures.entry("urn:ID-SENTINEL").toString()
        assertEquals(
            "FeedEntry(idChars=15, title=present, link=https://links.example.test, summary=present, content=present, author=present, " +
                "publishedAt=present, updatedAt=present, categories=1, enclosures=1)",
            text,
        )
        assertFalse(text.contains("SENTINEL"))
        assertEquals(
            "FeedEntry(idChars=2, title=none, link=none, summary=none, content=none, author=none, publishedAt=none, updatedAt=none, categories=0, enclosures=0)",
            FeedEntry("id").toString(),
        )
    }

    @Test
    fun `the factory applies no limit and no fallback`() {
        val longTitle = "t".repeat(300_000)
        val longId = "i".repeat(5_000)
        val entry = FeedEntry(longId) { title = longTitle }
        assertEquals(300_000, entry.title!!.length)
        assertEquals(5_000, entry.id.length)
    }
}
