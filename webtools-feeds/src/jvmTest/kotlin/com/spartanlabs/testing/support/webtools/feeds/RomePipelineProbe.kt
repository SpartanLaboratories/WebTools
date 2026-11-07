package com.spartanlabs.testing.support.webtools.feeds

import com.rometools.rome.io.XmlReader
import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedLimits
import com.spartanlabs.webtools.feeds.FeedRefusal
import com.spartanlabs.webtools.feeds.HardenedWireFeedInput
import com.spartanlabs.webtools.feeds.toFeedFailure
import java.io.ByteArrayInputStream

/** What one `XmlReader` shape, Rome's repair reader and the hardened JDK SAX build did with a document. */
enum class PipelineOutcome { ReachesDoctype, Parses, Error }

/**
 * The real pipeline the prolog scan must stay ahead of: each of seven `XmlReader` constructor shapes feeds
 * `HardenedWireFeedInput` (DOCTYPE ban on). The shapes cover the header-aware constructor lenient and strict, the header-less
 * constructor lenient and strict, the header-aware pair again with `UTF-8` as the default encoding, and the header-less lenient form with
 * `UTF-8` that the module's recipe uses for UTF-16 input and for a header charset the JDK does not know. "Reaches DOCTYPE" is Rome's own lock firing (`Refused(Doctype)` after the mapping); any other exception is
 * an error.
 */
object RomePipelineProbe {
    private const val DEFAULT = "UTF-8"

    /** The seven reader shapes, by name. */
    val shapes: Map<String, (ByteArray, String?) -> XmlReader> = linkedMapOf(
        "(is, ct, lenient)" to { b, ct -> XmlReader(ByteArrayInputStream(b), ct, true) },
        "(is, ct, strict)" to { b, ct -> XmlReader(ByteArrayInputStream(b), ct, false) },
        "(is, lenient)" to { b, _ -> XmlReader(ByteArrayInputStream(b), true) },
        "(is, strict)" to { b, _ -> XmlReader(ByteArrayInputStream(b), false) },
        "(is, ct, lenient, UTF-8)" to { b, ct -> XmlReader(ByteArrayInputStream(b), ct, true, DEFAULT) },
        "(is, ct, strict, UTF-8)" to { b, ct -> XmlReader(ByteArrayInputStream(b), ct, false, DEFAULT) },
        "(is, lenient, UTF-8)" to { b, _ -> XmlReader(ByteArrayInputStream(b), true, DEFAULT) },
    )

    /** The outcome of one shape. */
    fun run(shape: (ByteArray, String?) -> XmlReader, document: ByteArray, contentType: String?, limits: FeedLimits = FeedLimits.Defaults): PipelineOutcome =
        try {
            shape(document, contentType).use { HardenedWireFeedInput(limits).build(it) }
            PipelineOutcome.Parses
        } catch (e: Exception) {
            val mapped = e.toFeedFailure()
            if (mapped is FeedFailure.Refused && mapped.reason == FeedRefusal.Doctype) PipelineOutcome.ReachesDoctype else PipelineOutcome.Error
        } catch (_: StackOverflowError) {
            PipelineOutcome.Error
        }

    /** The outcomes of every shape, by shape name. */
    fun runAll(document: ByteArray, contentType: String?): Map<String, PipelineOutcome> = shapes.mapValues { (_, shape) -> run(shape, document, contentType) }

    /** The combined verdict: reaches DOCTYPE if any shape did, else parses if any did, else error. */
    fun verdict(document: ByteArray, contentType: String?): PipelineOutcome {
        val outcomes = runAll(document, contentType).values
        return when {
            PipelineOutcome.ReachesDoctype in outcomes -> PipelineOutcome.ReachesDoctype
            PipelineOutcome.Parses in outcomes -> PipelineOutcome.Parses
            else -> PipelineOutcome.Error
        }
    }
}
