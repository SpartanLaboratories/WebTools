# Research: the call budget port and its windowed default (`call-budget`)

[SpartanLaboratories/WebTools#67](https://github.com/SpartanLaboratories/WebTools/issues/67), unit 8
(`call-budget`) of the HTTP client stack,
[#55](https://github.com/SpartanLaboratories/WebTools/issues/55). The systems-level design is
[`../../architecture.md`](../../architecture.md) (§6.5, with §6.2, §6.6, §6.9, §7.2, §7.4, §9, §10, §13 and
§11 unit 8) and the systems-level consequences [`../../consequences.md`](../../consequences.md) (§7 and §8).
This folder researches the implementation details that design leaves to the unit and that need evidence
from outside this repository.

## The work

`webtools-http` gains its call budget, all of it in `commonMain` (package `com.spartanlabs.webtools.http`):

- **The port** `CallBudget : AutoCloseable`, which a consumer implements only with
  `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`:
  - a suspend `acquire(cost, lane, maxWait): Result<Permit>` that fails fast with
    `HttpFailure.BudgetExhausted(wait, reason, window)` when the computed wait exceeds `maxWait`
    (`Duration.INFINITE` waits as long as the plan needs; zero fails unless the call fits now);
  - a suspend `utilisation(): BudgetUtilisation`;
  - a non-suspending `close()`;
  - a suspend `flush(): Result<Unit>`, the exact final write, whose default body succeeds.
- **`Permit`**, gated as well: `cost`, `lane`, `complete()` and `complete(signals)` (idempotent, run under
  `NonCancellable`) and `release()` for a permit whose request was never sent. **`withPermit(cost, lane,
  maxWait, block)`**, a plain suspend function, completes the permit as spent in `finally` unless the block
  completed or released it; an exception the block throws propagates. **`CallBudget.Unlimited`** is a shared
  object that grants at once and ignores signals and `close()`.
- **The vocabulary:** `Lane(name)` with the constants `Interactive` and `Tracker`; `RateWindow` (a name, the
  published limit, a period aligned to multiples of itself since the Unix epoch, a margin and a `hard` flag;
  the enforced limit is the published limit minus the margin); `RateSignals` (throttled, a retry-after and
  readings by window name) with `WindowReading(remaining, resetIn)`; `RetryAfter.parse(value, now)` and
  `parse(value, now, serverDate)`; `BudgetUtilisation` and `WindowUtilisation`.
- **The seams:** `RateSignalsReader` (`read(response, nowEpochMillis): RateSignals`; its `Standard` treats a
  `429` as throttled, a `503` as throttled only when its `Retry-After` parses, takes the server's date from
  `Date`, and reports no counts); `BudgetStore` (suspend `load()` and `save(snapshot)`) with
  `InMemoryBudgetStore` and the plain snapshot values `BudgetSnapshot` and `WindowSnapshot`; `BudgetObserver`
  (`onEvent(BudgetEvent)`, with `None`). `BudgetEvent` is an open abstract root, subclass-gated like
  `HttpFailure`, with the final leaves `Granted`, `Refused`, `Paused`, `HardStop`, `Reconciled`,
  `StoreFailed` and `Restored` (the windows restored, the windows dropped, and whether the snapshot was
  ignored as stamped too far ahead).
- **The default** `WindowedCallBudget`, final, built by a factory with a builder over `windows` (required),
  `laneShares` (`Interactive` 20 %), `boundaryGuard` (1 s), `maxPause` (24 h), `maxQueued` (1,000, applied
  to each lane), `persistAhead` (10), `maxRestoreSkew` (5 min), `storeTimeout` (2 s), `timeSource`,
  `wallClock`, `store` and `observer`; it exposes `maxBurst`, the smallest window's enforced limit.
- **How the default behaves:**
  - an integer ledger of epoch-aligned fixed windows with a boundary guard: a grant within the guard of a
    boundary is charged to both adjacent windows;
  - reservations are planned ahead under a coroutine `Mutex`, first come first served within each lane,
    and awaited outside the lock;
  - a cancellation refunds its reservation and re-plans the waiters behind it; a waiter whose new wait
    exceeds its cap after a re-plan fails at once;
  - a lane's share is a floor held back for it, never lent;
  - a server's remaining count only raises local usage;
  - a pause is the maximum of every hint, never shortens and covers every lane;
  - a `hard` window is persisted write-ahead in blocks of `persistAhead` grants and raises `HardStop` once
    per window.
- **Persistence:**
  - the restore runs once, under the lock, at the first of `acquire`, `utilisation()` and `flush()`; it
    drops stale windows, keeps `max(local, saved)` for the current one and ignores a snapshot stamped
    further ahead than `maxRestoreSkew`;
  - a `load()` that fails raises `StoreFailed`: the budget grants from its in-process count, a later
    `acquire` retries the restore at a bounded rate, no write-ahead save is made before a restore succeeds,
    and a late restore adds the grants made since the start to the stored count;
  - a failed or timed-out save is logged and ignored; the in-process stop still holds.
- **After `close()`:** `acquire` and every waiter return `Result.failure(HttpFailure.Closed("budget"))`.
  Everything else keeps working: `flush()` writes the exact counts, `utilisation()` reads, and `complete()`
  and `release()` on permits granted before the close still update the ledger.
- **Events and logging:** one emission point logs through the module's logging seam and forwards to the
  observer; events are delivered outside the ledger's lock; an observer's exception is caught and logged and
  never breaks a grant.
- **Programmer errors** throw `IllegalArgumentException`: a published limit below 1, a margin outside
  `0 until published`, a period that is not positive or not a whole number of milliseconds, blank or
  duplicated window names, no windows, a lane share outside `(0, 1]` or shares whose reserved units do not
  fit every window, a guard above a quarter of the shortest period, a cost below 1 and a negative `maxWait`.
- **Fixtures:** `RecordingBudgetObserver` and `FakeBudgetStore` (failure injection, a delay, a preloaded
  snapshot) join the published test fixtures.
- **Tests:** Level 2 on the published `VirtualClock` (first come first served, refunds and re-plans, lane
  floors, pauses from each hint, the hard stop, the restore rules, write-ahead after a simulated crash,
  `complete` from a cancelled coroutine, the three clocks together); Level 4a (the HTTP-date table against
  `java.time`, window arithmetic, the guard's two-cell charge, share rounding, a seeded property test).

What shapes the questions below:

- **Failures and cancellation.** Expected failures travel in `kotlin.Result`; a refusal and `Closed` are
  values. The budget runs inside the response cache's load coroutine
  ([`../../66-response-cache/consequences.md`](../../66-response-cache/consequences.md) §4): a
  `CancellationException` the budget throws while that coroutine is still active reaches every caller of
  the load as an `IllegalStateException` failure. The only cancellation the budget may throw is its
  caller's own, and that one always propagates.
- **`close()`** is non-suspending, idempotent, never throws and may be called from any thread.
- **Platform.** The common code is compiled for `js` by `webtools-http-commoncheck`: kotlinx-coroutines-core
  1.10.2 and the Kotlin 2.2.0 common standard library only, no atomicfu, no experimental API in production
  code, no new dependency. The budget owns no scope and no dispatcher: a wait runs in the caller's
  coroutine.
- **Time.** The budget reads the injected `TimeSource` and `WallClock` and never the system clock. Under
  `runTest` the published `VirtualClock` supplies both, and its `StandardTestDispatcher` shares the test's
  scheduler, so Level 2 runs on virtual time with no real waiting. `WallClock`'s own contract says a reading
  may step backwards when the system clock is adjusted.
- **Billing.** A `hard` window guards a provider that bills per UTC day: a crash or restart must never
  under-count it.

## Existing findings this research builds on

These are cited, not researched again. The load-bearing claims this unit relies on are re-opened in the
verification below; their measured results are probe output with no source to re-open.

- **[`../../research/call-budget.md`](../../research/call-budget.md).** The providers' fixed wall-clock
  windows (F1); the algorithm comparison that chose aligned windows with a 1 s guard (F2); the integer
  ledger, margins and cost (F3); reservations, first come first served, refunds and re-plans, the
  granted-but-undelivered case and the cost of a re-plan (F4); lane floors and why lending fails (F5); the
  pause and reconciliation rules (F6); the hard stop, the snapshot, the restore rules and the write-ahead
  high-water mark (F7); the permit's lifecycle (F8); the utilisation reading (F9); time (F10); common code
  (F11). Its type sketch predates the architecture, which overrides it: a non-suspending `close()` plus
  `flush()`, one flat package, no opt-in marker on a value type, factory and builder instead of defaulted
  constructor parameters, `withPermit` not `inline`, `complete()` and `complete(signals)` as overloads.
- **[`../../research/test-support-and-time.md`](../../research/test-support-and-time.md)** F1 and F2: one
  virtual clock drives the cache's ticker, the budget's windows, the wall clock and `delay`, and faking one
  clock of three passes for the wrong reason.
- **[`../../research/api-shape-and-failures.md`](../../research/api-shape-and-failures.md):** the API
  conventions (the gate on seams only, plain interfaces instead of `fun interface`, open constants classes,
  no public data class, explicit overloads, no public `inline`).
- **[`../../66-response-cache/research/load-lifecycle.md`](../../66-response-cache/research/load-lifecycle.md)**
  F3 and F7: telling a coroutine's own cancellation from a cancellation raised inside it with
  `currentCoroutineContext().ensureActive()`, and how `runTest` 1.10.2 drains the shared scheduler.

The codebase blast radius is small and is surveyed directly in the summary below: the unit adds new files to
`webtools-http` and its fixtures, extends its ABI dump and two KDoc lists, and has no caller in the
repository yet.

## Questions

### 1. How should waiters wait, be re-planned, refunded and ended by a non-suspending `close()` in a ledger guarded by a coroutine `Mutex`, in common kotlinx-coroutines 1.10.2 code?

- **Options and what each must establish.**
  - **How a waiter waits for its planned slot**, so that a re-plan can move it earlier (a waiter ahead of
    it was cancelled or released) or later (a pause began), and every wake re-reads the plan: a per-waiter
    `CompletableDeferred` signalled by a re-plan and awaited under `withTimeoutOrNull(slot − now)`; a
    per-waiter conflated `Channel`; `select` over the signal and a timeout; or a plain `delay` that re-checks
    the plan after each wake. For each: its stability in 1.10.2 (stable or behind an experimental opt-in),
    its cost per wake, how it behaves under `runTest` with a `StandardTestDispatcher` on the shared
    scheduler, and how the waiter tells its own cancellation from a wake.
  - **How `close()` ends every waiter without suspending and without the lock:** a budget-owned `Job` as
    the parent of each waiter's signal, so that one `cancel()` reaches all of them; a close signal each
    waiter races against its slot; a `@Volatile` flag plus a signal; `Mutex.tryLock`. For each: whether it
    is thread-safe and non-throwing from any thread; how a woken waiter returns `Closed` as a value and never
    lets a `CancellationException` escape while its own coroutine is active; what happens to a call that is
    between the lock and its wait, or still queued on the lock, when `close()` runs.
  - **Refunds and re-plans on cancellation:** taking the lock inside `withContext(NonCancellable)` from a
    coroutine that is already cancelled (whether `Mutex.lock` can still throw or hang there); the
    granted-but-undelivered race (the slot was reached, the waiter's coroutine was cancelled before it
    resumed); `complete()` and `release()` called from a cancelled coroutine.
  - **What runs under the lock and what after it:** `Mutex` is not reentrant, so an observer that calls
    `utilisation()` from `onEvent` deadlocks if events are delivered under the lock; whether the store's
    suspend `save` (write-ahead, bounded by `storeTimeout`) and `load` (the restore) run under the lock or
    outside it, and what `withTimeout` inside `withLock` does under virtual time; how a bounded retry rate
    for a failed restore is expressed.
  - **What is thread-safe in Kotlin 2.2.0 common code without atomicfu:** `kotlin.concurrent.Volatile`,
    `kotlin.concurrent.atomics` (and its opt-in), `MutableStateFlow.compareAndSet`, and the completion of a
    `Job` or `CompletableDeferred`; which of them a library may use without an experimental opt-in.
- **Requirements.**
  - `close()` is non-suspending, idempotent, never throws and is safe from any thread. After it, `acquire`
    and every waiter return `Result.failure(HttpFailure.Closed("budget"))` as a value, and no
    `CancellationException` reaches a waiter whose own coroutine is still active. `flush()`,
    `utilisation()`, `complete()` and `release()` keep working.
  - A waiter's own cancellation is thrown as `CancellationException`; its reservation is refunded and the
    waiters behind it are re-planned.
  - `complete()` and `release()` are idempotent, run under `NonCancellable` and take effect even when called
    from a cancelled coroutine.
  - First come first served within each lane; `maxQueued` per lane; a wait above `maxWait` fails fast; a
    re-plan after a cancellation, a release or a `complete` that changed something (none after a `complete`
    with no signals); a waiter whose new wait exceeds its cap fails at once with `BudgetExhausted`.
  - Events are delivered outside the lock; an observer's exception is caught and logged.
  - Write-ahead: a `hard` window's count is saved before the grant that would exceed the persisted mark;
    a save is bounded by `storeTimeout`, and its failure is logged and ignored; no save is made before a
    restore succeeds; a failed restore is retried by a later `acquire` at a bounded rate.
  - Common code compiled for `js`: kotlinx-coroutines-core 1.10.2 and the Kotlin 2.2.0 common standard
    library only; no atomicfu and no experimental API in production code.
  - Level 2 is deterministic on `VirtualClock` under `runTest`: every wait is virtual, and a test asserts on
    a call's completion state without joining it.
- **Evidence that settles it.** kotlinx-coroutines 1.10.2's source and KDoc (`Mutex` and `MutexImpl`,
  `SemaphoreImpl`, `CompletableDeferred`, `JobSupport`, `NonCancellable`, `withTimeout` and
  `withTimeoutOrNull`, `select` and `onTimeout`, `Channel`, `MutableStateFlow`) and `kotlinx-coroutines-test`
  1.10.2 (`TestCoroutineScheduler`, `StandardTestDispatcher`, `runTest`); the Kotlin 2.2.0 standard
  library's `kotlin.concurrent`; comparable common-code limiters and pools that end their waiters when they
  close (Kord's `rest` rate limiters, kotlinx-coroutines' own `Semaphore`, Ktor's channels and pools,
  Arrow's `CircuitBreaker`, resilience4j-kotlin's rate limiter).
- **Findings file:** [`waits-and-close.md`](waits-and-close.md).

### 2. Which inputs should `RetryAfter.parse` accept, and how does a `jvmTest` differential against `java.time` compare it without false mismatches?

- **Options and what each must establish.**
  - **The grammar:** RFC 9110's exact grammar (`delay-seconds` as `1*DIGIT`; IMF-fixdate; the obsolete
    RFC 850 and asctime forms), or a lenient set such as other clients accept (OkHttp's browser-compatible
    formats, Ktor's GMT date parser, Go's `http.ParseTime`, curl's `parsedate`, urllib3's
    `parse_retry_after`). For each case: the letter case of day names, month names and `GMT`; surrounding
    whitespace; leading zeros; a sign or a fraction; an out-of-range field (`Feb 31`, hour 25); a leap
    second (`23:59:60`); a day name that does not match the date.
  - **Numbers:** a `delay-seconds` too large for a `Duration` or a `Long` (saturate, or fail); zero.
  - **Two-digit years:** the RFC 850 rule (a year that appears more than 50 years in the future is the most
    recent past year with the same last two digits), and the reading it is applied against: the call's
    `now`, or the response's own `Date`.
  - **Past dates and the server's `Date`:** a date before `now` gives zero; with `serverDate` the delay is
    the difference of two server readings rather than a server reading minus the local clock; what a
    malformed or missing `Date` changes.
  - **The differential oracle:** which `java.time` formatter or parser matches each form exactly
    (`DateTimeFormatter.RFC_1123_DATE_TIME` accepts inputs the RFC grammar does not), the input generator
    (random instants over a range for each form, the RFC's own examples, malformed inputs) and the
    comparison (both accept and agree, or both reject).
  - **The technique in common Kotlin:** civil date to epoch day with no `java.*` (Hinnant's
    `days_from_civil`), and the tables the Level 4a test holds.
- **Requirements.**
  - Common Kotlin with no `java.*`; text from outside the process returns `Result`, and a failure's message
    is constant and never echoes the header value.
  - The result is never a negative `Duration` (`BudgetExhausted` and `RateLimited` refuse one); a date in
    the past gives `Duration.ZERO`.
  - The two overloads `parse(value, nowEpochMillis)` and `parse(value, nowEpochMillis,
    serverDateEpochMillis)`, both taking epoch milliseconds; `RateSignalsReader.Standard` reads the
    response's `Date` with the same date grammar.
  - The pause a parsed value causes is capped elsewhere, by `maxPause`.
  - A Level 4a table (the three forms, the two-digit-year rule, the leap second, the rejections) and a
    `jvmTest` differential against `java.time`.
- **Evidence that settles it.** RFC 9110 §5.6.7 (dates), §10.2.3 (`Retry-After`) and §6.6.1 (`Date`), and
  RFC 5322 §3.3, where IMF-fixdate's form comes from; the `java.time` Javadoc of `DateTimeFormatter`; the
  parsers named above at named tags; Howard Hinnant's date algorithms.
- **Findings file:** [`retry-after-parsing.md`](retry-after-parsing.md).

### 3. How should the ledger combine the monotonic `TimeSource` and the `WallClock`, so that a wall-clock step never under-counts a window and every wait stays virtual under `VirtualClock`?

- **Options and what each must establish.**
  - **The clock model:** the wall clock read at every operation and used as read; wall readings clamped so
    that the ledger's "now" never goes backwards; one wall-clock anchor taken at construction and advanced
    by the monotonic source; a hybrid (the monotonic source for durations and waits, the wall clock for
    window indices, re-anchored when the two disagree by more than a threshold).
  - **For each model:** where the window indices, the planned slots, the pause's end (persisted as epoch
    milliseconds), the waits (the `delay` durations), the snapshot's stamp and `maxRestoreSkew`, a grant's
    measured wait and a window's elapsed fraction come from; what a backward step does (an NTP step, a
    manual change, a virtual machine restored from a snapshot), what a forward jump does (a host resumed
    from sleep: windows roll, a pause may end), and what a slow slew does; whether a window already charged
    can be charged again from zero after a backward step; how far the ledger's windows may drift from the
    provider's.
