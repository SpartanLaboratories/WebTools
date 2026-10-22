package com.spartanlabs.testing.support.webtools.buildlogic

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * A harness-owned directory that holds one generated project for a TestKit check.
 *
 * It lives under the directory named by the system property [PROJECTS_PROPERTY] (set by
 * build-logic's nested-build test tasks, `integrationTest`, `e2eTest` and `nonfunctionalTest`, to
 * `build-logic/build/testkit/projects`), not in a JUnit
 * `@TempDir`: on Windows `@TempDir` fails a test when a lingering daemon still holds a file, and
 * this directory is instead deleted on a best-effort basis by [close] and reset by `clean`.
 * Directory names stay short (`p-<label>-<base36>`) because the Kotlin Gradle plugin's intermediate
 * paths are deep.
 *
 * @property dir the project directory; it exists once [create] has succeeded.
 */
class ProjectWorkspace private constructor(val dir: Path) : AutoCloseable {

    /**
     * Writes every file of [sample] into [dir], creating parent directories and replacing files
     * that already exist. Files of an earlier sample that [sample] does not hold are left in place.
     *
     * @param sample the project description to write.
     * @return success, or a failure carrying the I/O error.
     */
    fun write(sample: SampleProject): Result<Unit> = runCatching {
        sample.files.forEach { (relativePath, content) ->
            val file = dir.resolve(relativePath)
            Files.createDirectories(file.parent)
            Files.writeString(file, content)
        }
    }.onSuccess { log.info("wrote {} files into {}", sample.files.size, dir) }

    /**
     * Resolves [relativePath] against [dir]; the file need not exist.
     *
     * @param relativePath a path relative to the project directory, with forward slashes.
     * @return the absolute path.
     */
    fun resolve(relativePath: String): Path = dir.resolve(relativePath)

    /**
     * Deletes [dir] and everything in it on a best-effort basis. Never throws: an incomplete
     * deletion (for example a file still held by a TestKit daemon on Windows) is logged as a WARN
     * and the leftovers are removed by `clean`.
     */
    override fun close() {
        runCatching { check(dir.toFile().deleteRecursively()) { "some files could not be deleted" } }
            .onFailure { log.warn("could not fully delete the TestKit project {}: {}", dir, it.message) }
    }

    /** Factory for workspaces and the system property that locates them. */
    companion object {
        /** The system property naming the directory every workspace is created under. */
        const val PROJECTS_PROPERTY: String = "webtools.testkit.projects"

        private val log: Logger = LoggerFactory.getLogger(ProjectWorkspace::class.java)

        /**
         * Creates a new, empty workspace directory `p-<label>-<base36>` under the directory named by
         * [PROJECTS_PROPERTY].
         *
         * @param label a short name for the workspace, for example the test class's subject.
         * @return the workspace, or a failure: an [IllegalStateException] when [PROJECTS_PROPERTY]
         *   is unset (the class was not run through one of build-logic's nested-build test tasks), or the I/O
         *   error creating the directory.
         */
        fun create(label: String): Result<ProjectWorkspace> =
            (System.getProperty(PROJECTS_PROPERTY)
                ?.let { Result.success(Path.of(it)) }
                ?: Result.failure(IllegalStateException("System property $PROJECTS_PROPERTY is not set; run through :build-logic:integrationTest, :build-logic:e2eTest or :build-logic:nonfunctionalTest")))
                .mapCatching { root -> Files.createDirectories(root.resolve("p-$label-${System.nanoTime().toString(36)}")) }
                .map { ProjectWorkspace(it) }
                .onSuccess { log.info("created TestKit project {}", it.dir) }
    }
}
