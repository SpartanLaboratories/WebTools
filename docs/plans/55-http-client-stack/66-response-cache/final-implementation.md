# As-built record: `response-cache` (#66)

Plan: [`plan.md`](plan.md). Consequences: [`consequences.md`](consequences.md). This file records what was built, the procedures of plan §9.2
that were run, and every deviation from the plan; it is reconciled into the final as-built record once implementation ends.

## Procedures run

**P1, the gate (plan §9.2, stage 1).** Two scratch files, `webtools-http/src/commonTest/kotlin/ScratchCache.kt` and
`webtools-http/src/jvmTest/kotlin/ScratchCacheJvm.kt` (distinct names: both source sets compile in the one `compileTestKotlinJvm` task, so one
name in both reports a redeclaration that hides the result), each holding a top-level implementer of `ResponseCache` with every abstract member
overridden and **no** `@OptIn`.

- Without the opt-in, `:webtools-http:compileTestKotlinJvm` failed with the marker's message once per source set
  (`ScratchCache.kt:8:22` and `ScratchCacheJvm.kt:8:25`): *This class or interface requires opt-in to be implemented: Implementing or
  substituting this WebTools seam is Experimental and may change in a minor release. Using the supplied implementations needs no opt-in. Opt in
  with @OptIn(ExperimentalForInheritanceWebToolsApi::class).*
- With `@OptIn(ExperimentalForInheritanceWebToolsApi::class)` on each class, the same task compiled (exit 0).
- Both scratch files were deleted afterwards; neither is committed.

**P3, the failure-identity mutation (plan §9.2, stage 3).** Both removals of a lifetime-0 entry in `CaffeineResponseCache` were commented out at
once: the one after the await in `getOrLoad` (`if (entry.lifetimeNanos == 0L) cache.asMap().remove(key, claim.future)`) and the first-round stale
removal in `claim` (`cache.asMap().remove(key, first.future)`). `CaffeineResponseCacheFailureIdentityNonFunctionalTest` was then run five times
(`:webtools-http:nonfunctionalTest --tests '*CaffeineResponseCacheFailureIdentity*' --rerun`) on a 32-core host: **it failed in 5 of 5 runs**,
each in round 0, with *a retry received the failure its joiner just received (cores=32)*. The test detects the defect on this host. The two lines
were restored and the file compared byte for byte with a copy taken before the mutation (identical SHA-256).

**P4, the insert-then-check mutation (plan §9.2, stage 3).** The closed-flag re-check at the top of `startLoad`
(`if (closed.get()) return settle(key, future, CacheEntry.closed())`) was commented out and `CaffeineResponseCacheCloseRaceNonFunctionalTest` run
five times (`--tests '*CaffeineResponseCacheCloseRace*' --rerun`): **it passed in 5 of 5 runs**, so the test did not detect the mutation. By the
design of plan §6.4 this is expected rather than a gap in the race: when the cache has closed between a call's insert and its `launch`, the
scope's `SupervisorJob` is already cancelled, so the launched coroutine completes cancelled without running its body and the third completer
(`job.invokeOnCompletion`) settles the future with the closed entry; removing the re-check alone therefore opens no hang for the test to see. This
reading was not instrumented. The line was restored and the file compared byte for byte with the pre-mutation copy (identical SHA-256). Neither
mutation left a scratch file; both were in-place edits of `CaffeineResponseCache.kt`.

**P5, the Level 5 read (plan §9.2).** Run after the test review, as the plan writes it: the KDoc of each new type (`Lookup`, `CacheKey`, `CachePolicy`
and its `Builder` and factory, `CacheStats`, `ResponseCache` and `Disabled`, `CaffeineResponseCache` and its `Builder` and factory) read from the
source as the hover text a consumer sees, and the regenerated `webtools-http/api/webtools-http.api` read as the dump a consumer diffs.

- **Tier.** Every public type and factory names its tier in its first lines: **Stable Core** on each value type, on `Disabled`, on
  `CaffeineResponseCache`, its `Builder` and both factories; **Experimental (implement)** on `ResponseCache`, which also says that calling and
  passing it are Stable Core and that the two supplied implementations need no opt-in. The tier statements read correctly.