- **Requirements.**
  - Windows are aligned to multiples of their period since the Unix epoch (UTC), so that they meet the
    provider's own wall-clock boundaries; the boundary guard absorbs skew between the two clocks.
  - A `hard` window never under-counts the provider's day, across a wall-clock step as across a restart.
  - A pause's end and the snapshot's stamp are epoch milliseconds, because they outlive the process.
  - Under `VirtualClock`, the monotonic source, the wall clock and `delay` move together; a wait never needs
    real time.
  - `WallClock` may step backwards; `TimeSource.Monotonic` does not; the budget never reads the system clock
    directly; common code only.
- **Evidence that settles it.** The Kotlin standard library's `TimeSource`, `TimeMark` and
  `TimeSource.Monotonic` KDoc; the JVM's `System.currentTimeMillis()` and `System.nanoTime()` Javadoc;
  `clock_gettime(2)` (`CLOCK_REALTIME`, `CLOCK_MONOTONIC`, and what happens across a suspend); NTP stepping
  and slewing in chrony and ntpd; the Windows time service; the clock each comparable limiter uses and why
  (Envoy's `ratelimit`, Resilience4j's `AtomicRateLimiter`, Guava's `RateLimiter`, Bucket4j's `TimeMeter`,
  Kord's `IntervalRateLimiter`, JDA's rate limiter).
