package com.spartanlabs.testing.component.webtools.feeds

import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedLimit
import com.spartanlabs.webtools.feeds.FeedRefusal
import com.spartanlabs.webtools.feeds.NotAFeedReason
import com.spartanlabs.webtools.http.HttpFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Level 2 - `FeedFailure`: each leaf is a `FeedFailure` and an `HttpFailure` with a constant message that names its reason;
 * the reasons are equal by name, print their name and are distinct constants; `Malformed` has no reason and no leaf a
 * cause; no message holds anything but constant text and a reason name; a `when` over the leaves keeps an `else`.
 */
class FeedFailureComponentTest {

    private val leaves: List<FeedFailure> = listOf(
        FeedFailure.NotAFeed(NotAFeedReason.Html), FeedFailure.Malformed(),
        FeedFailure.Refused(FeedRefusal.Doctype), FeedFailure.LimitExceeded(FeedLimit.Entries),
    )

    @Test
    fun `each leaf is a FeedFailure and an HttpFailure with a constant message that names its reason`() {
        for (leaf in leaves) assertTrue(leaf is HttpFailure, leaf.toString())
        assertEquals("The document is not a feed: Html", FeedFailure.NotAFeed(NotAFeedReason.Html).message)
        assertEquals("The document is not a feed: UnknownFormat", FeedFailure.NotAFeed(NotAFeedReason.UnknownFormat).message)
        assertEquals("The feed document is malformed", FeedFailure.Malformed().message)
        assertEquals("The feed document was refused: Doctype", FeedFailure.Refused(FeedRefusal.Doctype).message)
        assertEquals("The feed document was refused: AmbiguousProlog", FeedFailure.Refused(FeedRefusal.AmbiguousProlog).message)
        assertEquals("The feed document exceeds a limit: Entries", FeedFailure.LimitExceeded(FeedLimit.Entries).message)
        assertEquals("The feed document exceeds a limit: NamespaceUris", FeedFailure.LimitExceeded(FeedLimit.NamespaceUris).message)
        assertEquals(NotAFeedReason.Html, (leaves[0] as FeedFailure.NotAFeed).reason)
        assertEquals(FeedRefusal.Doctype, (leaves[2] as FeedFailure.Refused).reason)
        assertEquals(FeedLimit.Entries, (leaves[3] as FeedFailure.LimitExceeded).limit)
    }

    @Test
    fun `the reasons are equal by name, print their name and are distinct constants`() {
        val notAFeed = listOf(NotAFeedReason.Html, NotAFeedReason.Empty, NotAFeedReason.NotXml, NotAFeedReason.UnknownFormat)
        assertEquals(listOf("Html", "Empty", "NotXml", "UnknownFormat"), notAFeed.map { it.name })
        assertEquals(listOf("Html", "Empty", "NotXml", "UnknownFormat"), notAFeed.map { it.toString() })
        assertEquals(4, notAFeed.toSet().size)

        val refusals = listOf(FeedRefusal.Doctype, FeedRefusal.UnsupportedEncoding, FeedRefusal.PrologTooLong, FeedRefusal.AmbiguousProlog)
        assertEquals(listOf("Doctype", "UnsupportedEncoding", "PrologTooLong", "AmbiguousProlog"), refusals.map { it.name })
        assertEquals(refusals.map { it.name }, refusals.map { it.toString() })
        assertEquals(4, refusals.toSet().size)

        val limits = listOf(FeedLimit.Entries, FeedLimit.Depth, FeedLimit.Attributes, FeedLimit.Elements, FeedLimit.NamespaceUris)
        assertEquals(listOf("Entries", "Depth", "Attributes", "Elements", "NamespaceUris"), limits.map { it.name })
        assertEquals(limits.map { it.name }, limits.map { it.toString() })
        assertEquals(5, limits.toSet().size)

        assertSame(FeedRefusal.Doctype, FeedRefusal.Doctype)
        assertEquals(FeedRefusal.Doctype.hashCode(), "Doctype".hashCode())
        assertEquals(NotAFeedReason.Empty.hashCode(), "Empty".hashCode())
        assertEquals(FeedLimit.Depth.hashCode(), "Depth".hashCode())
        assertNotEquals<Any>(NotAFeedReason.Html, FeedRefusal.Doctype)
        assertNotEquals<Any>(FeedLimit.Entries, "Entries")
    }

    @Test
    fun `Malformed has no reason and no cause`() {
        val malformed = FeedFailure.Malformed()
        assertNull(malformed.cause)
        for (leaf in leaves) assertNull(leaf.cause, leaf.toString())
        assertNotEquals<FeedFailure>(FeedFailure.Malformed(), FeedFailure.Malformed(), "equality is identity")
    }

    @Test
    fun `no message holds anything but constant text and a reason name`() {
        val expected = mapOf(
            "The document is not a feed: Html" to leaves[0], "The feed document is malformed" to leaves[1],
            "The feed document was refused: Doctype" to leaves[2], "The feed document exceeds a limit: Entries" to leaves[3],
        )
        for ((message, leaf) in expected) {
            assertEquals(message, leaf.message)
            assertTrue(leaf.message!!.all { it in 'A'..'Z' || it in 'a'..'z' || it == ' ' || it == ':' }, message)
        }
    }

    @Test
    fun `a when over the leaves keeps an else`() {
        val other: HttpFailure = HttpFailure.DecodeFailure()
        val described = (leaves + other).map { failure ->
            when (failure) {
                is FeedFailure.NotAFeed -> "notAFeed:${failure.reason}"
                is FeedFailure.Malformed -> "malformed"
                is FeedFailure.Refused -> "refused:${failure.reason}"
                is FeedFailure.LimitExceeded -> "limit:${failure.limit}"
                else -> "other"
            }
        }
        assertEquals(listOf("notAFeed:Html", "malformed", "refused:Doctype", "limit:Entries", "other"), described)
    }
}