- **Contract points.** `ResponseCache` numbers its eleven points; `invalidate`, `invalidateAll` and `stats` refer back to points 7 and 8 by number,
  and `CaffeineResponseCache`'s bullets restate the points it refines (coalescing, lifetimes from completion, the weight bound, `maxAge`, failures
  never stored, the three execution contexts, `close()`, thread safety, logging) without contradicting the port. One point reads less sharply than
  the rest: `getOrLoad`'s `@return` says "or `Closed("cache")` after close", which a reader could take for a thrown or returned failure rather than
  the `Result.failure(HttpFailure.Closed("cache"))` that point 6 spells out two paragraphs above.
- **"Never a value".** Stated where a value could leak: `CacheKey` (`toString()` prints names only; never put a tenant, header or credential in a
  key), `Lookup.Found` (`toString()` prints the weight, never the value), both factories' `@throws` ("the message names the property and never a
  value"), `CacheStats` (the message names the property) and `CaffeineResponseCache`'s logging bullet ("Never a value, a parameter value, a
  header, a credential or a message text; nothing per hit or join"). Each reads as a consumer-facing guarantee.
- **Unclear, as found.** (1) `CachePolicy.Builder`'s KDoc ends "A later `staleFor` is added here": in hover text this reads as a property that may
  already exist, and a consumer has to look at the dump to learn it does not. (2) Point 8 calls `stats()` "a snapshot of four counts" while
  `CacheStats` and `stats()`'s `@return` say the four "are read separately, not as one atomic snapshot"; the two statements are consistent once
  both are read, but "snapshot" alone promises more than the counts give. (3) The dump shows neither `sealed` on `Lookup` nor
  `@SubclassOptInRequired` on `ResponseCache` (the plan's §9 review item), so a consumer diffing the dump cannot see either guarantee; and every
  `Duration`-typed member appears under its mangled name (`getTtl-UwyO8pc`, `CachePolicy-VtjQ1oo`), which is what a Java reader of the dump
  meets for any `kotlin.time.Duration` API. None of these is a defect in the plan's terms; (1) and (2) are wording a later pass may tighten.
- **Left to the maintainer**, as plan §9 says: the IDE checks (completion of `CaffeineResponseCache { }`, the opt-in error on an implementer with
  its quick-fix, the `@WebToolsDsl` scoping of `CachePolicy { }`) wait for the next IntelliJ session.

## Acceptance

Plan §1's criteria, each with the evidence that discharges it:

1. **A consumer without the opt-in compiles against the surface** — the two Level 1 classes, `ResponseCacheConsumerGatingTest` (`commonTest`:
   `ResponseCache` in signatures, `Disabled` as a default, every value type built, an exhaustive `when` over `Lookup`) and
   `CaffeineResponseCacheConsumerGatingTest` (`jvmTest`: `CaffeineResponseCache { }` used as a `ResponseCache` on production defaults).
2. **An implementer needs the opt-in** — P1 above: the compile failure with the marker's message once per source set, and the compile with
   `@OptIn`.
3. **Every contract point of R2 holds on `VirtualClock`, and `Disabled` runs one loader per call** — the Level 2 classes: the eighteen
   `CaffeineResponseCache*ComponentTest` classes, `LoaderRunnerComponentTest` and `DisabledResponseCacheComponentTest`, with the value types'
   four `commonTest` classes.
4. **N concurrent callers cost one real request and a later call is a hit** — `CaffeineResponseCacheLoopbackIntegrationTest` (16 callers,
   `requestCount == 1`, hits 15 and misses 1; the `Disabled` control costs one request per caller).
5. **The size bound on real threads, failure identity, and secrecy** — the four Level 4c classes: `CaffeineResponseCacheSizeBoundNonFunctionalTest`,
   `CaffeineResponseCacheFailureIdentityNonFunctionalTest` (with P3 showing it detects the defect), `CaffeineResponseCacheSecrecyNonFunctionalTest`
   and `CaffeineResponseCacheCloseRaceNonFunctionalTest`.
6. **The module's checks are green and nothing in another module changes** — `:webtools-http:check` (once, at the end of stage 3), `apiCheck`
   against the regenerated dump, and `:webtools-http-commoncheck:build` (the `js` compile of `commonMain`), all recorded under the runs above; no
   other module's source, build file or dependencies changed.

The IDE checks of plan §9 (completion of `CaffeineResponseCache { }`, the opt-in error on an implementer, the `@WebToolsDsl` scoping) are not
part of these criteria and stay for the maintainer's next IntelliJ session, as the plan says.

## Runs (stages 1 and 2)

Only `webtools-http`'s own tasks and the check module's `js` compile were run (plan §9.2); no other module's suite, before or after.

- `:webtools-http:gatingTest`, `:webtools-http:componentTest`, `:webtools-http:deterministicTest`: green after each stage. The classes this
  change adds or amends all ran: Level 1 `ResponseCacheConsumerGatingTest` (3) and `CaffeineResponseCacheConsumerGatingTest` (1); Level 2
  `CacheKeyComponentTest` (5), `CachePolicyComponentTest` (6), `LookupComponentTest` (5), `CacheStatsComponentTest` (4),
  `LoaderRunnerComponentTest` (8), `DisabledResponseCacheComponentTest` (10), the eighteen `CaffeineResponseCache*ComponentTest` classes (80 in
  all), `WebToolsDslComponentTest` (1) and `VirtualClockThreeClocksComponentTest` (2); Level 4a `CacheKeyCanonicalFormDeterministicTest` (9)
  and `CacheEntryRulesDeterministicTest` (8). No test skipped, none failed. Three of the Level 2 classes
  (`CaffeineResponseCacheCloseComponentTest`, `CaffeineResponseCacheConstructionComponentTest` and `CaffeineResponseCacheLoggingComponentTest`)
  close the cache directly rather than with `use`, because `close()` is what they test.
- `:webtools-http:apiDump` after each stage; the dump grew from 434 to 557 lines by exactly the classes and members of plan §7.16 (stage 1:
  `CacheKey`, `CachePolicy` with `Builder`, `Companion` and `CachePolicyKt`, `CacheStats` with `Companion`, `Lookup` with `Found` and `Missing`,
  `ResponseCache` with `DefaultImpls` and `Disabled`; stage 2: `CaffeineResponseCache` with `Builder`, `Companion` and `CaffeineResponseCacheKt`).
- `:webtools-http:apiCheck`, `:webtools-http:verifyJavaFloor` and `:webtools-http-commoncheck:build` (the `js` compile of `commonMain`): green.
- `:webtools-http:check` is the one run plan §9.2 schedules "once, at the end", after the Level 3 and Level 4c suites of stage 3; see the
  stage 3 runs below.

## Runs (stage 3)

- `:webtools-http:integrationTest :webtools-http:nonfunctionalTest` (the stage 3 line of plan §9.2): green. The new classes all ran: Level 3
  `CaffeineResponseCacheLoopbackIntegrationTest` (4); Level 4c `CaffeineResponseCacheSizeBoundNonFunctionalTest` (1),
  `CaffeineResponseCacheFailureIdentityNonFunctionalTest` (1), `CaffeineResponseCacheSecrecyNonFunctionalTest` (1) and
  `CaffeineResponseCacheCloseRaceNonFunctionalTest` (1). The whole `integrationTest` task ran 98 tests in 25 classes and `nonfunctionalTest` 35
  tests in 19 classes; no test skipped, none failed (the known `UrlParseLinearTimeNonFunctionalTest` flake under parallel execution, WebTools#87,
  did not show).
- `:webtools-http:check`, once, at the end (plan §9.2): green (exit 0) with `jvmTest` (883 tests in 180 classes, none skipped, none failed, the
  five stage 3 classes among them), `apiCheck`, `verifyJavaFloor` (up to date from the stage 2 run: stage 3 changed no main input) and the
  fixtures-publication check of `build-logic`. No other module's suite was run.

## Runs (after the test review)

The test review asked for coverage of two branches of `CaffeineResponseCache` no Level 2 case reached, two tables over `internal` members of
`CacheEntry.kt`, the move of `LoaderRunnerComponentTest`'s plain cases from `runBlocking` to `runTest`, and one test renamed to a behaviour
statement (deviation 3 below). No production code changed.

- `:webtools-http:componentTest --rerun :webtools-http:deterministicTest --rerun`: green (exit 0). `componentTest` ran 517 tests in 81 classes,
  `deterministicTest` 214 in 46; none skipped, none failed. The amended classes ran their expected counts, read from
  `webtools-http/build/test-results/<task>/TEST-*.xml`: `CaffeineResponseCacheWaiterCancellationComponentTest` 6 (was 5),
  `CaffeineResponseCacheRejectedLoadComponentTest` 2 (was 1), `LoaderRunnerComponentTest` 8 (unchanged; the `withTimeout(10)` case now takes
  5 ms of wall time, on virtual time), `CaffeineResponseCacheLoggingComponentTest` 4 (unchanged) and `CacheEntryRulesDeterministicTest` 10
  (was 8). The nineteen `CaffeineResponseCache*ComponentTest` classes now hold 82 tests.
- **Observation, for the reconciliation.** The DEBUG "load ended … storage=stored" line of the load body is logged whether or not that load's
  `future.complete` wins: after `close()` with a loader that ignores cancellation, or after `invalidate` mid-flight, the body still says `stored`
  for a result that was discarded, and `close()` writes a second "load ended" line for that key (its `settle`, with `closed` and `not stored: not
  a result`). This follows plan §7.9's code exactly (the body logs `residency.storage.note` before returning, and only `settle` is guarded by the
  outcome of `complete`); §6.9's prose, "a load ended — logged by whoever completes the shared future", is looser than the code. Nothing a test
  asserts depends on it (`CaffeineResponseCacheLoggingComponentTest` counts the line per load end, not per completion), and no value is logged.
  **Resolved** by deviation 4 below, after the code review: the body's line is now written only by the completion that wins.

