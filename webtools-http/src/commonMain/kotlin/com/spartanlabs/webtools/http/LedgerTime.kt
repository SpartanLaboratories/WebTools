package com.spartanlabs.webtools.http

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** One consistent reading of both clocks, taken under the ledger's lock: the wall clock decides where in the calendar now is, the elapsed `Duration` measures how long things take. */
internal class LedgerNow(val wallMillis: Long, val elapsed: Duration)

/** Reads both clocks. One mark on [timeSource] is taken at construction; the elapsed reading never goes back. Every call is made under the budget's lock. */
internal class LedgerClock(timeSource: TimeSource, private val wallClock: WallClock) {
    private val origin: TimeMark = timeSource.markNow()
    private var previous: Duration = Duration.ZERO

    fun read(): LedgerNow {
        val wall = wallClock.nowEpochMillis()
        val elapsed = maxOf(previous, origin.elapsedNow())      // an injected TimeSource need not be monotonic
        previous = elapsed
        return LedgerNow(wall, elapsed)
    }
}

/** [this] rounded up to a whole millisecond; `Long.MAX_VALUE` for an infinite duration. Not for a negative duration. */
internal fun Duration.ceilToMillis(): Long {
    if (isInfinite()) return Long.MAX_VALUE
    val floor = inWholeMilliseconds
    return if (this > floor.milliseconds) floor + 1 else floor
}

/** A pause: a duration, so monotonic in process and epoch-stamped for the snapshot; it is over when **either** axis says so (a backward step never lengthens it, a resume from sleep ends it). */
internal class Pause(val untilElapsed: Duration, val untilEpochMillis: Long) {
    fun isOver(now: LedgerNow): Boolean = now.elapsed >= untilElapsed || now.wallMillis >= untilEpochMillis

    fun remaining(now: LedgerNow): Duration =
        if (isOver(now)) Duration.ZERO else minOf(untilElapsed - now.elapsed, (untilEpochMillis - now.wallMillis).milliseconds)

    /** The longer of the two, axis by axis: a later hint never shortens a pause. */
    fun extendedTo(other: Pause): Pause = Pause(maxOf(untilElapsed, other.untilElapsed), maxOf(untilEpochMillis, other.untilEpochMillis))

    companion object {
        /** A pause of [lengthMillis] (already capped by `maxPause`) starting at [now]. */
        fun of(now: LedgerNow, lengthMillis: Long): Pause = Pause(now.elapsed + lengthMillis.milliseconds, now.wallMillis + lengthMillis)

        /** A saved pause converted once to the monotonic axis: its remaining length, capped by [maxPauseMillis]. */
        fun restored(now: LedgerNow, savedEpochMillis: Long, maxPauseMillis: Long): Pause =
            of(now, (savedEpochMillis - now.wallMillis).coerceIn(0L, maxPauseMillis))
    }
}
