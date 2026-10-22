package com.spartanlabs.testing.integration.webtools.buildlogic

import com.spartanlabs.testing.support.webtools.buildlogic.NestedBuild
import com.spartanlabs.testing.support.webtools.buildlogic.ProjectWorkspace
import com.spartanlabs.testing.support.webtools.buildlogic.SampleProject
import com.spartanlabs.testing.support.webtools.buildlogic.TestReports
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Level 3 - TestKit check that `webtools.kotlin-multiplatform-library`'s lifecycle `test` task runs
 * the JVM test suite: a multiplatform module has no `test` task of its own, so without the alias
 * `./gradlew test` would silently skip it. The lifecycle task's own outcome is not asserted.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LifecycleTestTaskIntegrationTest {

    private lateinit var workspace: ProjectWorkspace
    private lateinit var testResult: BuildResult

    @BeforeAll
    fun runTest() {
        workspace = ProjectWorkspace.create("life").getOrThrow()
        workspace.write(SampleProject.multiplatformJvm("sample")).getOrThrow()
        testResult = NestedBuild(workspace).build("test")
    }

    @AfterAll
    fun closeWorkspace() {
        workspace.close()
    }

    @Test
    fun `test runs jvmTest to success`() {
        assertEquals(TaskOutcome.SUCCESS, testResult.task(":jvmTest")?.outcome)
    }

    @Test
    fun `jvmTest reports the three sample test classes`() {
        assertEquals(
            setOf(SampleProject.COMMON_TEST_CLASS, SampleProject.JVM_TEST_CLASS, SampleProject.UNLEVELED_TEST_CLASS),
            TestReports.resultClasses(workspace, "jvmTest"),
        )
    }
}
