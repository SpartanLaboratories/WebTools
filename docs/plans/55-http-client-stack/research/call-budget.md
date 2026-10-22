# Research: the call budget port and its default implementation

Part of [`README.md`](README.md), question 7. Everything marked *measured* comes from throwaway
probes built outside the repository (Kotlin 2.2.0, kotlinx-coroutines and `-test` 1.10.2, Gradle
9.7.1, `jvmTarget` 11), run on 2026-10-21. A KMP probe (`jvm()` and `js(IR)`) compiled the budget
and the HTTP-date parser as common code. Nothing in the repository was built or changed.

## 1. Question

What algorithm and shape should the call budget port (`acquire(cost, lane, maxWait): Result<Permit>`,
`Permit.complete(signals)`) and its default implementation have, so that it:

- enforces several windows (minute, day) set below the published limits;
- gives interactive calls a reserved share;
- pauses on `429` until `Retry-After` or the window boundary, and lets a server-reported remaining
  count only lower the local balance;
- keeps a hard daily stop for keyed providers within one process, with an opt-in seam to persist
  the counters (in-memory default);
- exposes a utilisation reading, never runs a retry loop, reads signals through a small
  consumer-supplied reader, and takes every policy number as a constructor parameter;
- lives in `commonMain` (no `java.*`, no library type in a public signature), reports failure
  through `Result` with cancellation propagating, is usable without opt-in in its supplied default,
  and is testable at Level 2 on fake time?

## 2. Bottom line

- **Algorithm: fixed windows aligned to UTC epoch multiples of the period, plus a boundary guard
  band, in an integer unit ledger.** Every provider found counts in fixed wall-clock windows. A
  greedy token bucket let 119 calls into one server minute against a limit of 60; plain aligned
  windows overspent in 510 of 1,000 skewed bursty trials; aligned windows with a 1 s guard (a grant
  within the guard of a boundary is charged to both adjacent windows) overspent in none and granted
  within 2% of the unguarded count. GCRA and sliding logs are safe but pace or waste capacity and do
  not match a UTC-day bill. Token buckets, GCRA and Bucket4j are not the default.
- **Reservation: plan-ahead slots under a coroutine `Mutex`, strict FIFO per lane, wait outside the
  lock, refund and re-plan on cancel or release, fail fast with `BudgetExhausted(wait)`.** Measured:
  FIFO held in 25-way contention and in 600 randomized scripts; a cancelled reservation's slot is
  reclaimed by the waiters behind it only if the waiters are re-planned; a granted-but-undelivered
  permit is refunded on cancel; a permit that already reached the caller is spent unless
  `release()` is called.
- **Lanes: a reserved floor per lane per window, held back, not lent.** An interactive call
  bypassed a 600-call tracker backlog at 0 ms up to its reserve and was refused with the exact wait
  after it. Lending unused reserve to other lanes does not work in a plan-ahead scheduler (measured:
  naive lending drove interactive grants to 0 of 55-60 per minute). The cost of hold-back is the
  share itself (tracker 48 of 60 per minute with an idle 20% interactive share).
- **Signals: `RateSignals(throttled, retryAfter, readings: window -> (remaining, resetIn))`; the
  server count only raises local usage; the pause is the maximum of every hint and never shortens;
  it applies to all lanes.** `Retry-After` is read as seconds or HTTP-date (IMF-fixdate plus both
  obsolete forms, 116 lines of common Kotlin, differential-tested against `java.time` on 600,000
  random instants). The IETF `RateLimit` header draft (revision 11, expires 2026-11-24) is not read
  generically.
- **Hard stop and persistence: a `hard` window refuses until its boundary with no margin borrowing;
  persistence is a suspend `load`/`save` of plain snapshot values with a write-ahead high-water
  mark.** The store is written once per `persistAhead` hard-window grants (1/10 writes per grant at
  10), a crash can only over-count by at most `persistAhead - 1`, never under-count (0 violations in
  800 randomized crashes). The in-memory store is the default.
- **Time: the pair `kotlin.time.TimeSource` (shared with the cache ticker and `delay`) plus a
  library-owned `WallClock` of epoch milliseconds.** `kotlin.time.Clock`/`Instant` are
  `@ExperimentalTime` (error-level opt-in) in the Kotlin 2.2.0 standard library and stable only from
  2.3.0, so neither appears in a signature; the default `WallClock` uses `Clock.System` internally
  under `@OptIn`, which compiled for `jvm` and `js` and ran on stdlib 2.2.0, 2.3.21 and 2.4.10.
  kotlinx-datetime is not needed. `permit` is not `AutoCloseable` and needs no lease timeout.
- No binding requirement fails. Two points need a call (section 6, *Considerations*): the default
  wall-clock source, and how the transport's dispatcher is sized against the budget's burst.

## 3. Findings

Tiers: **consensus** (spec or several independent credible sources), **practice** (what mature
libraries visibly do), **opinion** (one source), **code-read** (read from a provider's source, not a
contract), **measured** (by a probe).

### F1. The providers count in fixed, wall-clock-aligned windows (code-read; practice)

- **OpenDota** (`odota/core` `svc/web.ts` at `2a006be5c2`, 2026-10-15; `config.ts` at `449fb7bf7b`,
  2026-10-19): the middleware increments a per-identifier counter (API key, else IP) in a Redis hash
  that expires at the next minute boundary (`getStartOfBlockMinutes(1, 1)`) and a second hash that
  expires at `getEndOfDay()`. Defaults: 60 per minute without a key, 300 with one, 3,000 per day
  (`API_FREE_LIMIT`: "If using an API key, calls over this are charged."). The increment runs
  **before** the limit check, so a refused request still counts; the `X-Rate-Limit-Remaining-Minute`
  header is set on every response (it goes negative on the `429`), and `X-Rate-Limit-Remaining-Day`
  only for requests without a key. The file contains no occurrence of `retry`, so there is no
  `Retry-After`. A live request on 2026-10-21 returned `X-Rate-Limit-Remaining-Minute: 59` and
  `X-Rate-Limit-Remaining-Day: 2999`, which matches the defaults.
- **A quirk worth designing for:** `getEndOfDay()` is `moment.utc().endOf("day").unix()`, which is
  23:59:59, not 00:00:00 (`svc/util/time.ts` at `8e1fb8b52c`). Read from the code, the daily counter
  expires one second early. A 1 s guard band covers that (code-read; behaviour not observed).
- **Cost is not always 1.** `POST /request/{match_id}` "counts as 10 calls for rate limit (but not
  billing) purposes" (the live OpenAPI document, version 31.1.0). So rate cost and billing cost can
  differ for one call.
- **Open-Meteo** (`Sources/App/Helper/Vapor/RateLimiter.swift` at `680194cb5a`, 2026-07-11):
  per-IP counters for minute (600), hour (5,000) and day (10,000); the counters are cleared by a
  once-a-minute callback (`if (now % 3600) < 60`), so reset instants are approximate; `increment`
  takes a `Float` weight ("calls with many weather variables may account for more than just 1");
  a `429` carries only a reason string ("Daily API request limit exceeded. Please try again
  tomorrow.") and the live response had no rate-limit headers. The free-tier figures match the
  provider's terms page.
- **Path of Exile** documents dynamic rules: "These limits are dynamic and can change at any time",
  with `X-Rate-Limit-{$rule}` (`hits:period:restriction`) and `-State` headers and a `Retry-After` in
  seconds. A fixed list of windows cannot follow that; see Known gaps.
- **Practice:** Envoy's `ratelimit` service (`CacheKeyGenerator.GenerateCacheKey`, `cache_key.go` at
  `053bfeb542`) keys a counter by `bucketStart = (now / divider) * divider`, i.e. fixed windows
  aligned to the Unix epoch (per second, minute, hour, day). The same shape as the providers it
  protects.