## Runs (after the code review)

The code review asked for one change to `CaffeineResponseCache` (deviation 4 below: the load body's DEBUG line moves to the completion that wins, and
says `removed` when the key was invalidated in flight), one `internal` `Storage` constant in `CacheEntry.kt`, and two Level 2 tests pinning it. No
public signature and no line of the ABI dump changed.

- `:webtools-http:componentTest --rerun :webtools-http:apiCheck`: green (exit 0). `componentTest` ran 519 tests in 81 classes; none skipped, none
  failed. Read from `webtools-http/build/test-results/componentTest/TEST-*.xml`: `CaffeineResponseCacheLoggingComponentTest` 6 (was 4: a loader
  that ignores cancellation and finishes after `close()` yields exactly one "load ended" line for its key, `closed` and `not stored: not a result`,
  and none when the loader finishes; a load whose key is invalidated in flight yields one line, `found` and `not stored: removed before the load
  ended`, and the next call reloads and logs `stored`), `CaffeineResponseCacheCloseComponentTest` 6, `CaffeineResponseCacheCaffeineLogComponentTest`
  2 and `CaffeineResponseCacheInvalidationComponentTest` 5 (all three unchanged). `apiCheck` passed against the unchanged dump. The
  `CaffeineResponseCache*ComponentTest` classes now hold 84 tests.

