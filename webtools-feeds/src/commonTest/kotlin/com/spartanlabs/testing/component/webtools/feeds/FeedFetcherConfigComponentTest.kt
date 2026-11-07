package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.webtools.feeds.FeedFetcherConfig
import com.spartanlabs.webtools.feeds.MSG_ACCEPT
import com.spartanlabs.webtools.feeds.MSG_MAX_CONCURRENT_PARSES
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Level 2 - `FeedFetcherConfig`: the defaults are two parses, the documented `Accept` and `Dispatchers.Default`; `DEFAULT_ACCEPT` is
 * the documented list; `maxConcurrentParses` below 1 is refused with a constant message; a blank `accept` is refused; an `accept`
 * with a control or non-ASCII character is refused and a tab inside accepted; a valid configuration resolves to the values set.
 */
class FeedFetcherConfigComponentTest {

    @Test
    fun `the defaults are two parses, the documented Accept and Dispatchers Default`() {
        val config = FeedFetcherConfig()
        assertEquals(2, config.maxConcurrentParses)
        assertEquals(FeedFetcherConfig.DEFAULT_MAX_CONCURRENT_PARSES, config.maxConcurrentParses)
        assertEquals(FeedFetcherConfig.DEFAULT_ACCEPT, config.accept)
        assertSame(Dispatchers.Default, config.parseDispatcher)
        val resolved = config.resolve()
        assertSame(config.parser, resolved.parser)
        assertEquals(2, resolved.maxConcurrentParses)
    }

    @Test
    fun `DEFAULT_ACCEPT is the documented list`() {
        assertEquals(
            "application/atom+xml, application/rss+xml, application/rdf+xml;q=0.9, application/xml;q=0.8, text/xml;q=0.8, */*;q=0.1",
            FeedFetcherConfig.DEFAULT_ACCEPT,
        )
        assertTrue(FeedFetcherConfig.DEFAULT_ACCEPT.endsWith("*/*;q=0.1"))
        assertFalse(FeedFetcherConfig.DEFAULT_ACCEPT.contains("application/feed+json"))
    }

    @Test
    fun `maxConcurrentParses below 1 is refused with a constant message`() {
        for (bad in listOf(0, -1, Int.MIN_VALUE)) {
            val e = assertFailsWith<IllegalArgumentException>(bad.toString()) { FeedFetcherConfig().apply { maxConcurrentParses = bad }.resolve() }
            assertEquals(MSG_MAX_CONCURRENT_PARSES, e.message)
        }
        assertEquals(1, FeedFetcherConfig().apply { maxConcurrentParses = 1 }.resolve().maxConcurrentParses)
    }

    @Test
    fun `a blank accept is refused`() {
        for (bad in listOf("", " ", "\t", " \t ")) {
            val e = assertFailsWith<IllegalArgumentException>("'$bad'") { FeedFetcherConfig().apply { accept = bad }.resolve() }
            assertEquals(MSG_ACCEPT, e.message)
        }
    }

    @Test
    fun `an accept with CR, LF, NUL, DEL or a non-ASCII character is refused and a tab inside is accepted`() {
        for (bad in listOf("a\rb", "a\nb", "a\u0000b", "a\u007Fb", "café", "text/xml中")) {
            val e = assertFailsWith<IllegalArgumentException>(bad) { FeedFetcherConfig().apply { accept = bad }.resolve() }
            assertEquals(MSG_ACCEPT, e.message)
        }
        assertEquals("application/rss+xml,\tapplication/atom+xml", FeedFetcherConfig().apply { accept = "application/rss+xml,\tapplication/atom+xml" }.resolve().accept)
    }

    @Test
    fun `a valid configuration resolves to the values set`() {
        val dispatcher = Dispatchers.Unconfined
        val config = FeedFetcherConfig().apply {
            maxConcurrentParses = 4
            accept = "application/rss+xml"
            parseDispatcher = dispatcher
        }
        val resolved = config.resolve()
        assertEquals(4, resolved.maxConcurrentParses)
        assertEquals("application/rss+xml", resolved.accept)
        assertSame(dispatcher, resolved.parseDispatcher)
        assertSame(config.parser, resolved.parser)
    }
}
