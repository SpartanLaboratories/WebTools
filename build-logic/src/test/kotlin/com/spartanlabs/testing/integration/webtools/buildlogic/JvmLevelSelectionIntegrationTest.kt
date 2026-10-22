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
 * Level 3 - TestKit check of `webtools.kotlin-jvm-library`, the plugin the published Kotlin/JVM
 * modules apply. It registers the seven level test tasks over the `test` source set, each selecting
 * its tests by package. `test` runs every test class on the JUnit Platform with the shared test
 * dependencies. A class outside every level package runs in `test` and in no level task. `check`
 * runs no level task. The evidence is the result XML files and the `check` dry run, not task
 * outcomes. It runs a generated sample, never a module's test suite.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JvmLevelSelectionIntegrationTest {

    private val levelTasks: List<String> = listOf(
        "gatingTest", "componentTest", "integrationTest", "deterministicTest", "e2eTest", "nonfunctionalTest", "uatTest",
    )

    private lateinit var workspace: ProjectWorkspace
    private lateinit var tasksResult: BuildResult
    private lateinit var checkDryRun: BuildResult

    @BeforeAll
    fun runLevelTasks() {
        workspace = ProjectWorkspace.create("jvm").getOrThrow()
        workspace.write(SampleProject.kotlinJvm("sample")).getOrThrow()
        val build = NestedBuild(workspace)
        tasksResult = build.build("tasks", "--group", "verification")
        build.build(*(levelTasks + "test").toTypedArray())
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
    fun `componentTest runs only the component-package class`() {
        assertEquals(setOf(SampleProject.JVM_COMPONENT_TEST_CLASS), TestReports.resultClasses(workspace, "componentTest"))
    }

    @Test
    fun `integrationTest runs only the integration-package class`() {
        assertEquals(setOf(SampleProject.JVM_INTEGRATION_TEST_CLASS), TestReports.resultClasses(workspace, "integrationTest"))
    }

    @Test
    fun `the levels with no sample class run nothing`() {
        listOf("gatingTest", "deterministicTest", "e2eTest", "nonfunctionalTest", "uatTest").forEach { task ->
            assertEquals(emptySet<String>(), TestReports.resultClasses(workspace, task), "$task ran tests")
        }
    }

    @Test
    fun `test runs every sample class`() {
        assertEquals(
            setOf(SampleProject.JVM_COMPONENT_TEST_CLASS, SampleProject.JVM_INTEGRATION_TEST_CLASS, SampleProject.UNLEVELED_TEST_CLASS),
            TestReports.resultClasses(workspace, "test"),
        )
    }

    @Test
    fun `the unleveled class runs in no level task`() {
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
