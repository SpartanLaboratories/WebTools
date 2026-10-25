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
 * Level 4c - TestKit check that a module applying `webtools.kotlin-common-check` is
 * configuration-cache compatible, including the source directory it takes from a sibling directory.
 * The second identical invocation reuses the cache entry the first one stored. The generated project
 * turns the cache on in its own `gradle.properties`, because TestKit ignores the user-level one.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommonCheckConfigurationCacheReuseNonFunctionalTest {

    private lateinit var workspace: ProjectWorkspace
    private lateinit var secondRun: BuildResult

    @BeforeAll
    fun runTwice() {
        workspace = ProjectWorkspace.create("cccc").getOrThrow()
        workspace.write(SampleProject.commonCheck("sample")).getOrThrow()
        val build = NestedBuild(workspace)
        build.build("build")
        secondRun = build.build("build")
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
