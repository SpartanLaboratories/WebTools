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
 * Level 3 - TestKit check of the binary-compatibility-validator wiring in
 * `webtools.kotlin-multiplatform-library`: `apiCheck` fails before a dump exists, `apiDump` writes
 * `api/<rootProject.name>.api`, `apiCheck` then passes, `check` depends on `apiCheck`, and a change
 * to the public API fails `apiCheck`. On a JDK 23 or newer host the `apiDump` step is also the
 * regression guard for the plugin's ASM pin (ASM 9.6 rejects class files above Java 22).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiValidationIntegrationTest {

    private val rootProjectName: String = "sample"

    private lateinit var workspace: ProjectWorkspace
    private lateinit var checkBeforeDump: BuildResult
    private lateinit var checkAfterDump: BuildResult
    private lateinit var checkDryRun: BuildResult
    private lateinit var checkAfterApiChange: BuildResult

    @BeforeAll
    fun runApiValidation() {
        workspace = ProjectWorkspace.create("api").getOrThrow()
        val sample = SampleProject.multiplatformJvm(rootProjectName)
        workspace.write(sample).getOrThrow()
        val build = NestedBuild(workspace)
        checkBeforeDump = build.buildAndFail("apiCheck")
        build.build("apiDump")
        checkAfterDump = build.build("apiCheck")
        checkDryRun = build.build("check", "--dry-run")
        workspace.write(
            sample.withFile(
                SampleProject.GREETER_PATH,
                //language=kotlin
                """
                package sample

                class Greeter {
                    fun greet(name: String): String = "Hello, " + name

                    fun farewell(name: String): String = "Goodbye, " + name
                }
                """.trimIndent() + "\n",
            ),
        ).getOrThrow()
        checkAfterApiChange = build.buildAndFail("apiCheck")
    }

    @AfterAll
    fun closeWorkspace() {
        workspace.close()
    }

    @Test
    fun `apiCheck fails before any dump exists`() {
        assertEquals(TaskOutcome.FAILED, checkBeforeDump.task(":jvmApiCheck")?.outcome)
        assertTrue(
            checkBeforeDump.output.contains("Expected file with API declarations"),
            "missing-dump message not in the output",
        )
    }

    @Test
    fun `apiDump writes the dump named after the root project with the public class`() {
        // apiCheck never rewrites the dump, so the later steps leave it as apiDump wrote it.
        val dumpText = Files.readString(workspace.resolve("api/$rootProjectName.api"))
        assertTrue(dumpText.contains("sample/Greeter"), "sample/Greeter not in the dump:\n$dumpText")
    }

    @Test
    fun `apiCheck passes once the dump exists`() {
        assertEquals(TaskOutcome.SUCCESS, checkAfterDump.task(":jvmApiCheck")?.outcome)
    }

    @Test
    fun `check depends on apiCheck`() {
        assertTrue(checkDryRun.output.contains(":apiCheck"), ":apiCheck not in the check dry run")
    }

    @Test
    fun `apiCheck fails after a public member is added`() {
        assertEquals(TaskOutcome.FAILED, checkAfterApiChange.task(":jvmApiCheck")?.outcome)
    }
}
