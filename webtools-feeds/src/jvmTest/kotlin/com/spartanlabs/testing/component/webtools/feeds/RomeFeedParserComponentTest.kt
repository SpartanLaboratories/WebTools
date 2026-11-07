package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.FeedFixtures
import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedInput
import com.spartanlabs.webtools.feeds.FeedLimit
import com.spartanlabs.webtools.feeds.FeedLimits
import com.spartanlabs.webtools.feeds.RomeFeedParser
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Level 2 - `RomeFeedParser` as a class: both constructors parse a minimal feed; the no-argument parser uses the default limits
 * and a tightened parser enforces its own; `parse` never throws for hostile bytes; `parse` does not modify the input and the
 * same input gives equal feeds twice.
 */
class RomeFeedParserComponentTest {

    private val minimal = "<rss version=\"2.0\"><channel><title>t</title><item><title>one</title><link>https://x.test/1</link></item></channel></rss>"
    private val twoItems = "<rss version=\"2.0\"><channel><title>t</title><item><guid>a</guid></item><item><guid>b</guid></item></channel></rss>"

    @Test
    fun `both constructors build a parser that parses a minimal feed`() {
        val byDefault = RomeFeedParser().parse(FeedFixtures.input(minimal)).getOrThrow()
        val byLimits = RomeFeedParser(FeedLimits { maxEntries = 10 }).parse(FeedFixtures.input(minimal)).getOrThrow()
        assertEquals("t", byDefault.title)
        assertEquals(listOf("https://x.test/1"), byDefault.entries.map { it.id })
        assertEquals(byDefault, byLimits)
    }

    @Test
    fun `the no-argument parser uses the default limits and a tightened parser enforces its own`() {
        assertEquals(2, RomeFeedParser().parse(FeedFixtures.input(twoItems)).getOrThrow().entries.size)
        val failure = RomeFeedParser(FeedLimits { maxEntries = 1 }).parse(FeedFixtures.input(twoItems)).exceptionOrNull()
        assertEquals(FeedLimit.Entries, assertIs<FeedFailure.LimitExceeded>(failure).limit)
    }

    @Test
    fun `parse never throws for hostile bytes`() {
        val parser = RomeFeedParser()
        val random = Random(20261106)
        repeat(1_000) { i ->
            val bytes = ByteArray(random.nextInt(0, 400)) { random.nextInt(256).toByte() }
            val result = parser.parse(FeedInput(bytes, if (i % 2 == 0) "application/xml" else null, FeedFixtures.baseUrl))
            assertTrue(result.isSuccess || result.exceptionOrNull() is FeedFailure, "random $i: $result")
        }
        for (name in FeedFixtures.names) {
            val whole = FeedFixtures.bytes(name)
            for (cut in listOf(0, 1, 5, 17, whole.size / 3, whole.size / 2, whole.size - 7, whole.size - 1)) {
                val result = parser.parse(FeedFixtures.input(whole.copyOf(cut)))
                assertTrue(result.isSuccess || result.exceptionOrNull() is FeedFailure, "$name cut at $cut: $result")
            }
        }
    }

    @Test
    fun `parse does not modify the input and the same input gives equal feeds twice`() {
        val parser = RomeFeedParser()
        for (name in FeedFixtures.names) {
            val original = FeedFixtures.bytes(name)
            val input = FeedFixtures.fixture(name)
            val first = parser.parse(input).getOrThrow()
            val second = parser.parse(input).getOrThrow()
            assertEquals(first, second, name)
            assertEquals(first.hashCode(), second.hashCode(), name)
            assertContentEquals(original, input.document(), name)
        }
    }
}
