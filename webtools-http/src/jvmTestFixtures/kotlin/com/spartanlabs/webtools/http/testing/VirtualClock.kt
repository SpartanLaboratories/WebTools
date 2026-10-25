package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi
import com.spartanlabs.webtools.http.WallClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import java.time.Instant
import java.util.concurrent.Executor
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * One virtual clock for a test: a monotonic [timeSource], a [wallClock], a coroutine [dispatcher] and a
 * [maintenanceExecutor], all driven by one `kotlinx-coroutines-test` [TestCoroutineScheduler], so that a
 * cache's ticker, a `delay` and a wall-clock index move together when [advanceBy] is called. Under
 * `runTest`, build it with [virtualClock] so it shares the test's scheduler.
 *
 * **Stable Core, source compatibility only.** The fixtures and `webtools-http` are released and versioned
 * together: the fixtures call the module's `internal` members, and Gradle resolves both from one component,
 * so a consumer never mixes versions. The scheduler is the single master of time; nothing here reads the
 * wall clock of the host. Equality is identity. `toString()` prints the start instant and the elapsed
 * virtual time, nothing else.
 *
 * Thread-safety: [timeSource], [elapsed], [wallClock] and [maintenanceExecutor] may be read from any thread
 * (a Caffeine maintenance thread included; the scheduler reads its time under its own lock). [advanceBy] is
 * called from the test's thread only.
 *
 * Never use it at Level 3: real I/O under `runTest` turns `withTimeout` into a spurious timeout, because the
 * scheduler jumps over idle virtual time while a real call is pending.
 *
 * @param scheduler the scheduler that owns virtual time; under `runTest`, the test's `testScheduler`.
 * @param startEpochMillis the wall-clock reading at construction, in milliseconds since the Unix epoch;
 *   [DEFAULT_START_EPOCH_MILLIS] (2026-01-01T00:00:00Z) by default.
 */
public class VirtualClock(
    private val scheduler: TestCoroutineScheduler,
    /** The wall-clock reading at construction, in milliseconds since the Unix epoch. */
    public val startEpochMillis: Long = DEFAULT_START_EPOCH_MILLIS,
) {
    /**
     * The scheduler's monotonic time source. Its marks move only as the scheduler's virtual time moves: through
     * [advanceBy], or through the test's own scheduler operations when the scheduler is shared. Safe from any thread.
     */
    public val timeSource: TimeSource.WithComparableMarks = scheduler.timeSource

    // Taken at construction: every reading is relative to it.
    private val origin: ComparableTimeMark = timeSource.markNow()

    /** The virtual time elapsed since construction. Safe from any thread. */
    public val elapsed: Duration get() = origin.elapsedNow()

    /**
     * A [WallClock] reading [startEpochMillis] plus [elapsed], so a wall-clock index (a UTC day or minute)
     * moves with the scheduler. Usable without the opt-in; safe from any thread. Its `toString()` is
     * `VirtualClock.wallClock`.
     */
    public val wallClock: WallClock = VirtualWallClock()

    /**
     * A dispatcher on the scheduler, for a facade or cache scope that would otherwise run on
     * `Dispatchers.Default`: a `delay` on it is virtual and completes when [advanceBy] reaches it.
     */
    public val dispatcher: CoroutineDispatcher = StandardTestDispatcher(scheduler, name = "VirtualClock")

    /**
     * An executor that runs each task inline on the calling thread, before `execute` returns. A Caffeine
     * cache given it performs its maintenance synchronously, so expiry follows the clock instead of a
     * background thread. Safe from any thread.
     */
    public val maintenanceExecutor: Executor = Executor { task -> task.run() }

    /**
     * Moves virtual time forward by [duration] and runs every task that became due, including those due
     * exactly at the new time ([TestCoroutineScheduler.advanceTimeBy] leaves those to [TestCoroutineScheduler.runCurrent]).
     * A zero duration runs the tasks due now. Call it from the test's thread.
     *
     * @param duration how far to advance; not negative.
     * @throws IllegalArgumentException if [duration] is negative; the clock is unchanged.
     */
    public fun advanceBy(duration: Duration) {
        require(!duration.isNegative()) { "duration must not be negative" }
        scheduler.advanceTimeBy(duration)   // does not run tasks due exactly at the target ...
        scheduler.runCurrent()              // ... so run them now
    }

    override fun toString(): String = "VirtualClock(start=${Instant.ofEpochMilli(startEpochMillis)}, elapsed=$elapsed)"

    // The one implementer of a gated port in this file; the @OptIn is the line a consumer's own WallClock needs.
    @OptIn(ExperimentalForInheritanceWebToolsApi::class)
    private inner class VirtualWallClock : WallClock {
        override fun nowEpochMillis(): Long = startEpochMillis + elapsed.inWholeMilliseconds
        override fun toString(): String = "VirtualClock.wallClock"
    }

    /** The default start of the wall clock. */
    public companion object {
        /** 2026-01-01T00:00:00Z, the default [startEpochMillis]. */
        public const val DEFAULT_START_EPOCH_MILLIS: Long = 1_767_225_600_000L
    }
}

/**
 * Builds a [VirtualClock] on this test's scheduler, so `delay` in the test body, on the clock's
 * [VirtualClock.dispatcher] and in the code under test all move with [VirtualClock.advanceBy].
 *
 * **Stable Core, source compatibility only.**
 *
 * @param startEpochMillis the wall-clock reading at construction; [VirtualClock.DEFAULT_START_EPOCH_MILLIS] by default.
 * @return the clock, sharing `testScheduler`.
 */
public fun TestScope.virtualClock(startEpochMillis: Long = VirtualClock.DEFAULT_START_EPOCH_MILLIS): VirtualClock =
    VirtualClock(testScheduler, startEpochMillis)
