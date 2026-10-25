package com.spartanlabs.testing.support.webtools.buildlogic

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.streams.asSequence

/**
 * The sources of the `webtools-http` module, copied into a generated producer build so that the
 * fixtures-publication check publishes the real module with its real test-fixtures variant.
 *
 * The module directory comes from the system property [MODULE_PROPERTY], which
 * `:build-logic:fixturesPublicationCheck` and `:build-logic:e2eTest` set to the absolute path of the sibling
 * `webtools-http` directory. Only the
 * module's `build.gradle.kts` and its `src/commonMain`, `src/jvmMain` and `src/jvmTestFixtures` trees are
 * copied (regular files only), never `build/`, `.gradle/`, the tests or `api/`, so no nested build works in
 * the directory the outer build is executing in. All three trees must exist: a missing one would let the
 * check pass without the fixtures. The repository's root build file is the module directory's sibling.
 */
object ModuleSource {
    /** The system property naming the module's directory. */
    const val MODULE_PROPERTY: String = "webtools.http.dir"

    private val SOURCE_TREES: List<String> = listOf("commonMain", "jvmMain", "jvmTestFixtures")

    private val log: Logger = LoggerFactory.getLogger(ModuleSource::class.java)

    /**
     * Copies the module's build file and `src/commonMain`, `src/jvmMain` and `src/jvmTestFixtures` into
     * [targetDir] under [workspace], replacing files already there.
     *
     * @param workspace the generated build.
     * @param targetDir the module's directory relative to the workspace, with forward slashes, for example
     *   `producer/webtools-http`.
     * @return success, or a failure: an [IllegalStateException] when [MODULE_PROPERTY] is unset (the class was
     *   not run through `:build-logic:fixturesPublicationCheck` or `:build-logic:e2eTest`), a
     *   [NoSuchFileException] when the build file or one of the three trees is missing, or the I/O error of the copy.
     */
    fun copyInto(workspace: ProjectWorkspace, targetDir: String): Result<Unit> =
        moduleDir()
            .mapCatching { source -> copy(source, workspace.resolve(targetDir)) }
            .onSuccess { count -> log.info("copied {} files of webtools-http into {}", count, workspace.resolve(targetDir)) }
            .map { }

    /**
     * The text of the repository's root `build.gradle.kts`, the module directory's sibling (the module sits
     * directly under the repository root).
     *
     * @return the file's text, or a failure: an [IllegalStateException] when [MODULE_PROPERTY] is unset, a
     *   [NoSuchFileException] when the file is missing, or the I/O error.
     */
    fun rootBuildFile(): Result<String> =
        moduleDir().mapCatching { module ->
            val file = module.resolve("..").resolve("build.gradle.kts").normalize()
            if (!Files.isRegularFile(file)) throw NoSuchFileException(file.toString())
            Files.readString(file)
        }

    private fun moduleDir(): Result<Path> =
        System.getProperty(MODULE_PROPERTY)
            ?.let { Result.success(Path.of(it)) }
            ?: Result.failure(IllegalStateException("System property $MODULE_PROPERTY is not set; run through :build-logic:fixturesPublicationCheck or :build-logic:e2eTest"))

    // Returns the number of files copied.
    private fun copy(source: Path, target: Path): Int {
        val buildFile = source.resolve("build.gradle.kts")
        if (!Files.isRegularFile(buildFile)) throw NoSuchFileException(buildFile.toString())
        val trees = SOURCE_TREES.map { source.resolve("src").resolve(it) }
        trees.firstOrNull { !Files.isDirectory(it) }?.let { throw NoSuchFileException(it.toString()) }
        val sources = trees.flatMap { tree ->
            Files.walk(tree).use { paths -> paths.asSequence().filter { Files.isRegularFile(it) }.toList() }
        }
        (listOf(buildFile) + sources).forEach { file ->
            val destination = target.resolve(source.relativize(file))
            Files.createDirectories(destination.parent)
            Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING)
        }
        return sources.size + 1
    }
}
