package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi
import com.spartanlabs.webtools.http.WallClock
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

/**
 * The virtual wall clock plus a step offset and a constant slew, the only thing the published `VirtualClock.wallClock` cannot do: a test steps the
 * reading backwards or forwards with [stepBy] (a host that sleeps is `stepBy(+d)` with no scheduler advance) and makes it run fast or slow with
 * [slewAt]. Safe from any thread.
 */
@OptIn(ExperimentalForInheritanceWebToolsApi::class)
internal class SteppableWallClock(private val base: WallClock) : WallClock {
    private val stepMillis = AtomicLong(0)
    private val slewPpm = AtomicLong(0)                       // parts per million of the time elapsed since construction
    private val origin = base.nowEpochMillis()

    /** Adds [delta] to every later reading; a negative delta steps the clock back. */
    fun stepBy(delta: Duration) {
        stepMillis.addAndGet(delta.inWholeMilliseconds)
    }

    /** Makes the clock run [ppm] parts per million fast (negative: slow), measured from construction. */
    fun slewAt(ppm: Long) {
        slewPpm.set(ppm)
    }

    override fun nowEpochMillis(): Long {
        val b = base.nowEpochMillis()
        return b + stepMillis.get() + (b - origin) * slewPpm.get() / 1_000_000
    }

    override fun toString(): String = "SteppableWallClock"
}
