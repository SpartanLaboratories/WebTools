# Final implementation: `response-cache` (Issue #66)

The as-built record of `SpartanLaboratories/WebTools#66`, unit 7 (`response-cache`) of [#55's architecture](../architecture.md). It states what was built,
where the work departs from [`plan.md`](plan.md) and why, and the measurements that show it holds. What the unit changes for the repository, its published
artifacts and the sibling units, and the known gaps and follow-ups it leaves, are in [`consequences.md`](consequences.md).

- **Status:** built through stage 3 of [`plan.md`](plan.md) §5, with the deviations of §2. Procedures P1 to P5 of plan §9.2 ran (§4); the Level 5 pass of P5
  is §5.
- **Branch and commits:** `feat/issue-66-response-cache`, cut from `master` at `e8f0a88`, with the research commits `c19db19`, `97beb79`, `879abff` and
  `826200e` and the code commits `e5f1a4c` (stage 1, with this folder's `plan.md` and planned `consequences.md`), `ea167b9` (stage 2) and `d1deae5` (stage 3),
  commits 1 to 3 of plan §11, then `38483ea` (`docs: record the response cache as built and correct the research count (Issue #66)`), the code-landing
  commit. The `README.md` and `CHANGELOG.md` delta of plan §8, the KDoc wording of the documentation review (§5) and the reconciled documents are committed
  after it. PR TBD (it closes #66).
- **Architecture:** [`../architecture.md`](../architecture.md) carries dated corrections (2026-11-03) for this unit at §6.4 and in §7.4's row for the
  facade's call to `ResponseCache`; [`consequences.md`](consequences.md) §5 states each refinement.
- **Host of the measurements:** Windows 11, JDK 23.0.2, Gradle 9.7.1, Kotlin 2.2.0, Caffeine 3.3.1, kotlinx-coroutines 1.10.2, 32 cores. Linux and macOS
  were not exercised.
- **Versions moved:** none. `webtools-http` 1.0.0 stays unreleased until #55's stack is complete, and this landing is not a release candidate.

## 1. What was built

Everything plan §7 lists, in the plan's shape, with the deviations of §2. Paths are under `webtools-http/src/`.

- **The public surface** (package `com.spartanlabs.webtools.http`):
  - `commonMain`: the port `ResponseCache : AutoCloseable`, `@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)`, with the abstract
    `getOrLoad(key, policy, maxAge, loader)`, the three-argument `getOrLoad(key, policy, loader)` whose body passes `Duration.INFINITE`, `invalidate`,
    `invalidateAll`, `stats` and an empty default `close()`, and its nested `public object Disabled`; the values `CacheKey` (two constructors), `CachePolicy`
    with `CachePolicy.Builder`, `CachePolicy.DEFAULT_MISS_TTL` and the factory `CachePolicy(ttl) { }`, the sealed `Lookup` with `Lookup.Found` (two
    constructors) and `Lookup.Missing`, and `CacheStats` with `CacheStats.Empty`. Implementing the port needs the opt-in; everything else is Stable Core and
    usable without it.
  - `jvmMain`: the final class `CaffeineResponseCache` over an `internal` constructor, with `CaffeineResponseCache.Builder`, `DEFAULT_MAX_WEIGHT` (1,000) and
    the factory `CaffeineResponseCache { }`, Stable Core and usable without the opt-in.
  - The ABI dump `webtools-http/api/webtools-http.api` grows from 434 to 557 lines by exactly the classes and members of plan §7.16 (§4, P2).
- **The parts** (`internal`): `commonMain/.../LoaderRunner.kt` (`runLoader`, `LOADER_CANCELLED_MESSAGE`, `NEGATIVE_MAX_AGE_MESSAGE`), which `Disabled` and the
  Caffeine default share; `jvmMain/.../CacheEntry.kt` (`CacheOutcome`, `CacheEntry`, the `Storage` notes of plan §7.8 with `Removed` beside them (D4),
  `Residency`, `residencyOf`, `isUsable`, `outcomeKind`, `CacheExpiry`); and, in `CaffeineResponseCache.kt`, the call, the total load body with its three
  completers, and the test seams `cleanUp`, `weightedSize` and `futureOf`.
- **Changed:** the KDoc lists of `ExperimentalForInheritanceWebToolsApi.kt` (gains `ResponseCache`) and `WebToolsDsl.kt` (gains the two builders);
  `WebToolsDslComponentTest`, whose list gains the two builders; the KDoc of `VirtualClockThreeClocksComponentTest`; the dump.
- **Files:** 46 new under `webtools-http/` (6 in `commonMain`, 2 in `jvmMain`, 6 test classes in `commonTest`, and in `jvmTest` the 5 support files of plan
  §9.1 and D1 and 27 test classes); 5 changed (the dump, the two KDoc lists and the two tests above).
- **Unchanged:** `webtools-http/build.gradle.kts` (Caffeine 3.3.1 was already a `jvmMain` dependency), the published test fixtures, `webtools-udp`,
  `webtools-scraping`, `webtools-browser`, `build-logic` and `webtools-http-commoncheck`: `git diff --stat e8f0a88..38483ea` is empty for each of them.

**The tests** are in the level packages of plan §9, with the additions of D3. Level 4b has none, as plan §9 says. The baseline is `e8f0a88`, as #65's run of
record measured it; the final counts are the final run of record (§4):

| Level | Classes, baseline → final | Tests, baseline → final |
|---|---|---|
| Level 1, gating | 7 → 9 | 19 → 23 |
| Level 2, component | 57 → 81 | 397 → 519 |
| Level 3, integration | 24 → 25 | 94 → 98 |
| Level 4a, deterministic | 44 → 46 | 195 → 214 |
| Level 4c, non-functional | 15 → 19 | 31 → 35 |

## 2. Deviations

Each entry names what differs from the plan, where, and why; it cites the plan section it departs from and links secondarily to what that section was based
on. D4 changes one production rule, the load-end log line, and no public signature or line of the ABI dump; the other four change tests and their support
only. D3 comes from the test review and D4 from the code review.

### D1. `Waiter.kt` is added to the test support

- **Plan:** §7.12 and §9.1 list the support files `CacheTestSupport.kt`, `ParkingDispatcher.kt`, `JulCapture.kt` and `Boom.kt`; §12 lists the names the test
  support takes (`cacheOn`, `key`, `LoadLog`, `ParkingDispatcher`, `captureCaffeineLogs`, `Boom`).
- **As built:** `support/webtools/http/Waiter.kt` holds `Waiter`, one `getOrLoad` call launched as its own coroutine with its result, or the throwable it
  threw, recorded as the instance, and `CoroutineScope.call(...)`, which launches one. Sixteen of the eighteen `CaffeineResponseCache*ComponentTest` classes
  drive their callers through them. `Waiter` and `call` join the names of §12.
- **Why:** §9's Level 2 convention drives every interleaving with the virtual clock and asserts on `isCompleted`, `isCancelled` and the recorded result, never
  `join()`, and catches a waiter's throwable inside its coroutine so that `assertSame` sees the instance. Those lines were the same in every class, so they
  live once.
- **Based on:** §9 (the Level 2 template and the instance rule) and [`research/load-lifecycle.md`](research/load-lifecycle.md), "Level 2 tests on
  `VirtualClock`".

### D2. The `maxAge` age edge at Level 2 is one millisecond, not one nanosecond

- **Plan:** §9 Level 2, `CaffeineResponseCacheMaxAgeComponentTest`: "at age 5 minutes + 1 ns it reloads", and `Duration.ZERO` "at the same virtual instant is
  served and one nanosecond later reloads".
- **As built:** the two cases step the clock by `1.milliseconds` (`an age equal to maxAge is served and one millisecond more reloads`, `a zero maxAge serves
  an entry stored at the same instant and reloads one millisecond later`). `VirtualClock.advanceBy` moves time through
  `TestCoroutineScheduler.advanceTimeBy`, which takes whole milliseconds (`delayTime.inWholeMilliseconds`, kotlinx-coroutines-test 1.10.2), so the virtual
  clock cannot step by a nanosecond. The nanosecond edges of the rule (an age of `maxAge + 1 ns`, and of 1 ns under `maxAge = ZERO`) are tabled on the pure
  `isUsable` by `CacheEntryRulesDeterministicTest`, as §9 Level 4a has them.
- **Based on:** §6.6 (the usability rule, unchanged) and the stack-level [`../research/test-support-and-time.md`](../research/test-support-and-time.md) F1:
  the scheduler's `TimeSource`, which `VirtualClock` reads (architecture §6.7), is an `AbstractLongTimeSource(DurationUnit.MILLISECONDS)`.

### D3. Four test classes cover more than plan §9's tables, and `LoaderRunnerComponentTest` runs under `runTest`

- **Plan:** §9's Level 2 and Level 4a tables for `CaffeineResponseCacheWaiterCancellationComponentTest`, `CaffeineResponseCacheRejectedLoadComponentTest`,
  `CacheEntryRulesDeterministicTest` and `LoaderRunnerComponentTest`; §9 names no runner for `LoaderRunnerComponentTest`'s plain cases.
- **As built,** with no production code changed:
  - `CaffeineResponseCacheWaiterCancellationComponentTest` gains the case Assumption 2 guards: the shared future cancelled from outside (through the
    `futureOf` seam) while its waiter is active reads as `Closed("cache")`, and the waiter is neither cancelled nor thrown into (the `awaitEntry`
    fall-through of §6.3);
  - `CaffeineResponseCacheRejectedLoadComponentTest` gains a `loadDispatcher` whose `dispatch` throws an `Error`, the non-`Exception` branch of §6.4's
    `entryOfThrowable`: the waiter has the same instance rethrown, `failedLoads` is 1, and one `WARN` names the class;
  - `CacheEntryRulesDeterministicTest` gains a table over `CacheExpiry` (the precomputed lifetime on create and update, the current duration on read; §6.6)
    and one over `CacheEntry.isFailure` with the companion's `closed()`, `failed()` and `fatal()` (lifetime 0, weight 0, `storedAt` carried);
  - `LoaderRunnerComponentTest` runs its plain cases under `runTest` instead of `runBlocking`, so its `withTimeout(10)` case expires on virtual time.
- **Why:** the test review found two branches of §7.9's code and two `internal` members of §7.8 that no listed case reached; the tables of §9 are the minimum
  each class locks down.
- **Based on:** §3 (Assumption 2), §6.3, §6.4 and §6.6; [`research/load-lifecycle.md`](research/load-lifecycle.md), Gaps: the waiter's guard for a future
  cancelled from outside "cannot be reached from a Level 2 test without reaching into the cache", which the `futureOf` seam of §7.9 does.

### D4. The load-end `DEBUG` line is written by the completion that wins

- **Plan:** §7.9's code block: `load()` logs `residency.storage.note` before it returns its entry, and `startLoad` runs `future.complete(load(…))`; only
  `settle` is guarded by the outcome of `complete`. R12 and §6.9 say `DEBUG` once per load end, "whether it was stored or why not", "logged by whoever
  completes the shared future".
- **As built:**
  - `load()` returns the entry together with its storage reason (a private `Ended` holder, because an entry of lifetime 0 does not say which of the three
    "not stored" notes applies), and the `scope.launch` body in `startLoad` logs the line only when its `future.complete(entry)` returns `true`. `settle` is
    unchanged, and the body still counts `failedLoads`.
  - For an entry the residency rule stores, the note is `stored` only if the key still maps that future once `complete()` has returned; otherwise it is
    `not stored: removed before the load ended`, a new `internal` `Storage.Removed` beside the notes of §7.8 (an `invalidate` or a size eviction in the
    instant between the two reads the same way). That is the case of an `invalidate(key)` during the load: nobody else completes the future, so the body's
    completion wins, but Caffeine's completion callback re-inserts only by identity and the future is no longer mapped, so the result is discarded. A
    zero-TTL, oversized, failed or fatal entry keeps its own "not stored" reason.
  - The logger, the line's text and the existing notes are unchanged; no public signature and no line of the dump change.
- **Why:** §7.9's code met R12 only when the body's completion won. When `close()` had already settled the future (a loader that ignores cancellation),
  the body wrote a second "load ended" line for the load, after `close()`'s own `closed` line, saying `stored` for a result the cache had discarded; after
  an in-flight `invalidate`, it said `stored` for a result that was not stored. `CaffeineResponseCacheLoggingComponentTest`, which counts the line per load
  end, passed under both; it gains the two cases that pin the rule (§4).
- **Based on:** §6.4 (the three completers; the first wins) and §6.5, resting on [`research/load-lifecycle.md`](research/load-lifecycle.md) F6 and claim 21
  (`CompletableFuture.complete` reports whether this call completed the future) and [`research/caffeine-mechanics.md`](research/caffeine-mechanics.md) F5
  ("The completion does not re-insert"). Architecture §6.4 carries the rule in its dated correction.

### D5. The close-race test's callers and closer are coroutines on `Dispatchers.Default`

- **Plan:** §9 Level 4c, `CaffeineResponseCacheCloseRaceNonFunctionalTest`: "16 threads call `getOrLoad` (loaders that finish quickly or suspend) while
  another thread closes the cache"; §9's conventions: Level 3 and 4c use real time under `runBlocking` with a `withTimeout` bound.
- **As built:** each of 300 rounds builds a fresh cache on the production defaults (with `maxWeight = 100`) and starts 16 callers with
  `async(Dispatchers.Default)` over four keys, with loaders that return at once, suspend 2 ms or suspend until cancelled, and a closer with
  `launch(Dispatchers.Default)` that waits on the same gate and spins a seeded 0 to 20,000 volatile writes before `close()`. Callers and closer are children
  of one `withTimeout(10.seconds)` per round; every result must be a `Found` or `Closed("cache")`, and a `CancellationException` that is not the bound's own
  fails the round.
- **Why:** the bound is a coroutine timeout and cancels only coroutines: with the callers and the closer as its children, a hang fails the round at the bound
  instead of blocking `runBlocking`. They run on `Dispatchers.Default`'s worker threads, in parallel but not necessarily one thread each, as the other Level
  4c classes of §9 run their workers; the window needs one caller and the closer running at once.
- **Based on:** §9 (the Level 3 and 4c conventions; the failure-identity and size-bound classes on `Dispatchers.Default`) and
  [`research/load-lifecycle.md`](research/load-lifecycle.md), "Level 2 tests on `VirtualClock`" ("many threads calling `getOrLoad` while another closes,
  every call must return within a bound"). What P4 showed about this test is in §4 and [`consequences.md`](consequences.md) §8.

## 3. Acceptance

The plan's acceptance items (§1), with the runs that show them (§4):

1. **Met.** A consumer without the opt-in compiles against the surface: `ResponseCacheConsumerGatingTest` (`commonTest`: `ResponseCache` in a function, a
   class and a constructor signature, `Disabled` as a default, every value type built, a `when` over `Lookup` with no `else`) and
   `CaffeineResponseCacheConsumerGatingTest` (`jvmTest`: `CaffeineResponseCache { maxWeight = 10 }` used as a `ResponseCache` on production defaults).
2. **Met.** An implementer needs the opt-in: P1, the compile failure with the marker's message once per source set and the compile with `@OptIn`.
3. **Met.** Every contract point of R2 holds on `VirtualClock`, and `Disabled` runs one loader per call: the eighteen `CaffeineResponseCache*ComponentTest`
   classes (84 tests), `LoaderRunnerComponentTest` and `DisabledResponseCacheComponentTest`, with the value types' four `commonTest` classes.
4. **Met.** N concurrent callers cost one real request and a later call is a hit: `CaffeineResponseCacheLoopbackIntegrationTest` (16 callers,
   `requestCount == 1`, hits 15 and misses 1, the count still 1 after a later call; the `Disabled` control costs one request per caller).
5. **Met.** The size bound on real threads, failure identity and secrecy: `CaffeineResponseCacheSizeBoundNonFunctionalTest`,
   `CaffeineResponseCacheFailureIdentityNonFunctionalTest` (P3 shows it detects the defect it guards) and `CaffeineResponseCacheSecrecyNonFunctionalTest`;
   beside them `CaffeineResponseCacheCloseRaceNonFunctionalTest`, which P4 shows does not detect the loss of the insert-then-check
   ([`consequences.md`](consequences.md) §8).
6. **Met.** `:webtools-http:check` passed on the final tree on 2026-11-03 (exit 0): `jvmTest` with 889 tests in 180 classes, none failed, errored or
   skipped, `apiCheck` against the regenerated dump, `verifyJavaFloor` and the fixtures-publication check (§4). It had passed once before, at the end of
   stage 3, and `:webtools-http-commoncheck:build` passed in stages 1 and 2. No other module's source, build file or dependency changed (§1).

## 4. Measurements

Procedure results and runs, in the order they ran. Only `webtools-http`'s own tasks and the check module's `js` compile were run (plan §9.2), no other
module's suite.

- **P1, the gate (stage 1).** Two scratch files, `webtools-http/src/commonTest/kotlin/ScratchCache.kt` and `webtools-http/src/jvmTest/kotlin/ScratchCacheJvm.kt`
  (distinct names: both source sets compile in the one `compileTestKotlinJvm` task, so one name in both reports a redeclaration that hides the result), each
  holding a top-level implementer of `ResponseCache` with every abstract member overridden and **no** `@OptIn`.
  - Without the opt-in, `:webtools-http:compileTestKotlinJvm` failed with the marker's message once per source set (`ScratchCache.kt:8:22` and
    `ScratchCacheJvm.kt:8:25`): *This class or interface requires opt-in to be implemented: Implementing or substituting this WebTools seam is Experimental
    and may change in a minor release. Using the supplied implementations needs no opt-in. Opt in with @OptIn(ExperimentalForInheritanceWebToolsApi::class).*
  - With `@OptIn(ExperimentalForInheritanceWebToolsApi::class)` on each class, the same task compiled (exit 0).
  - Both scratch files were deleted afterwards; neither is committed.
- **P2, the `js` compile and the dump (each code stage).** `:webtools-http-commoncheck:build`, `:webtools-http:apiCheck` and `:webtools-http:verifyJavaFloor`
  passed. `apiDump` after each stage grew the dump from 434 to 557 lines by exactly the classes and members of plan §7.16: stage 1 `CacheKey`, `CachePolicy`
  with `Builder`, `Companion` and `CachePolicyKt`, `CacheStats` with `Companion`, `Lookup` with `Found` and `Missing`, and `ResponseCache` with `DefaultImpls`
  and `Disabled`; stage 2 `CaffeineResponseCache` with `Builder`, `Companion` and `CaffeineResponseCacheKt`. No `internal` declaration and no Caffeine or
  `CompletableFuture` type is in it; its one coroutine type is `CoroutineDispatcher`, on the builder's `loadDispatcher`.
- **Stages 1 and 2.** `:webtools-http:gatingTest`, `:webtools-http:componentTest` and `:webtools-http:deterministicTest` passed after each stage. The classes
  this change adds or amends all ran: Level 1 `ResponseCacheConsumerGatingTest` (3) and `CaffeineResponseCacheConsumerGatingTest` (1); Level 2
  `CacheKeyComponentTest` (5), `CachePolicyComponentTest` (6), `LookupComponentTest` (5), `CacheStatsComponentTest` (4), `LoaderRunnerComponentTest` (8),
  `DisabledResponseCacheComponentTest` (10), the eighteen `CaffeineResponseCache*ComponentTest` classes (80 in all), `WebToolsDslComponentTest` (1) and
  `VirtualClockThreeClocksComponentTest` (2); Level 4a `CacheKeyCanonicalFormDeterministicTest` (9) and `CacheEntryRulesDeterministicTest` (8). None skipped,
  none failed. Three Level 2 classes (`CaffeineResponseCacheCloseComponentTest`, `CaffeineResponseCacheConstructionComponentTest` and
  `CaffeineResponseCacheLoggingComponentTest`) close the cache directly rather than with `use`, because `close()` is what they test.
- **Stage 3.** `:webtools-http:integrationTest :webtools-http:nonfunctionalTest` passed: Level 3 `CaffeineResponseCacheLoopbackIntegrationTest` (4); Level 4c
  `CaffeineResponseCacheSizeBoundNonFunctionalTest`, `CaffeineResponseCacheFailureIdentityNonFunctionalTest`, `CaffeineResponseCacheSecrecyNonFunctionalTest`
  and `CaffeineResponseCacheCloseRaceNonFunctionalTest` (1 each). The tasks ran 98 tests in 25 classes and 35 in 19, none skipped, none failed; the
  `UrlParseLinearTimeNonFunctionalTest` flake under concurrent tasks (WebTools#87) did not show.
- **`:webtools-http:check`, once, at the end of stage 3 (plan §9.2):** passed, with `jvmTest` (883 tests in 180 classes, none skipped, none failed),
  `apiCheck`, `verifyJavaFloor` (up to date from stage 2: stage 3 changed no main input) and the fixtures-publication check of `build-logic`.
- **P3, the failure-identity mutation (stage 3).** Both removals of a lifetime-0 entry in `CaffeineResponseCache` were commented out at once: the one after
  the await in `getOrLoad` (`if (entry.lifetimeNanos == 0L) cache.asMap().remove(key, claim.future)`) and the first-round stale removal in `claim`
  (`cache.asMap().remove(key, first.future)`). `CaffeineResponseCacheFailureIdentityNonFunctionalTest`, run five times (`:webtools-http:nonfunctionalTest
  --tests '*CaffeineResponseCacheFailureIdentity*' --rerun`), **failed in 5 of 5 runs**, each in round 0, with *a retry received the failure its joiner just
  received (cores=32)*: the test detects the defect on this host. The two lines were restored, and the file was byte-identical to a copy taken before the
  mutation (same SHA-256).
- **P4, the insert-then-check mutation (stage 3).** The closed-flag re-check at the top of `startLoad` (`if (closed.get()) return settle(key, future,
  CacheEntry.closed())`) was commented out and `CaffeineResponseCacheCloseRaceNonFunctionalTest` run five times (`--tests '*CaffeineResponseCacheCloseRace*'
  --rerun`): **it passed in 5 of 5 runs**, so the test does not detect the loss of the re-check; [`consequences.md`](consequences.md) §8 carries what that
  leaves open, with the reading recorded with this run. The line was restored, and the file was byte-identical to the pre-mutation copy (same SHA-256).
  Neither mutation left a scratch file; both were in-place edits of `CaffeineResponseCache.kt`.
- **After the test review (D3).** `:webtools-http:componentTest --rerun :webtools-http:deterministicTest --rerun` passed: `componentTest` 517 tests in 81
  classes, `deterministicTest` 214 in 46, none skipped, none failed. From the result XML: `CaffeineResponseCacheWaiterCancellationComponentTest` 6 (was 5),
  `CaffeineResponseCacheRejectedLoadComponentTest` 2 (was 1), `LoaderRunnerComponentTest` 8 (its `withTimeout(10)` case now takes 5 ms of wall time),
  `CaffeineResponseCacheLoggingComponentTest` 4 and `CacheEntryRulesDeterministicTest` 10 (was 8); one test method was renamed to state its behaviour. The
  eighteen `CaffeineResponseCache*ComponentTest` classes held 82 tests. The five level tasks then passed together: gating 9 classes / 23 tests, component
  81 / 517, integration 25 / 98, deterministic 46 / 214, nonfunctional 19 / 35, so 887 passed, 0 failed, 0 skipped.
- **After the code review (D4).** `:webtools-http:componentTest --rerun :webtools-http:apiCheck` passed: `componentTest` 519 tests in 81 classes, none
  skipped, none failed. `CaffeineResponseCacheLoggingComponentTest` ran 6 (was 4): a loader that ignores cancellation and finishes after `close()` yields
  exactly one "load ended" line for its key, `closed` and `not stored: not a result`, and none when the loader finishes; a load whose key is invalidated in
  flight yields one line, `found` and `not stored: removed before the load ended`, and the next call reloads and logs `stored`.
  `CaffeineResponseCacheCloseComponentTest` (6), `CaffeineResponseCacheCaffeineLogComponentTest` (2) and `CaffeineResponseCacheInvalidationComponentTest` (5)
  ran unchanged. `apiCheck` passed against the unchanged dump.
- **The final run of record,** on the code of `38483ea` (the working tree differed from it only in comment and KDoc lines of the documentation review): the
  five level tasks in one invocation, which Gradle ran concurrently (11 s): gating 9 classes / 23 tests, component 81 / 519, integration 25 / 98,
  deterministic 46 / 214, nonfunctional 19 / 35, so 889 passed, 0 failed, 0 skipped; WebTools#87's flake did not show. The eighteen
  `CaffeineResponseCache*ComponentTest` classes hold 84 tests.
- **`:webtools-http:check` on the final tree** (2026-11-03T19:06Z; the code of `38483ea` with the documentation review's comment-only edits): passed, exit
  0, no warning printed. `jvmTest` ran 889 tests in 180 classes, none failed, errored or skipped; `verifyJavaFloor` reported "java floor 11 (class-file
  major <= 55): 267 classes in 2 jar(s), 6 library variants in 2 metadata file(s), 3753 classes on the runtime classpath"; `apiCheck` passed; and the
  fixtures-publication check of `build-logic` passed its 10 tests.

## 5. Level 5 pass

Plan §9 (Level 4b and Level 5) and P5, run after the test review: the KDoc of each new type (`Lookup`, `CacheKey`, `CachePolicy` with its `Builder` and
factory, `CacheStats`, `ResponseCache` and `Disabled`, `CaffeineResponseCache` with its `Builder` and factory) read from the source as the hover text a
consumer sees, and the regenerated dump read as the dump a consumer diffs.

- **Tier.** Every public type and factory names its tier in its first lines: **Stable Core** on each value type, on `Disabled`, on `CaffeineResponseCache`,
  its `Builder` and both factories; **Experimental (implement)** on `ResponseCache`, which also says that calling and passing it are Stable Core and that the
  two supplied implementations need no opt-in.
- **Contract points.** `ResponseCache` numbers its eleven points; `invalidate`, `invalidateAll` and `stats` refer back to points 7 and 8 by number, and
  `CaffeineResponseCache`'s bullets restate the points it refines (coalescing, lifetimes from completion, the weight bound, `maxAge`, failures never stored,
  the three execution contexts, `close()`, thread safety, logging) without contradicting the port.
- **"Never a value."** Stated where a value could leak: `CacheKey` (`toString()` prints names only; never put a tenant, header or credential in a key),
  `Lookup.Found` (`toString()` prints the weight, never the value), both factories' `@throws` ("the message names the property and never a value"),
  `CacheStats` (the message names the property) and `CaffeineResponseCache`'s logging bullet ("Never a value, a parameter value, a header, a credential or a
  message text; nothing per hit or join").
- **Three wordings, settled in the documentation review's KDoc.** The four-argument `getOrLoad`'s `@return`, "or `Closed("cache")` after close", now names
  `Result.failure(HttpFailure.Closed("cache"))`, as point 6 does. `CachePolicy.Builder`'s "A later `staleFor` is added here", which read in hover text as a
  property that might exist, now says that `missTtl` is its only property and that further optional properties go there, not into the factory's parameters.
  Point 8 and `stats()`'s `@return`, which called the counts "a snapshot", now call them an immutable reading of four counts, read separately and not as one
  atomic snapshot, as `CacheStats` says. The same review rewords point 4's condition (the coroutine running the loader is still active: the caller's for
  `Disabled`, the cache's own until `close()` for a cache that loads in its own scope) and `Lookup.Found`'s `@throws` reason, and moves two notes on KDoc
  links into line comments.
- **What the dump cannot show.** It records neither `sealed` on `Lookup` nor `@SubclassOptInRequired` on `ResponseCache`. `sealed` is held by
  `ResponseCacheConsumerGatingTest`, whose `when` expression over a `Lookup` has no `else` and would not compile over an open type; the gate is held only by P1
  and review (architecture §13; [`consequences.md`](consequences.md) §8). Every `Duration`-typed member appears under its mangled name (`getTtl-UwyO8pc`,
  `CachePolicy-VtjQ1oo`), as a Java reader meets any `kotlin.time.Duration` API.
- **Left to the maintainer:** the IDE checks of plan §9 (completion of `CaffeineResponseCache { }`, the opt-in error on an implementer with its quick fix, the
  `@WebToolsDsl` scoping of `CachePolicy { }`); [`consequences.md`](consequences.md) §8 has their owner and trigger.