## Deviations

Each entry names what differs from the plan, the plan section it departs from, the reasoning, and what that part of the plan was based on.

1. **`Waiter.kt` added to the test support** (plan §7.12 and §9.1 list `CacheTestSupport.kt`, `ParkingDispatcher.kt`, `JulCapture.kt` and
   `Boom.kt` only). The Level 2 convention of §9 drives every interleaving with the virtual clock and asserts on `isCompleted`, `isCancelled`
   and "the recorded result", never `join()`; recording a launched `getOrLoad` call's result or throwable needs the same few lines in each of
   the cache's Level 2 classes, so they live once in `Waiter` (the recorder) and `CoroutineScope.call(...)` (the launcher), under
   `com.spartanlabs.testing.support.webtools.http`. Nothing in the plan's design or in a research finding is affected; the §12 list of names
   the test support takes gains `Waiter` and `call`.
2. **The `maxAge` age edge at Level 2 is one millisecond, not one nanosecond** (plan §9, `CaffeineResponseCacheMaxAgeComponentTest`: "at age
   5 minutes + 1 ns it reloads" and "one nanosecond later reloads"). `VirtualClock` moves time through `TestCoroutineScheduler.advanceTimeBy`,
   which truncates its `Duration` to whole milliseconds (`delayTime.inWholeMilliseconds`, kotlinx-coroutines-test 1.10.2), so the clock cannot
   step by a nanosecond. The Level 2 test steps by `1.milliseconds`, the clock's finest step; the exact nanosecond edge of the rule is tested on
   the pure function by `CacheEntryRulesDeterministicTest` (`age = maxAge + 1 ns` is not usable). The rule itself (plan §6.6, architecture
   §6.7's `VirtualClock`) is unchanged.
3. **Four test classes cover more than plan §9's tables list for them**, after the test review. `CaffeineResponseCacheWaiterCancellationComponentTest`
   gains the case the plan's Assumption 2 guards (the shared future cancelled from outside while the waiter is active reads as `Closed("cache")`,
   the `awaitEntry` fall-through of §6.3); `CaffeineResponseCacheRejectedLoadComponentTest` gains a `loadDispatcher` whose `dispatch` throws an
   `Error`, the non-`Exception` branch of §6.4's `entryOfThrowable` (the dispatcher helper takes the throwable to throw, a
   `RejectedExecutionException` by default); `CacheEntryRulesDeterministicTest` gains a table over `CacheExpiry` (§6.6: the precomputed lifetime
   on create and update, the current duration on read) and one over `CacheEntry.isFailure` with the companion's `closed()`, `failed()` and
   `fatal()` (lifetime 0, weight 0, `storedAt` carried). `LoaderRunnerComponentTest`, whose six plain cases ran under `runBlocking` (the plan
   names no runner for them), runs them under `runTest`, so the `withTimeout(10)` case expires on virtual time instead of waiting on the wall
   clock. The plan's tables are the minimum each class locks down; these additions cover two branches of §7.9's code and two `internal` members
   of §7.8 that no listed case reached. No design, signature or research finding is affected.