- **Findings file:** [`ledger-time.md`](ledger-time.md).

## Verification

### Outside-world claims

Every load-bearing claim of the three findings files here, and the claims of the stack-level
[`../../research/call-budget.md`](../../research/call-budget.md) that this unit relies on, was re-opened at its
source on 2026-11-03.

**76 claims: 75 hold, 1 fails, none is unclear.**

| Findings file | Claims | Result |
|---|---|---|
| [`waits-and-close.md`](waits-and-close.md) | 17 (1 to 17) | all hold |
| [`retry-after-parsing.md`](retry-after-parsing.md) | 25 (1 to 25) | 24 hold; 13 fails as cited |
| [`ledger-time.md`](ledger-time.md) | 19 (1 to 19) | all hold |
| [`../../research/call-budget.md`](../../research/call-budget.md) | 15 (1, 2, 7, 9, 10, 11, 12, 13, 14, 21, 23, 24, 25, 26 and 28) | all hold |

**The failing claim.** `retry-after-parsing.md` claim 13 cites Go's `src/net/http/http.go` at `go1.25.0` for
`ParseTime`, and that file does not contain it. The quoted text is verbatim in `src/net/http/header.go` at the
same tag (lines 126 to 129), so the fact holds at that path and only the citation is wrong. No recommendation
rests on the path: the claim is one of six comparable parsers in that file's section 4.

