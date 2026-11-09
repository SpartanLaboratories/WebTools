package com.spartanlabs.testing.gating.webtools.buildlogic

import WebToolsBuild
import com.spartanlabs.testing.support.webtools.buildlogic.BuildLogicSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Level 1 - build-logic's pre-commit gate on what the repository's root scripts and this build must agree on: the
 * versions of the plugins both apply, the modules the root aggregates, the root's `failOnWarning`, and the Dokka task the
 * two workflows run. A difference between the two scripts' versions shows nowhere else, because the build stays green with
 * them apart. It reads text files only and starts no nested build. A commented-out line does not count: `//` line comments
 * are dropped before matching (block comments are not handled, and these scripts have none) and each pattern is anchored
 * at the start of its line or, for `include`, of its statement. The
 * workflows are held to the task's name and not to their job list, which issue #58 extends. The directories come from
 * [BuildLogicSource.SOURCE_PROPERTY], set by `:build-logic:gatingTest` and `:build-logic:test`.
 */
class RootScriptsAgreementGatingTest {

    private val buildLogicDir: Path = Path.of(
        checkNotNull(System.getProperty(BuildLogicSource.SOURCE_PROPERTY)) {
            "System property ${BuildLogicSource.SOURCE_PROPERTY} is not set; run through :build-logic:gatingTest or :build-logic:test"
        },
    )
    private val rootDir: Path = buildLogicDir.resolve("..").normalize()
    private val settings: String = uncommented(Files.readString(rootDir.resolve("settings.gradle.kts")))
    private val rootScript: String = uncommented(Files.readString(rootDir.resolve("build.gradle.kts")))
    private val buildLogicScript: String = uncommented(Files.readString(buildLogicDir.resolve("build.gradle.kts")))

    // Drops each `//` line comment. The pattern steps over quoted strings whole, so a `//` inside one (a URL) stays; a string
    // that spans lines is not handled, and these scripts have none.
    private fun uncommented(text: String): String =
        // `*+` only saves backtracking on a line with no comment: the alternatives are mutually exclusive, so giving back an
        // iteration could never find a match.
        //language=RegExp
        Regex("""(?m)^((?:[^"/\r\n]|"(?:[^"\\\r\n]|\\.)*"|/(?!/))*+)//.*$""").replace(text) { it.groupValues[1] }

    private fun rootVersion(id: String): String? =
        //language=RegExp
        Regex("""(?m)^\s*id\("${Regex.escape(id)}"\) version "([^"]+)"""").find(settings)?.groupValues?.get(1)

    private fun markerVersion(id: String): String? =
        //language=RegExp
        Regex("""(?m)^\s*implementation\("${Regex.escape(id)}:${Regex.escape(id)}\.gradle\.plugin:([^"]+)"""").find(buildLogicScript)?.groupValues?.get(1)

    private fun includedModules(): List<String> =
        // Two stages: each include(...) statement, at the start of a line or after a `;`, then each quoted argument, with or
        // without its leading `:`.
        //language=RegExp
        Regex("""(?m)(?:^|;)\s*include\(([^)]*)\)""").findAll(settings)
            .flatMap { statement -> Regex("""":?([\w-]+)"""").findAll(statement.groupValues[1]).map { it.groupValues[1] } }
            .toList()

    // A module publishes when it applies one of the two library convention plugins.
    private fun publishes(module: String): Boolean =
        //language=RegExp
        Regex("""(?m)^\s*id\("webtools\.kotlin-(jvm|multiplatform)-library"\)""")
            .containsMatchIn(uncommented(Files.readString(rootDir.resolve(module).resolve("build.gradle.kts"))))

    @Test
    fun `the root's plugin versions equal build-logic's markers`() {
        listOf("org.jetbrains.kotlin.jvm", "org.jetbrains.kotlin.multiplatform", "org.jetbrains.dokka").forEach { id ->
            val root = rootVersion(id)
            assertNotNull(root, "settings.gradle.kts declares no version of $id")
            assertEquals(markerVersion(id), root, "$id: build-logic/build.gradle.kts against settings.gradle.kts")
        }
    }

    @Test
    fun `the root aggregates exactly the modules that publish`() {
        //language=RegExp
        val aggregated = Regex("""(?m)^\s*dokka\(project\(":([\w-]+)"\)\)""").findAll(rootScript).map { it.groupValues[1] }.toSortedSet()
        assertEquals(includedModules().filter(::publishes).toSortedSet(), aggregated)
    }

    @Test
    fun `the root build sets failOnWarning`() {
        assertTrue(
            //language=RegExp
            Regex("""(?m)^(?!\s*//).*failOnWarning\.set\(true\)""").containsMatchIn(rootScript),
            "the root build.gradle.kts does not set failOnWarning",
        )
    }

    @Test
    fun `both workflows run the Dokka publication task`() {
        // `(?:- )?`: a workflow step writes `- run:` on its own line, or `run:` under a `- name:` line.
        //language=RegExp
        val runsTask = Regex("""(?m)^\s*(?:- )?run: \./gradlew\b.*\b${Regex.escape(WebToolsBuild.DOKKA_PUBLICATION_TASK)}\b""")
        listOf("ci.yml", "pages.yml").forEach { workflow ->
            val text = Files.readString(rootDir.resolve(".github/workflows").resolve(workflow))
            assertTrue(runsTask.containsMatchIn(text), "$workflow does not run ${WebToolsBuild.DOKKA_PUBLICATION_TASK}")
        }
    }
}
