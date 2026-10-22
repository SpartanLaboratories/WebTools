package com.spartanlabs.testing.support.webtools.buildlogic

import groovy.json.JsonSlurper // groovy-json arrives with gradleApi(); no dependency is declared for it
import java.nio.file.Path

/**
 * The variants of a Gradle Module Metadata file (`module.json`) and their attributes, as a nested
 * build's `generateMetadataFileFor<Publication>Publication` task wrote them.
 *
 * @property variantAttributes each variant's name mapped to its attributes; a read-only map.
 */
class ModuleMetadata private constructor(val variantAttributes: Map<String, Map<String, Any?>>) {

    /**
     * The `org.gradle.jvm.version` attribute of the variant named [variantName].
     *
     * @param variantName the variant, for example `jvmApiElements-published`.
     * @return the attribute's value converted with `toInt()`, or a failure ([NoSuchElementException])
     *   when the variant or the attribute is missing or the value is not a number.
     */
    fun jvmVersion(variantName: String): Result<Int> =
        (variantAttributes[variantName]
            ?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("No variant '$variantName' in ${variantAttributes.keys}")))
            .mapCatching { attributes ->
                (attributes[JVM_VERSION_ATTRIBUTE] as? Number)?.toInt()
                    ?: throw NoSuchElementException("Variant '$variantName' has no numeric $JVM_VERSION_ATTRIBUTE: $attributes")
            }

    /** Factory for parsed metadata. */
    companion object {
        /** The Gradle attribute that carries a variant's target JVM version. */
        const val JVM_VERSION_ATTRIBUTE: String = "org.gradle.jvm.version"

        /**
         * Parses a `module.json` file.
         *
         * @param file the metadata file.
         * @return the parsed metadata, or a failure when the file is missing or is not module
         *   metadata with a `variants` list.
         */
        fun parse(file: Path): Result<ModuleMetadata> = runCatching {
            val root = JsonSlurper().parse(file.toFile()) as Map<*, *>
            val variants = root["variants"] as? List<*> ?: throw IllegalArgumentException("No 'variants' list in $file")
            variants.filterIsInstance<Map<*, *>>().associate { variant ->
                val attributes = (variant["attributes"] as? Map<*, *>).orEmpty()
                variant["name"].toString() to attributes.entries.associate { (key, value) -> key.toString() to value }
            }
        }.map { ModuleMetadata(it) }
    }
}
