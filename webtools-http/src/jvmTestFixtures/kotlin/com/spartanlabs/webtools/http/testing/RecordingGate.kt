package com.spartanlabs.webtools.http.testing

/**
 * Whether recording is allowed in this process: the one way to obtain the [Enabled] a [FixtureRecorder]
 * needs. [evaluate] reads the environment: recording is off unless `WEBTOOLS_HTTP_RECORD` ([RECORD_VARIABLE])
 * holds a recognised value, and is refused whenever any of the twelve CI variables ([CI_VARIABLES]) is present
 * with any value, the empty string included.
 *
 * **Stable Core, source compatibility only.** Sealed: the recorder accepts only [Enabled], whose constructor is
 * `internal`, so the live path cannot be reached without passing the gate. "Never runs in CI" holds for every
 * CI that sets one of the twelve variables; it is not a guarantee against a CI that sets none of them and a
 * developer who sets the variable there. Equality is identity; `toString()` prints the mode or the reason,
 * never a value from the environment.
 *
 * A `Test` task is `UP-TO-DATE` when only an environment variable changed, so a build that records declares the
 * variable as an input of its test tasks:
 * ```kotlin
 * tasks.withType<Test>().configureEach {
 *     inputs.property("webtoolsHttpRecord", providers.environmentVariable("WEBTOOLS_HTTP_RECORD").orElse(""))
 * }
 * ```
 */
public sealed interface RecordingGate {
    /**
     * Recording is on, in [mode].
     *
     * @property mode `New` for `1`, `true` or `new`; `Overwrite` for `all` or `overwrite`.
     */
    public class Enabled internal constructor(
        /** How an existing file is treated. */
        public val mode: RecordMode,
    ) : RecordingGate {
        override fun toString(): String = "RecordingGate.Enabled($mode)"
    }

    /**
     * Recording is off; [reason] is one of three constants or `refusing to record: <VAR> is set`, naming the
     * variable only. A test uses it with `assumeTrue(gate is Enabled)`.
     *
     * @property reason why recording is off; never echoes a value.
     */
    public class Disabled internal constructor(
        /** Why recording is off. */
        public val reason: String,
    ) : RecordingGate {
        override fun toString(): String = "RecordingGate.Disabled($reason)"
    }

    /** The variables and the evaluation. */
    public companion object {
        /** The variable that turns recording on: `WEBTOOLS_HTTP_RECORD`. */
        public const val RECORD_VARIABLE: String = "WEBTOOLS_HTTP_RECORD"

        /** The twelve CI variables whose presence, with any value, refuses recording. */
        public val CI_VARIABLES: List<String> = listOf(
            "CI", "GITHUB_ACTIONS", "GITLAB_CI", "JENKINS_URL", "BUILDKITE", "CIRCLECI", "TF_BUILD",
            "TEAMCITY_VERSION", "TRAVIS", "APPVEYOR", "CODEBUILD_BUILD_ARN", "BITBUCKET_COMMIT",
        )

        /**
         * Evaluates the gate against [env], in this order: [RECORD_VARIABLE] absent or empty -> off; a CI
         * variable present -> refused; `1`, `true` or `new` (trimmed, ASCII case-insensitive) -> [Enabled] in
         * [RecordMode.New]; `all` or `overwrite` -> [Enabled] in [RecordMode.Overwrite]; anything else -> off
         * with the unrecognised-value reason.
         *
         * @param env the environment; `System.getenv()` by default.
         * @return the gate; a value, never a failure.
         */
        public fun evaluate(env: Map<String, String> = System.getenv()): RecordingGate {
            val value = env[RECORD_VARIABLE]?.trim()?.let(::asciiLowercase).orEmpty()
            val ci = CI_VARIABLES.firstOrNull { it in env }
            return when {
                value.isEmpty() -> Disabled(OFF)
                ci != null -> Disabled("refusing to record: $ci is set")
                value == "1" || value == "true" || value == "new" -> Enabled(RecordMode.New)
                value == "all" || value == "overwrite" -> Enabled(RecordMode.Overwrite)
                else -> Disabled(UNRECOGNISED)
            }
        }

        private const val OFF = "recording is off: set WEBTOOLS_HTTP_RECORD to 1, true, new, all or overwrite to record"
        private const val UNRECOGNISED = "WEBTOOLS_HTTP_RECORD is not 1, true, new, all or overwrite"
    }
}
