package com.spartanlabs.testing.component.webtools.buildlogic

import WebToolsBuild
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test as JUnitTest

/**
 * Level 2 - [WebToolsBuild.registerLevelTasks] in isolation, on a [ProjectBuilder] project with no
 * plugin applied: the seven tasks it registers, that each runs on the JUnit Platform, their group,
 * description and package filter, the
 * test classes and classpath it wires (what both library plugins rely on), and the loud failure of
 * a second registration on the same project.
 */
class RegisterLevelTasksComponentTest {

    private val expectedTaskNames: List<String> = listOf(
        "gatingTest", "componentTest", "integrationTest", "deterministicTest", "e2eTest", "nonfunctionalTest", "uatTest",
    )

    private val project: Project = ProjectBuilder.builder().build()
    private val classesDirs: FileCollection = project.files("classes")
    private val classpath: FileCollection = project.files("cp")

    private fun register(): List<TaskProvider<Test>> = WebToolsBuild.registerLevelTasks(project, classesDirs, classpath)

    private fun undecorated(type: Class<*>): Class<*> = if (type.name.endsWith("_Decorated")) type.superclass else type

    @JUnitTest
    fun `seven providers are returned in level order and the project has exactly those tasks`() {
        val providers = register()

        assertEquals(expectedTaskNames, providers.map { it.name })
        assertEquals(expectedTaskNames.toSet(), project.tasks.names.toSet())
    }

    @JUnitTest
    fun `each task is a plain Test task in the verification group`() {
        register().map { it.get() }.forEach { task ->
            // Gradle instantiates a generated `Test_Decorated` subclass; its declared type is the plain Test.
            assertEquals(Test::class.java, undecorated(task.javaClass))
            assertEquals("verification", task.group)
        }
    }

    @JUnitTest
    fun `each task runs on the JUnit Platform`() {
        // The function sets useJUnitPlatform() itself: on this plugin-less project nothing else does.
        register().map { it.get() }.forEach { task ->
            assertTrue(task.options is JUnitPlatformOptions, "${task.name} does not run on the JUnit Platform: ${task.options.javaClass.name}")
        }
    }

    @JUnitTest
    fun `each description is the level description with a full stop and no Tag mention`() {
        register().zip(WebToolsBuild.LEVELS).forEach { (provider, level) ->
            val description = provider.get().description
            assertEquals("${level.description}.", description)
            assertFalse(description.orEmpty().contains("@Tag"), "description mentions @Tag: $description")
        }
    }

    @JUnitTest
    fun `each task selects exactly its level package and tolerates no matching tests`() {
        register().zip(WebToolsBuild.LEVELS).forEach { (provider, level) ->
            val filter = provider.get().filter
            assertEquals(setOf("com.spartanlabs.testing.${level.id}.*"), filter.includePatterns)
            assertFalse(filter.isFailOnNoMatchingTests)
        }
    }

    @JUnitTest
    fun `each task runs the test classes and classpath it was given`() {
        register().map { it.get() }.forEach { task ->
            assertSame(classesDirs, task.testClassesDirs)
            assertSame(classpath, task.classpath)
        }
    }

    @JUnitTest
    fun `a second registration on the same project fails with InvalidUserDataException`() {
        register()

        val failure = assertThrows(InvalidUserDataException::class.java) { register() }
        assertTrue(failure.message.orEmpty().contains("gatingTest"), "unexpected message: ${failure.message}")
    }
}