4. **The load body's DEBUG "load ended" line is written by the completion that wins, not by the body** (plan §7.9's code block: `load()` logs
   `residency.storage.note` before returning its entry, and `startLoad` does `future.complete(load(…))`). As built after the code review,
   `load()` returns the entry together with its storage note (a private `Ended` holder, because an entry with lifetime 0 does not say which of the
   three "not stored" notes applies), and the `scope.launch` body in `startLoad` logs the line only when its `future.complete(entry)` returns `true`;
   `settle` is unchanged and `failedLoads` is still counted in the body. Reason: R12 and §6.9 say `DEBUG` **once per load end**, "whether it was
   stored or why not", "logged by whoever completes the shared future"; §7.9's code met that only when the body's completion won. When
   `close()` had already settled the future (a loader that ignores cancellation), the body wrote a second "load ended" line for the same load,
   saying `stored` for a result the cache discarded. The change rests on the same fact the safety nets of §6.4 rest on:
   `CompletableFuture.complete` is first-wins and reports whether this call completed the future (research `load-lifecycle.md`, finding 21;
   summarised in `research/README.md`'s "A waiter never hangs"), so the winner is known at the moment of completion.
   **The note after an in-flight `invalidate(key)`.** There nobody else completes the future, so the body's completion wins, yet Caffeine discards
   the result: its completion callback re-inserts only by identity (`replace(key, future, future)`) and the future is no longer mapped
   (`research/caffeine-mechanics.md`, "The completion does not re-insert", and plan §6.5). A storable entry's note is therefore
   `residency.storage.note` only if `cache.asMap()[key]` is still that future once `complete()` has returned (the callback has run on the
   completing thread by then, so the check is exact); otherwise the line says `not stored: removed before the load ended`, a new `internal`
   `Storage.Removed` constant in `CacheEntry.kt` beside the three notes of §7.8. The check applies only to an entry the residency rule would store:
   a zero-TTL, oversized, failed or fatal entry keeps its own "not stored" reason, which is still the accurate one (and is what the existing
   `each load end logs its outcome kind and storage note` case asserts; an unconditional check would have relabelled those lines). Architecture
   §6.4's text on completion is unaffected; the log text, the logger and the existing note strings are the same.
