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