### F2. Algorithm comparison against a fixed-window server (measured)

A discrete simulation (limit 60 per server minute, aligned to the true clock; 30 minutes of
saturating backlog; effective limit E = 60) gave:

| Limiter | Granted | Max in one server minute | Notes |
|---|---:|---:|---|
| Greedy token bucket, capacity E, refill E/60 s | 1,859 | **119** | starts full, so the first minute holds 2E |
| Token bucket, capacity E/2 | 929 | 59 | safe, wastes half |
| GCRA, tau = 0 (paced) | 1,800 | 60 | safe; after 5.5 min idle, burst of 120: #30 waits 29 s, #60 waits 59 s |
| Sliding log | 1,800 | 60 | safe; stores a timestamp per grant (3,000 per day) |
| Aligned fixed, client clock = true clock | 1,800 | 60 | exact |
| Aligned fixed, client clock off by 250 ms | 1,860 | **120** | one boundary lets a whole backlog into the next server minute |

With demand pulses of 60 calls just before and 60 just after our boundary, and our clock off from the
server's by 100-900 ms (either sign): plain aligned windows reached 120 per server minute in all 6
skewed cases; aligned windows with a 1 s guard stayed at 60 (1,740 grants against 1,800-1,860);
sliding stayed at 60. In 1,000 randomized trials of 40 bursts each, skew uniform in +/-900 ms:
plain aligned overspent in **510**, aligned with guard in **0**, sliding in **0**; grants over 25
minutes were 1,514,055 (plain), 1,483,306 (guard) and 1,483,851 (sliding). The guard costs about 2%
and no more than sliding does.

Why aligned-with-guard rather than sliding: a sliding log is exact against any rolling provider, but
the providers above reset on a clock boundary, so after a burst at second 59 a sliding limiter
idles for 59 seconds the provider has already refilled. For a **hard daily stop** the bill is
per UTC day, so only a UTC-aligned counter matches it exactly; a sliding 24 h window refuses
earlier than the bill requires and cannot be persisted as one integer.

Evidence tier: **measured** for the numbers; **consensus** that greedy buckets permit boundary
bursts of twice the rate against a fixed-window counter (Bucket4j documents the greedy/intervally
distinction; Lyft and the providers use fixed windows).

### F3. Integer ledger, margins, cost (measured; practice)

- Per `(window, index)` cell the ledger keeps `granted`, `planned` (reserved for waiters),
  `phantom` (server-reported usage we did not grant) and a per-lane count. All `Int`/`Long`; the
  index is `floorDiv(epochMillis, periodMs)`; no floating point except the lane share conversion.
- A window's enforced limit is `published - margin`. The margin absorbs other processes behind the
  same IP, which a local budget cannot see (OpenDota keys by IP when there is no key).
- `cost` is an integer number of units charged to every window. Open-Meteo weights are fractional
  and OpenDota's rate and billing costs can differ (F1), so fractional or per-window costs need
  scaled windows or a later extension; they are not modelled.
- `cost` larger than any window's limit can never be granted: `BudgetExhausted(wait = INFINITE)`
  immediately (measured, cost 51 against limit 50).
- Strict FIFO per lane means a large head request blocks smaller followers in its own lane (measured:
  window 10, 8 used, a cost-5 call at t=1 s got its slot at 60 s and a cost-1 call behind it also at
  60 s although 2 units were free; a different lane took them at 3 s). That is deliberate: backfilling
  starves expensive calls.

### F4. Reservation, fairness and cancellation (measured)

All on `runTest` virtual time with a single injected `TimeSource` and `WallClock`.

- **FIFO under contention:** 25 callers arriving in id order against a 10-per-minute window were
  granted in id order, 10/10/5 per server minute, with the guard at 0 and at 1 s.
- **Cancel of a waiter** (2/min; A,B granted; C,D planned at 60 s; E,F at 120 s; G at 180 s; C
  cancels at 10 s; newcomer H at 11 s): with refund only, E,F stay at 120 and G,H at 180, and the
  freed slot at 60 s is wasted; with a **re-plan of the remaining waiters** E moves to 60, F and G to
  120, H to 180. A cancelled reservation therefore does not hurt the waiters behind it provided the
  budget re-plans.
- **Granted-but-undelivered:** a waiter cancelled after its slot is reached but before it resumed is
  refunded. A waiter that has already been handed its permit is **not**: the permit is spent and the
  caller must `release()` it if it never sends (measured: a job cancelled by a sibling at the instant
  of delivery received `Result.success` and the unit stayed charged).
- **Randomized check:** 300 seeds x 2 settings x 80 operations (acquire with random lane, cost 1-2
  and wait cap; cancel; release; `complete` with a `429` and `Retry-After`; windows 2-7 per minute,
  day = 20x, 25% interactive share, 1 s guard): 0 per-minute or per-day violations measured outside
  the budget, 0 same-lane FIFO violations, 0 leaked ledger entries or waiters after 3 days of
  virtual time. A further 400 scripts with up to 90 s pauses found 0 grants strictly inside a pause
  created by an earlier `429`, over 19,463 grants and 4,826 pauses.
- **Cost of the re-plan:** O(waiters x windows) per cancel, release or completion that changes
  anything. One cancel with 3,000 waiters cost about 1.2-1.7 ms; a plain `complete()` with 3,000
  waiters about 0.6-1.1 ms (the probe re-plans on every completion; it should skip that when the
  signals are empty); cancelling all 3,000 at once cost about 1.1 s. A realistic queue is bounded by
  `rate x maxWait`, because `maxWait` fails fast.
- **Real clocks:** 400 coroutines on `Dispatchers.Default`, a 20-per-second window, 30% cancelled at
  random: at most 20 grants in any wall-clock second, empty ledger and queue afterwards, no deadlock.
- **Cancellation of `complete`:** `complete` and `release` run in `NonCancellable`; a `complete`
  called from the `finally` of a cancelled coroutine still applied its signals (usage 4 as the server
  said, 30 s pause).
- `Mutex.lock` "is fair; suspended callers are resumed in first-in-first-out order" and is
  cancellable (kotlinx-coroutines 1.10.2), which is what makes "reserve under the lock, wait outside
  it" cheap: the lock is held for arithmetic only.

### F5. Lanes and reserved shares (measured; practice)

- **Definition:** a lane's share is a fraction of each window's enforced limit, converted to a
  reserved unit count per window (`ceil`, at least 1 for a share above zero). The reserve is a
  **floor for that lane**, not a ceiling: other lanes may use at most `limit - (unused reserve of
  every other lane)`, and the lane itself may use the whole window.
- **Bypassing a backlog:** 60 per minute, 600 tracker calls queued, one interactive call per second
  from t=5 s with a 5 s wait cap. Without a share: all 16 were refused (wait 595 s falling to 580
  s). With a 20% interactive share: 12 granted at 0 ms, then refused with the real wait (43 s, the
  time to the next boundary). Because the planner reserves the interactive units in every future
  window too, the backlog never plans into them.
- **No starvation of the reserved lane:** 1,000 interactive and 1,000 tracker queued at t=0:
  interactive 60 per minute and tracker 0 with only an interactive share (the floor protects the
  interactive lane; the other lane can be starved by saturation), versus interactive 42 and tracker
  18 per minute when both lanes hold shares (20% and 30%). A lane that must not starve needs its own
  share.
- **Waste:** with an idle 20% interactive share, the tracker got 48 of 60 per minute (the reserve is
  held back, not lent).
- **Lending does not work in a plan-ahead scheduler (measured, two variants).** Lending the unused
  reserve once a window is 50% or 75% elapsed, decided at the slot's time, let the tracker backlog
  pre-claim the reserve of every future window, and interactive grants fell to **0** with 55-60
  refusals per minute. Deciding at planning time instead (lend only if the clock is already past the
  lend point) never fired, because nothing re-plans after the last grant of a window; it would need a
  timer per window and gives a guarantee only for the first part of each window.