**Notes on claims that hold:**

- `retry-after-parsing.md` claim 24: the `Duration` source caps a converted value at `MAX_MILLIS`, which is
  `Long.MAX_VALUE / 2`; it is `Duration.INFINITE.inWholeMilliseconds` that reads as `Long.MAX_VALUE`, as the
  body of that file says.
- `waits-and-close.md` claim 11: the KDoc of `invokeOnCompletion` says a handler's exception is wrapped and
  rethrown; that a `Job` which is not a coroutine rethrows it to its caller is stated in `JobSupport.kt` at
  1.10.2.
- `waits-and-close.md` claims 15 and 17 state readings of code. The quoted code is present at Kord 0.17.0 and
  Arrow 2.3.0. That Kord's REST bucket leaks its lock on cancellation was read, not executed; Arrow's
  `CircuitBreaker` queues no requests (it has an `awaitClose()` close notification).
- `call-budget.md` claim 23: GitHub's page reads "If the `retry-after` response header is present"; the quote
  drops "response", which does not change its meaning. Claim 12: OpenDota's OpenAPI document now reports
  version 31.1.1 (31.1.0 when recorded) with the same text, and the live headers still read 59 and 2999.
- **Nothing newer changes an answer at the pinned versions.** kotlinx-coroutines 1.11.0 (2026-05-08) exists and
  rewrites some KDoc; the module pins 1.10.2. Kord 0.18.1 and Arrow 2.3.0.1 exist; the claims are judged at the
  cited tags. RFC 9110's only erratum on §5.6.7 (9164, reported) concerns the Appendix A form of `date3`, not
  the §5.6.7 grammar quoted.

