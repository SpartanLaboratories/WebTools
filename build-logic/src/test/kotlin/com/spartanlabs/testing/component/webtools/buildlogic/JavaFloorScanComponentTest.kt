package com.spartanlabs.testing.component.webtools.buildlogic

import FloorScan
import JavaFloorScan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Level 2 - [JavaFloorScan] in isolation, on jars, class directories and metadata files that the
 * test writes itself: the class-file check (version, multi-release entries, the magic number, what
 * is not a class, directories, unreadable input) and the Gradle Module Metadata check (which
 * variants count, the three ways a variant breaks the floor, unreadable input). It starts no Gradle
 * build. A "class" here is the eight-byte class-file header, the only part the scan reads.
 */
class JavaFloorScanComponentTest {

    @TempDir
    lateinit var dir: File

    private val java11 = 55

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

    //region class files
    @Test
    fun `classes at or below the floor have no violations`() {
        val scan = JavaFloorScan.classFiles(jar("ok.jar", "a/A.class" to classHeader(55), "a/B.class" to classHeader(52)), java11)

        assertEquals(Result.success(FloorScan(2, emptyList())), scan)
    }

    @Test
    fun `a class above the floor is reported with its jar, entry and releases`() {
        val scan = JavaFloorScan.classFiles(jar("new.jar", "a/A.class" to classHeader(55), "a/New.class" to classHeader(61)), java11).getOrThrow()

        assertEquals(2, scan.checked)
        assertEquals(listOf("new.jar!a/New.class: class-file major 61 (Java 17), above 55 (Java 11)"), scan.violations)
    }

    @Test
    fun `entries under META-INF versions are not checked`() {
        val scan = JavaFloorScan.classFiles(
            jar("multi.jar", "a/A.class" to classHeader(55), "META-INF/versions/17/a/A.class" to classHeader(61)),
            java11,
        )

        assertEquals(Result.success(FloorScan(1, emptyList())), scan)
    }

    @Test
    fun `a class entry without the class-file magic number is reported`() {
        val scan = JavaFloorScan.classFiles(jar("bad.jar", "a/A.class" to "not a class".toByteArray()), java11).getOrThrow()

        assertEquals(listOf("bad.jar!a/A.class: not a class file"), scan.violations)
    }

    @Test
    fun `a class entry shorter than a header is reported`() {
        val scan = JavaFloorScan.classFiles(jar("short.jar", "a/A.class" to byteArrayOf(0xCA.toByte(), 0xFE.toByte())), java11).getOrThrow()

        assertEquals(listOf("short.jar!a/A.class: not a class file"), scan.violations)
    }

    @Test
    fun `entries that are not classes are not counted`() {
        val scan = JavaFloorScan.classFiles(
            jar("res.jar", "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0".toByteArray(), "a/readme.txt" to "text".toByteArray()),
            java11,
        )

        assertEquals(Result.success(FloorScan.EMPTY), scan)
    }

    @Test
    fun `a class directory is checked like a jar`() {
        val classes = File(dir, "classes").apply { File(this, "a").mkdirs() }
        File(classes, "a/Old.class").writeBytes(classHeader(52))
        File(classes, "a/New.class").writeBytes(classHeader(65))
        File(classes, "META-INF/versions/21/a").mkdirs()
        File(classes, "META-INF/versions/21/a/New.class").writeBytes(classHeader(65))

        val scan = JavaFloorScan.classFiles(classes, java11).getOrThrow()

        assertEquals(2, scan.checked)
        assertEquals(listOf("classes/a/New.class: class-file major 65 (Java 21), above 55 (Java 11)"), scan.violations)
    }

    @Test
    fun `a file that does not exist is a failure`() {
        assertTrue(JavaFloorScan.classFiles(File(dir, "missing.jar"), java11).isFailure)
    }

    @Test
    fun `a file that is not a zip is a failure`() {
        val notAZip = File(dir, "plain.jar").apply { writeText("plain text") }

        assertTrue(JavaFloorScan.classFiles(notAZip, java11).isFailure)
    }

    @Test
    fun `scans add up`() {
        assertEquals(FloorScan(3, listOf("a", "b")), FloorScan(1, listOf("a")) + FloorScan(2, listOf("b")))
        assertEquals(FloorScan(1, listOf("a")), FloorScan.EMPTY + FloorScan(1, listOf("a")))
    }
    //endregion

    //region module metadata
    @Test
    fun `library variants at the floor have no violations and sources variants are not checked`() {
        val file = metadata(
            "maven",
            listOf(
                variant("apiElements", "java-api", "library", 11),
                variant("runtimeElements", "java-runtime", "library", 11),
                variant("sourcesElements", "java-runtime", "documentation", null),
            ).joinToString(", "),
        )

        assertEquals(Result.success(FloorScan(2, emptyList())), JavaFloorScan.moduleMetadata(file, 11))
    }

    @Test
    fun `a variant that declares another version is reported with its publication`() {
        val file = metadata("maven", variant("apiElements", "java-api", "library", 23))

        assertEquals(
            listOf("maven/module.json: variant 'apiElements' declares org.gradle.jvm.version 23, not 11"),
            JavaFloorScan.moduleMetadata(file, 11).getOrThrow().violations,
        )
    }

    @Test
    fun `a library variant without the attribute is reported`() {
        val file = metadata("jvm", variant("jvmRuntimeElements-published", "java-runtime", "library", null))

        assertEquals(
            listOf("jvm/module.json: variant 'jvmRuntimeElements-published' declares org.gradle.jvm.version nothing, not 11"),
            JavaFloorScan.moduleMetadata(file, 11).getOrThrow().violations,
        )
    }

    @Test
    fun `variants of another usage are not checked`() {
        val file = metadata("kotlinMultiplatform", variant("metadataApiElements", "kotlin-metadata", "library", null))

        assertEquals(Result.success(FloorScan.EMPTY), JavaFloorScan.moduleMetadata(file, 11))
    }

    @Test
    fun `metadata without a variants list is a failure`() {
        val file = File(dir, "module.json").apply { writeText("""{"formatVersion": "1.1"}""") }

        assertTrue(JavaFloorScan.moduleMetadata(file, 11).isFailure)
    }

    @Test
    fun `a metadata file that is not JSON is a failure`() {
        val file = File(dir, "module.json").apply { writeText("not json") }

        assertTrue(JavaFloorScan.moduleMetadata(file, 11).isFailure)
    }

    @Test
    fun `a metadata file whose root is a list is a failure`() {
        val file = File(dir, "module.json").apply { writeText("[]") }

        assertTrue(JavaFloorScan.moduleMetadata(file, 11).isFailure)
    }

    @Test
    fun `a library variant whose attribute is a string is reported as declaring nothing`() {
        // Pins the current wording: a value that is not a number is read as absent, so a string "11" "declares ... nothing".
        val file = metadata(
            "maven",
            """{"name": "apiElements", "attributes": {"org.gradle.usage": "java-api", "org.gradle.category": "library", "org.gradle.jvm.version": "11"}}""",
        )

        assertEquals(
            listOf("maven/module.json: variant 'apiElements' declares org.gradle.jvm.version nothing, not 11"),
            JavaFloorScan.moduleMetadata(file, 11).getOrThrow().violations,
        )
    }
    //endregion
}
