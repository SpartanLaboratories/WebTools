package com.spartanlabs.testing.component.webtools.buildlogic

import VerifyJavaFloor
import WebToolsBuild
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Level 2 - [VerifyJavaFloor.verify] in isolation, on a [ProjectBuilder] project with the task
 * registered through [WebToolsBuild.registerVerifyJavaFloor] and its inputs pointed at jars and
 * metadata files the test writes itself: the report of a passing run, the "nothing to check" and
 * "cannot read" failures, a dependency above the floor, the capped violation list and the removal
 * of a stale report. It starts no Gradle build; the task action is called directly.
 */
class VerifyJavaFloorComponentTest {

    @TempDir
    lateinit var dir: File

    // Built on first use: the temp dir is injected after construction, and the project dir decides where the report lands.
    private val project: Project by lazy { ProjectBuilder.builder().withProjectDir(dir).build() }

    private fun classHeader(major: Int): ByteArray =
        byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 0, 0, (major shr 8).toByte(), major.toByte())

    private fun jar(name: String, vararg entries: Pair<String, ByteArray>): File =
        File(dir, name).also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                entries.forEach { (entryName, bytes) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }

    private fun metadata(publication: String, variants: String): File =
        File(dir, "$publication/module.json").also { file ->
            file.parentFile.mkdirs()
            file.writeText("""{"formatVersion": "1.1", "variants": [$variants]}""")
        }

    private fun variant(name: String, usage: String, category: String, jvmVersion: Int?): String =
        """{"name": "$name", "attributes": {"org.gradle.usage": "$usage", "org.gradle.category": "$category"""" +
            (jvmVersion?.let { """, "org.gradle.jvm.version": $it""" } ?: "") + "}}"

    private fun libraryMetadata(): File =
        metadata(
            "maven",
            listOf(variant("apiElements", "java-api", "library", 11), variant("runtimeElements", "java-runtime", "library", 11)).joinToString(", "),
        )

    private fun task(jars: File, metadata: File, classpath: File): VerifyJavaFloor =
        WebToolsBuild.registerVerifyJavaFloor(project, project.files(jars), project.files(classpath)).get()
            .also { it.moduleMetadata.from(metadata) }

    private fun failure(task: VerifyJavaFloor): String = assertThrows(GradleException::class.java) { task.verify() }.message.orEmpty()

    @Test
    fun `a module at the floor passes and writes the one-line report`() {
        val task = task(jar("own.jar", "a/A.class" to classHeader(55)), libraryMetadata(), jar("dep.jar", "d/D.class" to classHeader(52)))

        task.verify()

        assertEquals(
            "java floor 11 (class-file major <= 55): 1 classes in 1 jar(s), 2 library variants in 1 metadata file(s), 1 classes on the runtime classpath\n",
            task.report.get().asFile.readText(),
        )
    }

    @Test
    fun `a jar without classes fails with no class to verify`() {
        val task = task(jar("own.jar", "a/readme.txt" to "text".toByteArray()), libraryMetadata(), jar("dep.jar"))

        val message = failure(task)

        assertTrue(message.contains("no class to verify"), "unexpected message: $message")
        assertTrue(message.contains("own.jar"), "unexpected message: $message")
    }

    @Test
    fun `metadata without a library variant fails with no library variant to verify`() {
        val sourcesOnly = metadata("maven", variant("sourcesElements", "java-runtime", "documentation", null))
        val task = task(jar("own.jar", "a/A.class" to classHeader(55)), sourcesOnly, jar("dep.jar"))

        val message = failure(task)

        assertTrue(message.contains("no library variant to verify"), "unexpected message: $message")
        assertTrue(message.contains("module.json"), "unexpected message: $message")
    }

    @Test
    fun `a dependency class above the floor on the runtime classpath is a violation`() {
        val task = task(jar("own.jar", "a/A.class" to classHeader(55)), libraryMetadata(), jar("dep.jar", "d/New.class" to classHeader(61)))

        val message = failure(task)

        assertTrue(message.contains("breaks the Java 11 floor"), "unexpected message: $message")
        assertTrue(message.contains("- dep.jar!d/New.class: class-file major 61 (Java 17), above 55 (Java 11)"), "unexpected message: $message")
    }

    @Test
    fun `an unreadable input fails with cannot read`() {
        val notAZip = File(dir, "own.jar").apply { writeText("plain text") }
        val task = task(notAZip, libraryMetadata(), jar("dep.jar"))

        val message = failure(task)

        assertTrue(message.contains("cannot read"), "unexpected message: $message")
        assertTrue(message.contains("own.jar"), "unexpected message: $message")
    }

    @Test
    fun `more violations than the list holds are counted as and N more`() {
        val classes = (1..25).map { "a/C$it.class" to classHeader(61) }.toTypedArray()
        val task = task(jar("own.jar", *classes), libraryMetadata(), jar("dep.jar"))

        val message = failure(task)

        assertEquals(20, message.lines().count { it.startsWith("  - ") }, "unexpected message: $message")
        assertTrue(message.endsWith("\n  ... and 5 more"), "unexpected message: $message")
    }

    @Test
    fun `a failing run deletes the report of the last passing run`() {
        val task = task(jar("own.jar", "a/New.class" to classHeader(61)), libraryMetadata(), jar("dep.jar"))
        val report = task.report.get().asFile.apply { parentFile.mkdirs(); writeText("stale\n") }

        failure(task)

        assertFalse(report.exists(), "the stale report survived the failing run")
    }
}
