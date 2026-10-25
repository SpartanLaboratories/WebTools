package com.spartanlabs.testing.e2e.webtools.buildlogic

import com.spartanlabs.testing.support.webtools.buildlogic.BuildLogicSource
import com.spartanlabs.testing.support.webtools.buildlogic.ModuleSource
import com.spartanlabs.testing.support.webtools.buildlogic.NestedBuild
import com.spartanlabs.testing.support.webtools.buildlogic.ProjectWorkspace
import com.spartanlabs.testing.support.webtools.buildlogic.SampleProject
import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Level 4b - both branches of the edge from `webtools-http`'s `check` to `:build-logic:fixturesPublicationCheck`
 * (plan R10), proved by dry runs on a copy of the root build.
 *
 * The **root** workspace has the repository's shape: `pluginManagement { includeBuild("build-logic") }`, the
 * real root `build.gradle.kts`, a copy of `build-logic` ([BuildLogicSource]) and of the module ([ModuleSource]);
 * `:webtools-http:check --dry-run` there must schedule the publication check. The **consumer** workspace includes
 * that root as `webtools`, the shape of a downstream development composite; `:webtools:webtools-http:check --dry-run`
 * there must not schedule it (`gradle.parent != null`). Neither run executes a task.
 *
 * Run by `e2eTest` only: `fixturesPublicationCheck`'s filter names the other class, so the edge's own proof never
 * runs inside `webtools-http`'s `check`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FixturesCheckWiringEndToEndTest {

    private lateinit var root: ProjectWorkspace
    private lateinit var consumer: ProjectWorkspace
    private lateinit var rootRun: BuildResult
    private lateinit var consumerRun: BuildResult

    @BeforeAll
    fun runDryRuns() {
        root = ProjectWorkspace.create("wiring").getOrThrow()
        root.write(SampleProject.fixturesWiringRoot(ModuleSource.rootBuildFile().getOrThrow())).getOrThrow()
        BuildLogicSource.copyInto(root).getOrThrow()
        ModuleSource.copyInto(root, "webtools-http").getOrThrow()
        rootRun = NestedBuild(root, injectPluginClasspath = false).build(":webtools-http:check", "--dry-run")

        consumer = ProjectWorkspace.create("wiringc").getOrThrow()
        // Forward slashes: the path lands inside a settings-script string literal, where a backslash is an escape.
        consumer.write(SampleProject.fixturesWiringConsumer(root.dir.toString().replace('\\', '/'))).getOrThrow()
        consumerRun = NestedBuild(consumer, injectPluginClasspath = false).build(":webtools:webtools-http:check", "--dry-run")
    }

    @AfterAll
    fun closeWorkspaces() {
        consumer.close()
        root.close()
    }

    @Test
    fun `the module's check schedules the publication check when WebTools is the root build`() {
        assertTrue(
            rootRun.output.lineSequence().any { it.trim() == ":build-logic:fixturesPublicationCheck SKIPPED" },
            "the root dry run did not schedule the publication check:\n${rootRun.output}",
        )
    }

    @Test
    fun `a build that includes WebTools never schedules the publication check`() {
        assertTrue(
            consumerRun.output.lineSequence().any { it.trim() == ":webtools:webtools-http:check SKIPPED" },
            "the consumer dry run did not reach the module's check:\n${consumerRun.output}",
        )
        assertFalse(consumerRun.output.contains("fixturesPublicationCheck"), "the consumer dry run scheduled the publication check:\n${consumerRun.output}")
    }
}
