package com.spartanlabs.testing.e2e.webtools.buildlogic

import WebToolsBuild
import com.spartanlabs.testing.support.webtools.buildlogic.BuildLogicSource
import com.spartanlabs.testing.support.webtools.buildlogic.NestedBuild
import com.spartanlabs.testing.support.webtools.buildlogic.ProjectWorkspace
import com.spartanlabs.testing.support.webtools.buildlogic.SampleProject
import com.spartanlabs.testing.support.webtools.buildlogic.TestReports
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Level 4b - the convention plugins along the repository's own path, end to end.
 *
 * A generated composite includes a copy of this `build-logic` build through
 * `pluginManagement { includeBuild("build-logic") }`, with no injected plugin classpath. One module
 * applies each library plugin by id and configures `mavenPublishing {}` through its generated
 * accessor, and a module script reads [WebToolsBuild].
 *
 * It checks that:
 * - the whole composite configures (a dry run of `build`) with the Kotlin Gradle plugin loaded
 *   once;
 * - the module script prints the level list;
 * - a level task runs in each kind of module;
 * - `verifyJavaFloor` is part of `build` and passes in each kind of module.
 *
 * The `webtools.kotlin-common-check` plugin is not part of this composite; it is covered at Level 3 by
 * [com.spartanlabs.testing.integration.webtools.buildlogic.CommonCheckPluginIntegrationTest].
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IncludedBuildEndToEndTest {

    private lateinit var workspace: ProjectWorkspace
    private lateinit var dryRun: BuildResult
    private lateinit var levelRun: BuildResult
    private lateinit var floorRun: BuildResult

    @BeforeAll
    fun runComposite() {
        workspace = ProjectWorkspace.create("e2e").getOrThrow()
        workspace.write(SampleProject.includedBuildComposite("composite")).getOrThrow()
        BuildLogicSource.copyInto(workspace).getOrThrow()
        val build = NestedBuild(workspace, injectPluginClasspath = false)
        dryRun = build.build("build", "--dry-run")
        levelRun = build.build(":jvm-lib:printLevels", ":jvm-lib:componentTest", ":kmp-lib:componentTest")
        // run, not build: while the guard is missing the build fails, and only this test should.
        floorRun = build.run(":jvm-lib:verifyJavaFloor", ":kmp-lib:verifyJavaFloor")
    }

    @AfterAll
    fun closeWorkspace() {
        workspace.close()
    }

    @Test
    fun `the composite configures through the included build`() {
        listOf(
            ":jvm-lib:compileKotlin", ":kmp-lib:compileKotlinJvm", ":kmp-lib:apiCheck",
            ":jvm-lib:verifyJavaFloor", ":kmp-lib:verifyJavaFloor",
        ).forEach { task ->
            assertTrue(dryRun.output.contains(task), "$task is not in the build dry run")
        }
    }

    @Test
    fun `the Kotlin Gradle plugin is loaded once`() {
        assertFalse(dryRun.output.contains("loaded multiple times"), "the Kotlin Gradle plugin was loaded more than once")
    }

    @Test
    fun `a module script reads WebToolsBuild`() {
        val expected = SampleProject.LEVELS_LINE_PREFIX + WebToolsBuild.LEVELS.joinToString(",") { it.id }
        assertTrue(levelRun.output.lineSequence().any { it.trim() == expected }, "'$expected' was not printed")
    }

    @Test
    fun `verifyJavaFloor passes in each kind of module`() {
        listOf(":jvm-lib:verifyJavaFloor", ":kmp-lib:verifyJavaFloor").forEach { task ->
            assertEquals(TaskOutcome.SUCCESS, floorRun.task(task)?.outcome, task)
        }
    }

    @Test
    fun `a level task runs in each kind of module`() {
        assertEquals(
            setOf(SampleProject.COMPOSITE_JVM_COMPONENT_TEST_CLASS),
            TestReports.resultClasses(workspace, "componentTest", SampleProject.COMPOSITE_JVM_MODULE),
        )
        assertEquals(
            setOf(SampleProject.COMPOSITE_MULTIPLATFORM_COMPONENT_TEST_CLASS),
            TestReports.resultClasses(workspace, "componentTest", SampleProject.COMPOSITE_MULTIPLATFORM_MODULE),
        )
    }
}
