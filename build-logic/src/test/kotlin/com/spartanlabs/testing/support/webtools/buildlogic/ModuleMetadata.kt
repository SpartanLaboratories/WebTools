package com.spartanlabs.testing.support.webtools.buildlogic

import groovy.json.JsonSlurper // groovy-json arrives with gradleApi(); no dependency is declared for it
import java.nio.file.Path

/**
 * The variants of a Gradle Module Metadata file (`module.json` or a published `.module`): each variant's
 * attributes, capabilities, dependencies and files, as a nested build's
 * `generateMetadataFileFor<Publication>Publication` task wrote them.
 *
 * @property variants the variants, in file order; a read-only list.
 */
class ModuleMetadata private constructor(val variants: List<Variant>) {

    /** Each variant's name mapped to its attributes; a read-only map. */
    val variantAttributes: Map<String, Map<String, Any?>> get() = variants.associate { it.name to it.attributes }

    /**
     * The variant named [name].
     *
     * @param name the variant, for example `testFixturesApiElements`.
     * @return the variant, or a failure ([NoSuchElementException]) when there is none.
     */
    fun variant(name: String): Result<Variant> =
        variants.firstOrNull { it.name == name }
            ?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("No variant '$name' in ${variants.map { it.name }}"))

    /**
     * The `org.gradle.jvm.version` attribute of the variant named [variantName].
     *
     * @param variantName the variant, for example `jvmApiElements-published`.
     * @return the attribute's value converted with `toInt()`, or a failure ([NoSuchElementException])
     *   when the variant or the attribute is missing or the value is not a number.
     */
    fun jvmVersion(variantName: String): Result<Int> =
        variant(variantName).mapCatching { variant ->
            (variant.attributes[JVM_VERSION_ATTRIBUTE] as? Number)?.toInt()
                ?: throw NoSuchElementException("Variant '$variantName' has no numeric $JVM_VERSION_ATTRIBUTE: ${variant.attributes}")
        }

    /**
     * One variant.
     *
     * @property name the variant name.
     * @property attributes the attributes, key to value.
     * @property capabilities the capabilities as `group:name:version`.
     * @property dependencies the declared dependencies.
     * @property files the `name` of each file entry.
     */
    class Variant(
        val name: String,
        val attributes: Map<String, Any?>,
        val capabilities: List<String>,
        val dependencies: List<Dependency>,
        val files: List<String>,
    )

    /**
     * One dependency of a variant.
     *
     * @property group the group.
     * @property module the module name.
     * @property requires the `version.requires` text, or `null` when none is declared.
     */
    class Dependency(val group: String, val module: String, val requires: String?) {
        override fun toString(): String = "$group:$module:${requires ?: "*"}"
    }

    /** Factory for parsed metadata. */
    companion object {
        /** The Gradle attribute that carries a variant's target JVM version. */
        const val JVM_VERSION_ATTRIBUTE: String = "org.gradle.jvm.version"

        /**
         * Parses a module metadata file.
         *
         * @param file the `module.json` or `.module` file.
         * @return the parsed metadata, or a failure when the file is missing or is not module
         *   metadata with a `variants` list.
         */
        fun parse(file: Path): Result<ModuleMetadata> = runCatching {
            val root = JsonSlurper().parse(file.toFile()) as Map<*, *>
            val variants = root["variants"] as? List<*> ?: throw IllegalArgumentException("No 'variants' list in $file")
            variants.filterIsInstance<Map<*, *>>().map { variant ->
                val attributes = (variant["attributes"] as? Map<*, *>).orEmpty()
                Variant(
                    name = variant["name"].toString(),
                    attributes = attributes.entries.associate { (key, value) -> key.toString() to value },
                    capabilities = (variant["capabilities"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
                        .map { "${it["group"]}:${it["name"]}:${it["version"]}" },
                    dependencies = (variant["dependencies"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { dependency ->
                        Dependency(
                            group = dependency["group"].toString(),
                            module = dependency["module"].toString(),
                            requires = (dependency["version"] as? Map<*, *>)?.get("requires")?.toString(),
                        )
                    },
                    files = (variant["files"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { it["name"].toString() },
                )
            }
        }.map { ModuleMetadata(it) }
    }
}