**Not re-opened.** Claims 3, 4, 5, 6, 8, 15, 16, 17, 18, 19, 20, 22 and 27 of the stack-level file are not
relied on here: 4, 5, 6 and 8 support the library-owned `WallClock` over `kotlin.time.Clock`, which is built;
3 (the IETF rate-limit header draft) stands behind a non-goal; 15 (dynamic rate rules) behind a known gap; 16
to 20, 22 and 27 behind the alternatives the architecture rejected.

### Codebase facts

The repository facts the findings rest on were checked against `master` at `8122af7`:

- `LoaderRunner.kt:26` declares `runLoader`, and `:38` is the `ensureActive()` that tells the runner's own
  cancellation from a loader's.
- `CaffeineResponseCache.kt:84-85` take one mark on the injected `TimeSource` for the ticker; `:123` checks the
  caller's cancellation first; `:166-171` are `awaitEntry`'s guard; `:259` is the non-suspending `close()`;
  `:295` documents that ages are clamped at zero.
- `CallBridge.kt:61` settles a continuation exactly once.
- `VirtualClock.kt:94`: the fixtures' wall clock is the start plus the virtual elapsed time, so it never steps.
- `HttpSyntax.trimOws` (`HttpSyntax.kt:23`) and `HttpSyntax.asciiLower` (`:36`) exist for a date parser to use;
  `UrlProblem.kt` is the precedent for an `internal` problem enum with constant messages inside an `internal`
  `IllegalArgumentException` subclass.

## Summary

### Waiting, re-planning, refunds and `close()`

- **The wait.** Each waiter holds a conflated `Channel<Unit>` as its wake signal and waits in
  `withTimeoutOrNull(remaining) { select { closing.onJoin { }; wake.onReceive { } } }`. After every wake or
  timeout it re-reads its plan under the ledger's `Mutex`, so a lost, stale or double wake costs one extra lock
  section and nothing else. A re-plan signals only a waiter that moved earlier or failed; a waiter that moved
  later wakes at its old time, re-reads and sleeps again. Every piece is stable in kotlinx-coroutines 1.10.2;
  `select`'s `onTimeout` clause is experimental and is not used
  ([`waits-and-close.md`](waits-and-close.md) F2; claims 5 to 8).
- **`close()`** is `closing.complete()` on a budget-owned `Job()` used as a latch, completed normally and never
  cancelled: thread-safe, idempotent, one caller wins (and logs), no lock taken and none of the budget's code
  run on the closer's thread. `closing.isCompleted` is the closed flag, so no atomic and no `@Volatile` is
  needed. A waiter sees the latch through `onJoin`, leaves through the lock and returns
  `Result.failure(HttpFailure.Closed("budget"))` as a value; a call parked on the lock, or between its lock
  section and its wait, also returns `Closed`. No `CancellationException` is involved, so nothing reaches the
  response cache's loader as an `IllegalStateException` (F3; claims 4 and 5).
- **The grant.** A waiter grants itself inside its own lock section, after
  `currentCoroutineContext().ensureActive()` and before any mutation, so the budget owns no scope, ticker or
  dispatcher. A cancellation that lands after that check leaves the caller holding a permit it must complete
  or release, which `withPermit`'s `finally` does (F5;
  [`../../research/call-budget.md`](../../research/call-budget.md) F4 and F8).
- **Refunds.** A `try/finally` around the wait loop runs
  `withContext(NonCancellable) { lock { if still waiting: unlink, re-plan } }`. A contended `Mutex.lock` inside
  `NonCancellable` waits for the holder rather than throwing; every holder is bounded by `storeTimeout`.
  `complete()` and `release()` run in the same shell, with their idempotency kept under the lock (F4; claims 1
  to 3; measured).
