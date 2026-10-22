package com.spartanlabs.testing.nonfunctional.webtools.buildlogic

import com.spartanlabs.testing.support.webtools.buildlogic.NestedBuild
import com.spartanlabs.testing.support.webtools.buildlogic.ProjectWorkspace
import com.spartanlabs.testing.support.webtools.buildlogic.SampleProject
import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Level 4c - TestKit check that a module applying `webtools.kotlin-multiplatform-library` is
 * configuration-cache compatible. That is the non-functional property the maintainer's builds rely
 * on. The second identical invocation reuses the cache entry the first one stored. The generated
 * project turns the cache on in its own `gradle.properties`, because TestKit ignores the user-level
 * one.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigurationCacheReuseNonFunctionalTest {

    private lateinit var workspace: ProjectWorkspace
    private lateinit var secondRun: BuildResult

    @BeforeAll
    fun runTwice() {
        workspace = ProjectWorkspace.create("cc").getOrThrow()
        workspace.write(SampleProject.multiplatformJvm("sample")).getOrThrow()
        val build = NestedBuild(workspace)
        build.build("componentTest")
        secondRun = build.build("componentTest")
    }

    @AfterAll
    fun closeWorkspace() {
        workspace.close()
    }

    @Test
    fun `the second identical invocation reuses the configuration cache`() {
        assertTrue(secondRun.output.contains("Reusing configuration cache"), "no configuration-cache reuse:\n${secondRun.output}")
    }
}
