package com.spartanlabs.webtools.feeds

import com.rometools.rome.io.WireFeedInput
import org.jdom2.JDOMException
import org.jdom2.input.sax.XMLReaderJDOMFactory
import org.xml.sax.InputSource
import org.xml.sax.XMLReader
import java.io.StringReader
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

private const val FEATURE_DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"
private const val FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities"
private const val FEATURE_EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities"
private const val FEATURE_LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd"
// The URL spellings: the jdk.xml.* spellings are not recognised by the JDK 11 parser, which then rejects every document.
private const val PROPERTY_MAX_ELEMENT_DEPTH = "http://www.oracle.com/xml/jaxp/properties/maxElementDepth"
private const val PROPERTY_ELEMENT_ATTRIBUTE_LIMIT = "http://www.oracle.com/xml/jaxp/properties/elementAttributeLimit"

/**
 * Rome's input with a SAX parser of its own: nothing here is shared with another parse or with the host application's
 * XML parsing. [createSAXBuilder] never calls `super`, because Rome's own builder uses JDOM's process-wide SAX factory,
 * on which a JAXP limit persists for every later parse in the JVM from JDK 22. The four DOCTYPE and entity features are
 * set directly (a provider that does not recognise one fails the build with a `JDOMException`, which is `Malformed`,
 * instead of being skipped), so Rome's own DOCTYPE ban is a second lock behind the prolog scan.
 */
internal class HardenedWireFeedInput(private val limits: FeedLimits) : WireFeedInput(false, Locale.US) {
    override fun createSAXBuilder(): com.rometools.rome.io.SAXBuilder =
        com.rometools.rome.io.SAXBuilder(PrivateJdkSaxReaders).apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
            setFeature(FEATURE_DISALLOW_DOCTYPE, true)
            setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false)
            setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false)
            setFeature(FEATURE_LOAD_EXTERNAL_DTD, false)
            setExpandEntities(false)
            setProperty(PROPERTY_MAX_ELEMENT_DEPTH, limits.maxDepth)
            setProperty(PROPERTY_ELEMENT_ATTRIBUTE_LIMIT, limits.maxAttributesPerElement)
            setSAXHandlerFactory { factory -> CountingSaxHandler(factory, limits) }
        }
}

/** Stateless: every call builds a fresh JDK SAX factory (namespace aware, not validating, secure processing), so no limit set on one parser reaches another. */
private object PrivateJdkSaxReaders : XMLReaderJDOMFactory {
    override fun createXMLReader(): XMLReader =
        catchNonFatal {
            SAXParserFactory.newDefaultInstance().apply {
                isNamespaceAware = true
                isValidating = false
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }.newSAXParser().xmlReader
        }.getOrElse { throw JDOMException("no JDK SAX parser", it) }

    override fun isValidating(): Boolean = false
}
