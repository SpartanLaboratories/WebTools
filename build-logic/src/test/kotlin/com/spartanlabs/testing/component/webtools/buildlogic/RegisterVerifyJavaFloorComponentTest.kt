package com.spartanlabs.testing.component.webtools.buildlogic

import WebToolsBuild
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Level 2 - [WebToolsBuild.registerVerifyJavaFloor] in isolation, on a [ProjectBuilder] project with
 * no Kotlin plugin applied: the task it registers, what it carries, the loud failure of a second
 * registration, and that the task picks up the metadata task of a publication created after it.
 * The task's behaviour on real files is covered by [JavaFloorScanComponentTest] and the TestKit
 * checks.
 */
class RegisterVerifyJavaFloorComponentTest {

    private val project: Project = ProjectBuilder.builder().build()
    private val jars: FileCollection = project.files("a.jar")
    private val classpath: FileCollection = project.files("dependency.jar")

    private fun register() = WebToolsBuild.registerVerifyJavaFloor(project, jars, classpath)

    @Test
    fun `the task is named verifyJavaFloor and sits in the verification group`() {
        val task = register().get()

        assertEquals("verifyJavaFloor", task.name)
        assertEquals("verification", task.group)
    }

    @Test
    fun `the task carries the floor, the jars, the runtime classpath and a report under build reports`() {
        val task = register().get()

        assertEquals(11, task.javaFloor.get())
        assertEquals(jars.files, task.jars.files)
        assertEquals(classpath.files, task.runtimeClasspath.files)
        assertEquals(
            project.layout.buildDirectory.file("reports/java-floor/verifyJavaFloor.txt").get().asFile,
            task.report.get().asFile,
        )
    }

    @Test
    fun `the task picks up the metadata task of a publication created after it`() {
        val task = register().get()
        project.plugins.apply("java")
        project.plugins.apply("maven-publish")
        project.extensions.getByType(PublishingExtension::class.java).publications
            .create("mavenJava", MavenPublication::class.java) { from(project.components.getByName("java")) } // Action receives the publication as `this` (sam-with-receiver)

        assertEquals(
            setOf(project.layout.buildDirectory.file("publications/mavenJava/module.json").get().asFile),
            task.moduleMetadata.files,
        )
    }

    @Test
    fun `a second registration on the same project fails with InvalidUserDataException`() {
        register()

        val failure = assertThrows(InvalidUserDataException::class.java) { register() }
        assertTrue(failure.message.orEmpty().contains("verifyJavaFloor"), "unexpected message: ${failure.message}")
    }
}
