package com.spartanlabs.testing.support.webtools.buildlogic

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.streams.asSequence

/**
 * The sources of this `build-logic` build, copied into a generated composite so that its nested
 * builds reach the convention plugins the way the repository does: through
 * `pluginManagement { includeBuild("build-logic") }`, with no injected plugin classpath.
 *
 * The source directory comes from the system property [SOURCE_PROPERTY], which `:build-logic:e2eTest`
 * sets to this build's project directory. Only `settings.gradle.kts`, `build.gradle.kts` and
 * `src/main/` are copied, never `build/`, `.gradle/` or the tests, so no nested build works in the
 * directory the outer build is executing in.
 */
object BuildLogicSource {
    /** The system property naming this build's project directory. */
    const val SOURCE_PROPERTY: String = "webtools.buildlogic.dir"

    private val log: Logger = LoggerFactory.getLogger(BuildLogicSource::class.java)

    /**
     * Copies this build's settings file, build script and `src/main/` into `build-logic/` under
     * [workspace], replacing files already there.
     *
     * @param workspace the generated composite.
     * @return success, or a failure: an [IllegalStateException] when [SOURCE_PROPERTY] is unset (the
     *   class was not run through `:build-logic:e2eTest`), or the I/O error of the copy.
     */
    fun copyInto(workspace: ProjectWorkspace): Result<Unit> =
        (System.getProperty(SOURCE_PROPERTY)
            ?.let { Result.success(Path.of(it)) }
            ?: Result.failure(IllegalStateException("System property $SOURCE_PROPERTY is not set; run through :build-logic:e2eTest")))
            .mapCatching { source -> copy(source, workspace.resolve("build-logic")) }
            .onSuccess { log.info("copied build-logic into {}", workspace.resolve("build-logic")) }

    private fun copy(source: Path, target: Path) {
        val mainSources = Files.walk(source.resolve("src/main")).use { paths ->
            paths.asSequence().filter { Files.isRegularFile(it) }.toList()
        }
        (listOf(source.resolve("settings.gradle.kts"), source.resolve("build.gradle.kts")) + mainSources).forEach { file ->
            val destination = target.resolve(source.relativize(file))
            Files.createDirectories(destination.parent)
            Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
