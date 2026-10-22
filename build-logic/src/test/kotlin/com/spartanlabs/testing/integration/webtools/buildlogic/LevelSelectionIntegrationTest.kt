package com.spartanlabs.testing.integration.webtools.buildlogic

import com.spartanlabs.testing.support.webtools.buildlogic.NestedBuild
import com.spartanlabs.testing.support.webtools.buildlogic.ProjectWorkspace
import com.spartanlabs.testing.support.webtools.buildlogic.SampleProject
import com.spartanlabs.testing.support.webtools.buildlogic.TestReports
import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Level 3 - TestKit check that `webtools.kotlin-multiplatform-library` registers the seven level
 * test tasks over the `jvm()` test compilation and that each selects its tests by package, from
 * `commonTest` and `jvmTest` alike. A test class outside every level package runs in `jvmTest` and
 * in no level task, and `check` runs none of the level tasks. The evidence is the result XML files
 * and the `check` dry run, not task outcomes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LevelSelectionIntegrationTest {

    private val levelTasks: List<String> = listOf(
        "gatingTest", "componentTest", "integrationTest", "deterministicTest", "e2eTest", "nonfunctionalTest", "uatTest",
    )

    private lateinit var workspace: ProjectWorkspace
    private lateinit var tasksResult: BuildResult
    private lateinit var levelsResult: BuildResult
    private lateinit var checkDryRun: BuildResult

    @BeforeAll
    fun runLevelTasks() {
        workspace = ProjectWorkspace.create("lvl").getOrThrow()
        workspace.write(SampleProject.multiplatformJvm("sample")).getOrThrow()
        val build = NestedBuild(workspace)
        tasksResult = build.build("tasks", "--group", "verification")
        levelsResult = build.build(*(levelTasks + "jvmTest").toTypedArray())
        checkDryRun = build.build("check", "--dry-run")
    }

    @AfterAll
    fun closeWorkspace() {
        workspace.close()
    }

    /** The task names listed in the `Verification tasks` section of the `tasks` report. */
    private fun verificationTaskNames(): Set<String> =
        tasksResult.output.lineSequence()
            .dropWhile { it.trim() != "Verification tasks" }
            .drop(2) // the heading and its underline
            .takeWhile { it.isNotBlank() }
            .map { it.substringBefore(" - ").trim() }
            .toSet()

    @Test
    fun `the seven level tasks and test are listed under verification`() {
        val listed = verificationTaskNames()
        (levelTasks + "test").forEach { name ->
            assertTrue(name in listed, "$name is not listed under verification: $listed")
        }
    }

    @Test
    fun `componentTest runs only the commonTest class in the component package`() {
        assertEquals(setOf(SampleProject.COMMON_TEST_CLASS), TestReports.resultClasses(workspace, "componentTest"))
    }

    @Test
    fun `integrationTest runs only the jvmTest class in the integration package`() {
        assertEquals(setOf(SampleProject.JVM_TEST_CLASS), TestReports.resultClasses(workspace, "integrationTest"))
    }

    @Test
    fun `the levels with no sample class run nothing`() {
        listOf("gatingTest", "deterministicTest", "e2eTest", "nonfunctionalTest", "uatTest").forEach { task ->
            assertEquals(emptySet<String>(), TestReports.resultClasses(workspace, task), "$task ran tests")
        }
    }

    @Test
    fun `the unleveled class runs in jvmTest and in no level task`() {
        assertTrue(SampleProject.UNLEVELED_TEST_CLASS in TestReports.resultClasses(workspace, "jvmTest"))
        levelTasks.forEach { task ->
            assertTrue(
                SampleProject.UNLEVELED_TEST_CLASS !in TestReports.resultClasses(workspace, task),
                "${SampleProject.UNLEVELED_TEST_CLASS} ran in $task",
            )
        }
    }

    @Test
    fun `check runs no level task`() {
        val inCheck = levelTasks.filter { task -> checkDryRun.output.lineSequence().any { it.startsWith(":$task ") } }
        assertEquals(emptyList<String>(), inCheck, "level tasks in the check dry run")
    }
}
