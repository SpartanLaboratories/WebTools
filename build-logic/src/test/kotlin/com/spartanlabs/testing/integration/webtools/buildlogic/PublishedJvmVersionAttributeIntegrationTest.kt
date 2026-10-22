package com.spartanlabs.testing.integration.webtools.buildlogic

import com.spartanlabs.testing.support.webtools.buildlogic.ModuleMetadata
import com.spartanlabs.testing.support.webtools.buildlogic.NestedBuild
import com.spartanlabs.testing.support.webtools.buildlogic.ProjectWorkspace
import com.spartanlabs.testing.support.webtools.buildlogic.SampleProject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Level 3 - TestKit check that `webtools.kotlin-multiplatform-library` publishes the
 * `org.gradle.jvm.version` attribute on the `jvm()` target's API and runtime variants, in both the
 * `jvm` and the root `kotlinMultiplatform` Gradle Module Metadata. A multiplatform `jvm()` target
 * writes no such attribute unless it is set on the target. Only the metadata files are generated;
 * nothing is published or signed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublishedJvmVersionAttributeIntegrationTest {

    // The feature version of the JVM running the tests, which also runs the nested daemon (TestKit
    // offers no JDK selection): what the plugin sets until the Java floor lands.
    private val expectedJvmVersion: Int = Runtime.version().feature()

    private val jvmVariants: List<String> = listOf("jvmApiElements-published", "jvmRuntimeElements-published")

    private lateinit var workspace: ProjectWorkspace
    private lateinit var jvmMetadata: ModuleMetadata
    private lateinit var rootMetadata: ModuleMetadata

    @BeforeAll
    fun generateMetadata() {
        workspace = ProjectWorkspace.create("attr").getOrThrow()
        workspace.write(SampleProject.multiplatformJvm("sample")).getOrThrow()
        NestedBuild(workspace).build(
            "generateMetadataFileForJvmPublication",
            "generateMetadataFileForKotlinMultiplatformPublication",
        )
        jvmMetadata = ModuleMetadata.parse(workspace.resolve("build/publications/jvm/module.json")).getOrThrow()
        rootMetadata = ModuleMetadata.parse(workspace.resolve("build/publications/kotlinMultiplatform/module.json")).getOrThrow()
    }

    @AfterAll
    fun closeWorkspace() {
        workspace.close()
    }

    @Test
    fun `the jvm module metadata carries the JVM version on both jvm variants`() {
        jvmVariants.forEach { variant ->
            assertEquals(Result.success(expectedJvmVersion), jvmMetadata.jvmVersion(variant), variant)
        }
    }

    @Test
    fun `the root module metadata carries the JVM version on both jvm variants`() {
        jvmVariants.forEach { variant ->
            assertEquals(Result.success(expectedJvmVersion), rootMetadata.jvmVersion(variant), variant)
        }
    }
}
