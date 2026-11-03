# Research: the response cache port and its Caffeine default (`response-cache`)

[SpartanLaboratories/WebTools#66](https://github.com/SpartanLaboratories/WebTools/issues/66), unit 7
(`response-cache`) of the HTTP client stack,
[#55](https://github.com/SpartanLaboratories/WebTools/issues/55). The systems-level design is
[`../../architecture.md`](../../architecture.md) (§6.4, with §6.2, §6.6, §6.9, §7.2, §7.4, §9, §12 and §11
unit 7) and the systems-level consequences [`../../consequences.md`](../../consequences.md) (§7 and §8).
This folder researches the implementation details that design leaves to the unit and that need evidence
from outside this repository.

## The work

`webtools-http` gains its response cache:

- **Public, in `commonMain`** (package `com.spartanlabs.webtools.http`):
  - the port `ResponseCache : AutoCloseable`, which a consumer implements only with
    `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`: one suspend
    `getOrLoad(key, policy, maxAge, loader): Result<Lookup<V>>`, an overload without `maxAge` that accepts
    any live entry, `invalidate(key)`, `invalidateAll()`, `stats()` and `close()`;
  - the values it speaks: `CacheKey<V>(endpointId, params)` (phantom-typed; equal over the id and the
    parameters sorted by name with a stable sort; its `toString` prints parameter names only),
    `CachePolicy(ttl) { missTtl }` (a factory with a builder; `missTtl` 60 s by default), the sealed
    `Lookup` (`Found(value, weight)` or `Missing`) and `CacheStats` (hits, misses, evictions and failed
    loads, a call that joins a load in flight counting as a hit);
  - `ResponseCache.Disabled`, a pass-through that stores nothing and shares nothing, the only
    implementation constructible in common code.
- **Public, in `jvmMain`:** `CaffeineResponseCache`, final, built by a factory with a builder over
  `timeSource` (`TimeSource.Monotonic`), `maxWeight` (1,000; weight 1 per entry unless the loader reports
  one), `loadDispatcher` (`Dispatchers.Default`) and `maintenanceExecutor` (the common pool), on a
  Caffeine 3.3.1 `AsyncCache` with a variable `Expiry`, a `Ticker` derived from one mark on the injected
  `TimeSource`, `maximumWeight` with a weigher, `recordStats()` and no `Scheduler`.
- **How the default behaves:**
  - one load per key at a time, started by the cache in a `CoroutineScope(SupervisorJob() + loadDispatcher)`
    it owns, after Caffeine's mapping function has returned;
  - every waiter awaits the shared future through `asDeferred().await()`, so one waiter's cancellation
    touches neither the load nor the other waiters, and a load nobody waits for any more still completes
    and is stored;
  - a failure completes the future normally, as a value with zero lifetime that each waiter removes by
    identity before it returns, so Caffeine logs nothing and no later call joins the failed load;
  - `maxAge` is an age check on read (an entry whose age is at most `maxAge` is served) with a
    compare-and-remove, so N callers on one stale entry cost one load;
  - `close()` cancels the scope, every waiter returns `HttpFailure.Closed("cache")`, and so does every
    later call.
- **Tests:** Level 2 on the published `VirtualClock` (hits, expiry, the miss TTL, `maxAge`, coalescing,
  one waiter's cancellation, close, and the cache ticker, `delay` and the wall clock moving together);
  Level 4a for the key's canonical form; Level 4c for the size bound and a 2,000-retry check that no retry
  receives a previous failure.

What shapes the questions below:

- **Failures and cancellation.** Expected failures travel in `kotlin.Result`, and nothing returns null. A
  loader's `Exception` becomes the call's failure and is never stored; an `Error` is rethrown to every
  waiter. A waiter's own cancellation is thrown as `CancellationException`, and no `CancellationException`
  may reach a waiter that is still active, because it would look like that waiter's own cancellation.
  `close()` is non-suspending, idempotent and never throws.
- **Time.** One injected `TimeSource` drives the cache's ticker and the entries' ages; under `runTest` the
  published `VirtualClock` supplies it, together with a `StandardTestDispatcher` for the load scope and a
  same-thread maintenance executor, so Level 2 runs on virtual time with no network.
- **The bound.** The cache is always bounded: `maxWeight` limits what is stored, whatever a loader reports.
- **Statistics.** `stats()` is what the call facades (#68) expose as `cacheStats()`; its counts must mean
  the same thing on every run, whatever Caffeine's maintenance timing.
- **Platform.** Java 11 bytecode and API; Caffeine 3.3.1 and kotlinx-coroutines 1.10.2 as the module pins
  them; no Caffeine or coroutine-internal type in a public signature beyond `CoroutineDispatcher`.

## Existing findings this research builds on

These are cited, not researched again. Their versions are the ones the module pins today (Caffeine 3.3.1,
kotlinx-coroutines 1.10.2, Kotlin 2.2.0); their load-bearing claims are re-opened in the verification
below.

- **[`../../research/response-cache.md`](../../research/response-cache.md).** Single-flight through
  `AsyncCache.get`, and an `Expiry` and weigher that see the value only once the future completes, so a TTL
  runs from completion (F1); why a failure is a value with zero lifetime removed by each waiter (F2: 45 of
  2,000 immediate retries joined the failed future without the removal, 0 with it); `asDeferred().await()`
  against `CompletionStage.await()` (F3); run-to-completion when every waiter leaves (F4); the conversion of
  a cancelled scope into `Closed` (F5); starting the load outside the mapping function (F6); `maxAge` as an
  age check with a compare-and-remove (F7: 1 load for 50 stale callers, against 10 to 12 for
  invalidate-then-get); the `Ticker` from one `TimeSource` mark (F8); the executor, the absent `Scheduler`,
  the eventual size bound and Caffeine's own hit counting (F9); decoded values (F10); misses as values
  (F11); keys (F12); the common port (F13); logging (F14). Its measured results are probe output with no
  source to re-open.
- **[`../../research/test-support-and-time.md`](../../research/test-support-and-time.md).** One virtual
  clock for the cache ticker, the budget and `delay`, and why faking one clock of three passes for the
  wrong reason (F1, F2).
- **As built, `VirtualClock`** (`webtools-http/src/jvmTestFixtures/.../testing/VirtualClock.kt`, #63):
  `timeSource` is the scheduler's `TimeSource.WithComparableMarks`, `dispatcher` a `StandardTestDispatcher`
  on the same scheduler, `maintenanceExecutor` runs each task inline, and `advanceBy` advances the scheduler
  and runs what became due. `VirtualClockThreeClocksComponentTest` proves the three clocks together with a
  plain Caffeine cache standing in for this unit's.

The codebase blast radius is small and is surveyed directly in the summary below: the unit adds new files to
`webtools-http`, extends its ABI dump and two KDoc lists, and has no caller in the repository yet.

## Questions

### 1. Which Caffeine 3.3.1 behaviours do the default's bound, statistics, invalidation and close rest on, for an async cache whose futures the cache creates and completes itself?

- **Options and what each must establish.**
  - **What `CacheStats.evictions` counts, and from where.** Caffeine's `stats().evictionCount()` under
    `recordStats()`: which removal causes it counts (a size eviction, an expiry, a zero-lifetime entry
    found expired, an explicit removal), and when it is incremented relative to maintenance. An
    `evictionListener` or `removalListener` that counts `RemovalCause.SIZE` only: its cost, the thread it
    runs on, and whether it sees an async cache's entries. Whether `recordStats()` still earns its cost when
    the cache counts its own hits, misses and failed loads.
  - **The bounds of a reported weight.** What Caffeine does with an entry of weight 0 (whether size
    eviction ever selects it), with a weight above `maximumWeight`, and with the weigher's `int` range;
    what an in-flight future weighs.
  - **`invalidate` and `invalidateAll` against a future still in flight.** Whether the future leaves the
    map; whether its later completion puts it back (the `replace` in `LocalAsyncCache.handleCompletion`);
    what the callers already waiting on it receive; whether a caller after the invalidation starts a second
    load.
  - **What `close()` must do on Caffeine's side.** Whether the cache drops its entries itself
    (`invalidateAll`, `cleanUp`), and whether anything in Caffeine holds a thread or another resource once
    no `Scheduler` is configured and the executor is not the cache's own.
  - **The mapping function.** `AsyncCache.get(key, BiFunction)` whose function returns a
    `CompletableFuture` it has just created and not completed: whether the executor is used, how long the
    per-key lock is held, and what happens if the function throws.
  - **`Expiry` on the async path.** Which `Expiry` method runs when the cache's own future completes, with
    which `currentDuration`; what a returned duration of 0 does there (the zero-lifetime entries of failures
    and of a `ttl` of zero); what `Duration.INFINITE` becomes.
  - **Observing the bound and the failure path in tests.** Deterministic eviction under a same-thread
    executor (`cleanUp()`, `estimatedSize()`, `policy().eviction().get().weightedSize()`); how a test on
    JDK 11 to 23 observes Caffeine's `System.Logger` output, so that it can assert that a failed load logs
    nothing; how the 2,000-retry race is provoked reliably on real threads, and how long it runs.
- **Requirements.**
  - The bound holds for every stored entry: nothing a loader reports can make storage unbounded.
  - Failures are never stored, and a failed load produces no Caffeine warning.
  - After `invalidate(key)`, the next call for that key does not receive anything loaded before it.
  - Each statistic counts one well-defined event, the same on every run, for a consumer tuning `maxWeight`
    and the TTLs; `evictions` counts entries the size bound removed.
  - Java 11 API; Caffeine 3.3.1; no Caffeine type in a public signature; no `Scheduler`.
  - Level 2 is deterministic on the virtual clock with the same-thread executor.
- **Evidence that settles it.** Caffeine 3.3.1's source at tag `v3.3.1` (`Caffeine`, `AsyncCache`,
  `LocalAsyncCache`, `Async`, `BoundedLocalCache`, `RemovalCause`, `StatsCounter`, `CacheStats`), its
  Javadoc and its wiki pages on eviction, removal and statistics, and its own tests; the statistics
  vocabulary of comparable caches (Guava's `CacheStats`, Micrometer's cache metrics). Behaviour that only
  a run would show is left to the unit's own tests, which the findings name.
- **Findings file:** [`caffeine-mechanics.md`](caffeine-mechanics.md).

### 2. How should a load's coroutine start, end and report under kotlinx-coroutines 1.10.2, in production and under `runTest`, so that every waiter receives exactly one outcome?

- **Options and what each must establish.**
  - **How the load's coroutine is started.** `launch` or `async` in the cache's
    `CoroutineScope(SupervisorJob() + loadDispatcher)`, and the start mode. Where each sends a loader's
    fatal `Error`: a `CoroutineExceptionHandler`, the thread's uncaught-exception handler in production, and
    what `kotlinx-coroutines-test` 1.10.2 does under `runTest` with an unhandled exception from a coroutine
    that is not the test's child but runs on the test's scheduler.
  - **How completion reaches the future.** `Job.invokeOnCompletion` for a coroutine whose scope was
    cancelled before the coroutine started (a `close()` between Caffeine's mapping function and the start
    of the load): whether the body runs at all, whether the handler runs, and with which cause.
  - **Telling `close()` apart from a cancellation the loader throws on its own.** A `withTimeout` inside a
    loader expiring while the cache is open throws `TimeoutCancellationException` out of the load: what the
    load's coroutine and its `invokeOnCompletion` see in that case and in the `close()` case, and what a
    waiter should receive so that neither becomes `Closed` by mistake nor a `CancellationException` in a
    waiter that is still active.
  - **Waiting.** `CompletionStage.asDeferred()`: its fast path for a future already complete; how it
    unwraps an exceptional completion that holds an `Error`; the callback each call leaves on the shared
    future when its waiter is cancelled first; how a waiter tells its own cancellation from a load that
    ended (`ensureActive`, `currentCoroutineContext().isActive`).
  - **Deterministic tests on the virtual clock.** Driving one waiter's cancellation, a load in flight at
    `close()`, and the close-before-start interleaving with a `StandardTestDispatcher` on the shared
    scheduler; what `runTest` does when the test body ends while the cache's own scope still holds work or
    a pending `delay`.
- **Requirements.**
  - A waiter's own cancellation is thrown as `CancellationException` and cancels only that waiter; a
    `CancellationException` never reaches a waiter that is still active.
  - Every waiter returns exactly one outcome and never hangs; a fatal `Error` reaches every waiter and is
    not reported a second time somewhere else.
  - The load runs in the cache's own scope on the injected dispatcher, never in a caller's context.
  - `close()` is non-suspending and idempotent and completes every waiter with `Closed("cache")`; a call
    after it returns `Closed("cache")` without starting a load.
  - Level 2 is deterministic under `runTest` with the published `VirtualClock`.
- **Evidence that settles it.** kotlinx-coroutines 1.10.2 source at tag `1.10.2` (`Builders.common.kt`,
  `JobSupport.kt`, `Supervisor.kt`, `CoroutineExceptionHandler.kt` and its JVM implementation,
  `future/Future.kt`) and `kotlinx-coroutines-test` 1.10.2 (`TestScope.kt`, `TestBuilders.kt`, its internal
  exception collector, `TestCoroutineScheduler`), their KDoc and their own tests. Behaviour that only a run
  would show is left to the unit's own tests, which the findings name.
- **Findings file:** [`load-lifecycle.md`](load-lifecycle.md).

## Verification

Every load-bearing claim was re-opened at its source on 2026-11-03: claims 1 to 24 of
[`caffeine-mechanics.md`](caffeine-mechanics.md), claims 1 to 25 of [`load-lifecycle.md`](load-lifecycle.md),
and claims 1 to 19, 27 and 30 of the stack-level [`../../research/response-cache.md`](../../research/response-cache.md),
which this unit rests on again: 68 claims. **All hold; none fails and none is unclear.** The measured results
of the stack-level research are probe output with no source to re-open.

| File | Claims | Result |
|---|---|---|
| [`caffeine-mechanics.md`](caffeine-mechanics.md) | 1 to 24 | hold |
| [`load-lifecycle.md`](load-lifecycle.md) | 1 to 25 | hold |
| [`../../research/response-cache.md`](../../research/response-cache.md) | 1 to 19, 27 and 30 | hold |

Two imprecisions, neither of which changes a conclusion:

- **load-lifecycle 24.** The hash given for Ktor `3.4.0` is the annotated tag object; the commit the tag
  points to is `bf25bb96eab570d1983279ef81c255bb1c489964`. The quoted code and the tag are right.
- **caffeine-mechanics F1** (prose, not a numbered claim). In `doComputeIfAbsent`, `notifyEviction` runs
  before the mapping function; `recordEviction` and `notifyRemoval` run after the `computeIfPresent` block.
  Claim 16 states only the first and holds.

**Currency.**

- **Caffeine 3.3.1** is the latest release, on Maven Central and on GitHub (2026-10-13). Its release notes
  change none of the behaviour above.
- **kotlinx-coroutines 1.11.0** (2026-05-08) is the latest release, and no 1.10.x follows 1.10.2. A consumer's
  graph may resolve 1.11.0, so the areas the design rests on were compared between the two tags:
  - `CompletionStage.asDeferred` and `await` are unchanged; the `future { }` KDoc adds that a coroutine
    cancelled before it starts does not run its code, which agrees with `CoroutineStart.DEFAULT`.
  - `JobSupport.kt`, `CoroutineStart.kt` and `Deferred.kt` are unchanged; `Supervisor.kt` and `Job.kt` change
    in KDoc only.
  - `withTimeout` adds the coroutine's name to the timeout's message; the exception is still a
    `CancellationException` thrown by a scoped coroutine.
  - `runTest`'s end-of-body drain, its 60 s default timeout and the exception collector are unchanged;
    `runTest(dispatchTimeoutMs, ...)` becomes an error-level deprecation and the legacy `migration` test API
    is removed.
  - The JVM's last-resort handler now swallows an exception that an `UncaughtExceptionHandler` itself throws.
    The design never reaches that path.

**Claims about this repository**, checked directly against the code: no build file declares a
`System.LoggerFinder` provider or a JUL bridge (no `slf4j-jdk-platform-logging`, no `jul-to-slf4j`), so
Caffeine's `System.Logger` output reaches `java.util.logging` in this module's tests; `CallBridge.kt` resumes
its continuation exactly once, with a value, from a `finally`; and no JUnit parallel execution is configured
(no `junit-platform.properties`), so the test module's exception collector serves one test at a time in each
test JVM.

## Summary

What the two findings establish, the approach each leads to, and where the sources disagree. The findings
files hold the evidence, the per-case detail and the quoted sources.

### 1. How a load starts, ends and reaches its waiters

- **The load is a `launch` with the default start in the cache's `SupervisorJob` scope, and its body never
  throws.** It runs the first caller's loader and completes the shared future normally with one of four
  outcomes: loaded (a `Lookup`), failed (an `Exception`), fatal (an `Error`, kept by identity) or closed.
  Because nothing escapes the body, `launch`'s uncaught-exception path (the thread's handler in production,
  the test module's exception collector under `runTest`) is never used, Caffeine never sees an exceptional
  completion, and stack-trace recovery under assertions never re-creates the `Error`: each waiter rethrows the
  original instance ([`load-lifecycle.md`](load-lifecycle.md) F1, F4, F5). `async` is not used: under a
  supervisor it holds an unawaited failure silently, and the future already is the result channel.
- **A cancellation the loader throws on its own is a failure: never `Closed`, never a
  `CancellationException`.** `withTimeout` is a scoped coroutine, so when one expires inside a loader the
  load's own coroutine is still active (F3). After catching a `CancellationException`, the load calls
  `currentCoroutineContext().ensureActive()`: that throws when `close()` cancelled the load; otherwise the
  cancellation came from inside the loader, and the call's failure is an `IllegalStateException` with a
  constant message and the original as its `cause`. A loader that returns a `Result.failure` holding a
  `CancellationException` is treated the same way, because `getOrThrow()` would throw it into a caller that is
  still active, where it reads as that caller's own cancellation. The failure model has no leaf for this case,
  and a JDK type keeps every caller safe without a new public type.
- **A waiter never hangs, whatever the loader does.** `close()` sets an atomic closed flag, cancels the scope,
  then completes every future still in the map with the closed outcome; the call that created a future
  re-checks the flag after `get` returns and completes its own future if the cache closed in between; a
  `launch` that throws (a rejecting dispatcher) completes the future itself; and an `invokeOnCompletion`
  handler on the load's job stays as the last completer. `CompletableFuture.complete` is first-wins, so the
  extra completers are harmless, and the waiters' release never waits for a loader to unwind (F6). The
  `invokeOnCompletion(onCancelling = true)` overload, which would fire at cancellation, is internal API and is
  not used (F2).
- **Waiting.** Each waiter awaits `future.asDeferred().await()` inside a `catch (e: CancellationException)`
  that calls `ensureActive()` and otherwise reads the closed outcome, so the only `CancellationException` a
  waiter ever throws is its own. `getOrLoad` also calls `ensureActive()` before anything else, so a caller that
  is already cancelled gets its `CancellationException` even on a hit, which never suspends (F4). A cancelled
  waiter leaves one callback and one `CompletableDeferred` on the shared future until the load ends.
- **`runTest` 1.10.2 drains the whole shared scheduler before it collects unhandled exceptions**, the cache's
  own queued coroutines and pending `delay`s included, and its default timeout is 60 s of real time (F7). A
  cache scope that is not the test's child is never reported as a leaked child; an exception escaping it would
  still fail the test, which the total load body prevents.

### 2. What `CacheStats` counts, and from where

- **Caffeine's own statistics are not used, and `recordStats()` is off.** Its `evictionCount` counts every
  automatic removal (`SIZE`, `EXPIRED`, `COLLECTED`), so each failed or zero-lifetime entry counts as an
  eviction, and whether it does depends on callback order, so the count changes from run to run; its hit and
  miss counts see every `get`, so a `maxAge` re-read counts one call twice; its `loadFailureCount` stays 0,
  because failures complete the future normally; and `recordStats()` reads `System.nanoTime` around every load
  ([`caffeine-mechanics.md`](caffeine-mechanics.md) F1, F3).
- **The cache keeps four counters of its own.** A hit is a call whose final `get` took an existing future (a
  stored entry or a load in flight); a miss is a call whose final `get` ran the mapping function; a failed
  load is one load that ended failed or fatal, counted once by the load; an eviction is an entry the size bound
  removed, counted by an `evictionListener` that increments only for `RemovalCause.SIZE`. The listener runs
  synchronously inside the atomic removal, receives the unwrapped entry and is called only for completed
  futures (F2). Consumers who bind statistics to Micrometer know `cache.evictions` as Caffeine's wider count,
  so the KDoc of `evictions` says it counts the size bound's removals only (F1).

### 3. Weights and the bound

- **A weight of 0 is never selected for size eviction, so `Lookup.Found` requires a weight of at least 1.**
  Caffeine skips zero-weight entries when it evicts for size, which is how in-flight futures stay safe, so a
  stored entry of weight 0 would sit outside the bound; a negative weight would throw inside Caffeine's
  completion callback and log a `WARNING` (F4). `Found(value)` weighs 1, and `Found(value, weight)` refuses a
  weight below 1 with `IllegalArgumentException`. `maxWeight` is at least 1.
- **The cache computes each entry's weight and lifetime once, when the load ends.** A stored `Found` weighs
  what it reports and lives `ttl`; a stored `Missing` weighs 1 and lives `missTtl`; a failed, fatal or closed
  outcome, an outcome whose TTL is zero, and a `Found` whose weight exceeds `maxWeight` weigh 0 and live 0. The
  last case is delivered to its waiters and never stored, so the bound holds at every instant and `evictions`
  keeps meaning "the bound removed a resident entry"; stored and then evicted, an oversized entry would be
  readable until the next drain and counted as a size eviction (F4).
- **A call that finds a completed entry of zero lifetime treats it as absent.** It removes that future by
  identity and looks again, so a failure, a zero-TTL value or an oversized value is never served to a call
  that did not wait for it, whatever order the completion callbacks run in (F7, F11).

### 4. Invalidation and close

- **`invalidate(key)` is `asMap().remove(key)` and `invalidateAll()` is `asMap().clear()`, on the async view.**
  Both remove a stored or an in-flight entry and never touch its future: the calls already waiting still
  receive that load's outcome, the late completion is never re-inserted (`replace` is a `computeIfPresent`
  guarded by the future's identity), and the next call starts a new load, so it receives nothing loaded before
  the invalidation (F5). The `synchronous().asMap()` view joins a pending future and can block; the cache
  never uses it.
- **`close()` needs nothing from Caffeine.** With no `Scheduler` there is no thread, timer or shutdown hook,
  and the maintenance task holds the cache only weakly. The cache clears its map at close, so the stored values
  are released even while the closed object stays reachable, and it never shuts the `maintenanceExecutor` down:
  that executor belongs to the caller and must outlive the cache's last write (F6).

### 5. The mapping function and the `Expiry`

- **The mapping function only allocates** the future and records that it ran: it holds the bin lock while it
  runs, and the `BiFunction` form never uses its executor for a fresh future. An expired entry is replaced by
  a new mapping in the same call (F7).
- **On completion Caffeine calls the cache's `expireAfterCreate` once**, with the ticker's reading, through
  `AsyncExpiry`; returning 0 makes the entry expired at once, and `Long.MAX_VALUE` (a `Duration.INFINITE` TTL)
  is capped at about 150 years. The `Expiry` is an `object` (it has three abstract methods, so it is not a SAM)
  that returns the entry's precomputed lifetime from `expireAfterCreate` and `expireAfterUpdate`, and the
  current duration from `expireAfterRead`. Caffeine's interfaces are JSpecify `@NullMarked`, which Kotlin
  applies strictly, so their parameters are non-null in Kotlin (F8).

### 6. Tests

- **Level 2 runs on `VirtualClock`:** its `dispatcher` as `loadDispatcher` and its same-thread
  `maintenanceExecutor`, so every write drains maintenance inline, and `cleanUp()` followed by the eviction
  policy's `weightedSize()` is exact (F9). Each interleaving is driven with `advanceBy` and asserted with
  `isCompleted`, never `join()` ([`load-lifecycle.md`](load-lifecycle.md), "Level 2 tests on `VirtualClock`");
  only the close-before-start case needs a test-local parking dispatcher, because `runCurrent()` also runs what
  the tasks it runs enqueue.
- **Caffeine's log output** goes through `System.getLogger`, which the default `LoggerFinder` routes to
  `java.util.logging` on this module's test classpath. A handler on the root JUL logger, filtered on Caffeine's
  logger names, observes it; a positive control that provokes one warning first keeps the assertion "a failed
  load logs nothing" from passing vacuously, and a structural assertion (the future is never completed
  exceptionally) needs no logging at all (F10).
- **Level 4c** provokes the failure-identity race on real threads: `Dispatchers.Default` and the common pool,
  8 to 32 joiners per key, an immediate retry on failure, fresh keys per round. Caffeine's completion callback
  is registered first and runs last on JDK 11's dependents stack, which opens the window the waiter-side
  removal closes; the test asserts that no retry receives a previous failure (F11). A size test on a real
  executor loops `cleanUp()` until the weighted size is stable before it asserts the bound, because only the
  common pool reschedules unfinished maintenance at once (F9).

### 7. Codebase blast radius (surveyed directly)

- **No caller.** Nothing in the repository caches HTTP responses or calls a cache; the call facades (#68) are
  the first users. `webtools-http/build.gradle.kts` already declares Caffeine 3.3.1 as an `implementation`
  dependency of `jvmMain`, so no build line changes.
- **New files** in `webtools-http`: the port and its values in `commonMain` (compiled for `js` by the check
  module like every common file), the Caffeine default and its internal parts in `jvmMain`, and their tests in
  `commonTest` (the values, the key's canonical form, the consumer's view without the opt-in) and `jvmTest`
  (everything that suspends: only `jvmTest` has `kotlinx-coroutines-test`, through the fixtures).
- **Changed:** the committed ABI dump `api/webtools-http.api`; the KDoc of `ExperimentalForInheritanceWebToolsApi`
  (its list of gated types gains `ResponseCache`) and of `WebToolsDsl` (its list of builders gains the two new
  builders); `WebToolsDslComponentTest`'s list of builders; the KDoc of `VirtualClockThreeClocksComponentTest`,
  whose plain Caffeine cache keeps proving the clock apart from the response cache; the `webtools-http` row of
  `README.md`; `CHANGELOG.md` under `[Unreleased]`.
- **Build cost:** the fixtures-publication check runs again, because `jvmMain` is among its inputs.

### Where the sources disagree, and which view holds

- **Ending a load.** The stack-level cache research ([`../../research/response-cache.md`](../../research/response-cache.md)
  F5 and its recommended `startLoad`) completes the future from an `invokeOnCompletion` handler that maps every
  cancellation to `Closed` and completes exceptionally on an `Error`. Its measurements hold, but the coroutine
  source shows three defects in that shape: a loader's own timeout would close every waiter while the cache
  stays open; an `Error` would be reported by `launch` and again by Caffeine's warning; and the waiters' release
  would wait for the loader to unwind. The total load body and the four completers of §1 replace it.
- **Statistics.** The same research expected the size evictions from Caffeine's statistics, with no listener
  (its F9). Caffeine's `evictionCount` also counts expiry and failure removals, so a size-only listener counts
  instead and `recordStats()` is dropped.
- **Weights.** That research's sketch allowed a weight of 0; Caffeine never evicts such an entry for size, so
  the lower bound is 1.

### Options that do not fit

- `async` for the load (silent on an unawaited failure); `CoroutineStart.ATOMIC` (runs a loader after
  `close()`) and `UNDISPATCHED` (runs the loader's first segment on the caller's thread); the internal
  `invokeOnCompletion(onCancelling = true)`.
- Caffeine's `evictionCount` as the eviction count; a `removalListener` (asynchronous, every cause);
  `recordStats()`.
- Accepting a weight of 0, or storing an oversized entry until the next drain; clamping an oversized weight to
  `maxWeight` (one entry would empty the cache); a `maxWeight` of 0 (a `ttl` of zero already coalesces without
  storing).
- `synchronous().asMap()` for removal (it joins a pending future); `Expiry.creating` (a `java.time.Duration`
  detour for a value already in nanoseconds).
- A new failure leaf for a loader's own cancellation: it would extend the failure model, which this unit does
  not own, and the JDK type with the original as `cause` already keeps every caller safe; a typed leaf can be
  added in a later feature release.
