package com.spartanlabs.testing.integration.webtools.buildlogic

import com.spartanlabs.testing.support.webtools.buildlogic.NestedBuild
import com.spartanlabs.testing.support.webtools.buildlogic.PomFile
import com.spartanlabs.testing.support.webtools.buildlogic.ProjectWorkspace
import com.spartanlabs.testing.support.webtools.buildlogic.SampleProject
import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * Level 3 - TestKit check of the publishing convention, `webtools.published-library`, as both
 * library plugins apply it. It checks four things:
 * - every published POM carries the shared metadata;
 * - the Maven Central repository and one signing task per publication are registered;
 * - the Kotlin/JVM publication ships a sources jar and a Dokka javadoc jar;
 * - the multiplatform `jvm` publication ships a sources jar and a Dokka javadoc jar.
 *
 * Each sample publishes only to a folder inside its own project, with its `Sign` tasks disabled:
 * nothing reaches a remote repository, and nothing is signed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublishingConventionIntegrationTest {

    // The shared POM metadata webtools.published-library sets: element path below <project> to text.
    private val sharedPom: Map<String, String> = mapOf(
        "inceptionYear" to "2026",
        "url" to "https://github.com/SpartanLaboratories/WebTools",
        "licenses/license/name" to "The Apache License, Version 2.0",
        "licenses/license/url" to "http://www.apache.org/licenses/LICENSE-2.0.txt",
        "licenses/license/distribution" to "http://www.apache.org/licenses/LICENSE-2.0.txt",
        "developers/developer/id" to "SpaSinghOut",
        "developers/developer/name" to "Spartak Singh",
        "developers/developer/url" to "https://github.com/SpaSinghOut",
        "scm/url" to "https://github.com/SpartanLaboratories/WebTools/",
        "scm/connection" to "scm:git:git://github.com/SpartanLaboratories/WebTools.git",
        "scm/developerConnection" to "scm:git:ssh://git@github.com/SpartanLaboratories/WebTools.git",
    )

    // Each sample's folder repository: group sample.webtools, version 1.0.0.
    private val repoDir: String = "build/repo/sample/webtools"

    private lateinit var jvmWorkspace: ProjectWorkspace
    private lateinit var multiplatformWorkspace: ProjectWorkspace
    private lateinit var jvmTasks: BuildResult
    private lateinit var multiplatformTasks: BuildResult

    @BeforeAll
    fun publishBothSamples() {
        jvmWorkspace = ProjectWorkspace.create("pubj").getOrThrow()
        jvmWorkspace.write(SampleProject.kotlinJvm("sample").withLocalPublishing()).getOrThrow()
        NestedBuild(jvmWorkspace).also { build ->
            jvmTasks = build.build("tasks", "--all")
            build.build("publishAllPublicationsToTestFolderRepository")
        }
        multiplatformWorkspace = ProjectWorkspace.create("pubm").getOrThrow()
        multiplatformWorkspace.write(SampleProject.multiplatformJvm("sample").withLocalPublishing()).getOrThrow()
        NestedBuild(multiplatformWorkspace).also { build ->
            multiplatformTasks = build.build("tasks", "--all")
            build.build("publishAllPublicationsToTestFolderRepository")
        }
    }

    @AfterAll
    fun closeWorkspaces() {
        jvmWorkspace.close()
        multiplatformWorkspace.close()
    }

    /** The task names a `tasks --all` report lists, one per `<name> - <description>` line. */
    private fun listedTasks(result: BuildResult): Set<String> =
        result.output.lineSequence().map { it.substringBefore(" - ").trim() }.toSet()

    private fun assertSharedPom(pom: Path) {
        val parsed = PomFile.parse(pom).getOrThrow()
        sharedPom.forEach { (path, expected) -> assertEquals(expected, parsed.text(path), "$path in $pom") }
    }

    @Test
    fun `the Kotlin JVM sample registers the Central repository and signs its publication`() {
        val listed = listedTasks(jvmTasks)
        listOf("publishAllPublicationsToMavenCentralRepository", "signMavenPublication").forEach { task ->
            assertTrue(task in listed, "$task is not registered")
        }
    }

    @Test
    fun `the multiplatform sample registers the Central repository and signs both publications`() {
        val listed = listedTasks(multiplatformTasks)
        listOf(
            "publishAllPublicationsToMavenCentralRepository",
            "signKotlinMultiplatformPublication",
            "signJvmPublication",
        ).forEach { task ->
            assertTrue(task in listed, "$task is not registered")
        }
    }

    @Test
    fun `the Kotlin JVM POM carries the shared metadata`() {
        assertSharedPom(jvmWorkspace.resolve("$repoDir/sample/1.0.0/sample-1.0.0.pom"))
    }

    @Test
    fun `both multiplatform POMs carry the shared metadata`() {
        assertSharedPom(multiplatformWorkspace.resolve("$repoDir/sample/1.0.0/sample-1.0.0.pom"))
        assertSharedPom(multiplatformWorkspace.resolve("$repoDir/sample-jvm/1.0.0/sample-jvm-1.0.0.pom"))
    }

    // The names of a jar's non-directory entries, as read from the zip.
    private fun javadocEntries(jar: Path): List<String> =
        ZipFile(jar.toFile()).use { zip -> zip.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toList() }

    private fun assertDokkaJar(jar: Path) {
        val entries = javadocEntries(jar)
        listOf("index.html", "navigation.html").forEach { name ->
            assertTrue(entries.any { it == name }, "$name is not an entry of $jar: $entries")
        }
    }

    @Test
    fun `the Kotlin JVM publication ships a sources jar and a Dokka javadoc jar`() {
        listOf("sample-1.0.0.jar", "sample-1.0.0-sources.jar", "sample-1.0.0-javadoc.jar").forEach { name ->
            assertTrue(Files.isRegularFile(jvmWorkspace.resolve("$repoDir/sample/1.0.0/$name")), "$name was not published")
        }
        assertDokkaJar(jvmWorkspace.resolve("$repoDir/sample/1.0.0/sample-1.0.0-javadoc.jar"))
    }

    @Test
    fun `the multiplatform jvm publication ships a sources jar and a Dokka javadoc jar`() {
        val dir = "$repoDir/sample-jvm/1.0.0"
        assertTrue(
            Files.isRegularFile(multiplatformWorkspace.resolve("$dir/sample-jvm-1.0.0-sources.jar")),
            "sample-jvm-1.0.0-sources.jar was not published",
        )
        assertDokkaJar(multiplatformWorkspace.resolve("$dir/sample-jvm-1.0.0-javadoc.jar"))
    }
}
