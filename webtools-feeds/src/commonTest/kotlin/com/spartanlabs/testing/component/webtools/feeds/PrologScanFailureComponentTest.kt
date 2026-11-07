package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedRefusal
import com.spartanlabs.webtools.feeds.NotAFeedReason
import com.spartanlabs.webtools.feeds.PrologScanResult
import com.spartanlabs.webtools.feeds.UndecodableReason
import com.spartanlabs.webtools.feeds.toFailureOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Level 2 - the mapping of a prolog-scan result to the failure that ends the parse: `NoDoctype` is null, `Empty`,
 * `NotMarkup` and `HtmlDocument` are `NotAFeed` with their reasons, `Doctype` and each `Undecodable` reason are `Refused`,
 * `IllFormed` is `Malformed`.
 */
class PrologScanFailureComponentTest {

    @Test
    fun `NoDoctype maps to null, so a parser may run`() {
        assertNull(PrologScanResult.NoDoctype.toFailureOrNull())
    }

    @Test
    fun `Empty, NotMarkup and HtmlDocument map to NotAFeed with Empty, NotXml and Html`() {
        assertEquals(NotAFeedReason.Empty, assertIs<FeedFailure.NotAFeed>(PrologScanResult.Empty.toFailureOrNull()).reason)
        assertEquals(NotAFeedReason.NotXml, assertIs<FeedFailure.NotAFeed>(PrologScanResult.NotMarkup.toFailureOrNull()).reason)
        assertEquals(NotAFeedReason.Html, assertIs<FeedFailure.NotAFeed>(PrologScanResult.HtmlDocument.toFailureOrNull()).reason)
    }

    @Test
    fun `Doctype and each Undecodable reason map to Refused with their reasons`() {
        assertEquals(FeedRefusal.Doctype, assertIs<FeedFailure.Refused>(PrologScanResult.Doctype.toFailureOrNull()).reason)
        val expected = mapOf(
            UndecodableReason.WideOrEbcdicPrefix to FeedRefusal.UnsupportedEncoding,
            UndecodableReason.UnlistedEncodingName to FeedRefusal.UnsupportedEncoding,
            UndecodableReason.PrologTooLong to FeedRefusal.PrologTooLong,
            UndecodableReason.MalformedSequence to FeedRefusal.AmbiguousProlog,
        )
        assertEquals(UndecodableReason.entries.toSet(), expected.keys)
        for ((reason, refusal) in expected) {
            assertEquals(refusal, assertIs<FeedFailure.Refused>(PrologScanResult.Undecodable(reason).toFailureOrNull()).reason, reason.name)
        }
    }

    @Test
    fun `IllFormed maps to Malformed`() {
        assertIs<FeedFailure.Malformed>(PrologScanResult.IllFormed.toFailureOrNull())
    }
}
