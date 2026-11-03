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
