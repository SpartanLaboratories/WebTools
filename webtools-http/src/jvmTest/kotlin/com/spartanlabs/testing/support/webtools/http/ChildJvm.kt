package com.spartanlabs.testing.support.webtools.http

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Launches [ChildExitMain] in a child JVM: the running JVM's `java` (`java.home`), its class path (`java.class.path`), the main class and the
 * mode, with the output discarded; waits 20 s, and on a timeout destroys the child forcibly and reports it.
 */
internal object ChildJvm {
    /**
     * The child's result.
     *
     * @property exitCode the exit code, or `null` when the child was still alive after 20 s and was destroyed.
     * @property elapsed how long the child ran, or the 20 s budget when it was destroyed.
     */
    class Outcome(val exitCode: Int?, val elapsed: Duration) {
        /** `true` when the child exited on its own. */
        val exited: Boolean get() = exitCode != null
    }

    /**
     * Runs the child in [mode] (`close` or `noclose`).
     *
     * @param mode `close` makes the child close the transport after its call; `noclose` (any other value) leaves it open.
     * @return the outcome.
     */
    fun run(mode: String): Outcome {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val process = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), ChildExitMain::class.java.name, mode)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val mark = TimeSource.Monotonic.markNow()
        val exited = process.waitFor(20, TimeUnit.SECONDS)
        val elapsed = mark.elapsedNow()
        if (!exited) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
            return Outcome(null, elapsed)
        }
        return Outcome(process.exitValue(), elapsed)
    }
}