- **Under the lock, and after it.** Arithmetic and the store calls run under the lock: the restore's `load()`
  and the `save()` of the write-ahead and of `flush()`, each bounded by `withTimeoutOrNull(storeTimeout)` and
  each placed before any mutation of its section. A store call that timed out is a failure, so a timed-out save
  never advances the persisted mark, and a cancellation the store leaks while the caller is active becomes a
  store failure through the `ensureActive()` idiom of `runLoader`. Events are collected during a section and
  emitted after the unlock, so an observer that calls `utilisation()` cannot deadlock the non-reentrant
  `Mutex`. The observer call catches `Exception`, a `CancellationException` included because `onEvent` is not a
  suspend function, and lets an `Error` through. Events of one call arrive in order; events of concurrent calls
  may interleave, and each event describes itself (F6 and F7; claims 1, 2 and 9).
- **A store that fails.** The restore's state is one of: not attempted, restored, or failed at a monotonic
  mark. An `acquire` retries a failed restore only once `storeRetryInterval` has passed since the last attempt,
  single-flight under the lock; `utilisation()` and `flush()` make the first attempt, and `flush()` retries
  unconditionally and returns the failure instead of saving while no restore has succeeded. A failed
  write-ahead save defers the next save attempt by the same interval, so a store that is down costs one
  `storeTimeout` per interval rather than one per grant; the in-process stop holds meanwhile.
  `storeRetryInterval` (30 s) is a builder property beside `storeTimeout`, because every policy number of a
  supplied default is one (F9).
- **A blocking store** cannot be interrupted by `withTimeoutOrNull` (cancellation is cooperative), so
  `BudgetStore`'s KDoc requires a cancellable implementation (F6; claim 9).
- **Common code.** `Mutex`, `Job`, `Channel`, `select`, `withTimeoutOrNull` and `NonCancellable` are all in
  kotlinx-coroutines' common sources; `kotlin.concurrent.atomics` needs an error-level opt-in on Kotlin 2.2.0
  and is not used (F7; claim 12).
- **Options that do not fit.** A plain `delay` cannot move a waiter earlier, so a cancelled waiter's slot is
  wasted. A per-wake `CompletableDeferred` is one-shot and, to close, would be cancelled, throwing a
  `CancellationException` into an active coroutine. `Mutex.tryLock` in `close()` fails whenever a store call
  holds the lock. A ticker in a budget-owned scope contradicts a budget that owns no scope. A budget-wide
  `MutableStateFlow` version works but wakes every waiter on every re-plan. Kord's REST buckets hold a lock
  across a cancellable wait; resilience4j-kotlin reserves and then `delay`s with no refund or re-plan; Ktor's
  CIO engine documents the `withTimeout` result-drop race the store calls guard against (section 4; claims 14
  to 16).
- **Measured** on a throwaway one-window stand-in (Kotlin 2.2.0, kotlinx-coroutines 1.10.2, `runTest`): 33 of
  33 checks on five runs, among them the exact re-planned grant times, a pause moving a waiter later, `close()`
  ending every waiter with a value, a store timeout that is virtual and exact inside `withLock`, and cleanup
  from a cancelled coroutine (F10).
- **Gaps.** `select` with `onJoin` was not compiled for `js`; the build's `js` compile of the common code
  establishes it. The cost per wake at `maxQueued` scale is not benchmarked; a Level 4c smoke test with real
  threads bounds it.

### `Retry-After` and HTTP dates

- **One parser.** An `internal` `HttpDate` parser in `commonMain` serves `RetryAfter.parse` and the `Date`
  reading of `RateSignalsReader.Standard` ([`retry-after-parsing.md`](retry-after-parsing.md) §6).
- **The grammar** is RFC 9110's three forms exactly (IMF-fixdate, the obsolete RFC 850 form and asctime;
  `delay-seconds` as `1*DIGIT`), with three liberties: day names, month names and `GMT` match in any ASCII
  case; SP and HTAB around the whole value are trimmed; a weekday that disagrees with the date is ignored.
  Rejected: other zones (`UTC`, `+0000`), unpadded days (asctime takes `Nov 06` or `Nov  6`, never `Nov 6`), a
  four-digit year in the RFC 850 form, signs, fractions, units and non-ASCII digits. Second 60 is accepted and
  read as the next second (F1 to F4; claims 1 to 5).
- **Numbers.** `delay-seconds` accepts leading zeros and zero itself, and saturates at 253,402,300,799 s (the
  instant 9999-12-31T23:59:59Z in seconds, the largest delay a date can express): a huge value is a very long
  wait, not a missing hint, and `now + delay` never overflows. `maxPause` still caps the pause (§6.2; claim 24).
- **Dates.** The delay is `max(0, date - reference)` in whole milliseconds, so a date in the past gives
  `Duration.ZERO`. The reference is `now` in `parse(value, now)` and `serverDate` in
  `parse(value, now, serverDate)`, so a skewed local clock cancels out of a pair of server readings. `Standard`
  passes the response's `Date` when it parses and otherwise calls the two-argument overload, which is RFC 9110
  §6.6.1's "use the time received" (F9; claim 7).
