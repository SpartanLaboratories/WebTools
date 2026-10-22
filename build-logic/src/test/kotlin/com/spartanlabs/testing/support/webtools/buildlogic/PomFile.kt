package com.spartanlabs.testing.support.webtools.buildlogic

import org.w3c.dom.Element
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A published POM file, read for the text of its elements by path below `<project>`, for example
 * `licenses/license/name`.
 */
class PomFile private constructor(private val project: Element) {

    /**
     * The trimmed text of the element at [path] below `<project>`, following the first child of each
     * name in turn.
     *
     * @param path the element names from `<project>` down, separated by `/`, for example `scm/url`.
     * @return the text, or `null` when no element is at [path].
     */
    fun text(path: String): String? =
        path.split('/')
            .fold(project as Element?) { element, name -> element?.let { firstChild(it, name) } }
            ?.textContent
            ?.trim()

    private fun firstChild(parent: Element, name: String): Element? =
        (0 until parent.childNodes.length)
            .map { parent.childNodes.item(it) }
            .filterIsInstance<Element>()
            .firstOrNull { it.tagName == name }

    /** Factory for parsed POM files. */
    companion object {
        /**
         * Parses a POM file.
         *
         * @param file the `.pom` file.
         * @return the parsed POM, or a failure when the file is missing, not well-formed XML, or carries a
         *   DOCTYPE.
         */
        fun parse(file: Path): Result<PomFile> = runCatching {
            DocumentBuilderFactory.newInstance()
                // Gradle writes the POM; a DOCTYPE declaration is refused all the same, so no external
                // entity is ever resolved.
                .apply { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                .newDocumentBuilder()
                .parse(file.toFile())
                .documentElement
        }.map { PomFile(it) }
    }
}
