package com.spartanlabs.testing.nonfunctional.webtools.feeds

import com.rometools.rome.io.WireFeedInput
import com.spartanlabs.testing.support.webtools.feeds.FeedFixtures
import com.spartanlabs.testing.support.webtools.feeds.HostileDocuments
import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedLimit
import com.spartanlabs.webtools.feeds.FeedLimits
import com.spartanlabs.webtools.feeds.RomeFeedParser
import java.io.StringReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Level 4c - `RomeFeedParser`'s limits stay inside the parser: a plain `WireFeedInput` still parses a 150-attribute document after a
 * `RomeFeedParser` rejected one (nothing leaks into JDOM's process-wide factory); two parsers with different limits give the right result
 * under sixteen threads; a parse leaves the context class loader and the JAXP system properties untouched.
 */
class RomeFeedParserIsolationNonFunctionalTest {

    @Test
    fun `a plain WireFeedInput still parses a 150-attribute document after a RomeFeedParser rejected one`() {
        val document = HostileDocuments.attributesOnOneElement(150)
        val rejected = RomeFeedParser().parse(FeedFixtures.input(document))
        assertEquals(FeedLimit.Attributes, assertIs<FeedFailure.LimitExceeded>(rejected.exceptionOrNull()).limit)
        val wire = WireFeedInput().build(StringReader(document.decodeToString()))
        assertEquals("rss_2.0", wire.feedType)
        // And the other way round: a plain build before ours changes nothing for ours.
        WireFeedInput().build(StringReader(document.decodeToString()))
        assertIs<FeedFailure.LimitExceeded>(RomeFeedParser().parse(FeedFixtures.input(document)).exceptionOrNull())
    }

    @Test
    fun `two parsers with different limits give the right result under sixteen threads`() {
        // The limit tightens only (ceiling 100), so the accepting parser runs on the default and the rejecting one on 50.
        val document = HostileDocuments.attributesOnOneElement(60)
        val tight = RomeFeedParser(FeedLimits { maxAttributesPerElement = 50 })
        val open = RomeFeedParser()
        val wrong = AtomicInteger()
        val start = CountDownLatch(1)
        val threads = List(16) { t ->
            Thread {
                start.await()
                repeat(400) {
                    val result = (if (t % 2 == 0) tight else open).parse(FeedFixtures.input(document))
                    val expectedRejection = t % 2 == 0
                    val rejected = (result.exceptionOrNull() as? FeedFailure.LimitExceeded)?.limit == FeedLimit.Attributes
                    if (expectedRejection != rejected || (!expectedRejection && result.isFailure)) wrong.incrementAndGet()
                }
            }.apply { start() }
        }
        start.countDown()
        threads.forEach { it.join(120_000) }
        assertEquals(0, wrong.get(), "wrong outcomes in 6,400 parses")
    }

    @Test
    fun `a parse leaves the context class loader and the JAXP system properties untouched`() {
        val loaderBefore = Thread.currentThread().contextClassLoader
        val propertiesBefore = System.getProperties().stringPropertyNames().filter { it.startsWith("jdk.xml") || it.startsWith("javax.xml") }.associateWith { System.getProperty(it) }
        val parser = RomeFeedParser(FeedLimits { maxDepth = 8; maxAttributesPerElement = 8 })
        parser.parse(FeedFixtures.fixture("rss20.xml")).getOrThrow()
        parser.parse(FeedFixtures.input(HostileDocuments.rssNested(20)))
        parser.parse(FeedFixtures.input(HostileDocuments.attributesOnOneElement(20)))
        assertTrue(Thread.currentThread().contextClassLoader === loaderBefore)
        val propertiesAfter = System.getProperties().stringPropertyNames().filter { it.startsWith("jdk.xml") || it.startsWith("javax.xml") }.associateWith { System.getProperty(it) }
        assertEquals(propertiesBefore, propertiesAfter)
    }
}
