# Research: the ledger's time model (`WindowedCallBudget`)

[SpartanLaboratories/WebTools#67](https://github.com/SpartanLaboratories/WebTools/issues/67), unit 8
(`call-budget`) of the HTTP client stack,
[#55](https://github.com/SpartanLaboratories/WebTools/issues/55). Question 3 of
[`README.md`](README.md). The providers' windows (F1), the aligned-window choice with a 1 s guard (F2), the
snapshot and restore rules (F7), the utilisation reading (F9) and the time findings (F10) of
[`../../research/call-budget.md`](../../research/call-budget.md), and the one-virtual-clock finding (F1, F2) of
[`../../research/test-support-and-time.md`](../../research/test-support-and-time.md), are cited, not repeated.
All sources were read on 2026-11-03.

## 1. Question

How should the ledger of `WindowedCallBudget` combine the injected monotonic `TimeSource` and the `WallClock`
so that: windows are epoch-aligned (UTC) and meet the provider's wall-clock boundaries; a `hard` window never
under-counts the provider's day across a wall-clock step as across a restart; a pause's end and the
snapshot's stamp are epoch milliseconds because they outlive the process; no wait hangs; every wait is virtual
under `VirtualClock`; and the budget never reads the system clock. Which reading defines each of: window
indices, planned slots, a pause's end, waits (`delay` durations), the snapshot's stamp and the
`maxRestoreSkew` check, a grant's measured wait and a window's elapsed fraction; what happens on a backward
step, a forward jump and a slew; and what a Level 2 test needs to exercise a step.

## 2. Bottom line

- **Wait on the monotonic source, decide on the wall clock.** Window indices, window boundaries, the
  snapshot stamp, the `maxRestoreSkew` check and the elapsed fraction come from the `WallClock` reading;
  every duration the budget waits, measures or compares (the `delay` argument, `maxWait` caps, a grant's
  measured wait, in-process pause expiry) comes from the `TimeSource`. One internal value, read once per
  operation, carries both readings. No epoch-millisecond value is held across a suspension point except the
  two that are persisted.
- **Read the wall clock at every operation and use it as read; do not anchor it and do not clamp it.** An
  anchor (wall time at construction plus monotonic elapsed) drifts by the monotonic source's frequency error
  (4.3 s a day at 50 ppm, past the 1 s guard in about 5.6 h), and on Linux and macOS stops across a suspend. A
  clamp (`max(wall, previous)`) freezes the ledger for as long as the wall clock was stepped back, which for a
  large step is a hang. Both were measured worse than the wall clock as read (F8).
- **The rule that keeps a `hard` window from under-counting is in the cells, not in the clock:** cells are
  keyed by window index and kept; a cell is never reset by a reading; and when the wall index falls below the
  highest index the ledger has charged, the current cell gains the sum of the cells above it (a conservative
  fold, one time per decrease). That needs no step threshold, handles a step of any size, and was the only
  variant in the model with no overspend and no standing refusal after a one-hour step and a one-year step
  (F8).
- **A pause is a duration, so it lives on the monotonic axis in process** (end as an elapsed `Duration`) and
  carries its epoch end beside it for the snapshot; it is over when *either* axis says so. A backward step
  therefore never lengthens a pause, and a forward jump (resume from sleep) ends it.
- **A forward jump is trusted.** The wall clock is the only reading that tracks the provider's boundary, and
  common code cannot tell a resume from sleep from a wrongly set clock. The residual risk (a clock set
  forward by mistake, left uncorrected, or corrected only after grants were charged to the wrong index
  forward) is stated as a gap, not hidden.
- **The published `VirtualClock.wallClock` cannot step**, so Level 2 needs a test-only wall clock in the
  module's own `jvmTest` support (`SteppableWallClock`: virtual base plus an adjustable offset and a constant
  slew), implementing the gated `WallClock` with its own `@OptIn`. It is not published.

## 3. Findings

Evidence labels: **documented** (a spec, Javadoc, man page or vendor document), **common practice** (what
mature libraries visibly do), **measured** (run during this research, with versions), **inference** (derived
here from the above).

### F1. Realtime steps; monotonic does not step, but is slewed, and does not count a suspend on Linux (documented)

`clock_gettime(2)`: CLOCK_REALTIME is "A settable system-wide clock that measures real (i.e., wall-clock)
time" and is affected by "discontinuous jumps in the system time". CLOCK_MONOTONIC "is not affected by
discontinuous jumps in the system time" but "is affected by frequency adjustments", and "does not count time
that the system is suspended". CLOCK_BOOTTIME is "identical to CLOCK_MONOTONIC, except that it also includes
any time that the system is suspended". Source:
<https://man7.org/linux/man-pages/man2/clock_gettime.2.html> (man-pages page as read 2026-11-03).

### F2. What the JVM's two clocks are on each platform (documented, code-read)

- `System.nanoTime()` "can only be used to measure elapsed time and is not related to any other notion of
  system or wall-clock time"; `System.currentTimeMillis()` has granularity that "depends on the underlying
  operating system and may be larger" (JDK 11 Javadoc,
  <https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/System.html>).
