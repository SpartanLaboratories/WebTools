package com.spartanlabs.testing.integration.webtools.buildlogic

import com.spartanlabs.testing.support.webtools.buildlogic.NestedBuild
import com.spartanlabs.testing.support.webtools.buildlogic.ProjectWorkspace
import com.spartanlabs.testing.support.webtools.buildlogic.SampleProject
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

/**
 * Level 3 - TestKit check of the Dokka convention, as both library plugins apply it through
 * `webtools.published-library`.
 *
 * For the Kotlin/JVM sample and the multiplatform sample, `dokkaGeneratePublicationHtml` succeeds and writes the themed
 * publication under `build/dokka/html`. A Kotlin/JVM sample with a broken KDoc link and one with an undocumented public
 * function each fail the task, proving `failOnWarning` and `reportUndocumented` reach it, and a Kotlin/JVM sample with
 * either theme file deleted fails it too. Each sample builds only in its own workspace; nothing is published or signed.
 *
 * The builds whose failure is under test run with [NestedBuild.run], so that each test fails on its own assertion, and
 * not all of them in the set-up, while the convention is broken.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DokkaConventionIntegrationTest {

    private val task: String = "dokkaGeneratePublicationHtml"

    private lateinit var jvmWorkspace: ProjectWorkspace
    private lateinit var multiplatformWorkspace: ProjectWorkspace
    private lateinit var brokenWorkspace: ProjectWorkspace
    private lateinit var undocumentedWorkspace: ProjectWorkspace
    private lateinit var missingThemeWorkspace: ProjectWorkspace
    private lateinit var missingLogoWorkspace: ProjectWorkspace
    private lateinit var jvmRun: BuildResult
    private lateinit var multiplatformRun: BuildResult
    private lateinit var brokenRun: BuildResult
    private lateinit var undocumentedRun: BuildResult
    private lateinit var missingThemeRun: BuildResult
    private lateinit var missingLogoRun: BuildResult

    // A Kotlin/JVM sample with one theme file deleted.
    private fun workspaceWithout(label: String, themeFile: String): ProjectWorkspace =
        ProjectWorkspace.create(label).getOrThrow().also { workspace ->
            workspace.write(SampleProject.kotlinJvm("sample")).getOrThrow()
            Files.delete(workspace.resolve(themeFile))
        }

    @BeforeAll
    fun runDokka() {
        jvmWorkspace = ProjectWorkspace.create("dokj").getOrThrow()
        jvmWorkspace.write(SampleProject.kotlinJvm("sample")).getOrThrow()
        jvmRun = NestedBuild(jvmWorkspace).build(task)

        multiplatformWorkspace = ProjectWorkspace.create("dokm").getOrThrow()
        multiplatformWorkspace.write(SampleProject.multiplatformJvm("sample")).getOrThrow()
        multiplatformRun = NestedBuild(multiplatformWorkspace).build(task)

        brokenWorkspace = ProjectWorkspace.create("dokb").getOrThrow()
        brokenWorkspace.write(
            SampleProject.kotlinJvm("sample").withFile(
                "src/main/kotlin/sample/Broken.kt",
                "package sample\n\n/** Links [missing]. */\nfun broken() {}\n",
            ),
        ).getOrThrow()
        brokenRun = NestedBuild(brokenWorkspace).run(task)

        undocumentedWorkspace = ProjectWorkspace.create("doku").getOrThrow()
        undocumentedWorkspace.write(
            SampleProject.kotlinJvm("sample").withFile(
                "src/main/kotlin/sample/Undocumented.kt",
                "package sample\n\nfun undocumented() {}\n",
            ),
        ).getOrThrow()
        undocumentedRun = NestedBuild(undocumentedWorkspace).run(task)

        missingThemeWorkspace = workspaceWithout("dokt", "docs/dokka/styles/dokka-styles.css")
        missingThemeRun = NestedBuild(missingThemeWorkspace).run(task)

        missingLogoWorkspace = workspaceWithout("dokl", "docs/brand/spartan-laboratories-logo.png")
        missingLogoRun = NestedBuild(missingLogoWorkspace).run(task)
    }

    @AfterAll
    fun closeWorkspaces() {
        jvmWorkspace.close()
        multiplatformWorkspace.close()
        brokenWorkspace.close()
        undocumentedWorkspace.close()
        missingThemeWorkspace.close()
        missingLogoWorkspace.close()
    }

    /** The paths of the regular files under [root], relative to it, with forward slashes. */
    private fun filesUnder(root: Path): List<String> =
        Files.walk(root).use { paths ->
            paths.asSequence().filter { Files.isRegularFile(it) }.map { root.relativize(it).toString().replace('\\', '/') }.toList()
        }

    // sourceSet is the Dokka source set holding sample.Greeter; both samples' Gradle project is named `sample`.
    private fun assertPublication(workspace: ProjectWorkspace, sourceSet: String) {
        val output = workspace.resolve("build/dokka/html")
        listOf("index.html", "styles/dokka-styles.css", "images/spartan-laboratories-logo.png").forEach { name ->
            assertTrue(Files.isRegularFile(output.resolve(name)), "$name is not in the publication")
        }
        val files = filesUnder(output)
        val greeterPage = files.firstOrNull { it.endsWith("sample/-greeter/index.html") }
        assertNotNull(greeterPage, "no page of sample.Greeter in the publication: $files")
        val sourceLink = "href=\"https://github.com/SpartanLaboratories/WebTools/blob/master/sample/src/$sourceSet/kotlin/sample/Greeter.kt#L"
        val page = Files.readString(output.resolve(greeterPage!!))
        assertTrue(page.contains(sourceLink), "the Greeter page has no source link starting $sourceLink:\n$page")
        val footer = "<div>&copy; 2026 Spartan Laboratories</div>"
        assertTrue(page.contains(footer), "the Greeter page has no footer $footer:\n$page")
    }

    @Test
    fun `the Kotlin JVM sample generates the themed publication`() {
        assertEquals(TaskOutcome.SUCCESS, jvmRun.task(":$task")?.outcome)
        assertPublication(jvmWorkspace, "main")
    }

    @Test
    fun `the multiplatform sample generates the themed publication`() {
        assertEquals(TaskOutcome.SUCCESS, multiplatformRun.task(":$task")?.outcome)
        assertPublication(multiplatformWorkspace, "commonMain")
    }

    @Test
    fun `a broken KDoc link fails the task`() {
        assertEquals(TaskOutcome.FAILED, brokenRun.task(":$task")?.outcome, brokenRun.output)
        assertTrue(brokenRun.output.contains("Couldn't resolve link"), "the broken link was not reported:\n${brokenRun.output}")
    }

    @Test
    fun `an undocumented public function fails the task`() {
        assertEquals(TaskOutcome.FAILED, undocumentedRun.task(":$task")?.outcome, undocumentedRun.output)
        assertTrue(undocumentedRun.output.contains("Undocumented:"), "the undocumented function was not reported:\n${undocumentedRun.output}")
    }

    @Test
    fun `a missing theme file fails the task`() {
        assertEquals(TaskOutcome.FAILED, missingThemeRun.task(":$task")?.outcome, missingThemeRun.output)
        assertTrue(missingThemeRun.output.contains("NoSuchFileException"), "the missing theme file was not reported:\n${missingThemeRun.output}")
    }

    @Test
    fun `a missing logo fails the task`() {
        assertEquals(TaskOutcome.FAILED, missingLogoRun.task(":$task")?.outcome, missingLogoRun.output)
        assertTrue(missingLogoRun.output.contains("NoSuchFileException"), "the missing logo was not reported:\n${missingLogoRun.output}")
    }
}
