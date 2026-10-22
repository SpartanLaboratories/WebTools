package com.spartanlabs.testing.support.webtools.buildlogic

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Runs nested Gradle builds of a [ProjectWorkspace] through Gradle TestKit.
 *
 * By default the convention plugins are injected by `GradleRunner.withPluginClasspath()` (the
 * plugin-under-test metadata that `kotlin-dsl` wires into the `test` source set). A workspace that
 * holds its own copy of `build-logic` (see [BuildLogicSource]) runs with [injectPluginClasspath]
 * off, so its builds reach the plugins only through `pluginManagement { includeBuild("build-logic") }`.
 *
 * The TestKit directory, which is also the nested builds' Gradle user home, is the one named by the
 * system property [TESTKIT_HOME_PROPERTY]: `build-logic/build/testkit/home`, set by build-logic's
 * nested-build test tasks. Every nested build of every such task, and later runs, share it. Without
 * the property, TestKit's default directory is used. Nothing from the user-level Gradle properties
 * is forwarded.
 *
 * A cold TestKit home downloads the Kotlin compiler and the test dependencies, so the first run needs
 * the network. [build], [buildAndFail] and [run] block until the nested build ends and have no timeout
 * of their own: only the calling task's 30-minute timeout bounds them. The TestKit daemon outlives
 * each call and is reused until the test JVM exits.
 *
 * @param workspace the project the builds run in.
 * @param injectPluginClasspath whether the convention plugins are injected with
 *   `GradleRunner.withPluginClasspath()`; `false` for a workspace that includes its own copy of
 *   `build-logic`.
 */
class NestedBuild(
    private val workspace: ProjectWorkspace,
    private val injectPluginClasspath: Boolean = true,
) {

    /**
     * A runner for [args] in the workspace, with the shared TestKit directory and, unless switched
     * off, the plugin classpath injected, for a build that the caller configures further before
     * running it.
     *
     * @param args the Gradle command-line arguments, for example task names.
     * @return the configured, not yet started runner.
     */
    fun runner(vararg args: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(workspace.dir.toFile())
            .let { runner -> System.getProperty(TESTKIT_HOME_PROPERTY)?.let { runner.withTestKitDir(File(it)) } ?: runner }
            .let { runner -> if (injectPluginClasspath) runner.withPluginClasspath() else runner }
            .withArguments(*args)

    /**
     * Runs a build that is expected to succeed.
     *
     * @param args the Gradle command-line arguments.
     * @return the build's result.
     * @throws org.gradle.testkit.runner.UnexpectedBuildFailure if the build fails; its message holds the full output.
     */
    fun build(vararg args: String): BuildResult = timed(args) { runner(*args).build() }

    /**
     * Runs a build that is expected to fail.
     *
     * @param args the Gradle command-line arguments.
     * @return the build's result.
     * @throws org.gradle.testkit.runner.UnexpectedBuildSuccess if the build succeeds; its message holds the full output.
     */
    fun buildAndFail(vararg args: String): BuildResult = timed(args) { runner(*args).buildAndFail() }

    /**
     * Runs a build without expecting a particular outcome.
     *
     * @param args the Gradle command-line arguments.
     * @return the build's result, whatever its outcome.
     */
    fun run(vararg args: String): BuildResult = timed(args) { runner(*args).run() }

    private fun timed(args: Array<out String>, start: () -> BuildResult): BuildResult {
        val command = args.joinToString(" ")
        log.info("nested build in {}: {}", workspace.dir, command)
        val startedAt = System.nanoTime()
        return start().also {
            log.info("nested build in {} finished in {} ms: {}", workspace.dir, (System.nanoTime() - startedAt) / 1_000_000, command)
        }
    }

    /** The system property the harness reads for the shared TestKit directory, and the class logger. */
    companion object {
        /** The system property naming the TestKit directory every nested build shares. */
        const val TESTKIT_HOME_PROPERTY: String = "webtools.testkit.home"

        private val log: Logger = LoggerFactory.getLogger(NestedBuild::class.java)
    }
}
