package com.spartanlabs.testing.support.webtools.feeds

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The fixed-heap gate: launches [HostileParseMain] (or another main class) in a child JVM with `-Xmx<heap>m` and
 * `-XX:+ExitOnOutOfMemoryError` on the running JVM's class path, redirects its output and error to a temporary file, waits for the
 * budget, destroys the child forcibly on a timeout (then waits 5 s), and returns the exit code, the elapsed time and the output
 * lines. A class path longer than the platform's command line allows is passed through an `@argfile`.
 */
object HostileParseJvm {
    /**
     * The child's result.
     *
     * @property exitCode the exit code, or `null` for a child that outlived the budget and was destroyed.
     * @property elapsed how long the child ran.
     * @property lines what the child printed, standard error merged in.
     */
    class Outcome(val exitCode: Int?, val elapsed: Duration, val lines: List<String>) {
        override fun toString(): String = "Outcome(exitCode=$exitCode, elapsed=$elapsed, lines=$lines)"
    }

    /** The exit code `-XX:+ExitOnOutOfMemoryError` gives a child that exhausted its heap. */
    const val EXIT_OUT_OF_MEMORY: Int = 3

    /** The exit code of a child from which a `StackOverflowError` escaped. */
    const val EXIT_STACK_OVERFLOW: Int = 11

    /** The exit code of a child from which any other throwable escaped. */
    const val EXIT_THROWN: Int = 12

    // Above this many characters the class path goes through an @argfile: Windows caps a command line near 32 K.
    private const val ARGFILE_THRESHOLD = 24_000

    /**
     * Runs [case] with [parses] concurrent parses of the parser [factory] builds, under [heapMegabytes] of heap.
     *
     * @param factory the `ParserFactory` class the child builds by name.
     * @param case the case name [HostileDocuments.byName] knows, or `smoke`, `oom` or `hang`.
     * @param parses how many threads parse the document at once.
     * @param heapMegabytes the child's `-Xmx`, in MB.
     * @param budget how long to wait before destroying the child.
     * @param javaHome the JDK whose `java` runs the child; the running JVM's by default.
     * @param mainClass the child's main class; [HostileParseMain] by default.
     * @return the outcome.
     */
    fun run(
        factory: Class<out ParserFactory>,
        case: String,
        parses: Int,
        heapMegabytes: Int = 128,
        budget: Duration = 30.seconds,
        javaHome: String = System.getProperty("java.home"),
        mainClass: Class<*> = HostileParseMain::class.java,
    ): Outcome {
        val java = Path.of(javaHome, "bin", "java").toString()
        val out = Files.createTempFile("hostile-parse", ".txt")
        var argfile: Path? = null
        try {
            val classPath = System.getProperty("java.class.path")
            val classPathArguments = if (classPath.length > ARGFILE_THRESHOLD) {
                argfile = Files.createTempFile("hostile-parse", ".args").also { Files.writeString(it, "-cp \"" + classPath.replace("\\", "\\\\") + "\"\n") }
                listOf("@$argfile")
            } else {
                listOf("-cp", classPath)
            }
            val command = listOf(java, "-Xmx${heapMegabytes}m", "-XX:+ExitOnOutOfMemoryError") + classPathArguments + listOf(mainClass.name, factory.name, case, parses.toString())
            val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(out.toFile()).start()
            val mark = TimeSource.Monotonic.markNow()
            val exited = process.waitFor(budget.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            if (!exited) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
            }
            val elapsed = mark.elapsedNow()
            return Outcome(if (exited) process.exitValue() else null, elapsed, retrying { Files.readAllLines(out) }.filter { it.isNotBlank() })
        } finally {
            retrying { Files.deleteIfExists(out) }
            argfile?.let { retrying { Files.deleteIfExists(it) } }
        }
    }

    // A destroyed child's file handle may outlive the process for a moment on Windows; a short retry covers it.
    private fun <T> retrying(block: () -> T): T {
        var last: Throwable? = null
        repeat(40) {
            try {
                return block()
            } catch (e: java.io.IOException) {
                last = e
                Thread.sleep(50)
            }
        }
        throw checkNotNull(last)
    }
}