- **Two-digit years** (the RFC 850 form only): `year = limit - (limit - yy).mod(100)` with
  `limit = clamp(yearOf(reference), 50, 9949) + 50`, the reference being the clock the delay is measured
  against. The rule is year-granular, at most one year from the RFC's wording at the single boundary year, and
  equals `java.time`'s reduced-year window. Mature clients use fixed pivots (Go 69, curl 70, CPython 68, RFC 5322
  §4.3 50); the reference-relative rule follows RFC 9110 instead (F6 to F8; claims 6, 9, 14, 16, 18 and 20).
- **Several `Retry-After` lines.** `Standard` parses every line of `getAll("Retry-After")` and takes the longest
  wait among those that parse, as the budget pauses until the maximum of every hint; a folded `120, 60` is
  malformed and ignored. `Date` is read with `get` (§6.9).
- **Failures** are an `internal` enum (`Empty`, `Format`, `Range`) with constant messages that never echo the
  value, returned inside an `internal` `IllegalArgumentException` subclass, as `UrlProblem` does (§6.5).
- **The differential.** The `java.time` oracle is one strict, locale-free `DateTimeFormatterBuilder` per form
  (explicit name maps, `appendValue(YEAR, 4)`, `padNext(2)` for the asctime day, `parseCaseInsensitive()`, the
  RFC 850 year as `appendValueReduced(YEAR, 2, 2, base)` with a base of at least 1), used both as printer and as
  strict parser. `RFC_1123_DATE_TIME` is not used (it accepts hour 24, a missing weekday, one-digit days,
  offsets and no seconds), nor `yyyy` (no year 0), nor `Locale`-dependent names (`Locale.UK` prints `Sept`). The
  liberties are normalised out of the oracle's input rather than excepted in the comparison. Measured on a
  line-for-line Java mirror of the sketch (JDK 23.0.2): 0 mismatches in about 2 million checks per seed over
  three seeds (F10 and F12; claims 19 to 22).
- **Where sources disagree.** RFC 9110 calls HTTP-date case sensitive; RFC 9111 §4.2 tells a cache to match
  case-insensitively, RFC 9110 encourages robust parsing of timestamps, and every mature parser read ignores
  case. Case-insensitive matching holds, because refusing a usable `Retry-After` for its letter case drops a
  hint the provider meant (F2; claims 4 and 5). RFC 5322 requires the weekday to match the date; Go, curl and
  Ktor ignore it, and the instant never depends on it, so a mismatch is ignored (F3; claim 8).
- **Options that do not fit.** OkHttp 5.5.0's own `Retry-After` reader ignores dates and is unbounded on
  digits, and its date fallbacks accept trailing text (claims 10 and 11). Extra shapes (`UTC` or offset zones,
  a four-digit RFC 850 year) have no provider that sends them and stay additive.
- **Gaps.** The Kotlin sketch was not compiled; its Java mirror is what was measured. The
  `appendValueReduced` behaviour was measured on JDK 23 only; the differential runs on the build JDK.

### The ledger's time model

- **Wait on the monotonic source, decide on the wall clock.** The wall reading decides where in the provider's
  calendar now is: window indices, starts and ends, the guard test, the snapshot's stamp, the
  `maxRestoreSkew` check and a window's elapsed fraction. The monotonic `TimeSource` measures every duration:
  each `delay`, the `maxWait` cap (a deadline on the monotonic axis), a grant's measured wait, the in-process
  end of a pause, `storeTimeout` and `storeRetryInterval`. Both are read together once per lock section into
  one `internal` value, and the elapsed reading is made non-decreasing, because an injected `TimeSource` need
  not be monotonic ([`ledger-time.md`](ledger-time.md) §6.1 and §6.2; claims 10 and 11).
- **No anchor and no clamp.** An anchor (the wall clock read once and advanced by the monotonic source) drifts
  at the oscillator's error, 4.3 s a day at 50 ppm, which spends the 1 s guard in under six hours, and it stops
  across a suspend on Linux and macOS. A clamp (`max(wall, previous)`) freezes the ledger for as long as the
  clock was stepped back, so a large backward step becomes a hang (F1, F2, F5 and F8; claims 1 to 6).
- **Cells by index.** Each window's counts live in cells keyed by window index: kept, never reset by a clock
  reading, pruned only below the previous index as the clock moves forward, and bounded in number. Resetting a
  cell on an index change overspent by up to two limits in the model (F8).
- **Pauses** carry two deadlines, an elapsed one for the process and an epoch one for the snapshot, and end
  when either passes: a backward step never lengthens a pause, and a resume from sleep ends it. A restored
  pause converts once to the monotonic axis, capped by `maxPause` (§6.2 and §6.3).
- **A forward jump is trusted.** The wall clock is the only reading that tracks the provider's boundary, and
  common code cannot tell a resume from sleep from a clock set forward by mistake (F2; claim 5).
- **Waits are sliced.** A waiter wakes at least once a minute (an `internal` cadence, not a policy a consumer
  tunes), so a step, a jump or a suspend takes effect within a minute rather than after a remaining wait of
  hours (§7.3).
- **A reservation names its window index.** The boundary it waits for derives from that index, and every wake
  recomputes the remaining wait from fresh readings. Both findings files have every wake re-read the plan under
  the lock; where `waits-and-close.md`'s sketch keeps a monotonic slot mark, the index holds, because a slot
  fixed on one clock is early or late by any step of the other (`ledger-time.md` section 5).