- **Rounding:** `ceil` with a minimum of 1: a 3-unit window with a 20% share reserved 1 (tracker took
  2, interactive 1); a 7-unit window reserved 2.

### F6. Server signals and pauses (measured; consensus for the HTTP rules)

- **`Retry-After`** (RFC 9110 section 10.2.3, June 2022): `Retry-After = HTTP-date / delay-seconds`,
  `delay-seconds = 1*DIGIT`. A recipient "MUST accept all three HTTP-date formats" (section 5.6.7):
  IMF-fixdate, the obsolete RFC 850 form with a two-digit year, and asctime. A two-digit year more
  than 50 years ahead "as representing the most recent year in the past that had the same last two
  digits".
- **Parser in common Kotlin** (no `java.*`, Hinnant's `days_from_civil`, 116 lines): 200,000 random
  instants per format from 1970 to 2100 matched `java.time` for IMF-fixdate and asctime, and 200,000
  within +/-49 years for RFC 850, with 0 mismatches; the RFC examples, a leap second (`23:59:60`),
  and nine malformed inputs (lower-case, `UTC`, `Feb 31`, hour 25, `-5`, `1.5`, unpadded day) gave
  the expected results. When the response carries its own `Date`, the delta is taken between two
  server readings: a clock 10 minutes fast gave 1 m instead of 0 s.
- **Reconciliation rules (as probed):**
  - a reading's `remaining` is "units left after the server counted this response"; local usage
    becomes `max(local, published - remaining)` for the window the permit was granted in; a reading
    for a window that has since rolled is ignored; a larger `remaining` never lowers usage (40 then
    59 left `used = 20`);
  - a throttled signal pauses until the **maximum** of: `Retry-After`; a reading with `remaining <= 0`
    and a `resetIn` (37 s was used, not the boundary 55 s away); else the next boundary of the
    exhausted windows (or, with no information, the shortest window) plus the guard; capped by
    `maxPause`; a later hint never shortens a pause;
  - the pause is global to the budget and so covers every lane. Waiters are re-planned behind it and
    refused at once if the new wait exceeds their cap (day exhausted: all three tracker waiters were
    refused immediately). Permits already granted are already on the wire; they report their own
    result through `complete`.
  - A `429` carries no counts for a window the reader does not know. Both OpenDota `429`s are
    distinguishable by headers (a negative `-Minute` or `-Day` remaining); Open-Meteo's only by the
    body, so the reader needs the body, not only the headers.
- **Practice:** Kord's `RequestResponse` has `GlobalRateLimit` and `BucketRateLimit` with a `Reset`;
  JDA keeps a global timestamp (`GlobalRateLimit`) beside per-bucket resets. GitHub: "Continuing to
  make requests while you are rate limited may result in the banning of your integration." Discord:
  "Your application should rely on the `Retry-After` header or `retry_after` field to determine when
  to retry the request." Kord issue #1074 (open, 2026-09-07) is a regret in this exact area: Kord read
  the shorter bucket reset, ignored a `Retry-After` of about 600 s, and retried every 10 s.
- **The IETF `RateLimit` draft** (`draft-ietf-httpapi-ratelimit-headers-11`, 23 May 2026, "Expires: 24
  November 2026", Active Internet-Draft, WG Document, not an RFC): structured fields
  `RateLimit-Policy: "burst";q=100;w=60` and `RateLimit: "default";r=50;t=30`, and "the
  Retry-After field MUST take precedence". It has changed shape across revisions (the changelog lists
  changes since drafts 00-08), no provider researched here sends it, and the providers' own headers
  differ. Opinion/tier: draft status is **consensus** (datatracker); the recommendation not to ship a
  generic reader is a judgement.

### F7. Hard daily stop and persistence (measured)

- A `hard` window has no margin borrowing and no overdraft: the 6th call of a 5-per-day window with a
  1 minute cap was refused with the true wait (2 h to the UTC midnight), and a fresh budget **without**
  restore granted straight away (the billing hole a restart opens).
- **Snapshot state:** per hard window `(name, windowStartEpochMillis, used)`, plus `takenAtEpochMillis`
  and `pausedUntilEpochMillis`, plus a version. All primitives; no serializer is shipped.
- **Restore rules:** a window whose start is before the current window is dropped (that day is over);
  a window in the current index keeps `max(local, saved)` (never forgive); a snapshot stamped further
  in the future than `maxRestoreSkew` (5 minutes) is ignored as untrusted (stamped 10 days ahead: ignored;
  4 minutes ahead: kept and still refused). Ignoring reopens the billing hole, so it must be logged.
- **When to save: a write-ahead high-water mark.** Before granting, if the new count would exceed the
  persisted one, persist `count + persistAhead - 1` (capped at the limit) under the lock. The persisted
  number is therefore always at least the true usage. Crash-simulated 200 times per setting: restored
  count was never below the truth and over-counted by at most `persistAhead - 1`; writes per grant were
  1.000, 0.202, 0.102 and 0.042 for `persistAhead` 1, 5, 10 and 25. `close()` writes the exact count.
  A pause is written with the next save (a lost pause costs one extra refused call).
- A multi-process keyed budget cannot use a snapshot store: it is last-writer-wins. Counting across
  processes needs an atomic increment in shared state (this is what Bucket4j's distributed back-ends
  offer; section 4).

### F8. `Permit` lifecycle (measured; practice)

- `complete` is idempotent (second call ignored); `release` is idempotent; `complete` after `release`
  and `release` after `complete` are ignored (usage and pause unchanged).
- A permit never completed is simply spent: it was charged at grant and costs nothing afterwards.
  The budget holds no lock or semaphore for it, so there is nothing to leak and no lease timeout is
  needed. This differs from Kord's `ExclusionRequestRateLimiter`, whose token holds a `Mutex`
  (`sequentialLock.lock()` in `await`, `unlock()` in `complete`), which is why Kord ships the
  `RequestRateLimiter.consume` helper that completes the token with `Error` in `finally`.
- `AutoCloseable` is not a fit: `complete` and `release` are suspend (they take the lock), `close()`
  cannot be; and the safe default on an exception is "spent, no signals", which `release()` must
  override. A `withPermit { }` helper gives the `use`-shaped ergonomics.
- Redirects: the transport follows redirects itself, one `Call` per hop, and every hop reaches the
  server (`http-transport.md`, F3 and the loop in the recommendation). One permit per **logical** call
  is the default; a margin absorbs the occasional extra hop. A provider with fixed hosts
  (`Trusted`) does not normally redirect.

### F9. Utilisation reading (measured)

Per window: `used` (granted plus server-reported), `queued` (planned for waiters), `limit`,
`published`, `windowStartEpochMillis`, `resetsAtEpochMillis`, `hard`, and `elapsedFraction`; plus
`pausedFor` and `waiting`. A consumer that wants to stretch polling intervals needs the burn ratio,
not the raw fraction: 40 of 50 at second 55 of a minute is slack, 40 of 50 at second 10 is not.
`fraction / elapsedFraction` is the projected end-of-window usage; this goes in the KDoc as a worked
example rather than as a derived property whose threshold would be policy.

### F10. Time (documented + measured)

- **Standard library status.** In Kotlin **2.2.0** `kotlin.time.Clock` is declared
  `@SinceKotlin("2.1") @ExperimentalTime public interface Clock`, and `Instant` is in the same
  package with the same marker. `ExperimentalTime` is `@RequiresOptIn(level = RequiresOptIn.Level.ERROR)`
  and says: "Any usage of a declaration annotated with `@ExperimentalTime` must be accepted either by
  annotating that usage with the `OptIn` annotation, e.g. `@OptIn(ExperimentalTime::class)`, or by
  using the compiler argument `-opt-in=kotlin.time.ExperimentalTime`", and "Do not use it if you
  develop a library since your library will become binary incompatible with the future versions of
  the standard library." Kotlin **2.3.0** "stabilizes the new time tracking functionality,
  `kotlin.time.Clock` and `kotlin.time.Instant`" (its `Clock` is `@SinceKotlin("2.3")
  @WasExperimental(ExperimentalTime::class)`). `TimeSource` is stable since 1.9.
- **Measured:** common code using `Clock.System.now().toEpochMilliseconds()` without the opt-in fails
  with "This declaration needs opt-in" under 2.2.0; with `@OptIn(ExperimentalTime::class)` it compiles
  for `jvm()` and `js(IR)`. A class compiled by Kotlin 2.2.0 ran unchanged on the stdlib of 2.2.0,
  2.3.21 and 2.4.10 (JVM only; JS and native were compiled, not run).
- **kotlinx-datetime:** 0.7.0 "Remove `kotlinx.datetime.Instant` and `kotlinx.datetime.Clock` in favor
  of `kotlin.time.Instant`", 0.7.1 added aliases, 0.8.0 (stable) publishes `Instant` and `Clock` in a
  separate compatibility artifact; its default build level is Kotlin 2.1.20. A dependency whose public
  types are the experimental `kotlin.time.Instant` pushes the opt-in to every consumer on 2.2.x. The
  budget needs no calendar arithmetic: UTC minute and day are `floorDiv` of epoch milliseconds, so the
  dependency buys nothing.
- **Alternatives:** (a) library-owned `fun interface WallClock { nowEpochMillis(): Long }`, which
  keeps any `Instant`/`Clock` out of signatures and makes the fake trivial; (b) kotlinx-datetime,
  rejected above; (c) a bare `() -> Long`, which loses the name and the KDoc contract but is what
  `webtools-udp` does for its monotonic clock (`LinkQualityTracker.kt:11-15`, `nanoClock: () -> Long =
  System::nanoTime`). (a) is the recommendation.
- **One fake for all three.** `TestScope.testTimeSource` is `@ExperimentalCoroutinesApi` and "the test
  scheduler as a TimeSource" (1.10.2); its readings are the scheduler's `currentTime`, the same clock
  `delay` runs on in `runTest`. Measured: a third call to a full window waited 10,000 ms of virtual
  time; a `TimeSource` mark taken from `testTimeSource` read 10,000 ms and `currentTime` 10,000 ms; after
  `delay(25.hours)` the UTC-day index advanced by one. The wall clock in tests is
  `WallClock { startEpochMillis + testScheduler.currentTime }`, with `startEpochMillis` chosen at a UTC
  midnight (or any offset, to test mid-minute behaviour).

### F11. Where it lives (measured; documented)

- The whole default (Mutex, Channel, `delay`, `TimeSource`, `Duration`, `Result`) is valid common
  Kotlin: the budget and the HTTP-date parser compiled for `jvm()` and `js(IR)` under 2.2.0 with
  coroutines 1.10.2. Kord's own limiters are in `commonMain` too (`rest/src/commonMain/.../ratelimit`).
- **Bucket4j 8.21.0** (`bucket4j_jdk17-core`, released 2026-10-02): `Bandwidth` builders include
  `refillIntervallyAligned(long tokens, Duration period, Instant timeOfFirstRefill)`, several limits
  can be stacked on one bucket, `consumeIgnoringRateLimits` makes tokens negative (a debt primitive
  that fits a server-reported surplus), `BlockingBucket.tryConsume(tokens, maxWait, ...)` fails fast
  ("consumes nothing and returns false immediately"), and distributed back-ends share counters between
  processes. It does **not** provide: suspending waits (blocking or `CompletableFuture`), FIFO across
  waiters with cancel refunds, lanes with reserves, a pause until a time, the boundary guard, or a
  hard-stop snapshot; the JDK 11 line (`bucket4j_jdk11-core`) stopped at 8.15.0 on 2025-08-24, so a
  Java 11 module cannot take the current line. It is a JVM-only alternative behind the port, worth it
  only for a multi-process shared counter.
- **Logging.** The default lives in common code, where slf4j is not available. Pauses, hard stops,
  refusals and store failures are published through a small observer hook (an event per occurrence),
  and the JVM default (`jvmMain`) maps those events to slf4j at the levels in section 6. The
  repository rule asks for structured logging of lifecycle events and failure states; the hook meets
  it without a logging dependency in `commonMain`.

## 4. How comparable projects do it

| System (version, date) | Shape | What it shows |
|---|---|---|
| **Kord** `RequestRateLimiter` (`main` `db459ec8`, 2026-09-23; Kotlin 2.3.10; `commonMain`) | `suspend fun await(request): RequestToken`; `RequestToken.complete(RequestResponse)`; `RequestResponse` is sealed (`Error`, `Accepted`, `GlobalRateLimit`, `BucketRateLimit`); buckets keyed by the server's bucket hash; a `Mutex` per bucket held from `await` to `complete`; `ExclusionRequestRateLimiter` adds one global `Mutex` (strict sequence), `ParallelRequestRateLimiter` is `@KordUnsafe`. `IntervalRateLimiter(limit, interval, timeSource | clock)` is a fixed interval counter. | The shape of `acquire`/`complete` is established practice. Regrets: a token that is never completed holds a lock (hence `consume { }`); #1074, `Retry-After` ignored; the request handler retries `429` itself (a retry loop). Kord already moved its clock to `kotlin.time.Clock`/`Instant`. |
| **JDA** `RestRateLimiter` / `SequentialRestRateLimiter` (`7c900019`, 2026-09-20) | Queue-and-execute: `enqueue(Work)`; `Work` has `isPriority`, `isCancelled`, `cancel()`; per-bucket queues; `GlobalRateLimit` as a shared timestamp; headers parsed inside. | Priority is a flag on work, not a reserved share; cancelled work is skipped, not refunded (it was never charged). Our budget is a permit, not an executor, because the facade owns the transport. |
| **Discord** client guidance | Per-route buckets keyed by `X-RateLimit-Bucket`; a global limit; "rate limits should not be hard coded"; invalid-request limit counts `401`, `403`, `429`. | Per-route buckets suit a server that names its buckets; the providers here publish windows per key or IP, so the budget is per provider with named windows. |
| **GitHub** REST guidance | `x-ratelimit-remaining`/`-reset`, `retry-after`, "wait for at least one minute" when a secondary limit has no hint. | The same pause rules: header if present, else a documented fallback, never a tight retry. |
| **Bucket4j** 8.21.0 | Token buckets with several bandwidths, aligned refill, debt via `consumeIgnoringRateLimits`, probes with `nanosToWaitForRefill`. | Right arithmetic, wrong waiting model for coroutines; see F11. |
| **Resilience4j** `RateLimiter` 2.4.0 (`AtomicRateLimiter`, 2026-03-14 release) | One `limitForPeriod` per `limitRefreshPeriod`, cycles counted "from the start of epoch" of `nanoTime`, a reservation can go negative; waits by parking the thread. | One window per limiter; the cycle is not wall-aligned; when the wait would exceed the timeout it still sleeps the whole timeout and then returns `false` (`waitForPermission(timeoutInNanos); return false;`), i.e. no fail-fast; no refund on interrupt. |
| **Guava** `RateLimiter` (`b0ab782522`, 2026-09-28; latest release 33.7.2-jre; `@Beta`, since 13.0) | Smooth bursty (saves up to one second of permits) and smooth warming up; reservation by "next free time". | "Note, however, that it does not guarantee fairness." The cost of an acquire is paid by the next caller, `acquire` sleeps uninterruptibly, and a one-second burst allowance is a 2x boundary burst against a fixed-window server. `tryAcquire(timeout)` is the fail-fast form. |
| **Envoy/Lyft `ratelimit`** (`053bfeb542`, 2026-10-19) | Fixed windows keyed `(now / divider) * divider`, units second/minute/hour/day. | The server side of the same problem; the aligned ledger mirrors it. |
| **redis-cell** (GCRA, 2025-11-24) | `CL.THROTTLE key max_burst count period [quantity]`; "provides a rolling time window". | GCRA suits protecting your own service with a smooth rolling limit; against a third party that resets on a clock it paces below what is allowed (F2). |
| **Weighted fair queuing** (concept) | "WFQ allows schedulers to specify, for each flow, which fraction of the capacity will be given." Work-conserving: idle share is redistributed. | The reserved share is WFQ's guaranteed fraction without the redistribution, because redistribution needs preemption that a plan-ahead scheduler lacks (F5). |

## 5. Pitfalls & regrets

1. **A greedy bucket against a fixed-window provider** doubles at every boundary (119 per server minute
   measured). Do not call it a safe lower bound.
2. **Aligned windows with no tolerance** overspent in half of the skewed bursty trials. A latency of
   a second between our grant and the server's count has the same effect as skew.
3. **Holding a lock for the life of a permit** (Kord's exclusion limiter) makes a forgotten
   `complete` a deadlock. Charge at grant, hold nothing.
4. **`Retry-After` shorter than a bucket reset** (Kord #1074) and **built-in retry on `429`** turn a
   pause into a ban. The budget pauses; the caller decides to retry; the transport never re-sends.
5. **Refund-only cancel** leaves a hole that a FIFO lane never reuses; re-plan the waiters (F4).
   Re-planning is O(waiters), so bound the queue (`maxQueued`) as well as the wait.
6. **A permit handed to a coroutine that is already cancelled** is spent. The facade's `finally`
   must `release()` when nothing was sent and `complete(...)` when something was, in `NonCancellable`.
7. **Lending reserve on a plan-ahead ledger** silently destroys the reserve (F5).
8. **Treating `cost` as 1** misses OpenDota's 10-unit `POST /request` and Open-Meteo's fractional
   weights (F1).
9. **A day boundary one second off** (OpenDota's 23:59:59) and a once-a-minute reset timer
   (Open-Meteo) are why the guard is a parameter and not a constant.
10. **Persisting every grant** puts a store write under the lock for every call; **persisting on a
    timer** loses the last interval on a crash. The write-ahead high-water mark gives one write per
    `persistAhead` grants and never under-counts.
11. **Sharing a snapshot store between processes** is last-writer-wins and under-counts.
12. **Experimental `kotlin.time.Clock`/`Instant` in a public signature** forces the opt-in on every
    consumer on Kotlin 2.2.x, and kotlinx-datetime 0.7.x does the same through its types.
13. **Public data classes and added constructor parameters.** Kotlin's library guidance: "Avoid using
    data classes in your API", and adding even a defaulted parameter changes the JVM signature (a
    `NoSuchMethodError` for old callers). The policy numbers will grow; see Considerations.
14. **`@SubclassOptInRequired` is limited to open/abstract classes and non-`fun` interfaces.** A
    `fun interface` reader cannot carry it; use a plain interface, or put the `@RequiresOptIn` marker
    on it directly.
15. **The dispatcher sizing hazard.** A fixed-window budget can grant its whole window at the first
    instant of the window, so the number of simultaneous permits can equal the smallest window's limit
    (50 for a 60-per-minute provider with a margin of 10). OkHttp's queue is invisible to the call
    timeout and the wait cap (`http-transport.md:121`).

## 6. Recommendation for this repo

Ship **`WindowedCallBudget`**, an aligned-fixed-window ledger with a boundary guard, behind the
**`CallBudget`** port, in `commonMain`. The port is small and substitutable (a Bucket4j or a
Redis-backed budget can implement it later); the default holds the policy; every number is a
parameter. Reasons: every provider counts in clock-aligned windows (F1), only an aligned ledger with
a guard is exact at the boundary and at the UTC-day bill (F2), and the hard parts of this budget are
fairness, cancellation, caps and header feedback, which none of the surveyed libraries provides for
coroutines (F4, F11).

I would reject the obvious alternative, a Bucket4j or token-bucket default: it is the easy half
(arithmetic), it is JVM-only, its current line needs Java 17, and it does not match the provider's
boundary (F2).

### Types (signatures only; package `com.spartanlabs.webtools.http.budget`, `explicitApi()`)

`SeamOptIn` below stands for the module's single `@RequiresOptIn` marker. Stable Core is what a
consumer calls or configures; Experimental is what a consumer implements or substitutes.

```kotlin
// ---- the port ------------------------------------------------------------ [Stable to call]
@SubclassOptInRequired(SeamOptIn::class)           // implementing the port is Experimental; calling it is not
public interface CallBudget {
    /** Reserves [cost] units on [lane]. Fails fast with [BudgetExhausted] when the computed wait exceeds
     *  [maxWait]; never queues beyond it. Throws only CancellationException; a cancelled call is refunded. */
    public suspend fun acquire(cost: Int, lane: Lane, maxWait: Duration): Result<Permit>
    public suspend fun utilisation(): BudgetUtilisation
    public suspend fun close()                      // waiters fail with BudgetClosed; writes the exact hard-window counts
}

public interface Permit {                          // not AutoCloseable: complete/release are suspend
    public val cost: Int
    public val lane: Lane
    /** The request was sent. Idempotent; runs in NonCancellable; unsent permits are spent unless [release]d. */
    public suspend fun complete(signals: RateSignals = RateSignals.None)   // [Experimental member: RateSignals]
    /** The request was never sent. Idempotent; refunds the units; ignored after [complete]. */
    public suspend fun release()
}

/** Charges the permit on acquire, completes it in a finally (spent, no signals) unless the block did. */
public suspend inline fun <T> CallBudget.withPermit(
    cost: Int, lane: Lane, maxWait: Duration, block: suspend (Permit) -> Result<T>,
): Result<T>

// ---- vocabulary ----------------------------------------------------------- [Stable Core]
public class Lane(public val name: String) {        // a plain class, not a data class; equals/hashCode on name
    public companion object { public val Interactive: Lane; public val Tracker: Lane }
}

public class RateWindow(
    public val name: String,
    public val published: Int,                      // what the provider documents
    public val period: Duration,                    // aligned to multiples of the period since the Unix epoch (UTC)
    public val margin: Int = 0,                     // enforced limit = published - margin
    public val hard: Boolean = false,               // no margin borrowing, refuses until the boundary, persisted
) { public val limit: Int }

public class BudgetExhausted(public val wait: Duration, public val reason: ExhaustedReason, public val window: String?)
public class BudgetClosed                           // both are BudgetFailure; the hierarchy belongs to the failure model
public enum class ExhaustedReason { Window, Paused, CostExceedsLimit, QueueFull }   // wait == INFINITE for CostExceedsLimit

// ---- the default ---------------------------------------------------------- [Stable Core]
public class WindowedCallBudget(
    windows: List<RateWindow>,
    laneShares: Map<Lane, Double> = mapOf(Lane.Interactive to 0.2),   // reserved floor per lane per window, ceil, min 1 unit
    boundaryGuard: Duration = 1.seconds,            // <= a quarter of the shortest window; charged to both windows near a boundary
    maxPause: Duration = 24.hours,                  // cap on any Retry-After or boundary pause
    maxQueued: Int = 1_000,                         // waiters beyond this fail with QueueFull (bounds the O(n) re-plan)
    persistAhead: Int = 10,                         // hard-window write-ahead block; 1 = write every grant
    maxRestoreSkew: Duration = 5.minutes,
    timeSource: TimeSource = TimeSource.Monotonic,  // the one source shared with the cache ticker and delay
    wallClock: WallClock = WallClock.System,
    store: BudgetStore = InMemoryBudgetStore(),     // [Experimental seam type]
    observer: BudgetObserver = BudgetObserver.None, // [Experimental seam type]
) : CallBudget {
    public val maxBurst: Int                        // the smallest window's enforced limit: most simultaneous permits
}

// ---- time ----------------------------------------------------------------- [Stable Core; WallClock is substitutable]
public fun interface WallClock {
    public fun nowEpochMillis(): Long
    public companion object { public val System: WallClock }   // internal @OptIn(ExperimentalTime) Clock.System
}

// ---- signals: the reader seam --------------------------------------------- [Experimental]
@SeamOptIn
public interface RateSignalsReader {                // a plain interface (a fun interface cannot take the opt-in)
    /** Parses one provider's response; [nowEpochMillis] resolves an HTTP-date. Never throws for bad input. */
    public fun read(response: HttpResponse, nowEpochMillis: Long): RateSignals
    public companion object { public val Standard: RateSignalsReader }  // 429, or 503 with Retry-After; no counts
}
@SeamOptIn
public class RateSignals(
    public val throttled: Boolean = false,          // a 429, or a 503 that asks for a pause
    public val retryAfter: Duration? = null,        // server-requested wait
    public val readings: Map<String, WindowReading> = emptyMap(),   // by RateWindow.name; unknown names ignored
) { public companion object { public val None: RateSignals } }
@SeamOptIn
public class WindowReading(public val remaining: Int, public val resetIn: Duration? = null)  // after the server counted this call; may be negative
@SeamOptIn
public object RetryAfter {
    public fun parse(value: String, nowEpochMillis: Long, serverDateEpochMillis: Long? = null): Result<Duration>
}

// ---- persistence seam ----------------------------------------------------- [Experimental]
@SeamOptIn
public interface BudgetStore {
    public suspend fun load(): Result<BudgetSnapshot>          // BudgetSnapshot.Empty when nothing is stored: no nullable result
    public suspend fun save(snapshot: BudgetSnapshot): Result<Unit>
}
@SeamOptIn public class BudgetSnapshot(public val version: Int, public val takenAtEpochMillis: Long,
    public val windows: List<WindowSnapshot>, public val pausedUntilEpochMillis: Long) { public companion object { public val Empty: BudgetSnapshot } }
@SeamOptIn public class WindowSnapshot(public val window: String, public val windowStartEpochMillis: Long, public val used: Int)
@SeamOptIn public class InMemoryBudgetStore : BudgetStore

// ---- observation ---------------------------------------------------------- [Experimental]
@SeamOptIn
public fun interface BudgetObserver { public fun onEvent(event: BudgetEvent); public companion object { public val None: BudgetObserver } }
// BudgetEvent: Granted(lane, cost, waited), Refused(lane, wait, reason), Paused(until, source), HardStop(window, resetsAt),
//              Reconciled(window, serverUsed), StoreFailed(cause)

// ---- utilisation ---------------------------------------------------------- [Stable Core]
public class BudgetUtilisation(public val windows: List<WindowUtilisation>, public val pausedFor: Duration, public val waiting: Int)
public class WindowUtilisation(
    public val name: String, public val used: Int, public val queued: Int, public val limit: Int, public val published: Int,
    public val windowStartEpochMillis: Long, public val resetsAtEpochMillis: Long, public val hard: Boolean,
    public val elapsedFraction: Double,
) { public val remaining: Int; public val fraction: Double }
```

Behavioural contract in short (this is what the KDoc and the Level-2 tests pin):

- **`acquire`:** under the lock, compute the earliest slot `>= max(now, pause end, this lane's last
  slot)` at which `cost` fits every window's enforced limit minus other lanes' unused reserve (cells
  near a boundary are checked and charged in both windows); if `slot - now > maxWait`, return
  `BudgetExhausted(slot - now, ...)` and charge nothing; else charge the cells as `planned`, add to the
  waiters, release the lock and wait for the slot (re-reading the clock on every wake). Cancelling
  during the wait refunds the cells and re-plans the others.
- **`complete`:** under the lock, once: reconcile each reading (usage only rises), compute the pause
  (maximum of every hint; never shortens), re-plan the waiters only if something changed, and fail any
  waiter whose new wait exceeds its cap.
- **Hard windows:** at grant, if the persisted count is not at least `used + cost`, save
  `used + cost + persistAhead - 1` (capped at the limit) first. A save error or a two-second timeout
  is logged and ignored: the in-process stop still holds.
- **Restore** runs once, lazily, at the first `acquire`, under the lock (the lazy trigger was not
  probed; the explicit `restore()` was).
- **Logging** (JVM observer to slf4j, structured key/value): a refusal at `DEBUG` (one per call is
  noise), a pause at `WARN` with `until` and its source (`Retry-After`, `resetIn`, `boundary`), a hard
  stop at `WARN` once per window, a reconciliation at `INFO` when it raises usage by more than the
  grant count, a store failure at `WARN`. Never headers or bodies.

### Level-2 tests (fake time, no network) the probe already shows to be writable

`com.spartanlabs.testing.component.webtools.http.budget`: FIFO in 25-way contention; cancel refunds
and later waiters move up; granted-but-undelivered refund; release and double `complete`; interactive
bypasses a backlog, reserve exhausted gives the exact wait; saturated lane does not starve a lane with
a share; pause from `Retry-After`, from `resetIn`, from the boundary, never shortens; day exhausted
refuses waiters at once; server count only raises usage and is ignored across a window roll; hard stop
and restore (stale day dropped, future snapshot ignored); write-ahead never under-counts after a crash;
`complete` from a cancelled coroutine; the three clocks agreeing under `runTest`.
`...testing.deterministic.webtools.http.budget`: the HTTP-date table (three formats, two-digit year
pivot, leap second, rejections), window index/boundary arithmetic, the guard's two-cell charge, share
rounding. A randomized property test (the probe's) belongs at Level 4a with a fixed seed.

### Considerations (a call is needed; recommendation given)

1. **Default wall-clock source.** Options: (a) `Clock.System` under an internal `@OptIn(ExperimentalTime)`;
   (b) `expect`/`actual` returning epoch milliseconds (`System.currentTimeMillis()` on `jvm`, a `js`
   actual if a check-only `js` target exists). (a) is one line and measured binary-compatible across
   stdlib 2.2.0, 2.3.21 and 2.4.10 on the JVM, but the stdlib's own warning says libraries should not
   use experimental time API, and non-JVM runtimes were not run; (b) has no such exposure at the cost of
   per-target code. **Recommend (a)** while the module publishes `jvm` only; switch to (b) the day a
   non-JVM target is published.
2. **Burst against the dispatcher.** The budget can grant its smallest window in one instant
   (`maxBurst`), and the transport research's defaults (`maxConcurrentRequests` 16, per host 5) would
   queue the rest invisibly (`http-transport.md:294`). Options: (a) size the dispatcher to `maxBurst`
   (threads are created on demand, so the cost is small); (b) add a `minSpacing` between grants (an
   additive parameter; but interactive grants then need slots between planned tracker slots, which the
   current planner does not do); (c) a `maxConcurrent` semaphore (needs a lease timeout for permits
   that are never completed). **Recommend (a)**, deriving the dispatcher from `maxBurst`, and deferring
   (b) and (c) until a consumer needs them.
3. **Lending unused reserve.** Options: (a) none (recommended: the cost is the share itself, and a
   consumer who finds it too high lowers the share); (b) lend after a fraction of the window, which needs
   a timer per window and guarantees the reserve only for the first part of it; (c) preemption, which
   a FIFO plan-ahead ledger cannot offer. **Recommend (a)**.
4. **How the policy numbers grow.** A flat constructor is what the README asks for; adding a defaulted
   parameter later changes the JVM constructor signature. Options: (a) flat constructor now and a manual
   secondary constructor for each addition; (b) a plain (non-data) `BudgetPolicy` class holding the
   numbers, so an addition is a new property. **Recommend (b)** for the budget's six policy numbers,
   with `windows`, `timeSource`, `wallClock`, `store` and `observer` staying parameters; this also
   belongs to the API-shape question and should be settled there.
5. **A shipped file-backed store.** Not in 1.0: the in-memory store is the supplied default and the
   KDoc worked example; BotTools has its own database. Add a JVM `FileBudgetStore` (atomic
   write-then-rename) only if a second consumer asks.

## 7. Known gaps

- **Calendar windows.** `RateWindow.period` is a fixed `Duration` aligned to the Unix epoch, which
  covers minute, hour and day. A calendar month (OpenDota's key-less tier is described as having a
  "monthly call limit" in its OpenAPI text; the figure and counting are not in the code read) cannot
  be expressed; a week would be aligned to Thursday. Not probed; a boundary function per window would
  generalise the ledger additively.
- **Per-window cost** (OpenDota rate cost 10, billing cost 1) and fractional weights (Open-Meteo) are
  not modelled; scale the windows or extend `cost` later.
- **Dynamic header-declared rules** (Path of Exile) are out of scope: readings for window names the
  budget does not have are ignored. The "windows are set from headers" case needs a different design.
- **Unprobed pieces of the recommended shape:** `maxQueued` and `QueueFull`; the lazy first-use
  restore; the `BudgetObserver` and its slf4j mapping; skipping the re-plan on an empty `complete`
  (the probe re-plans on every completion); a `WindowReading` for a window whose index differs from
  the grant's; the explicit `maxPause` interplay with hard windows (a `429` for the day with no hint
  paused 23 h 59 min 51 s, correct by construction but not tested against a skew case).
- **Deployed OpenDota values** are unverified beyond one live response (60 per minute and 3,000 per
  day for one IP on 2026-10-21); the 23:59:59 day boundary is read from the code, not observed;
  the keyless monthly limit is unknown. The provider terms pages were fetched through a summarising
  fetcher; the Open-Meteo figures are confirmed by its source (defaults 600/5,000/10,000).
- **Multiple processes behind one IP** are invisible to a local budget. The margin and the server's
  `remaining` count are the only defences; an atomic shared counter (Bucket4j with a distributed
  back-end, or a store with atomic increment) would be a second `CallBudget` implementation, not built.
- **Bucket4j adapter and a generic IETF `RateLimit` reader** were not built or probed. The Bucket4j
  statements come from its source and metadata; the IETF statements from the draft text.
- **Non-JVM execution.** The budget compiled for `js(IR)`; it was run only on the JVM, including the
  `Clock.System` binary-compatibility run.
- **Throughput under heavy contention** was smoke-tested (400 coroutines on real threads) but not
  benchmarked; the re-plan is O(n) and the numbers in F4 are single-machine and JIT-noisy.
- **A shared cache load carries the first caller's lane and wait cap** (`response-cache.md:32`), so an
  interactive caller joining a tracker load cannot re-lane the reservation already made; the cache
  research puts the interactive caller's own deadline in the facade (`withTimeout` around `getOrLoad`).
  How the facade chooses the lane for a coalesced load is not decided here.

## 8. Load-bearing claims

1. **RFC 9110 `Retry-After` grammar.** "The Retry-After field value can be either an HTTP-date or a
   number of seconds to delay after receiving the response." and "A delay-seconds value is a
   non-negative decimal integer, representing time in seconds." RFC 9110, section 10.2.3, June 2022,
   <https://www.rfc-editor.org/rfc/rfc9110.txt>.
2. **RFC 9110 date formats.** "A recipient that parses a timestamp value in an HTTP field MUST accept
   all three HTTP-date formats." and "Recipients of a timestamp value in rfc850-date format, which uses
   a two-digit year, MUST interpret a timestamp that appears to be more than 50 years in the future as
   representing the most recent year in the past that had the same last two digits." RFC 9110, section
   5.6.7, <https://www.rfc-editor.org/rfc/rfc9110.txt>.
3. **IETF draft status and precedence.** "Expires: 24 November 2026" and "draft-ietf-httpapi-ratelimit-headers-11"
   (23 May 2026); "If a response contains both the RateLimit and Retry-After fields, the Retry-After
   field MUST take precedence and the effective window MAY be ignored." Revision 11,
   <https://www.ietf.org/archive/id/draft-ietf-httpapi-ratelimit-headers-11.txt>; datatracker:
   <https://datatracker.ietf.org/doc/draft-ietf-httpapi-ratelimit-headers/> (Active Internet-Draft, WG
   Document).
4. **`Clock` is experimental in Kotlin 2.2.0.** `@SinceKotlin("2.1")` `@ExperimentalTime` `public
   interface Clock {` in `libraries/stdlib/src/kotlin/time/Clock.kt` at tag v2.2.0,
   <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/src/kotlin/time/Clock.kt>.
5. **`ExperimentalTime` is error-level and discourages library use.** "Any usage of a declaration
   annotated with `@ExperimentalTime` must be accepted either by annotating that usage with the
   [OptIn] annotation, e.g. `@OptIn(ExperimentalTime::class)`, or by using the compiler argument
   `-opt-in=kotlin.time.ExperimentalTime`." and "Do not use it if you develop a library since your
   library will become binary incompatible with the future versions of the standard library." (with
   `@RequiresOptIn(level = RequiresOptIn.Level.ERROR)`), v2.2.0,
   <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/src/kotlin/time/ExperimentalTime.kt>.
6. **`Clock` and `Instant` are stable in Kotlin 2.3.0.** "Kotlin 2.3.0 stabilizes the new time tracking
   functionality, `kotlin.time.Clock` and `kotlin.time.Instant`", What's new in Kotlin 2.3.0,
   <https://kotlinlang.org/docs/whatsnew23.html>; the 2.3.0 source has `@SinceKotlin("2.3")`
   `@WasExperimental(ExperimentalTime::class)` on `Clock`,
   <https://github.com/JetBrains/kotlin/blob/v2.3.0/libraries/stdlib/src/kotlin/time/Clock.kt>.
7. **`Clock.System` is a wall clock and not monotonic.** "Successive calls to [now] will not
   necessarily return increasing [Instant] values, and when they do, these increases will not
   necessarily correspond to the elapsed time." and "When you need predictable intervals between
   successive measurements, consider using [TimeSource.Monotonic]." Kotlin v2.2.0 `Clock.kt` (link in 4).
8. **kotlinx-datetime and `Instant`.** "Remove `kotlinx.datetime.Instant` and `kotlinx.datetime.Clock`
   in favor of `kotlin.time.Instant`" (0.7.0) and, for 0.8.0-rc02, "published without `Instant` and
   `Clock`. A separate compatibility artifact contains them for binary compatibility."
   <https://github.com/Kotlin/kotlinx-datetime/blob/master/CHANGELOG.md>; Maven Central lists 0.7.0,
   0.7.1, 0.8.0 and `-0.6.x-compat` variants,
   <https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-datetime/maven-metadata.xml>.
9. **`Mutex` is fair and cancellable.** "This function is fair; suspended callers are resumed in
   first-in-first-out order." and "This suspending function is cancellable", kotlinx-coroutines
   1.10.2, <https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/sync/Mutex.kt>.
10. **The test scheduler is a `TimeSource`.** "Returns the [TimeSource] representation of the virtual
    time of this scheduler." (`TestCoroutineScheduler.timeSource`) and `@ExperimentalCoroutinesApi public
    val TestScope.testTimeSource: TimeSource.WithComparableMarks get() = testScheduler.timeSource`,
    kotlinx-coroutines-test 1.10.2,
    <https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-test/common/src/TestCoroutineScheduler.kt>
    and `.../TestScope.kt`.
11. **OpenDota counts per wall-clock minute and UTC day, counts refused calls, bills keyed calls above
    the free limit.** `.hincrby("rate_limit", res.locals.usageIdentifier, rateCost)` /
    `.expireat("rate_limit", getStartOfBlockMinutes(1, 1))` / `.hincrby("daily_rate_limit", ...)` /
    `.expireat("daily_rate_limit", getEndOfDay())`, then `if (remMinute < 0) { return res.status(429).json(`
    (after the increments), in `svc/web.ts` at `2a006be5c2`,
    <https://github.com/odota/core/blob/2a006be5c2/svc/web.ts>; `API_FREE_LIMIT: "3000", // number of api
    requests per day before 429 is returned. If using an API key, calls over this are charged.` in
    `config.ts` at `449fb7bf7b`, <https://github.com/odota/core/blob/449fb7bf7b/config.ts>.
12. **OpenDota's day expires at 23:59:59 and a `POST /request` costs 10.** `return
    moment.utc().endOf("day").unix();` in `svc/util/time.ts` at `8e1fb8b52c`,
    <https://github.com/odota/core/blob/8e1fb8b52c/svc/util/time.ts>; "This call counts as 10 calls for
    rate limit (but not billing) purposes." and "Use an API key to remove monthly call limits and to
    receive higher rate limits." in the OpenAPI document, version 31.1.0, read 2026-10-21,
    <https://api.opendota.com/api>. A live response carried `X-Rate-Limit-Remaining-Minute: 59` and
    `X-Rate-Limit-Remaining-Day: 2999` the same day.
13. **OpenDota sends no `Retry-After`.** `svc/web.ts` at `2a006be5c2` contains no occurrence of
    "retry" (case-insensitive count 0), link as in 11.
14. **Open-Meteo windows, weights and `429` text.** `?? 10_000`, `?? 5_000`, `?? 600` for the daily,
    hourly and minutely limits; "`count` can be later used to increase the weight for \"heavy\" API
    calls. E.g. calls with many weather variables my account for more than just 1."; "Daily API request
    limit exceeded. Please try again tomorrow.", in `Sources/App/Helper/Vapor/RateLimiter.swift` at
    `680194cb5a`,
    <https://github.com/open-meteo/open-meteo/blob/680194cb5a/Sources/App/Helper/Vapor/RateLimiter.swift>.
15. **Path of Exile limits are dynamic.** "These limits are dynamic and can change at any time
    depending on our requirements." and "we include headers in response headers that should be parsed
    and followed by your application.", <https://www.pathofexile.com/developer/docs/index>, read
    2026-10-21.
16. **Envoy's `ratelimit` uses epoch-aligned fixed windows.** `divider := utils.UnitToDivider(limit.Limit.Unit)`
    / `bucketStart = (now / divider) * divider`, `src/limiter/cache_key.go` at `053bfeb542`,
    <https://github.com/envoyproxy/ratelimit/blob/053bfeb542/src/limiter/cache_key.go>.
17. **redis-cell is GCRA with a rolling window.** "Implements the fairly sophisticated generic cell
    rate algorithm (GCRA) which provides a rolling time window and doesn't depend on a background drip
    process.", <https://github.com/brandur/redis-cell/blob/d12bb050b9/README.md>.
18. **Bucket4j capabilities and Java line.** `BandwidthBuilderBuildStage refillIntervallyAligned(long
    tokens, Duration period, Instant timeOfFirstRefill);`; "Consumes {@code tokens} from bucket
    ignoring all limits. In result of this operation amount of tokens in the bucket could became
    negative."; `BlockingBucket`: "then consumes nothing and returns false immediately", in the
    repository at `959d97c570` (2026-10-04),
    <https://github.com/bucket4j/bucket4j>; the README lists `bucket4j_jdk17-core` under "Java 17
    dependency"; Maven Central: `bucket4j_jdk17-core` release 8.21.0 (2026-10-02) and
    `bucket4j_jdk11-core` release 8.15.0 (2025-08-24),
    <https://repo1.maven.org/maven2/com/bucket4j/bucket4j_jdk11-core/maven-metadata.xml>.
19. **Resilience4j.** "`AtomicRateLimiter` splits all nanoseconds from the start of epoch into cycles."
    and `waitForPermission(timeoutInNanos); return false;`, `AtomicRateLimiter.java` at `a8a3316425`,
    <https://github.com/resilience4j/resilience4j/blob/a8a3316425/resilience4j-ratelimiter/src/main/java/io/github/resilience4j/ratelimiter/internal/AtomicRateLimiter.java>;
    release 2.4.0 (2026-03-14).
20. **Guava.** "Note, however, that it does not guarantee fairness." and "it is the <i>next</i> request
    that will experience extra throttling, thus paying for the cost of the expensive task." and
    `stopwatch.sleepMicrosUninterruptibly(microsToWait);`, `RateLimiter.java` at `b0ab782522`
    (`@Beta`, `@since 13.0`),
    <https://github.com/google/guava/blob/b0ab782522/guava/src/com/google/common/util/concurrent/RateLimiter.java>.
21. **Kord's token, its exclusion lock and its regrets.** `public suspend fun await(request: Request<*, *>):
    RequestToken`; `ExclusionRequestRateLimiter`: `sequentialLock.lock()` in `await` and
    `sequentialLock.unlock()` in `complete`; `RequestRateLimiter.consume` "Throws an
    [IllegalStateException] if the supplied [RequestToken] was not completed.", in `main` at `db459ec8`
    (2026-09-23), <https://github.com/kordlib/kord/tree/db459ec8/rest/src/commonMain/kotlin/ratelimit>;
    issue "Retry-After header is not being used when getting 429 responses" (#1074, opened 2026-09-07,
    open), <https://github.com/kordlib/kord/issues/1074>.
22. **JDA.** `boolean isPriority();` on `RestRateLimiter.Work` and `interface GlobalRateLimit`, at
    `7c900019` (2026-09-20),
    <https://github.com/discord-jda/JDA/blob/7c900019/src/main/java/net/dv8tion/jda/api/requests/RestRateLimiter.java>.
23. **GitHub guidance.** "Continuing to make requests while you are rate limited may result in the
    banning of your integration." and "If the `retry-after` header is present, you should not retry your
    request until after that many seconds has elapsed.",
    <https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api>, read 2026-10-21.
24. **Discord guidance.** "Your application should rely on the `Retry-After` header or `retry_after`
    field to determine when to retry the request." and "rate limits should not be hard coded into your
    app", <https://docs.discord.com/developers/topics/rate-limits>, read 2026-10-21.
25. **Kotlin library guidance on data classes and added parameters.** "Avoid using data classes in your
    API" and "even adding default arguments can break compatibility",
    <https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html>, read 2026-10-21.
26. **`@SubclassOptInRequired`.** "Uses of this annotation are limited to open and abstract classes,
    and non-`fun` interfaces." with `@SinceKotlin("2.1")`, `OptIn.kt` at tag v2.2.0,
    <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/src/kotlin/annotations/OptIn.kt>.
27. **Weighted fair queuing.** "WFQ allows schedulers to specify, for each flow, which fraction of the
    capacity will be given.", <https://en.wikipedia.org/wiki/Weighted_fair_queueing>, read 2026-10-21.
28. **`TimeSource` is stable since 1.9 and may be non-monotonic.** Quoted in
    [`response-cache.md`](response-cache.md) claim 18 from
    <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-time-source/>.
