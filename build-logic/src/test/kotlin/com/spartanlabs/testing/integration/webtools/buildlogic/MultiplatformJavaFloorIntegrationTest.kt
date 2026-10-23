package com.spartanlabs.testing.integration.webtools.buildlogic

import com.spartanlabs.testing.support.webtools.buildlogic.JarClasses
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
 * Level 3 - TestKit check of the Java 11 floor in `webtools.kotlin-multiplatform-library`. A
 * generated module built on the build JDK must publish Java 11 bytecode (class-file major 55) in its
 * `jvm` jar and compile against the Java 11 API only. The published metadata is checked in
 * [PublishedJvmVersionAttributeIntegrationTest].
 *
 * KGP copies the `jvm` target's attributes onto every compilation's classpath configurations, so the
 * floor is also the JVM version each of those classpaths requests: a dependency that declares a
 * higher one is not selected.
 *
 * `verifyJavaFloor` must be part of `check`, pass on that module, and fail, naming the culprit, when
 * the jar holds a class above the floor or a variant declares another version. Only the generated
 * modules' jar, metadata and compile tasks run; nothing is published or signed.
 *
 * The builds whose outcome is under test run with [NestedBuild.run], so that each test fails on its
 * own assertion, and not all of them in the set-up, while the floor or the guard is missing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiplatformJavaFloorIntegrationTest {

    private val classpaths: List<String> =
        listOf("jvmCompileClasspath", "jvmRuntimeClasspath", "jvmTestCompileClasspath", "jvmTestRuntimeClasspath")

    private lateinit var floorWorkspace: ProjectWorkspace
    private lateinit var apiWorkspace: ProjectWorkspace
    private lateinit var tooNewWorkspace: ProjectWorkspace
    private lateinit var variantWorkspace: ProjectWorkspace
    private lateinit var verifyResult: BuildResult
    private lateinit var classpathResult: BuildResult
    private lateinit var checkDryRun: BuildResult
    private lateinit var apiResult: BuildResult
    private lateinit var tooNewResult: BuildResult
    private lateinit var variantResult: BuildResult

    @BeforeAll
    fun runBuilds() {
        floorWorkspace = ProjectWorkspace.create("mfl").getOrThrow()
        floorWorkspace.write(SampleProject.multiplatformJvm("sample").plusScript(PRINT_CLASSPATH_VERSIONS)).getOrThrow()
        NestedBuild(floorWorkspace).also { build ->
            build.build("jvmJar")
            verifyResult = build.run("verifyJavaFloor")
            classpathResult = build.build("printClasspathJvmVersions")
            checkDryRun = build.build("check", "--dry-run")
        }

        // java.util.HexFormat is new in Java 17, so it resolves on every build JDK Gradle supports (17 or
        // newer) unless the Java API is limited to 11.
        apiWorkspace = ProjectWorkspace.create("mfa").getOrThrow()
        apiWorkspace.write(
            SampleProject.multiplatformJvm("sample").withFile(
                "src/jvmMain/kotlin/sample/Hex.kt",
                //language=kotlin
                """
                package sample

                import java.util.HexFormat

                fun hex(bytes: ByteArray): String = HexFormat.of().formatHex(bytes)
                """.trimIndent() + "\n",
            ),
        ).getOrThrow()
        apiResult = NestedBuild(apiWorkspace).run("compileKotlinJvm")

        tooNewWorkspace = ProjectWorkspace.create("mft").getOrThrow()
        tooNewWorkspace.write(SampleProject.multiplatformJvm("sample").plusScript(ADD_CLASS_ABOVE_FLOOR)).getOrThrow()
        tooNewResult = NestedBuild(tooNewWorkspace).run("verifyJavaFloor")

        variantWorkspace = ProjectWorkspace.create("mfv").getOrThrow()
        variantWorkspace.write(SampleProject.multiplatformJvm("sample").plusScript(DECLARE_JAVA_17)).getOrThrow()
        variantResult = NestedBuild(variantWorkspace).run("verifyJavaFloor")
    }

    @AfterAll
    fun closeWorkspaces() {
        floorWorkspace.close()
        apiWorkspace.close()
        tooNewWorkspace.close()
        variantWorkspace.close()
    }

    @Test
    fun `every class in the jvm jar is Java 11 bytecode`() {
        val majors = JarClasses.majors(floorWorkspace.resolve("build/libs/sample-jvm-1.0.0.jar")).getOrThrow()
        assertEquals(mapOf("sample/Greeter.class" to 55), majors)
    }

    @Test
    fun `every jvm classpath requests the floor`() {
        classpaths.forEach { classpath ->
            assertTrue(
                classpathResult.output.lineSequence().any { it.trim() == "$classpath requests org.gradle.jvm.version=11" },
                "$classpath does not request 11:\n${classpathResult.output}",
            )
        }
    }

    @Test
    fun `a class added after Java 11 does not resolve`() {
        assertEquals(TaskOutcome.FAILED, apiResult.task(":compileKotlinJvm")?.outcome)
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
        assertTrue(tooNewResult.output.contains("sample-jvm-1.0.0.jar!TooNew.class: class-file major 61 (Java 17), above 55 (Java 11)"), tooNewResult.output)
    }

    @Test
    fun `verifyJavaFloor fails and names a variant that declares another version`() {
        assertEquals(TaskOutcome.FAILED, variantResult.task(":verifyJavaFloor")?.outcome)
        assertTrue(
            variantResult.output.contains("jvm/module.json: variant 'jvmApiElements-published' declares org.gradle.jvm.version 17, not 11"),
            variantResult.output,
        )
    }

    private fun SampleProject.plusScript(script: String): SampleProject =
        withFile("build.gradle.kts", files.getValue("build.gradle.kts") + script)

    private companion object {
        // Prints the JVM version each jvm classpath requests. The values are read when the task is
        // configured, which is after the Kotlin plugin has copied the target's attributes.
        //language=kotlin
        val PRINT_CLASSPATH_VERSIONS: String = "\n" + """
            tasks.register("printClasspathJvmVersions") {
                val requested = listOf("jvmCompileClasspath", "jvmRuntimeClasspath", "jvmTestCompileClasspath", "jvmTestRuntimeClasspath")
                    .associateWith { name ->
                        configurations.getByName(name).attributes
                            .getAttribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE)
                    }
                doLast {
                    requested.forEach { (name, version) -> println("${'$'}name requests org.gradle.jvm.version=${'$'}version") }
                }
            }
            """.trimIndent() + "\n"

        // Adds to the jvm jar a file that carries a class-file header for major 61 (Java 17): the
        // magic number 0xCAFEBABE, minor 0, major 61. The check reads headers only.
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

            tasks.named<Jar>("jvmJar") {
                from(classAboveFloor)
            }
            """.trimIndent() + "\n"

        // A module that sets the target attribute again after the plugin, to a release above the floor.
        //language=kotlin
        val DECLARE_JAVA_17: String = "\n" + """
            kotlin {
                jvm {
                    attributes {
                        attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
                    }
                }
            }
            """.trimIndent() + "\n"
    }
}
