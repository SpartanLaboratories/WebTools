package com.spartanlabs.testing.e2e.webtools.buildlogic

import com.spartanlabs.testing.support.webtools.buildlogic.BuildLogicSource
import com.spartanlabs.testing.support.webtools.buildlogic.ModuleSource
import com.spartanlabs.testing.support.webtools.buildlogic.NestedBuild
import com.spartanlabs.testing.support.webtools.buildlogic.ProjectWorkspace
import com.spartanlabs.testing.support.webtools.buildlogic.SampleProject
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

/**
 * Level 4b - the Dokka aggregation along the repository's own path, end to end.
 *
 * A generated two-module composite uses the repository's real `pluginManagement`, `plugins` and `dokka` blocks, includes a
 * copy of this `build-logic` build (no injected classpath) and aggregates both modules at its root. The real `plugins`
 * block's `apply false` Kotlin plugins are the guard against the modules rendering empty when the root and the modules load
 * the plugins in different classloaders, and the real `dokka` block carries the theme; this class checks that each module
 * has its pages, that the theme files are in the aggregate and that the Kotlin Gradle plugin loaded once. A second composite
 * has a broken KDoc link in one module and shows that it fails the root task.
 *
 * The builds run with [NestedBuild.run], so that each test fails on its own assertion, and not all of them in the set-up,
 * while the aggregation is broken.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DokkaAggregationEndToEndTest {

    private val task: String = "dokkaGeneratePublicationHtml"

    private lateinit var workspace: ProjectWorkspace
    private lateinit var brokenWorkspace: ProjectWorkspace
    private lateinit var run: BuildResult
    private lateinit var brokenRun: BuildResult
    private lateinit var output: Path

    private fun composite(): SampleProject =
        SampleProject.dokkaAggregationComposite(
            "composite",
            ModuleSource.rootBlock("settings.gradle.kts", "pluginManagement").getOrThrow(),
            ModuleSource.rootBlock("build.gradle.kts", "plugins").getOrThrow(),
            ModuleSource.rootBlock("build.gradle.kts", "dokka").getOrThrow(),
        )

    @BeforeAll
    fun aggregate() {
        workspace = ProjectWorkspace.create("doka").getOrThrow()
        workspace.write(composite()).getOrThrow()
        BuildLogicSource.copyInto(workspace).getOrThrow()
        run = NestedBuild(workspace, injectPluginClasspath = false).run(task)
        output = workspace.resolve("build/dokka/html")

        brokenWorkspace = ProjectWorkspace.create("dokab").getOrThrow()
        brokenWorkspace.write(
            composite().withFile(
                "${SampleProject.COMPOSITE_JVM_MODULE}/src/main/kotlin/sample/Broken.kt",
                "package sample\n\n/** Links [missing]. */\nfun broken() {}\n",
            ),
        ).getOrThrow()
        BuildLogicSource.copyInto(brokenWorkspace).getOrThrow()
        brokenRun = NestedBuild(brokenWorkspace, injectPluginClasspath = false).run(task)
    }

    @AfterAll
    fun closeWorkspaces() {
        workspace.close()
        brokenWorkspace.close()
    }

    private fun filesUnder(root: Path): List<String> =
        Files.walk(root).use { paths ->
            paths.asSequence().filter { Files.isRegularFile(it) }.map { root.relativize(it).toString().replace('\\', '/') }.toList()
        }

    @Test
    fun `the aggregate task succeeds`() {
        assertEquals(TaskOutcome.SUCCESS, run.task(":$task")?.outcome, run.output)
    }

    @Test
    fun `the aggregate index names both modules`() {
        val index = Files.readString(output.resolve("index.html"))
        listOf(SampleProject.COMPOSITE_JVM_MODULE, SampleProject.COMPOSITE_MULTIPLATFORM_MODULE).forEach { module ->
            assertTrue(index.contains(module), "the aggregate index does not name $module")
        }
    }

    @Test
    fun `each module renders its pages`() {
        listOf(SampleProject.COMPOSITE_JVM_MODULE, SampleProject.COMPOSITE_MULTIPLATFORM_MODULE).forEach { module ->
            val files = filesUnder(output.resolve(module))
            assertTrue(files.any { it.endsWith("sample/-greeter/index.html") }, "$module rendered no page of sample.Greeter: $files")
        }
    }

    @Test
    fun `the real dokka block puts the theme files in the aggregate`() {
        listOf("styles/dokka-styles.css", "images/spartan-laboratories-logo.png").forEach { name ->
            assertTrue(Files.isRegularFile(output.resolve(name)), "$name is not in the aggregate")
        }
    }

    // The composite's projects have version 1.0.0, so an empty version element shows the real block's moduleVersion.
    @Test
    fun `the real dokka block sets the aggregate's name, empty version and footer`() {
        val index = Files.readString(output.resolve("index.html"))
        assertTrue(Regex("""<a class="library-name--link"[^>]*>\s*WebTools\s*</a>""").containsMatchIn(index), "the aggregate is not named WebTools:\n$index")
        assertTrue(Regex("""<div class="library-version" id="library-version">\s*</div>""").containsMatchIn(index), "the aggregate names a version:\n$index")
        assertTrue(index.contains("<div>&copy; 2026 Spartan Laboratories</div>"), "the aggregate index has no footer:\n$index")
    }

    @Test
    fun `the Kotlin Gradle plugin is loaded once`() {
        assertFalse(run.output.contains("loaded multiple times"), "the Kotlin Gradle plugin was loaded more than once")
    }

    @Test
    fun `a broken link in one module fails the root task`() {
        assertEquals(TaskOutcome.FAILED, brokenRun.task(":${SampleProject.COMPOSITE_JVM_MODULE}:dokkaGenerateModuleHtml")?.outcome, brokenRun.output)
        assertTrue(brokenRun.output.contains("Couldn't resolve link"), "the broken link was not reported:\n${brokenRun.output}")
        assertTrue(brokenRun.output.contains("BUILD FAILED"), "the build did not fail:\n${brokenRun.output}")
    }
}
