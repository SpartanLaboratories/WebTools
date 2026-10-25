package com.spartanlabs.testing.support.webtools.buildlogic

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path

/**
 * Runs nested Gradle builds of a [ProjectWorkspace] through Gradle TestKit.
 *
 * By default the convention plugins are injected by `GradleRunner.withPluginClasspath()` (the
 * plugin-under-test metadata that `kotlin-dsl` wires into the `test` source set). A workspace that
 * holds its own copy of `build-logic` (see [BuildLogicSource]) runs with [injectPluginClasspath]
 * off, so its builds reach the plugins only through `pluginManagement { includeBuild("build-logic") }`.
 *
 * The build runs in [projectDir]: the workspace directory by default, or a sub-build of the workspace
 * (the fixtures-publication check keeps three builds in one workspace).
 *
 * The TestKit directory, which is also the nested builds' Gradle user home, is the one named by the
 * system property [TESTKIT_HOME_PROPERTY]: `build-logic/build/testkit/home`, set by build-logic's
 * nested-build test tasks. Every nested build of every such task, and later runs, share it. Without
 * the property, TestKit's default directory is used. Nothing from the user-level Gradle properties
 * is forwarded.
 *
 * Every nested build of every check runs with the environment of the build running the check **minus
 * the `ORG_GRADLE_PROJECT_*` variables** ([scrubbedEnvironment]): such a variable sets a Gradle project
 * property and beats a workspace's `gradle.properties`, so a release workflow that exports a signing key
 * as `ORG_GRADLE_PROJECT_signingInMemoryKey` would otherwise hand it to a nested build. The scrub covers that
 * prefix only: JVM options that set `org.gradle.project.*` system properties (`JAVA_TOOL_OPTIONS`, `GRADLE_OPTS`)
 * are out of scope - no supported release path passes a key that way, filtering them needs a quoted-JVM-argument
 * parser, and dropping the variables would break proxy or trust-store hosts.
 *
 * A cold TestKit home downloads the Kotlin compiler and the test dependencies, so the first run needs
 * the network. [build], [buildAndFail] and [run] block until the nested build ends and have no timeout
 * of their own: only the calling task's 30-minute timeout bounds them. The TestKit daemon outlives
 * each call and is reused until the test JVM exits.
 *
 * @param workspace the workspace holding the build(s); the default [projectDir].
 * @param injectPluginClasspath whether the convention plugins are injected with
 *   `GradleRunner.withPluginClasspath()`; `false` for a workspace that includes its own copy of
 *   `build-logic`.
 * @param projectDir the directory of the build to run; the workspace directory by default.
 */
class NestedBuild(
    private val workspace: ProjectWorkspace,
    private val injectPluginClasspath: Boolean = true,
    private val projectDir: Path = workspace.dir,
) {

    /**
     * A runner for [args] in [projectDir], with the shared TestKit directory, the scrubbed environment
     * and, unless switched off, the plugin classpath injected, for a build that the caller configures
     * further before running it.
     *
     * @param args the Gradle command-line arguments, for example task names.
     * @return the configured, not yet started runner.
     */
    fun runner(vararg args: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withEnvironment(scrubbedEnvironment())
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

    // The log names the directory and the command, never the environment.
    private fun timed(args: Array<out String>, start: () -> BuildResult): BuildResult {
        val command = args.joinToString(" ")
        log.info("nested build in {}: {}", projectDir, command)
        val startedAt = System.nanoTime()
        return start().also {
            log.info("nested build in {} finished in {} ms: {}", projectDir, (System.nanoTime() - startedAt) / 1_000_000, command)
        }
    }

    /** The system property the harness reads for the shared TestKit directory, the scrub rule and the class logger. */
    companion object {
        /** The system property naming the TestKit directory every nested build shares. */
        const val TESTKIT_HOME_PROPERTY: String = "webtools.testkit.home"

        /** Environment variables with this prefix set Gradle project properties and beat a workspace's `gradle.properties`. */
        const val SCRUBBED_ENVIRONMENT_PREFIX: String = "ORG_GRADLE_PROJECT_"

        private val log: Logger = LoggerFactory.getLogger(NestedBuild::class.java)

        /**
         * The environment a nested build receives: [source] without every variable whose name starts with
         * [SCRUBBED_ENVIRONMENT_PREFIX] (ASCII case-insensitive on the prefix).
         *
         * @param source the environment to scrub; the process environment by default.
         * @return the scrubbed copy, as a read-only map.
         */
        fun scrubbedEnvironment(source: Map<String, String> = System.getenv()): Map<String, String> =
            source.filterKeys { !it.startsWith(SCRUBBED_ENVIRONMENT_PREFIX, ignoreCase = true) }
    }
}
