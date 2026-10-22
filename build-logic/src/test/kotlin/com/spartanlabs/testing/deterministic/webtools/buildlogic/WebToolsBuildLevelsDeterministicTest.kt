package com.spartanlabs.testing.deterministic.webtools.buildlogic

import TestLevel
import WebToolsBuild
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Level 4a - pure input-to-output checks over [WebToolsBuild.LEVELS] and [TestLevel]: the level
 * order, the derived task names and package patterns, and the task descriptions, which must equal
 * the descriptions the root build's level tasks carried before the move with only their
 * `(@Tag("<id>"))` suffix removed.
 */
class WebToolsBuildLevelsDeterministicTest {

    // The former Gradle task descriptions, byte for byte, as the root build wrote them:
    // "$describe (@Tag(\"$tag\"))."
    private val formerDescriptions: Map<String, String> = mapOf(
        "gating" to "Level 1 - local pre-commit gating tests (@Tag(\"gating\")).",
        "component" to "Level 2 - isolated component behaviour tests (@Tag(\"component\")).",
        "integration" to "Level 3 - integration & external interface tests (@Tag(\"integration\")).",
        "deterministic" to "Level 4a - deterministic input-to-output tests (@Tag(\"deterministic\")).",
        "e2e" to "Level 4b - end-to-end system integration tests (@Tag(\"e2e\")).",
        "nonfunctional" to "Level 4c - non-functional (robustness / security / performance) tests (@Tag(\"nonfunctional\")).",
        "uat" to "Level 5 - user-acceptance evaluation (manual; mostly @Disabled) (@Tag(\"uat\")).",
    )

    @Test
    fun `the level ids are the seven levels in hierarchy order with no duplicates`() {
        val ids = WebToolsBuild.LEVELS.map { it.id }

        assertEquals(listOf("gating", "component", "integration", "deterministic", "e2e", "nonfunctional", "uat"), ids)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `each level's task name is the id followed by Test`() {
        WebToolsBuild.LEVELS.forEach { level ->
            assertEquals("${level.id}Test", level.taskName)
        }
    }

    @Test
    fun `each level's package pattern is the level package under com spartanlabs testing`() {
        WebToolsBuild.LEVELS.forEach { level ->
            assertEquals("com.spartanlabs.testing.${level.id}.*", level.packagePattern)
        }
    }

    @Test
    fun `each task description is the former description without its Tag suffix`() {
        assertEquals(formerDescriptions.keys, WebToolsBuild.LEVELS.map { it.id }.toSet())
        WebToolsBuild.LEVELS.forEach { level ->
            val expected = formerDescriptions.getValue(level.id).replace(" (@Tag(\"${level.id}\"))", "")
            assertEquals(expected, level.taskDescription)
        }
    }

    @Test
    fun `an empty id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { TestLevel("", "x") }
    }

    @Test
    fun `a blank id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { TestLevel("  ", "x") }
    }
}
