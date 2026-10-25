package com.spartanlabs.testing.deterministic.webtools.http.testing

import com.spartanlabs.webtools.http.testing.RecordMode
import com.spartanlabs.webtools.http.testing.RecordingGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Level 4a - `RecordingGate.evaluate` as a value x CI-variable matrix: every recognised and unrecognised
 * value against no CI variable and each of the twelve (present with a value, with `false`, and with an empty
 * value); the reasons are constants or name the variable only; `CI_VARIABLES` is the twelve in order; the
 * default reads `System.getenv()`.
 */
class RecordingGateDeterministicTest {

    private val off = "recording is off: set WEBTOOLS_HTTP_RECORD to 1, true, new, all or overwrite to record"
    private val unrecognised = "WEBTOOLS_HTTP_RECORD is not 1, true, new, all or overwrite"
    private val newValues = listOf("1", " 1 ", "true", "TRUE", "new", "New")
    private val overwriteValues = listOf("all", "ALL", "Overwrite", "overwrite")
    private val badValues = listOf("0", "false", "yes", "on", "record")
    private val twelve = listOf(
        "CI", "GITHUB_ACTIONS", "GITLAB_CI", "JENKINS_URL", "BUILDKITE", "CIRCLECI", "TF_BUILD",
        "TEAMCITY_VERSION", "TRAVIS", "APPVEYOR", "CODEBUILD_BUILD_ARN", "BITBUCKET_COMMIT",
    )

    private fun env(value: String?, ci: Pair<String, String>? = null): Map<String, String> =
        buildMap {
            if (value != null) put("WEBTOOLS_HTTP_RECORD", value)
            if (ci != null) put(ci.first, ci.second)
            put("PATH", "/usr/bin")   // an unrelated variable the gate must ignore
        }

    @Test
    fun `absent or empty is off whatever else is set`() {
        listOf<String?>(null, "", "  ").forEach { value ->
            assertEquals(off, assertIs<RecordingGate.Disabled>(RecordingGate.evaluate(env(value))).reason)
            twelve.forEach { ci -> assertEquals(off, assertIs<RecordingGate.Disabled>(RecordingGate.evaluate(env(value, ci to "true"))).reason) }
        }
    }

    @Test
    fun `the recognised values enable New or Overwrite with no CI variable`() {
        newValues.forEach { value -> assertEquals(RecordMode.New, assertIs<RecordingGate.Enabled>(RecordingGate.evaluate(env(value))).mode, value) }
        overwriteValues.forEach { value -> assertEquals(RecordMode.Overwrite, assertIs<RecordingGate.Enabled>(RecordingGate.evaluate(env(value))).mode, value) }
        assertEquals("RecordingGate.Enabled(New)", RecordingGate.evaluate(env("1")).toString())
        assertEquals("RecordingGate.Enabled(Overwrite)", RecordingGate.evaluate(env("all")).toString())
    }

    @Test
    fun `an unrecognised value is off with the constant reason`() {
        badValues.forEach { value ->
            val gate = assertIs<RecordingGate.Disabled>(RecordingGate.evaluate(env(value)), value)
            assertEquals(unrecognised, gate.reason)
            assertFalse(value in gate.reason.removePrefix(unrecognised))
        }
        assertEquals("RecordingGate.Disabled($unrecognised)", RecordingGate.evaluate(env("yes")).toString())
    }

    @Test
    fun `any of the twelve CI variables refuses a recognised value with any value including empty`() {
        (newValues + overwriteValues + badValues).forEach { value ->
            twelve.forEach { ci ->
                listOf("true", "false", "", "1").forEach { ciValue ->
                    val gate = assertIs<RecordingGate.Disabled>(RecordingGate.evaluate(env(value, ci to ciValue)), "$value / $ci=$ciValue")
                    assertEquals("refusing to record: $ci is set", gate.reason)
                }
            }
        }
    }

    @Test
    fun `every Disabled reason is a constant or names the variable only and never the value`() {
        val secret = "SECRET-CANARY-7f3a"
        val reasons = listOf(
            RecordingGate.evaluate(env(secret)),
            RecordingGate.evaluate(env("1", "CI" to secret)),
            RecordingGate.evaluate(env(null, "CI" to secret)),
        ).map { assertIs<RecordingGate.Disabled>(it).reason }
        reasons.forEach { reason ->
            assertTrue(reason == off || reason == unrecognised || twelve.any { reason == "refusing to record: $it is set" }, reason)
            assertFalse(secret in reason)
        }
    }

    @Test
    fun `CI_VARIABLES is the twelve in order and RECORD_VARIABLE is the name`() {
        assertEquals(twelve, RecordingGate.CI_VARIABLES)
        assertEquals("WEBTOOLS_HTTP_RECORD", RecordingGate.RECORD_VARIABLE)
    }

    @Test
    fun `the default reads the process environment and returns a gate`() {
        val gate = RecordingGate.evaluate()
        val expected = RecordingGate.evaluate(System.getenv())
        assertEquals(expected.toString(), gate.toString())
        assertTrue(gate is RecordingGate.Enabled || gate is RecordingGate.Disabled)
    }
}