- **Tests.** The published `VirtualClock.wallClock` cannot step, so the module's own `jvmTest` support gains a
  `SteppableWallClock` (the virtual wall clock plus a step offset and a constant slew), implementing `WallClock`
  with its own `@OptIn`; it is not published (§6.6).
- **Comparable limiters** pick one clock: Resilience4j, Guava, Bucket4j by default and Kord by default count
  monotonic time; Envoy's `ratelimit` and JDA count wall time. None documents its behaviour across a step, and
  none folds or re-anchors (section 4; claims 14 to 19).
- **Options that do not fit:** resetting cells on an index change, an anchor and a clamp (above). A step
  threshold to tell a slew from a step would be a host-dependent number with two code paths; the index-based
  rule needs none.
- **Known limit.** A clock set forward by mistake and left uncorrected, or a clock behind at start and stepped
  forward after grants were made, can under-count the provider's day; no reading in common code tells it from
  a resume from sleep. Comparing the server's `Date` with the wall reading would detect it, and is outside this
  unit (§7.6 and §8). The `js` monotonic source was not read.

### The restore's merge

A window of the snapshot below the current index is dropped. At the current index, the stored count and the
grants made since the start are added. The sum equals the architecture's `max(local, saved)` when nothing was
granted before the restore, and it is what keeps a late restore from under-counting after a store outage. A
count that a server reading raised is kept as a floor, so the merge is `max(saved + grantsSinceStart,
reconciled)`.

### Open trade-offs

Two choices remain open; each has a recommendation.

1. **A backward wall-clock step across a window boundary**, when the index falls below the highest index the
   ledger has charged.
   - (a) **Copy.** The current cell gains the sum of the cells above it, capped at the limit, and the higher
     cells stay. The provider's day is never under-counted, whichever reading was wrong. When the step corrected
     a clock that ran ahead, the window the early roll consumed stays refused for its index, so up to one
     period of calls the provider would have allowed is refused. It is the only variant with no overspend after
     both a one-hour and a one-year step in the model (`ledger-time.md` F8 and §7.1).
   - (b) **Move.** The counts move down and the higher cells are emptied. Nothing is refused wrongly after a
     correction; when the step was the error (the clock was right and is now behind), up to one limit is
     under-counted.
   - (c) **Keep only.** Cells stay at their own indices and the lower index counts from its own cell. It is the
     simplest; a large backward step grants a fresh window, an overspend of up to one limit (the model's
     one-year case).
   - Recommendation: (a), for a window that guards a bill; its cost is bounded by one period.
2. **A snapshot stamped further ahead than `maxRestoreSkew`.**
   - (a) **Ignore it entirely**, logged and reported in `Restored`. When the clock at start is behind (a host
     that starts from its hardware clock before it synchronises), the stored day's count is lost and the bill
     is under-counted.
   - (b) **Ignore its stamp and its pause, keep its window counts** as cells at their own indices (a future index
     is read only once the wall clock reaches it), still reported as ignored for skew in `Restored`. It keeps
     the restart guarantee when the clock at start is behind; a snapshot written by a clock that ran far ahead
     leaves a future cell that can over-count one far-off window, bounded by the cell cap.
   - Recommendation: (b), the only option under which a restart never under-counts when the clock at start is
     behind (`ledger-time.md` §6.4 and §7.2; claim 9).

### Codebase blast radius

Surveyed directly against `master` at `8122af7`:

- **No caller.** Nothing in the repository calls a budget; the first caller is the call facades (#68). The ABI
  dump `webtools-http/api/webtools-http.api` has no budget type beyond `HttpFailure.BudgetExhausted`, and no
  name the unit takes is in use.
- **New files** in `webtools-http`: the budget's types in `commonMain`; `RecordingBudgetObserver` and
  `FakeBudgetStore` in `src/jvmTestFixtures/.../testing/` (names reserved for this unit; each implementer
  carries its own `@OptIn`; no new fixtures dependency); the tests by level in `commonTest` and `jvmTest`, with
  `SteppableWallClock` and a call-launching helper for the budget in `jvmTest`'s `testing/support/` (the
  existing `Waiter.kt` is typed to the cache's `getOrLoad`).
- **Changed files:** the KDoc list of gated ports in `ExperimentalForInheritanceWebToolsApi.kt` and of builders
  in `WebToolsDsl.kt`; the builder list of `WebToolsDslComponentTest`; the KDoc of
  `VirtualClockThreeClocksComponentTest`, which names the cache's three-clocks proof and gains the budget's; the
  regenerated ABI dump; `README.md`'s `webtools-http` row; `CHANGELOG.md`'s `[Unreleased]` section.
- **Unchanged:** `webtools-http/build.gradle.kts` (kotlinx-coroutines-core 1.10.2 is already the `commonMain`
  `api` dependency, and the POM description already names a call budget), `webtools-http-commoncheck` (no new
  `expect` declaration; it compiles the new common code for `js`), the other modules and `build-logic`.
- **Build cost:** a change under `jvmTestFixtures` re-runs the fixtures-publication check in `webtools-http`'s
  `check`, 44 to 52 s ([`../../consequences.md`](../../consequences.md) §2).
- **Baseline tests** at `8122af7`, as the response cache's run of record measured them: gating 9 classes and
  23 tests; component 81 and 519; integration 25 and 98; deterministic (4a) 46 and 214; non-functional (4c) 19
  and 35 ([`../../66-response-cache/final-implementation.md`](../../66-response-cache/final-implementation.md)
  §1).
