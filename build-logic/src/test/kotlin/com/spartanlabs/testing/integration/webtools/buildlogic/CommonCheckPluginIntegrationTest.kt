package com.spartanlabs.testing.integration.webtools.buildlogic

import com.spartanlabs.testing.support.webtools.buildlogic.NestedBuild
import com.spartanlabs.testing.support.webtools.buildlogic.ProjectWorkspace
import com.spartanlabs.testing.support.webtools.buildlogic.SampleProject
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files

/**
 * Level 3 - TestKit check of `webtools.kotlin-common-check`. A check module compiles a directory of
 * common code for `js` and nothing else: the compile accepts JVM-free common code, rejects a JVM API
 * in it and an `expect` without a `js` `actual`; the module publishes nothing, starts no Node.js,
 * Yarn or npm task and writes no `kotlin-js-store/`; and the root project gains no plugin or task.
 *
 * The sample's settings declare no repositories, so the plugin's own `mavenCentral()` resolves the
 * Kotlin standard library for the compile. Only the check module's `build` and `compileKotlinJs` run;
 * nothing is downloaded beyond the Kotlin Gradle plugin's `js` compile dependencies.
 *
 * The builds whose outcome is under test run with [NestedBuild.run], so that each test fails on its
 * own assertion, and not all of them in the set-up, while the plugin is broken.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommonCheckPluginIntegrationTest {

    private lateinit var workspace: ProjectWorkspace
    private lateinit var leakWorkspace: ProjectWorkspace
    private lateinit var missingActualWorkspace: ProjectWorkspace
    private lateinit var buildResult: BuildResult
    private lateinit var dryRun: BuildResult
    private lateinit var checkDryRun: BuildResult
    private lateinit var allTasks: BuildResult
    private lateinit var leakResult: BuildResult
    private lateinit var missingActualResult: BuildResult

    @BeforeAll
    fun runBuilds() {
        workspace = ProjectWorkspace.create("ccp").getOrThrow()
        workspace.write(SampleProject.commonCheck("sample")).getOrThrow()
        NestedBuild(workspace).also { build ->
            buildResult = build.run("build")
            dryRun = build.run("build", "--dry-run")
            checkDryRun = build.run("check", "--dry-run")
            allTasks = build.run("tasks", "--all")
        }

        leakWorkspace = ProjectWorkspace.create("ccl").getOrThrow()
        leakWorkspace.write(
            SampleProject.commonCheck("sample").withFile(
                "${SampleProject.COMMON_CHECK_SHARED}/src/commonMain/kotlin/sample/Leak.kt",
                //language=kotlin
                """
                package sample

                import java.io.File

                fun leak(): String = File("x").name
                """.trimIndent() + "\n",
            ),
        ).getOrThrow()
        leakResult = NestedBuild(leakWorkspace).run("compileKotlinJs")

        missingActualWorkspace = ProjectWorkspace.create("ccm").getOrThrow()
        missingActualWorkspace.write(
            SampleProject.commonCheck("sample").withFile(
                "${SampleProject.COMMON_CHECK_SHARED}/src/commonMain/kotlin/sample/Extra.kt",
                //language=kotlin
                """
                package sample

                expect fun extra(): Int
                """.trimIndent() + "\n",
            ),
        ).getOrThrow()
        missingActualResult = NestedBuild(missingActualWorkspace).run("compileKotlinJs")
    }

    @AfterAll
    fun closeWorkspaces() {
        workspace.close()
        leakWorkspace.close()
        missingActualWorkspace.close()
    }

    @Test
    fun `build compiles the shared common code for js`() {
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":${SampleProject.COMMON_CHECK_MODULE}:compileKotlinJs")?.outcome, buildResult.output)
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":${SampleProject.COMMON_CHECK_MODULE}:build")?.outcome, buildResult.output)
    }

    @Test
    fun `check depends on the js compile`() {
        val compile = ":${SampleProject.COMMON_CHECK_MODULE}:compileKotlinJs "
        assertTrue(checkDryRun.output.lineSequence().any { it.startsWith(compile) }, "no $compile in the check dry run:\n${checkDryRun.output}")
    }

    @Test
    fun `a JVM API in the shared directory fails the js compile`() {
        assertEquals(TaskOutcome.FAILED, leakResult.task(":${SampleProject.COMMON_CHECK_MODULE}:compileKotlinJs")?.outcome, leakResult.output)
        assertTrue(leakResult.output.contains("Leak.kt"), "the error does not name the shared file:\n${leakResult.output}")
        assertTrue(leakResult.output.contains("Unresolved reference"), "no unresolved-reference error:\n${leakResult.output}")
    }

    @Test
    fun `an expect declaration without a js actual fails the js compile`() {
        assertEquals(TaskOutcome.FAILED, missingActualResult.task(":${SampleProject.COMMON_CHECK_MODULE}:compileKotlinJs")?.outcome, missingActualResult.output)
        assertTrue(missingActualResult.output.contains("has no actual declaration"), missingActualResult.output)
    }

    @Test
    fun `build starts no Node, Yarn or npm task and writes no lock store`() {
        val started = buildResult.tasks.map { it.path }.filter { path ->
            listOf("NodeJs", "Yarn", "Npm", "PackageJson").any { path.contains(it) }
        }
        assertEquals(emptyList<String>(), started, buildResult.output)
        assertTrue(Files.notExists(workspace.resolve("kotlin-js-store")), "the build wrote kotlin-js-store/")
        assertTrue(dryRun.output.lineSequence().none { it.contains("jsNodeTest") || it.contains("jsBrowserTest") }, dryRun.output)
    }

    @Test
    fun `the module has no publishing or signing task`() {
        val names = taskNames(allTasks.output) + taskNames(dryRun.output)
        assertTrue(names.isNotEmpty(), "no task names were read from:\n${allTasks.output}")
        val publishing = names.map { it.substringAfterLast(':') }.filter { name ->
            listOf("publish", "sign", "generatePomFile", "generateMetadataFile").any { name.startsWith(it) }
        }
        assertEquals(emptyList<String>(), publishing)
    }

    @Test
    fun `the root project gains no task`() {
        // A dry-run line of a root-project task: a path with one leading colon and no second one, then the outcome.
        //language=RegExp
        val rootTaskLine = Regex("^:[^:]+ ")
        val rootTasks = dryRun.output.lineSequence().filter { rootTaskLine.containsMatchIn(it) }.toList()
        assertEquals(emptyList<String>(), rootTasks, "the root project has tasks of its own:\n${dryRun.output}")
    }

    // The task names in a `--dry-run` or `tasks --all` listing: `:commoncheck:compileKotlinJs SKIPPED` and `commoncheck:compileKotlinJs - ...`.
    private fun taskNames(output: String): List<String> {
        //language=RegExp
        val taskLine = Regex("^:?([A-Za-z][A-Za-z0-9:_-]*)(?: - .*| [A-Z-]+)?$")
        return output.lineSequence().mapNotNull { taskLine.matchEntire(it.trim())?.groupValues?.get(1) }.toList()
    }
}
