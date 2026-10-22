package com.spartanlabs.testing.gating.webtools.buildlogic

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/**
 * Level 1 - build-logic's pre-commit gate: the build compiled and exposes its three convention
 * plugins, and the plugin-under-test metadata that the TestKit checks inject carries the plugins
 * those convention plugins apply. It reads classpath resources only and starts no nested build.
 */
class BuildLogicPluginsGatingTest {

    private val conventionPluginIds: List<String> = listOf(
        "webtools.published-library",
        "webtools.kotlin-jvm-library",
        "webtools.kotlin-multiplatform-library",
    )

    // The jar-name prefixes of the plugins the convention plugins apply by id.
    private val appliedPluginJarPrefixes: List<String> = listOf(
        "kotlin-gradle-plugin-",
        "gradle-maven-publish-plugin-",
        "binary-compatibility-validator-",
    )

    private fun loadProperties(resource: String): Properties =
        javaClass.classLoader.getResourceAsStream(resource)
            ?.use { stream -> Properties().apply { load(stream) } }
            ?: fail("$resource is not on the test runtime classpath")

    @Test
    fun `each convention plugin has a descriptor naming its implementation class`() {
        conventionPluginIds.forEach { id ->
            val implementationClass = loadProperties("META-INF/gradle-plugins/$id.properties").getProperty("implementation-class")
            assertFalse(implementationClass.isNullOrBlank(), "$id names no implementation-class")
        }
    }

    @Test
    fun `the injected plugin classpath carries the plugins the convention plugins apply`() {
        val jarNames = loadProperties("plugin-under-test-metadata.properties")
            .getProperty("implementation-classpath").orEmpty()
            .split(File.pathSeparatorChar)
            .map { File(it).name }
        appliedPluginJarPrefixes.forEach { prefix ->
            assertTrue(jarNames.any { it.startsWith(prefix) }, "no $prefix jar in the plugin-under-test metadata: $jarNames")
        }
    }
}
