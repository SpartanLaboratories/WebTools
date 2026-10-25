package com.spartanlabs.testing.support.webtools.buildlogic

import java.nio.file.Files
import java.nio.file.Path

/**
 * What a consumer's `resolutionReport` task wrote for one configuration: the variants Gradle selected and
 * the files it resolved. The task is applied from `resolution-report.gradle.kts`, written into each generated
 * consumer by [SampleProject], and writes `build/reports/resolution/<configuration>.tsv` with one
 * `variant<TAB>project|module<TAB>group:name:version<TAB>variant name<TAB>capabilities (;-separated)<TAB>attributes (k=v;...)`
 * line per selected variant and one `file<TAB>name` line per resolved file.
 *
 * @property variants the selected variants, in file order.
 * @property files the resolved file names, in file order.
 */
class ResolutionReport private constructor(val variants: List<ResolvedVariant>, val files: List<String>) {

    /**
     * One selected variant.
     *
     * @property source `project` for a project component, `module` for a published one.
     * @property module the component as `group:name:version`.
     * @property name the variant's display name.
     * @property capabilities the variant's capabilities as `group:name:version`.
     * @property attributes the variant's attributes, key to value text.
     */
    class ResolvedVariant(
        val source: String,
        val module: String,
        val name: String,
        val capabilities: List<String>,
        val attributes: Map<String, String>,
    ) {
        override fun toString(): String = "$source $module $name $capabilities"
    }

    /** Factory for parsed reports. */
    companion object {
        /**
         * Reads the report of [configuration] of the build at [projectDir].
         *
         * @param workspace the generated project.
         * @param projectDir the project's directory relative to [workspace], with forward slashes.
         * @param configuration the configuration name, for example `testRuntimeClasspath`.
         * @return the report, or a failure: an [IllegalStateException] when the file is missing, a line has an
         *   unknown kind or a `variant` or `file` line is short, or the `IOException` of reading the file.
         */
        fun read(workspace: ProjectWorkspace, projectDir: String, configuration: String): Result<ResolutionReport> {
            val file = workspace.resolve("$projectDir/build/reports/resolution/$configuration.tsv")
            if (!Files.isRegularFile(file)) return Result.failure(IllegalStateException("No resolution report at $file"))
            return runCatching { parse(Files.readAllLines(file)) }
        }

        private fun parse(lines: List<String>): ResolutionReport {
            val variants = ArrayList<ResolvedVariant>()
            val files = ArrayList<String>()
            lines.filter { it.isNotBlank() }.forEach { line ->
                // limit = 6, so an empty capabilities field survives and the attributes keep their own text.
                val fields = line.split("\t", limit = 6)
                when (fields[0]) {
                    "variant" -> {
                        check(fields.size == 6) { "Short variant line: $line" }
                        variants.add(
                            ResolvedVariant(
                                source = fields[1],
                                module = fields[2],
                                name = fields[3],
                                capabilities = fields[4].split(';').filter { it.isNotEmpty() },
                                attributes = fields[5].split(';').filter { it.isNotEmpty() }
                                    .associate { it.substringBefore('=') to it.substringAfter('=', "") },
                            ),
                        )
                    }
                    "file" -> {
                        check(fields.size >= 2) { "Short file line: $line" }
                        files.add(fields[1])
                    }
                    else -> throw IllegalStateException("Unknown report line: $line")
                }
            }
            return ResolutionReport(variants, files)
        }
    }
}
