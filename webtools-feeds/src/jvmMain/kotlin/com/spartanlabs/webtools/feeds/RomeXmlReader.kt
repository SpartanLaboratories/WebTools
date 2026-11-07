package com.spartanlabs.webtools.feeds

import com.rometools.rome.io.XmlReader
import java.io.ByteArrayInputStream
import java.io.UnsupportedEncodingException

// The default every XmlReader gets, so the prolog scan's allowlist covers the fallback; XmlReader.setDefaultEncoding
// (static) is never called.
private const val DEFAULT_ENCODING = "UTF-8"

/**
 * Opens the document the way the prolog scan reads it. (1) ASCII white space before the first `<` is dropped (the
 * repair would trim it, and it hides the XML declaration from the encoding detection). (2) Input that starts with a
 * UTF-16 mark or the UTF-16 `<?` pattern uses the constructor that ignores the header. (3) Anything else uses the
 * header-aware constructor, retried once without the header when it names a charset the JDK does not know.
 *
 * @throws java.io.IOException (checked in Java, unchecked in Kotlin) for a prolog or root element not found in 4,096
 *   bytes, an unexpected end or a second unsupported encoding: the caller's [inRomeRegion] maps it.
 */
internal fun openXmlReader(document: ByteArray, contentType: String?): XmlReader {
    val bytes = withoutLeadingAsciiWhitespace(document)
    val ignoringHeader = { XmlReader(ByteArrayInputStream(bytes), true, DEFAULT_ENCODING) }
    return if (looksLikeUtf16(bytes)) ignoringHeader()
    else catchNonFatal { XmlReader(ByteArrayInputStream(bytes), contentType, true, DEFAULT_ENCODING) }
        .getOrElse { failure -> if (failure is UnsupportedEncodingException) ignoringHeader() else throw failure }
}

/** The bytes without their leading SP, TAB, CR and LF when those are followed by an ASCII `<`; otherwise the same array. */
internal fun withoutLeadingAsciiWhitespace(bytes: ByteArray): ByteArray {
    var i = 0
    while (i < bytes.size && (bytes[i] == 0x20.toByte() || bytes[i] == 0x09.toByte() || bytes[i] == 0x0D.toByte() || bytes[i] == 0x0A.toByte())) i++
    return if (i > 0 && i < bytes.size && bytes[i] == 0x3C.toByte()) bytes.copyOfRange(i, bytes.size) else bytes
}

/** `FE FF`, `FF FE`, `00 3C 00 3F` or `3C 00 3F 00` at the start. */
internal fun looksLikeUtf16(bytes: ByteArray): Boolean {
    fun at(i: Int, v: Int) = i < bytes.size && (bytes[i].toInt() and 0xFF) == v
    return (at(0, 0xFE) && at(1, 0xFF)) || (at(0, 0xFF) && at(1, 0xFE)) ||
        (at(0, 0x00) && at(1, 0x3C) && at(2, 0x00) && at(3, 0x3F)) || (at(0, 0x3C) && at(1, 0x00) && at(2, 0x3F) && at(3, 0x00))
}
