package com.spartanlabs.webtools.feeds

import org.jdom2.JDOMFactory
import org.jdom2.input.sax.SAXHandler
import org.xml.sax.Attributes
import org.xml.sax.SAXException

/** Thrown by [CountingSaxHandler] when a structural limit is passed; found in the cause chain by [toFeedFailure]. A SAXException, not a SAXParseException, so the JAXP code reading never confuses the two. */
internal class LimitReached(val limit: FeedLimit) : SAXException()

/**
 * Counts what the parser builds: every element, and every distinct namespace URI, and aborts the parse as soon as a
 * bound is passed, before JDOM adds the 65th URI to its process-wide namespace cache, which never evicts. It counts
 * distinct URIs, not declarations: a real feed repeats one declaration hundreds of times. One instance per build; not
 * thread-safe.
 */
internal class CountingSaxHandler(factory: JDOMFactory, private val limits: FeedLimits) : SAXHandler(factory) {
    private var elements = 0
    private val namespaceUris = HashSet<String>()

    // The count is checked before super builds the element, so the element over the bound is never added to the tree.
    override fun startElement(namespaceURI: String?, localName: String?, qName: String?, atts: Attributes?) {
        elements++
        if (elements > limits.maxElements) throw LimitReached(FeedLimit.Elements)
        super.startElement(namespaceURI, localName, qName, atts)
    }

    override fun startPrefixMapping(prefix: String?, uri: String?) {
        if (uri != null && namespaceUris.add(uri) && namespaceUris.size > limits.maxNamespaceUris) throw LimitReached(FeedLimit.NamespaceUris)
        super.startPrefixMapping(prefix, uri)
    }
}
