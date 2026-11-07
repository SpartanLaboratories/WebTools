package com.spartanlabs.testing.component.webtools.feeds

import com.rometools.rome.io.ParsingFeedException
import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedLimit
import com.spartanlabs.webtools.feeds.FeedRefusal
import com.spartanlabs.webtools.feeds.LimitReached
import com.spartanlabs.webtools.feeds.NotAFeedReason
import com.spartanlabs.webtools.feeds.inRomeRegion
import com.spartanlabs.webtools.feeds.toFeedFailure
import org.jdom2.input.JDOMParseException
import org.xml.sax.SAXParseException
import java.io.IOException
import java.io.UnsupportedEncodingException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 2 - the exception table of `toFeedFailure` and `inRomeRegion`, on exceptions built by hand: the exact
 * `IllegalArgumentException("Invalid document")` is `NotAFeed(UnknownFormat)` and every other `IllegalArgumentException`
 * `Malformed`; a `LimitReached` in the chain is its limit; the JAXP depth and attribute codes and the DOCTYPE feature name
 * classify only on a `SAXParseException`; the chain is read to a depth of eight; everything else is `Malformed` with no
 * cause and no message kept; `inRomeRegion` maps a `StackOverflowError`, maps an `Exception`, rethrows another `Error` and a
 * cancellation.
 */
class RomeFailureMappingComponentTest {

    private class Boom : Error()

    private fun saxParse(message: String) = SAXParseException(message, null)
    private fun wrapped(cause: Throwable): Throwable = ParsingFeedException("Invalid XML", JDOMParseException("Error on line 1", cause))

    @Test
    fun `exactly IllegalArgumentException with the message Invalid document is NotAFeed UnknownFormat`() {
        val failure = assertIs<FeedFailure.NotAFeed>(IllegalArgumentException("Invalid document").toFeedFailure())
        assertEquals(NotAFeedReason.UnknownFormat, failure.reason)
    }

    @Test
    fun `a subclass of IllegalArgumentException with that message is Malformed`() {
        assertIs<FeedFailure.Malformed>(NumberFormatException("Invalid document").toFeedFailure())
        assertIs<FeedFailure.Malformed>(object : IllegalArgumentException("Invalid document") {}.toFeedFailure())
    }

    @Test
    fun `any other IllegalArgumentException is Malformed`() {
        assertIs<FeedFailure.Malformed>(IllegalArgumentException("Invalid document ").toFeedFailure())
        assertIs<FeedFailure.Malformed>(IllegalArgumentException("something else").toFeedFailure())
        assertIs<FeedFailure.Malformed>(IllegalArgumentException().toFeedFailure())
        assertIs<FeedFailure.Malformed>(NumberFormatException("For input string: \"abc\"").toFeedFailure())
    }

    @Test
    fun `a LimitReached in the cause chain is LimitExceeded of its limit, for Elements and NamespaceUris`() {
        assertEquals(FeedLimit.Elements, assertIs<FeedFailure.LimitExceeded>(wrapped(LimitReached(FeedLimit.Elements)).toFeedFailure()).limit)
        assertEquals(FeedLimit.NamespaceUris, assertIs<FeedFailure.LimitExceeded>(wrapped(LimitReached(FeedLimit.NamespaceUris)).toFeedFailure()).limit)
        assertEquals(FeedLimit.Elements, assertIs<FeedFailure.LimitExceeded>(LimitReached(FeedLimit.Elements).toFeedFailure()).limit)
    }

    @Test
    fun `a SAXParseException starting JAXP00010006 is LimitExceeded Depth`() {
        val message = "JAXP00010006: The element \"x\" has a depth of \"257\" that exceeds the limit \"256\" set by \"FEATURE_SECURE_PROCESSING\"."
        assertEquals(FeedLimit.Depth, assertIs<FeedFailure.LimitExceeded>(wrapped(saxParse(message)).toFeedFailure()).limit)
        assertEquals(FeedLimit.Depth, assertIs<FeedFailure.LimitExceeded>(saxParse(message).toFeedFailure()).limit)
    }

    @Test
    fun `a SAXParseException starting JAXP00010002 is LimitExceeded Attributes`() {
        val message = "JAXP00010002:  Element \"x\" has more than \"100\" attributes, \"100\" is the limit imposed by the JDK."
        assertEquals(FeedLimit.Attributes, assertIs<FeedFailure.LimitExceeded>(wrapped(saxParse(message)).toFeedFailure()).limit)
    }

