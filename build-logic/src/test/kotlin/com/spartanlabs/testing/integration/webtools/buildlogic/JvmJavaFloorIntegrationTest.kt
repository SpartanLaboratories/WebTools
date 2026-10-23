package com.spartanlabs.testing.integration.webtools.buildlogic

import com.spartanlabs.testing.support.webtools.buildlogic.JarClasses
import com.spartanlabs.testing.support.webtools.buildlogic.ModuleMetadata
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

/**
 * Level 3 - TestKit check of the Java 11 floor in `webtools.kotlin-jvm-library`, the plugin the
 * published Kotlin/JVM modules apply. A generated module built on the build JDK must publish:
 * - Java 11 bytecode (class-file major 55) in its jar;
 * - `org.gradle.jvm.version: 11` on its API and runtime variants;
 * - and compile against the Java 11 API only, so a use of a class added after Java 11 fails to compile.
 *
 * `verifyJavaFloor` must be part of `check`, pass on that module, and fail, naming the class, when
 * the jar holds a class above the floor. Only the generated modules' jar, metadata and compile
 * tasks run; nothing is published or signed.
 *
 * The builds whose outcome is under test run with [NestedBuild.run], so that each test fails on its
 * own assertion, and not all of them in the set-up, while the floor or the guard is missing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JvmJavaFloorIntegrationTest {

    private lateinit var floorWorkspace: ProjectWorkspace
    private lateinit var apiWorkspace: ProjectWorkspace
    private lateinit var tooNewWorkspace: ProjectWorkspace
    private lateinit var verifyResult: BuildResult
    private lateinit var checkDryRun: BuildResult
    private lateinit var apiResult: BuildResult
    private lateinit var tooNewResult: BuildResult
    private lateinit var metadata: ModuleMetadata

    @BeforeAll
    fun runBuilds() {
        floorWorkspace = ProjectWorkspace.create("jfl").getOrThrow()
        floorWorkspace.write(SampleProject.kotlinJvm("sample")).getOrThrow()
        NestedBuild(floorWorkspace).also { build ->
            build.build("jar", "generateMetadataFileForMavenPublication")
            verifyResult = build.run("verifyJavaFloor")
            checkDryRun = build.build("check", "--dry-run")
        }
        metadata = ModuleMetadata.parse(floorWorkspace.resolve("build/publications/maven/module.json")).getOrThrow()

        // java.util.HexFormat is new in Java 17, so it resolves on every build JDK Gradle supports (17 or
        // newer) unless the Java API is limited to 11.
        apiWorkspace = ProjectWorkspace.create("jfa").getOrThrow()
        apiWorkspace.write(
            SampleProject.kotlinJvm("sample").withFile(
                "src/main/kotlin/sample/Hex.kt",
                //language=kotlin
                """
                package sample

                import java.util.HexFormat

                fun hex(bytes: ByteArray): String = HexFormat.of().formatHex(bytes)
                """.trimIndent() + "\n",
            ),
        ).getOrThrow()
        apiResult = NestedBuild(apiWorkspace).run("compileKotlin")

        tooNewWorkspace = ProjectWorkspace.create("jft").getOrThrow()
        val sample = SampleProject.kotlinJvm("sample")
        tooNewWorkspace.write(sample.withFile("build.gradle.kts", sample.files.getValue("build.gradle.kts") + ADD_CLASS_ABOVE_FLOOR)).getOrThrow()
        tooNewResult = NestedBuild(tooNewWorkspace).run("verifyJavaFloor")
    }

    @AfterAll
    fun closeWorkspaces() {
        floorWorkspace.close()
        apiWorkspace.close()
        tooNewWorkspace.close()
    }

    @Test
    fun `every class in the jar is Java 11 bytecode`() {
        val majors = JarClasses.majors(floorWorkspace.resolve("build/libs/sample-1.0.0.jar")).getOrThrow()
        assertEquals(mapOf("sample/Greeter.class" to 55), majors)
    }

    @Test
    fun `the module metadata declares Java 11 on the API and runtime variants`() {
        listOf("apiElements", "runtimeElements").forEach { variant ->
            assertEquals(Result.success(11), metadata.jvmVersion(variant), variant)
        }
    }

    @Test
    fun `a class added after Java 11 does not resolve`() {
        assertEquals(TaskOutcome.FAILED, apiResult.task(":compileKotlin")?.outcome)
        assertTrue(apiResult.output.contains("Unresolved reference 'HexFormat'"), "no unresolved-reference error:\n${apiResult.output}")
    }

    @Test
    fun `check depends on verifyJavaFloor and it passes`() {
        assertTrue(checkDryRun.output.lineSequence().any { it.startsWith(":verifyJavaFloor ") }, ":verifyJavaFloor not in the check dry run")
        assertEquals(TaskOutcome.SUCCESS, verifyResult.task(":verifyJavaFloor")?.outcome)
    }

    @Test
    fun `verifyJavaFloor fails and names a class above the floor`() {
        assertEquals(TaskOutcome.FAILED, tooNewResult.task(":verifyJavaFloor")?.outcome)
        assertTrue(tooNewResult.output.contains("sample-1.0.0.jar!TooNew.class: class-file major 61 (Java 17), above 55 (Java 11)"), tooNewResult.output)
    }

    private companion object {
        // Adds to the jar a file that carries a class-file header for major 61 (Java 17): the magic
        // number 0xCAFEBABE, minor 0, major 61. The check reads headers only.
        //language=kotlin
        val ADD_CLASS_ABOVE_FLOOR: String = "\n" + """
            val classAboveFloor = tasks.register("writeClassAboveFloor") {
                val out = layout.buildDirectory.file("above-floor/TooNew.class")
                outputs.file(out)
                doLast {
                    out.get().asFile.apply { parentFile.mkdirs() }
                        .writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 0, 0, 0, 61))
                }
            }

            tasks.named<Jar>("jar") {
                from(classAboveFloor)
            }
            """.trimIndent() + "\n"
    }
}