- HotSpot on Linux reads `CLOCK_MONOTONIC` (`os::javaTimeNanos` in `os_linux.cpp`, jdk11u
  `d001c53e35a8`), so `nanoTime` stops across a suspend (F1). On Windows it reads `QueryPerformanceCounter`
  (`os::javaTimeNanos` in `os_windows.cpp`, same commit); Microsoft documents QPC as counting "the time when
  the machine was in a sleep state" and "completely independent of the system time and UTC" (F5). On macOS
  `nanoTime` stops during sleep while `currentTimeMillis` continues (JDK-8307677, "nano time does not
  increment while the system is asleep", closed Won't Fix, JDK 20).
- Consequence (inference): after a host resumes, the wall clock has jumped forward relative to the monotonic
  source on Linux and macOS, and has not on Windows. A design must be correct in both.

### F3. Kotlin's `TimeSource` promises monotonicity only for `Monotonic` (documented)

`TimeSource.Monotonic` "returns its readings from a source of monotonic time when it is available in a target
platform, and resorts to a non-monotonic time source otherwise"; on the JVM it is `System.nanoTime() - zero`
(`MonotonicTimeSource`, Kotlin 2.2.0 stdlib). `TimeMark.hasPassedNow` states its monotone behaviour
conditionally ("If the time source is monotonic, ..."), so an injected `TimeSource` need not be monotonic. The
repository's cache already defends against this: "ages are clamped at zero"
(`webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/CaffeineResponseCache.kt:295`); its ticker is
one mark taken at construction (`:84-85`). The scheduler's source for `runTest` is
`AbstractLongTimeSource(DurationUnit.MILLISECONDS)` reading `currentTime`
(`TestCoroutineScheduler.timeSource`, kotlinx-coroutines-test 1.10.2), so it has millisecond resolution and
never steps back.

### F4. A wall clock can step by any amount, forward or back, and is otherwise slewed (documented)

- ntpd: "the time is slewed if the offset is less than the step threshold, which is 128 ms by default" "and
  stepped if above the threshold"; "ntpd exits with a message to the system log if the offset exceeds the panic
  threshold, which is 1000 s by default" (the `-g` option allows one unrestricted set); "the slew rate of
  typical Unix kernels is limited to 0.5 ms/s" (<https://www.ntp.org/documentation/4.2.8-series/ntpd/>).
- chrony: "Normally chronyd will cause the system to gradually correct any time offset, by slowing down or
  speeding up the clock as required"; `makestep` "forces chronyd to step the system clock if the adjustment is
  larger than a threshold value, but only if there were no more clock updates since chronyd was started than
  the specified limit"; "By default, the maximum slew rate is set to 83333.333 ppm (one twelfth)"
  (<https://chrony-project.org/doc/4.6.1/chrony.conf.html>, 4.6.1).
- Windows: "computers can adopt time that can be days, months, years, or even tens of years in the future or in
  the past"; "When Windows starts, the kernel pulls the current time from the BIOS. This current time is used
  as the initial time until the W32Time service can sync up with another time source"; the largest correction
  the service makes is 54,000 s (15 h) for stand-alone clients and servers and unlimited for domain members
  (<https://learn.microsoft.com/troubleshoot/windows-server/identity/configure-w32ime-against-huge-time-offset>,
  updated 2026-02-18).
- Consequence (inference): the typical step is sub-second, but steps of hours or years occur (boot before
  sync, a virtual machine resumed, a manual change). A time model needs no step *threshold* if it is correct
  for steps of every size.

### F5. A monotonic source drifts against UTC at its oscillator's frequency error (documented, inference)

Microsoft: QPC "is completely independent of the system time and UTC"; "Crystal oscillators that are used in
personal computers and servers are typically manufactured with a frequency tolerance of ± 30 to 50 parts per
million"; "a frequency error of 100 ppm causes an error of 8.64 seconds after 24 hours"
(<https://learn.microsoft.com/windows/win32/sysinfo/acquiring-high-resolution-time-stamps>, updated
2025-12-13). On Linux CLOCK_MONOTONIC is slewed with the wall clock (F1), so it tracks UTC rate there; on
Windows it does not. Inference: a ledger that anchors the wall clock once and advances it with the monotonic
source accumulates 4.3 s a day at 50 ppm; the 1 s guard is spent in 1 s / 50 ppm = 20,000 s (5.6 h) and the
error is unbounded after that.

### F6. `delay` runs on the monotonic source; the test scheduler runs it on virtual time (documented, code-read)

kotlinx-coroutines 1.10.2 reads delays through `internal actual inline fun nanoTime(): Long =
timeSource?.nanoTime() ?: System.nanoTime()` (`AbstractTimeSource.kt`), used by the event loop's delayed-task
queue (`EventLoop.common.kt`). So a `delay(d)` counts `d` of monotonic time (and on Linux/macOS does not
advance across a suspend, F1). Under `runTest` the scheduler is the one master of time
([`../../research/test-support-and-time.md`](../../research/test-support-and-time.md) F1, F2). The repository's
`VirtualClock.wallClock` is `startEpochMillis + elapsed.inWholeMilliseconds`
(`webtools-http/src/jvmTestFixtures/kotlin/com/spartanlabs/webtools/http/testing/VirtualClock.kt:94`), so the
difference of its wall reading and its monotonic reading is constant: it can never step or slew.

### F7. How comparable limiters choose (common practice; see section 4)

Limiters that count a local rate use a monotonic reading and align windows to nothing (Resilience4j, Guava,
Bucket4j by default, Kord by default). Limiters whose windows must meet a clock they do not own use wall time
(Envoy's `ratelimit` epoch-aligned buckets, JDA's absolute `reset` instants, Bucket4j's wall-based meter for
distributed use, Kord's `Clock` variant). None combines the two, and none documents its behaviour across a
step. This unit counts locally but aligns to a remote wall boundary, so it needs both readings and a rule
that no comparable limiter supplies (inference).

### F8. Model comparison under clock faults (measured, throwaway model)

A 120-line Python 3.14.7 model (not repository code, not retained) of one `hard` window of 500 grants per UTC
day. Truth is a separate time axis; the provider counts each grant by true day. Demand: bursts of 300
attempts every 4 h at a random phase, retried each minute for 5 minutes; 6 simulated days; 60 randomised
trials per cell (fault time 1.2 to 3.8 days, step size 1 to 12 h). The ledger variants:

- `drop`: wall as read, only the current cell kept (a cell is reset when the index changes);
- `keep`: wall as read, cells kept by index (the previous cell and all higher ones), no fold;
- `fold`: as `keep`, plus on an index decrease the lower cell gains the sum of the higher cells (the
  recommendation);
- `clamp`: `now = max(wall, previous now)`;
- `anchor`: wall at the first reading plus monotonic elapsed, never re-read.

Worst provider-day overspend (grants above 500) over the trials, and the liveness cost (mean grants a day
below 500 over the days after the fault, 5 days):

| Scenario | drop | keep | fold | clamp | anchor |
|---|---|---|---|---|---|
| S1 wall 1-12 h ahead, then corrected | over 500 | 0, short 400 | 0, short 400 | 0, short 400 | 0 (*) |
| S2 wall set back 1-12 h, later restored | over 1000 | 0 | 0 | 0 | 0 (*) |
| S3 host sleeps 1-12 h (forward jump) | 0 | 0 | 0 | 0 | 0; first grant of a day 8.5 h late on average, against 2.0 h |
| S5 wall set back one year, kept | over 500 | over 500 | 0, short 75 (max 500: one day) | 0, short 1508 (refused to the horizon) | 0 (*) |
| S6 wall set forward one year, kept | over 500 | over 500 | over 500 | over 500 | 0 (*) |

(*) `anchor` shows no overspend only because the demand pattern is symmetric; a standing offset between the
ledger and the provider is exactly what the guard is meant to bound, and an anchor keeps the wrong offset (S1,
S6) and adds a new one on each sleep (S3). Its zeros are not evidence of safety. The "short" figures for the
fold in S1 and S5 are the conservative copy: the cell the early roll consumed stays consumed for its index.
`clamp` in S5 is the hang: the ledger waits for the wall clock to climb back a year.

What the model supports (inference from the model, with the arithmetic of F4 and F5): trusting the wall
clock for roll-over and folding on a decrease is the only combination with no overspend in the step-back
cases and a bounded refusal; no wall-trusting variant survives S6, and no variant that ignores the wall clock
survives a sleep or a long run.

## 4. How comparable projects do it

All code read through `gh api` on 2026-11-03 at the default-branch commit shown.

| Project | Clock | Evidence |
|---|---|---|
| Resilience4j `AtomicRateLimiter` (`resilience4j-ratelimiter`, master `aa9aacb8`, release v2.4.0) | monotonic; cycles counted from a start taken at construction; not aligned to any remote | constructor `this(name, rateLimiterConfig, System::nanoTime)`; `long currentNanoTime() { return this.nanoTimeSupplier.get() - nanoTimeStart; }`; `currentCycle = currentNanos / cyclePeriodInNanos` |
| Guava `RateLimiter` / `Stopwatch` (master `b0fd40a0`, v33.8.0) | monotonic by default | `SleepingStopwatch.createFromSystemTimer()`; Stopwatch Javadoc: wall time is "routinely affected by periodic clock corrections. Because this class (by default) uses System#nanoTime, it is unaffected by these changes." |
| Bucket4j `TimeMeter` (`bucket4j-core`, master `eca72978`, release 8.22.0) | both offered: `SYSTEM_NANOTIME` (`isWallClockBased() == false`) and `SYSTEM_MILLISECONDS` (`true`) | Javadoc of `isWallClockBased`: usable "in distributed scenarios and returns approximately the same time in all machines in cluster" |
| Kord `IntervalRateLimiter` (`common/.../ratelimit`, main `db459ec8`) | `TimeSource` default `TimeSource.Monotonic`; a `Clock` variant exists | `TimeSourceIntervalRateLimiter` keeps a `TimeMark` and `delay(interval - elapsed)`; `ClockIntervalRateLimiter` keeps an absolute `intervalEnd` and `delay(intervalEnd - now)`, so a backward step lengthens the wait by the step and a forward jump ends the interval early; neither is documented |
| Envoy `ratelimit` (`src/limiter/cache_key.go`, `src/utils/time.go`, main `11a831bd`) | wall; fixed windows aligned to the Unix epoch | `bucketStart = (now / divider) * divider`; `UnixNow()` is `time.Now().Unix()`; the counter lives in shared Redis, so every process must agree on wall time |
| JDA `SequentialRestRateLimiter` (master `7c900019`, release v6.7.0) | wall; absolute reset instants | `getNow()` is `System.currentTimeMillis()`; `bucket.reset = now + parseDouble(resetAfterHeader)` |

Reading: a limiter that mirrors a remote wall boundary (Envoy, JDA) uses wall time and accepts the step
behaviour; a local-rate limiter (Resilience4j, Guava, Kord default) uses a monotonic reading and cannot
align. Nobody in the set re-anchors, clamps, or folds; the combination below is this repository's own.

## 5. Pitfalls and regrets

- **Resetting a hard cell on an index change** (the `drop` variant) turns every backward step across a
  boundary into a fresh budget: overspend of one to two limits in the model (S1, S2, S5).
- **A ledger ahead of the provider is the unsafe direction; behind is the safe one.** Ahead rolls the cell
  early and double-spends the provider's day; behind only refuses for the lag. Any rule that can only move
  the ledger forward (a clamp, a standing positive offset) locks the unsafe state in.
- **A clamp or hold that waits for the wall clock to catch up** ties liveness to the size of an error nobody
  controls (a step of a year is a year).
- **An anchor makes the guard meaningless over time** (F5) and is wrong on every platform where the monotonic
  source skips a suspend (F1, F2).
- **Holding an absolute epoch-millisecond slot across an `await`** bakes in the clock as read at planning
  time; after a step the wake is early or late by the step. Hold an elapsed `Duration` and a window index.
- **`currentTimeMillis` granularity** (F2): a waiter woken on the monotonic clock may read the wall clock a
  millisecond or a tick before the boundary. The wake must re-check the wall index and wait the remainder, not
  assume the boundary has passed.
- **A copy of the future cells into the present is conservative, not exact.** If the step was a correction,
  the next window stays charged with the grants that belonged to the previous one (the "short" figures in F8).
  The alternative (move, not copy) restores liveness but under-counts when the step was the error; for a
  billing window the copy is right.
- **A step threshold to classify slew from step** (ntpd's 128 ms, chrony's 1 s) would be a magic number that
  differs per host and gives the ledger two code paths. The index-based fold needs none.

## 6. Recommendation for this repository

### 6.1 The clock model

The ledger reads both clocks together, once per operation, into one `internal` value and derives everything
from that pair. It owns one mark on the injected `TimeSource`, taken at construction (as the cache's ticker
does, `CaffeineResponseCache.kt:84-85`), and never reads `epochMillisNow()` or the system clock itself
(`architecture.md` §6.5, "Time").

- The wall clock is authoritative for **where in a provider's calendar** now is. It is read at every
  operation and used as read. There is no anchor and no clamp.
- The monotonic source is authoritative for **how long** something has taken or will take.
- Cells, not clocks, carry the no-under-count guarantee: keyed by window index, kept, never reset by a
  reading, folded downward on a decrease of the index.

### 6.2 Every quantity and the reading it comes from

| Quantity | Reading | How |
|---|---|---|
| Window index, start, end (`resetsAtEpochMillis`) | wall | `wall.floorDiv(periodMillis)`, `index * periodMillis`, `(index + 1) * periodMillis` |
| Boundary guard test (a grant within the guard is charged to both adjacent windows) | wall | `wall - start < guard` or `end - wall <= guard` |
| A reservation's target | a window **index** (wall quantity), plus a monotonic timer toward it | honoured when the wall index reaches it |
| Wait before a slot (the `delay` argument) | monotonic duration | `(boundaryEpoch - wall).milliseconds` at plan time, at most `maxWaitSlice`; the wake re-reads both clocks and re-plans |
| `maxWait` cap and the "new wait exceeds the cap" test | monotonic | a deadline `startElapsed + maxWait`, compared with `elapsed` |
| `BudgetExhausted.wait` | computed from the plan | a `Duration`; for a `hard` window the time to the wall boundary |
| Grant's measured wait (`Granted`) | monotonic | `elapsed - startElapsed` (never negative) |
| Window elapsed fraction | wall | `(wall - start) / periodMillis`, in `[0, 1)` by construction (the index comes from the same reading) |
| In-process pause end | both axes; over when **either** has passed | `untilElapsed: Duration` and `untilEpochMillis: Long` |
| Persisted pause end | wall | `untilEpochMillis`, from the pause or `wall + (untilElapsed - elapsed)` at save time |
| Snapshot stamp (`takenAtEpochMillis`) | wall | the pair read for the save |
| `maxRestoreSkew` check | wall | `snapshot.takenAt - wall > maxRestoreSkew` |
| Pause restored from a snapshot | wall to monotonic, once | `remaining = (untilEpoch - wall).coerceIn(0, maxPause)`; `untilElapsed = elapsed + remaining` |
| Restore retry rate, `storeTimeout`, `pausedFor`, queue ages | monotonic | `elapsed` marks and `withTimeout` (virtual under `runTest`) |
| Window start in `WindowSnapshot` | wall | `index * periodMillis` |

Two supporting rules. (1) The monotonic reading is itself made non-decreasing under the lock (`max(previous,
reading)`), because an injected `TimeSource` need not be monotonic (F3). (2) No epoch-millisecond value
survives an `await` except `untilEpochMillis` and the snapshot's values.

### 6.3 What a step, a jump and a slew do

| Event | Cells and hard windows | Pauses | Waiters | Measured waits and caps |
|---|---|---|---|---|
| **Backward step, same index** | nothing changes; counts stay; the elapsed fraction goes down | unchanged (monotonic coordinate) | a reservation for a later window is honoured later by the size of the step at most; the wake re-checks the index | unchanged |
| **Backward step across a boundary (index falls from `hi` to `c < hi`)** | cell `c` gains the sum of the cells above `c` (once); the higher cells stay for when the wall reaches them; a hard window cannot be charged again from zero | unchanged | as above; a waiter whose recomputed wait exceeds its cap fails at once with `BudgetExhausted` | unchanged |
| **Backward step of any size, e.g. a year** | same fold; the ledger runs at the lower index, aligned to the same boundaries because the step is applied to the same calendar; no wait exceeds one period plus the step's re-plan | unchanged | each wait is bounded by one period (the target is a boundary computed from the new wall), so nothing hangs | unchanged |
| **Forward jump** (resume from sleep, a correction of a slow clock) | windows roll by the wall; stale cells are pruned; a hard window opens fresh for the new day: the wall is trusted | ends when the wall passes `untilEpochMillis` | woken within `maxWaitSlice` (not after the full remaining monotonic wait) and granted when the wall index reaches the reservation | unchanged (a resumed host reports the awake time, not the sleep) |
| **Slew** (a rate change of 0.05 % for ntpd, up to 8.3 % for chrony's default ceiling, bounded by the offset being corrected) | indistinguishable from the wall clock running fast or slow; no special case | unchanged | a wake that reads the wall a few ms before its boundary waits the remainder; a grant is never made before the wall index reaches the reservation | unchanged |

The invariants behind the table: a cell charged for index `k` is never discarded while the ledger can still
read index `k` or a lower one; the ledger's wall index only ever *decides* (never accumulates); all waits are
bounded by `min(period, maxWaitSlice)` per wake.

### 6.4 The boundary guard, the restore and `maxRestoreSkew`

- **Guard.** The guard (1 s) absorbs the disagreement between the ledger's wall reading and the provider's at a
  boundary: NTP error (milliseconds), the delay from grant to arrival, wall-clock granularity, and
  OpenDota's 23:59:59 expiry (F1 of the stack research). It does not absorb a step; a step that crosses a
  boundary is the fold's job, and a step smaller than the guard that stays within a guard band is covered by
  the double charge. Because the wall clock is re-read at every operation the ledger accumulates no drift, so
  the guard's budget is not spent over time as it would be with an anchor (F5).
  The guard's two-cell charge enters the fold as ordinary cell content: a fold can double-count the grants
  that were charged to both cells, by at most the number of grants granted within the band; that over-count
  is on the safe side.
- **Restore.** The restore compares the snapshot stamp with the wall reading taken under the lock. A window in
  the snapshot with an index below the current one is dropped; at the current index the count is
  `max(local, saved)`; **an index above the current one is kept as a future cell** (it costs nothing and is
  what a later wall-clock correction needs). The persisted pause converts once to the monotonic axis. The
  write-ahead mark is a count for the window the snapshot names; a fold only raises the current count, which
  the next write-ahead check persists.
- **`maxRestoreSkew`.** `snapshot.takenAt - wall > maxRestoreSkew` marks the snapshot stamped in the future.
  See Considerations 7.2 for what to do with its cells; the pause of such a snapshot is dropped in every case,
  because a pause is the only field whose damage grows with the stamp (it is capped by `maxPause` anyway).

### 6.5 Sketch (common Kotlin)

```kotlin
package com.spartanlabs.webtools.http

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** One consistent reading of both clocks. Read once per operation, under the ledger's lock. */
internal class LedgerNow(val wallMillis: Long, val elapsed: Duration) {
    fun windowIndex(periodMillis: Long): Long = wallMillis.floorDiv(periodMillis)
    fun windowStart(periodMillis: Long): Long = windowIndex(periodMillis) * periodMillis
    /** Fraction of the current window elapsed, in [0, 1) because the index came from the same reading. */
    fun elapsedFraction(periodMillis: Long): Double = (wallMillis - windowStart(periodMillis)).toDouble() / periodMillis
    /** Monotonic duration to a wall instant; zero when it has passed. */
    fun until(epochMillis: Long): Duration = (epochMillis - wallMillis).coerceAtLeast(0L).milliseconds
}

internal class LedgerClock(timeSource: TimeSource, private val wallClock: WallClock) {
    private val origin: TimeMark = timeSource.markNow()
    private var previous: Duration = Duration.ZERO

    /** Call under the ledger's lock. A source that is not monotonic cannot move the ledger back. */
    fun read(): LedgerNow {
        val wall = wallClock.nowEpochMillis()
        val elapsed = maxOf(previous, origin.elapsedNow())
        previous = elapsed
        return LedgerNow(wall, elapsed)
    }
}

/** A pause is a duration: monotonic in process, epoch-stamped for the snapshot, over when either axis says so. */
internal class Pause(val untilElapsed: Duration, val untilEpochMillis: Long) {
    fun isOver(now: LedgerNow): Boolean = now.elapsed >= untilElapsed || now.wallMillis >= untilEpochMillis
    fun remaining(now: LedgerNow): Duration =
        if (isOver(now)) Duration.ZERO else minOf(untilElapsed - now.elapsed, now.until(untilEpochMillis))
    fun extendedTo(other: Pause) =
        Pause(maxOf(untilElapsed, other.untilElapsed), maxOf(untilEpochMillis, other.untilEpochMillis)) // the maximum of every hint
    companion object {
        fun of(now: LedgerNow, length: Duration) =                       // length already capped by maxPause
            Pause(now.elapsed + length, now.wallMillis + length.inWholeMilliseconds)
        fun restored(now: LedgerNow, savedEpoch: Long, maxPause: Duration): Pause {
            val remaining = (savedEpoch - now.wallMillis).coerceAtLeast(0L).milliseconds.coerceAtMost(maxPause)
            return of(now, remaining)
        }
    }
}

/** Cells of one window, keyed by index; the fold keeps a hard window from being charged again from zero. */
internal class Cells(private val limit: Int) {
    private val used = HashMap<Long, Int>()          // small; the implementer bounds it (see below)
    private var lastIndex: Long? = null

    /** Call with the index read under the lock, before charging. */
    fun enter(index: Long) {
        val last = lastIndex
        if (last != null && index < last) {          // the wall index fell: a backward step across a boundary
            val above = used.filterKeys { it > index }.values.sum()
            used[index] = minOf(limit, (used[index] ?: 0) + above)   // copy, not move: the higher cells stay
        }
        lastIndex = index
        used.keys.removeAll { it < index - 1 }       // forward pruning only: past cells below the guard neighbour
    }
    fun usedAt(index: Long): Int = used[index] ?: 0
    fun charge(index: Long, cost: Int) { used[index] = usedAt(index) + cost }
}
```

Notes on the sketch. `Long.floorDiv`, `Duration`, `TimeMark` and `maxOf` are stable common stdlib (no
`kotlin.time.Clock` or `Instant`, which are experimental on Kotlin 2.2.0 per the stack research, F10).
`LedgerClock.read()` is called inside the lock; a waiter re-reads when it takes the lock to re-plan. The
`Cells` map holds at most a handful of keys (the previous, current and next indices plus any higher cells
left by a step); an implementer caps it (for example at eight, dropping the farthest above the current index
first) so a flapping clock cannot grow it. Waits are `min(now.until(boundary), maxWaitSlice)`, with
`maxWaitSlice` an `internal` one-minute constant (Considerations 7.3), and a wake that finds the wall index
short of the reservation by a millisecond waits that millisecond and re-plans.

### 6.6 Level 2 tests that pin it

The published `VirtualClock.wallClock` cannot step (F6), so the module's own `jvmTest` support gains one
class. It is not published: a consumer rarely needs it and it can graduate when one does.

```kotlin
// webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/support/webtools/http/SteppableWallClock.kt
package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi
import com.spartanlabs.webtools.http.WallClock
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

/** The virtual wall clock plus a step offset and a constant slew; steps and slews are the only things it adds. */
@OptIn(ExperimentalForInheritanceWebToolsApi::class)
internal class SteppableWallClock(private val base: WallClock) : WallClock {
    private val stepMillis = AtomicLong(0)
    private val slewPpm = AtomicLong(0)            // parts per million, applied to base time since construction
    private val origin = base.nowEpochMillis()
    fun stepBy(delta: Duration) { stepMillis.addAndGet(delta.inWholeMilliseconds) }   // negative steps back
    fun slewAt(ppm: Long) { slewPpm.set(ppm) }
    override fun nowEpochMillis(): Long {
        val b = base.nowEpochMillis()
        return b + stepMillis.get() + (b - origin) * slewPpm.get() / 1_000_000
    }
    override fun toString(): String = "SteppableWallClock"
}
```

A host that sleeps is `stepBy(+d)` with no scheduler advance. Tests (one class per level and file, in
`com.spartanlabs.testing.component.webtools.http`, `runTest`, `virtualClock()`):

1. **Index and boundary, no fault:** a call on a full window waits exactly to the wall boundary in virtual
   time; `Granted` carries that wait; the elapsed fraction at second 55 of a minute is 55/60.
2. **Backward step across a boundary (hard):** the wall is stepped 1 h ahead so a day rolls early; grants are
   charged to the new index; step back 1 h; the lower cell now holds its own count plus the early cell's;
   the next grants stop at the limit and `BudgetExhausted.wait` equals the wall time to the next midnight.
3. **Backward step of one year:** after the fold, no grant exceeds the limit for the day, and a call with
   `Duration.INFINITE` completes within one period of virtual time (no hang).
4. **Backward step within one window:** counts unchanged; a waiter for the next window is granted at the
   wall boundary, the step later than planned at most, and never before the wall index reaches its window.
5. **Forward jump while a waiter is parked:** `stepBy(+8.hours)` with no scheduler advance; the waiter is
   granted within `maxWaitSlice` of virtual time, not after its full original wait; a hard window opens fresh
   for the new day.
6. **Pause and steps:** a 60 s pause then `stepBy(-1.hours)` ends at 60 s of virtual time; the same pause then
   `stepBy(+1.hours)` ends at once (either axis); `utilisation().pausedFor` follows.
7. **Measured wait:** a 30 s wait with `stepBy(+2.hours)` during it reports 30 s in `Granted`, never 2 h.
8. **Slew:** with `slewAt(500)` over a one-hour wait, the grant happens at the first wake where the wall index
   has reached the reservation, at most 0.05 % (1.8 s) of the wait after the monotonic target and never
   before the wall boundary.
9. **Snapshot and skew:** the saved stamp equals the wall reading; a restore with the stamp 4 min ahead of a
   `stepBy`-adjusted wall keeps the counts, 6 min ahead sets `Restored.ignored`; a pause restores as its
   remaining duration, capped by `maxPause`.
10. **Non-monotonic `TimeSource`:** an injected source that steps back once leaves every wait non-negative
    and every measured wait non-negative.
11. **Three clocks together** (`VirtualClock` only): `advanceBy` moves the window index, a wait and the cache
    ticker together (the control of
    [`../../research/test-support-and-time.md`](../../research/test-support-and-time.md) F2).

Level 4a (`testing.deterministic`): window arithmetic at `-1`, `0`, `+1` ms around a boundary, at a period
that does not divide a day, with `Long` extremes (`Duration.INFINITE.inWholeMilliseconds` saturates); the
fold as a pure function over a few cell maps (sum, cap at the limit, idempotent for repeated reads at the same
index, copy semantics).

## 7. Considerations (trade-offs left to the plan)

**7.1 Copy or move on a fold.** Copy leaves the higher cell charged, so after a *correction* the window the
early roll consumed stays refused for its index (F8: the "short" figures). Move frees it but under-counts if
the step was the error. Recommendation: copy, for a billing window; the cost is bounded by one period.

**7.2 A snapshot stamped further ahead than `maxRestoreSkew`.** The rule as stated ignores the snapshot, and
with it its counts, which reopens the billing hole in the case where the clock at boot was wrong (Windows
starts from the BIOS time "until the W32Time service can sync", F4). Options: (a) ignore everything (as
stated); (b) ignore the pause and the stamp but keep the window counts as cells at their own indices, which
cost nothing because a cell for a future index is only read when the wall reaches it, and still report the
snapshot as ignored in `Restored`. Recommendation: (b), because it is the only option consistent with "never
under-counts across a restart" when the wall clock is behind; it changes the stated behaviour from "ignores
the snapshot" to "ignores its stamp and pause", so the plan should confirm it before adopting.

**7.3 Slice long waits.** A `delay` runs on monotonic time that stops across a suspend on Linux and macOS
(F1, F2), so a parked waiter wakes late after a resume by up to its remaining wait, which for a daily window
is hours. Capping each wake at `maxWaitSlice` (1 minute, an `internal` constant, not a builder property) makes
every step, jump and slew take effect within a slice and costs one cheap wake per minute per parked waiter.
Recommendation: slice. Without slicing the failure is bounded lateness, not a hang.

**7.4 Pause expiry on either axis.** Ending a pause when either the monotonic deadline or the epoch deadline
passes means a resume from sleep ends a one-hour pause that slept through, and a backward step never extends
it. The cost is that a wrongly stepped-forward clock can end a pause early, which a server answers with
another `429` that sets a new pause. Recommendation: either-axis.

**7.5 Logging a step.** The ledger can log, at no behavioural cost, when `wall - elapsed` between two
consecutive readings differs by more than a tolerance (for example 250 ms plus 1000 ppm of the time between
them). It needs a number, so it is optional and log-only; no event leaf is added.

**7.6 Verifying the wall clock against the provider.** `RateSignalsReader.Standard` already takes the
server's date from `Date`. Comparing it with the ledger's wall reading would detect a standing skew larger
than the guard (the S6 case). It is out of this unit's scope and noted as a follow-up.

## 8. Gaps

- **No Kotlin was built or run.** The only probe is the Python model of F8; its demand pattern is a
  symmetric burst schedule, so it shows overspend and liveness under steps but does not show what a standing
  skew costs a limiter (shown by the arithmetic of F5, not by the model). The fold rule is this research's own
  and is validated only in the model; no comparable project implements it.
- **JS.** `TimeSource.Monotonic` on `js` falls back to a non-monotonic source "otherwise" (F3); the `js`
  actual was not read, so how a browser or Node monotonic reading behaves across a suspend is not
  established. The monotonisation in 6.2 and the slice in 7.3 limit the effect.
- **Linux `CLOCK_REALTIME` across a suspend.** The man page quoted does not say that the realtime clock
  jumps forward on resume; the forward jump is inferred from "does not count time that the system is
  suspended" for MONOTONIC and demonstrated for macOS by JDK-8307677. It was not measured on Linux or Windows.
- **A forward jump that is wrong and left uncorrected** (S6), or corrected only after grants were charged to
  the wrong index in the other direction (a clock behind at process start and stepped forward after a sync
  while grants were made), under-counts the provider's day. No reading in common code can distinguish it from
  a resume from sleep. The mitigations are outside the ledger: start the budget after the host is
  synchronised, and 7.2 and 7.6.
- **The fold's retention bound** (the cap on cells above the current index) and the exact value of
  `maxWaitSlice` are implementation choices not tested against a real workload.
- **Interaction with the waiter design** (question 1): this file assumes a waiter re-plans under the lock
  after each wake and that a reservation names a window index; if the waiter design stores an absolute
  epoch slot across the wait, rule "no epoch value across an `await`" is broken.
- **`maxPause` and a restored pause:** the restored pause is capped by `maxPause` at restore; whether a live
  pause is capped at creation is the pause rules' (F6 of the stack research) and was not re-examined here.

## 9. Load-bearing claims

Each claim is an outside-world claim the recommendation rests on. Quotes are verbatim from the source as
read on 2026-11-03.

1. **CLOCK_MONOTONIC is not affected by steps, is affected by frequency adjustment, and does not count a
   suspend.** "The CLOCK_MONOTONIC clock is not affected by discontinuous jumps in the system time" ... "but
   is affected by frequency adjustments." ... "This clock does not count time that the system is suspended."
   <https://man7.org/linux/man-pages/man2/clock_gettime.2.html>, man-pages as served 2026-11-03.
2. **CLOCK_REALTIME is settable and subject to jumps and NTP frequency adjustment; CLOCK_BOOTTIME includes
   suspend.** "A settable system-wide clock that measures real (i.e., wall-clock) time." ... "This clock is
   affected by discontinuous jumps in the system time" ... "except that it also includes any time that the
   system is suspended." Same page.
3. **`System.nanoTime()` is for elapsed time only; `currentTimeMillis()` granularity may be coarse.** "This
   method can only be used to measure elapsed time and is not related to any other notion of system or
   wall-clock time." ... "the granularity of the value depends on the underlying operating system and may be
   larger." <https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/System.html>, JDK 11.
4. **HotSpot's `nanoTime` is `CLOCK_MONOTONIC` on Linux and QPC on Windows.** Linux: `int status =
   Linux::clock_gettime(CLOCK_MONOTONIC, &tp);` in `os::javaTimeNanos`; Windows: `QueryPerformanceCounter(&current_count);`
   in `os::javaTimeNanos`. <https://github.com/openjdk/jdk11u/blob/d001c53e35a816f26718845b5d26a877878a7f5d/src/hotspot/os/linux/os_linux.cpp>
   and <https://github.com/openjdk/jdk11u/blob/d001c53e35a816f26718845b5d26a877878a7f5d/src/hotspot/os/windows/os_windows.cpp>,
   jdk11u master at `d001c53e` (2026-11-03).
5. **On macOS `nanoTime` stops during sleep while `currentTimeMillis` continues (JDK 20).** Bug title: "nano
   time does not increment while the system is asleep"; closed Won't Fix 2023-05-09; the report states
   `currentTimeMillis()` advanced about 60 s across a sleep while `nanoTime()` advanced one second.
   <https://bugs.openjdk.org/browse/JDK-8307677>.
6. **QPC is independent of the system time, counts sleep, and drifts by the oscillator's tolerance.** "QPC is
   completely independent of the system time and UTC." ... "including the time when the machine was in a
   sleep state such as standby, hibernate, or connected standby." ... "A convenient reference is that a
   frequency error of 100 ppm causes an error of 8.64 seconds after 24 hours." ... "Crystal oscillators that
   are used in personal computers and servers are typically manufactured with a frequency tolerance of ± 30
   to 50 parts per million".
   <https://learn.microsoft.com/windows/win32/sysinfo/acquiring-high-resolution-time-stamps>, updated
   2025-12-13.
7. **ntpd slews below 128 ms, steps above, panics at 1000 s, and kernels slew at most 0.5 ms/s.** "the time
   is slewed if the offset is less than the step threshold, which is 128 ms by default" ... "and stepped if
   above the threshold" ... "ntpd exits with a message to the system log if the offset exceeds the panic
   threshold, which is 1000 s by default." ... "the slew rate of typical Unix kernels is limited to 0.5 ms/s".
   <https://www.ntp.org/documentation/4.2.8-series/ntpd/>, 4.2.8 series.
8. **chrony slews by default, steps only under `makestep`, and may slew at up to 83333 ppm.** "Normally
   chronyd will cause the system to gradually correct any time offset, by slowing down or speeding up the
   clock as required." ... `makestep`: "forces chronyd to step the system clock if the adjustment is larger
   than a threshold value, but only if there were no more clock updates since chronyd was started than the
   specified limit." ... "By default, the maximum slew rate is set to 83333.333 ppm (one twelfth)."
   <https://chrony-project.org/doc/4.6.1/chrony.conf.html>, 4.6.1.
9. **Windows clocks can start from the BIOS time and adopt arbitrary time.** "A review of time rollbacks has
   shown that computers can adopt time that can be days, months, years, or even tens of years in the future or
   in the past." ... "When Windows starts, the kernel pulls the current time from the BIOS. This current time
   is used as the initial time until the W32Time service can sync up with another time source." ... "The
   default value for stand-alone clients and servers is 54,000 (15 hours)."
   <https://learn.microsoft.com/troubleshoot/windows-server/identity/configure-w32ime-against-huge-time-offset>,
   updated 2026-02-18.
10. **`TimeSource.Monotonic` may fall back to a non-monotonic source; on the JVM it is `System.nanoTime()`.**
    "This time source returns its readings from a source of monotonic time when it is available in a target
    platform, and resorts to a non-monotonic time source otherwise." and `private fun read(): Long =
    System.nanoTime() - zero`. Kotlin 2.2.0 stdlib sources (`commonMain/kotlin/time/TimeSource.kt`,
    `jvmMain/kotlin/time/MonoTimeSource.kt`), `kotlin-stdlib-2.2.0-sources.jar`; upstream
    <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/src/kotlin/time/TimeSource.kt>.
11. **`TimeMark`'s monotone behaviour is conditional on the source.** "If the time source is monotonic, it can
    change only from `false` to `true`, namely, when the time mark becomes behind the current point of the time
    source." Same stdlib sources, 2.2.0.
12. **The scheduler's time source is the virtual clock at millisecond resolution.** `public val timeSource:
    TimeSource.WithComparableMarks = object : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {` ...
    `override fun read(): Long = currentTime`.
    <https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-test/common/src/TestCoroutineScheduler.kt>,
    kotlinx-coroutines-test 1.10.2.
13. **`delay` counts monotonic time.** "internal actual inline fun nanoTime(): Long = timeSource?.nanoTime() ?: System.nanoTime()"
    (one statement, wrapped after the `=` in the source).
    <https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/jvm/src/AbstractTimeSource.kt>,
    kotlinx-coroutines 1.10.2 (the event loop's delayed-task queue calls `nanoTime()` in
    `EventLoop.common.kt` at the same tag).
14. **Resilience4j's rate limiter counts monotonic time from its own start.** "this(name, rateLimiterConfig,
    System::nanoTime);" and "return this.nanoTimeSupplier.get() - nanoTimeStart;".
    <https://github.com/resilience4j/resilience4j/blob/aa9aacb8cb5333f2dee2590ff07f05f2c942d602/resilience4j-ratelimiter/src/main/java/io/github/resilience4j/ratelimiter/internal/AtomicRateLimiter.java>,
    master 2026-11-03 (latest release v2.4.0).
15. **Guava states why its stopwatch is monotonic.** "wall time readings are inherently approximate, routinely
    affected by periodic clock corrections. Because this class (by default) uses System#nanoTime, it is
    unaffected by these changes."
    <https://github.com/google/guava/blob/b0fd40a0ea282fcb136f6d0e06cbcbea0e9398c8/guava/src/com/google/common/base/Stopwatch.java>,
    master 2026-11-03 (latest release v33.8.0).
16. **Bucket4j separates wall-clock and monotonic meters.** "Returns {@code true} if implementation of clock
    behaves the similar way as {@link System#currentTimeMillis()}, in other words if implementation can be
    used in distributed scenarios and returns approximately the same time in all machines in cluster".
    <https://github.com/bucket4j/bucket4j/blob/eca7297808dbe2aa01127ba3a4d224786153b701/bucket4j-core/src/main/java/io/github/bucket4j/TimeMeter.java>,
    master 2026-11-03 (latest release 8.22.0).
17. **Kord offers a monotonic default and an absolute-instant variant.** "@param timeSource The [TimeSource]
    for measuring intervals, [TimeSource.Monotonic] by default." and, in `ClockIntervalRateLimiter`,
    `val duration = intervalEnd - now`.
    <https://github.com/kordlib/kord/blob/db459ec8fc140e42c08997a2dcd21491660dba3d/common/src/commonMain/kotlin/ratelimit/RateLimiter.kt>
    and `.../ClockIntervalRateLimiter.kt`, main 2026-11-03.
18. **Envoy's `ratelimit` aligns fixed windows to the Unix epoch on wall time.** `bucketStart = (now /
    divider) * divider` and `return time.Now().Unix()`.
    <https://github.com/envoyproxy/ratelimit/blob/11a831bdeed53039a5f4335daa6a7b31ee48a20f/src/limiter/cache_key.go>
    and `.../src/utils/time.go`, main 2026-11-03.
19. **JDA keeps absolute wall-clock reset instants.** `return System.currentTimeMillis();` in `getNow()` and
    `bucket.reset = now + parseDouble(resetAfterHeader);`.
    <https://github.com/discord-jda/JDA/blob/7c900019ca511a994087320ce98d19cd79a2c4b0/src/main/java/net/dv8tion/jda/api/requests/SequentialRestRateLimiter.java>,
    master 2026-11-03 (latest release v6.7.0).