    @Test
    fun `the other JAXP codes, name length and entity limits, are Malformed`() {
        for (code in listOf("JAXP00010001", "JAXP00010003", "JAXP00010004", "JAXP00010005", "JAXP00010007")) {
            assertIs<FeedFailure.Malformed>(wrapped(saxParse("$code: some limit")).toFeedFailure(), code)
        }
    }

    @Test
    fun `a SAXParseException naming disallow-doctype-decl is Refused Doctype`() {
        val message = "DOCTYPE is disallowed when the feature \"http://apache.org/xml/features/disallow-doctype-decl\" set to true."
        assertEquals(FeedRefusal.Doctype, assertIs<FeedFailure.Refused>(wrapped(saxParse(message)).toFeedFailure()).reason)
        assertEquals(FeedRefusal.Doctype, assertIs<FeedFailure.Refused>(saxParse("Feature disallow-doctype-decl").toFeedFailure()).reason)
    }

    @Test
    fun `a message that merely mentions a code, on an exception that is not a SAXParseException, does not classify`() {
        assertIs<FeedFailure.Malformed>(RuntimeException("JAXP00010006: depth").toFeedFailure())
        assertIs<FeedFailure.Malformed>(ParsingFeedException("JAXP00010002: attributes").toFeedFailure())
        assertIs<FeedFailure.Malformed>(IOException("disallow-doctype-decl").toFeedFailure())
        assertIs<FeedFailure.Malformed>(ParsingFeedException("Invalid XML", RuntimeException("JAXP00010006")).toFeedFailure())
    }

    @Test
    fun `the cause chain is read to a depth of eight`() {
        fun nested(depth: Int, leaf: Throwable): Throwable {
            var t: Throwable = leaf
            repeat(depth) { t = RuntimeException("wrap", t) }
            return t
        }
        // The leaf is the eighth link when wrapped seven times, the ninth when wrapped eight times.
        assertIs<FeedFailure.LimitExceeded>(nested(7, LimitReached(FeedLimit.Elements)).toFeedFailure())
        assertIs<FeedFailure.Malformed>(nested(8, LimitReached(FeedLimit.Elements)).toFeedFailure())
        assertIs<FeedFailure.LimitExceeded>(nested(7, saxParse("JAXP00010006: depth")).toFeedFailure())
        assertIs<FeedFailure.Malformed>(nested(8, saxParse("JAXP00010006: depth")).toFeedFailure())
    }

    @Test
    fun `an IOException, an UnsupportedEncodingException, a NullPointerException and a RuntimeException are Malformed, and no failure keeps a cause or a message`() {
        val inputs = listOf(
            IOException("XML prolog or ROOT element not found on first 4096 bytes"), UnsupportedEncodingException("bogus-9"),
            NullPointerException("SECRET-SENTINEL"), RuntimeException("SECRET-SENTINEL"), ParsingFeedException("Invalid XML", NullPointerException("SECRET-SENTINEL")),
        )
        for (input in inputs) {
            val failure = input.toFeedFailure()
            assertIs<FeedFailure.Malformed>(failure, input::class.simpleName)
            assertNull(failure.cause)
            assertFalse(failure.message!!.contains("SENTINEL"))
            assertEquals("The feed document is malformed", failure.message)
        }
        for (mapped in listOf(IllegalArgumentException("Invalid document").toFeedFailure(), wrapped(saxParse("JAXP00010006: x")).toFeedFailure())) {
            assertNull(mapped.cause)
        }
    }

    @Test
    fun `inRomeRegion maps a StackOverflowError to LimitExceeded Depth, maps an Exception, rethrows another Error and rethrows a CancellationException`() {
        val overflow = inRomeRegion<Int> { throw StackOverflowError() }
        assertEquals(FeedLimit.Depth, assertIs<FeedFailure.LimitExceeded>(overflow.exceptionOrNull()).limit)
        val mapped = inRomeRegion<Int> { throw IOException("Unexpected end of XML stream") }
        assertIs<FeedFailure.Malformed>(mapped.exceptionOrNull())
        assertEquals(Result.success(7), inRomeRegion { 7 })
        assertFailsWith<Boom> { inRomeRegion<Int> { throw Boom() } }
        assertFailsWith<CancellationException> { inRomeRegion<Int> { throw CancellationException("cancelled") } }
        assertTrue(inRomeRegion<Int> { throw NumberFormatException("For input string: \"abc\"") }.exceptionOrNull() is FeedFailure.Malformed)
    }
}
