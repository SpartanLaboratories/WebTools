# Issue #66 — `response-cache` (consequences)

- **Covers:** `SpartanLaboratories/WebTools#66`, *"webtools-http: `ResponseCache` port and `CaffeineResponseCache` default"*. It is unit 7,
  `response-cache`, of [#55's architecture](../architecture.md) (§6.4, §6.9, §9, §11).
- **What this covers:** what the repository, its published artifacts, the sibling units and the neighbouring issues gain or lose with this unit, as
  built, and what it leaves behind. The file-level detail is in [`plan.md`](plan.md); the as-built record, with every measurement and deviation, is
  [`final-implementation.md`](final-implementation.md); the evidence is in [`research/README.md`](research/README.md) with its two findings files.
- **Status:** built at the code-landing commit `38483ea`, with the deviations of [`final-implementation.md`](final-implementation.md) §2. None changes a
  published artifact or a sibling contract; D4 adds the load-end log rule to the refinements of §5. [`../architecture.md`](../architecture.md) carries dated
  corrections (2026-11-03) for this unit at §6.4 and in §7.4's row for the facade's call to `ResponseCache`.
- **Baseline:** `master` `e8f0a88`; branch `feat/issue-66-response-cache`, with the research commits `c19db19`, `97beb79`, `879abff` and `826200e`, the code
  commits `e5f1a4c`, `ea167b9` and `d1deae5`, then `38483ea` (`docs: record the response cache as built and correct the research count (Issue #66)`), the
  code-landing commit.
- **Related docs:** [`plan.md`](plan.md) and [`final-implementation.md`](final-implementation.md); [`research/README.md`](research/README.md); the
  systems-level [`../consequences.md`](../consequences.md); the as-built records of the units this one builds on,
  [`../62-http-core/`](../62-http-core/final-implementation.md), [`../63-test-support/`](../63-test-support/final-implementation.md),
  [`../64-url-policy/`](../64-url-policy/final-implementation.md) and [`../65-okhttp-transport/`](../65-okhttp-transport/final-implementation.md).

## 1. Published artifacts

| Artifact | What changes | Version |
|---|---|---|
| `webtools-http` (new, unreleased) | The main variants gain the response cache. In common code: the port `ResponseCache` (implementing it needs the opt-in) with its pass-through `ResponseCache.Disabled`, and the values `CacheKey`, `CachePolicy` (with its builder and factory), `Lookup` (`Found`, `Missing`) and `CacheStats`. On the JVM: `CaffeineResponseCache`, its builder and its factory. All of them except the port's implementation are Stable Core and usable without the opt-in. The ABI dump grows by exactly these ([`final-implementation.md`](final-implementation.md) §1). No dependency of either variant changes: Caffeine 3.3.1 was already a runtime dependency of `webtools-http-jvm`. The test-fixtures variant does not change. | `1.0.0`, unreleased; **not a release candidate** at this landing; published once #55's stack is complete |
| `webtools-udp`, `webtools-scraping`, `webtools-browser` | Nothing: no source, build-script or test change. | unchanged |

**What a consumer of `webtools-http` 1.0.0 gets from this unit:**

- **One load for any number of concurrent callers of one key.** Calls that arrive while a load is in flight join it; a call's own
  cancellation leaves the load and the other callers intact, and a load nobody waits for any more still completes and is stored.
- **A bounded store with per-entry lifetimes.** A value lives its policy's `ttl` and a confirmed miss its `missTtl`, both from the load's
  completion; `maxWeight` bounds what is stored at every instant, and a value heavier than the bound is delivered and never stored.
- **Freshness on demand.** A per-call `maxAge` refuses an older entry; N callers on one stale entry cost one load.
- **Failures never stored.** A loader's failure reaches every caller of that load as one instance and is never served to a later call; an
  `Error` is rethrown to every caller; a cancellation the loader throws on its own becomes an `IllegalStateException` failure, never a
  cancellation of the caller.
- **A clean close.** After `close()` every waiting call and every later call returns `Closed("cache")`, without waiting for a loader to
  unwind.
- **Statistics** with one meaning each: hits (joins included), misses (loads started), size evictions only, and failed loads.
- **Logs without values:** `INFO` on creation and on `close()`, one `DEBUG` line per load end with the key's id and parameter names, the outcome
  kind and whether it was stored, and one `WARN` for a load the `loadDispatcher` refused; never a value, a parameter value or a message text, and nothing
  per hit or join.
- **Common code and an explicit opt-out:** `ResponseCache.Disabled` runs every loader, stores nothing and shares nothing.
- **Virtual time under test:** the cache's `timeSource`, `loadDispatcher` and `maintenanceExecutor` are builder properties, so the published
  `VirtualClock` drives its expiry together with `delay` and the wall clock.

## 2. Blast radius in this repository

### Source

- **New files, all in `webtools-http`** ([`final-implementation.md`](final-implementation.md) §1 lists them):
  - `commonMain`: the port and `Disabled`, the four value types and the `internal` loader runner both caches share (compiled for `js` by the
    check module like every common file);
  - `jvmMain`: `CaffeineResponseCache` and its `internal` entry, rules and `Expiry`;
  - their tests in `commonTest` (the values, the key's canonical form, the consumer's view without the opt-in) and `jvmTest` (everything
    that suspends), in the level packages, with their support classes.
- **Changed files:**
  - `ExperimentalForInheritanceWebToolsApi.kt` and `WebToolsDsl.kt`: KDoc only, their lists gain `ResponseCache` and the two new builders;
  - `WebToolsDslComponentTest`: its list of builders gains the two new builders;
  - `VirtualClockThreeClocksComponentTest`: KDoc only;
  - `webtools-http/api/webtools-http.api`: the new classes.
- **No caller yet.** Nothing in the repository calls a `ResponseCache`; the call facades (#68) are the first.

### Build

- **No build file changes.**
- **`./gradlew build` does more work:** the new tests of `webtools-http` (Level 3 with one loopback server, and Level 4c on real threads,
  the failure-identity check among them); the fixtures-publication check runs again, because `jvmMain` is among its inputs; `apiCheck`
  against the regenerated dump; the check module's `js` compile of the new common files. The module's five level tasks, 889 tests with these, ran in
  11 s together on the build host ([`final-implementation.md`](final-implementation.md) §4).

### Documentation

- **`README.md`:** the `webtools-http` row of the artifacts table names the response cache.
- **`CHANGELOG.md`:** one `### Added` entry under `## [Unreleased]`.
- **KDoc** on every new public declaration states its tier and contract.
- **[`../architecture.md`](../architecture.md)** carries dated corrections (2026-11-03) at §6.4 and in §7.4's row for the points of §5.
- **[`../consequences.md`](../consequences.md)**, the systems-level document, gains one line for this unit, linking to §4.
- **No other `docs/` change.** The evergreen `docs/webtools-http-architecture.md` is #70's.

## 3. Adoption

- **No existing class caches HTTP responses**, so nothing is moved onto the cache. Its first users are the call facades (#68).
- **`VirtualClockThreeClocksComponentTest` keeps its plain Caffeine cache.** It proves the virtual clock apart from the response cache; the
  new `CaffeineResponseCacheThreeClocksComponentTest` proves the same with the real cache.

## 4. Sibling units

[`plan.md`](plan.md) §12 has the exact contracts, and the build holds them as written; the points below are as built. In short:

- **Unit 9, `call-facades` (#68):**
  - it calls `getOrLoad(key: CacheKey<V>, policy: CachePolicy, maxAge: Duration, loader: suspend () -> Result<Lookup<V>>): Result<Lookup<V>>` (a `suspend`
    member, abstract on the port) for a `GET` that sets a `maxAge`, and `getOrLoad(key, policy, loader)`, whose body passes `Duration.INFINITE`, when it
    sets none, with `CacheKey(endpoint.id, canonicalParams)` and `CachePolicy(ttl) { missTtl = … }`;
  - `CacheKey` sorts its parameters by name itself, with a stable sort, so the facade passes the canonical encoded path and the query pairs
    in any order, repeated names in their own order; a key never carries a tenant, a header or a credential;
  - a negative `maxAge` throws `IllegalArgumentException`, so the facade checks `CallSpec.maxAge` first and returns `InvalidRequest` before
    anything is spent;
  - its loader returns failures as values. An `Exception` it throws is returned as the failure and an `Error` is rethrown to every caller. A cancellation it
    throws, or returns inside `Result.failure`, while the cache's load coroutine is still active (a `withTimeout` expiring inside it) comes back as an
    `IllegalStateException` failure with that cancellation as its `cause`, never as a `CancellationException` or `Closed`. The loader runs in the cache's
    scope on `loadDispatcher`, not in the caller's context, and must not wait for a load of its own key;
  - `Lookup.Found(value)` weighs 1; `Found(value, weight)` needs a weight of at least 1; a value heavier than the cache's `maxWeight` is delivered and
    not stored;
  - `ResponseCache.Disabled` is the common factory's default; it runs each loader on the caller's coroutine, and its `close()` does nothing, so a client may
    close it safely;
  - the JVM owning factory builds `CaffeineResponseCache { timeSource = …; loadDispatcher = …; maintenanceExecutor = … }` from the client's
    clock and dispatcher, and closes it first of the three components it owns; a cache a consumer passes in is never closed by the client;
  - `cacheStats()` returns `stats()`, in which a call that joins a load counts as a hit and `evictions` counts the size bound's removals only;
  - the port reports nothing per call: a call cannot tell a hit from a join, and only its own loader running tells the facade that the call started the
    load. The facade's per-call `DEBUG` line (architecture §6.6, "hit/miss/joined") is settled against that;
  - the cache logs on its own logger, `com.spartanlabs.webtools.http.CaffeineResponseCache`: `INFO` on creation and on `close()`, `DEBUG` once per load end,
    written by the completion of the shared future that wins (the load's body, `close()`'s sweep or a safety net), `WARN` once for a load coroutine that
    failed outside the loader; nothing per hit or join and never a value. The facade's own line is in addition and never carries a cache-key value;
  - `Storage.Removed`, the note D4 adds, is `internal` and names a log text only; nothing in the contract changes with it;
  - a coalesced caller with a tighter deadline wraps its own `getOrLoad` in `withTimeoutOrNull`, which cancels only that caller.
- **Unit 8, `call-budget` (#67):** nothing from this unit. Its permit is taken inside the facade's loader, so it runs in the cache's load coroutine on
  `loadDispatcher`: a cache's `close()` cancels a wait in progress, and a cancellation the budget throws on its own while that coroutine is active reaches the
  waiters as the `IllegalStateException` failure above, not as a budget failure: a refusal the facade must see as `BudgetExhausted` is returned as a value.
- **Unit 10, `feeds` (#69):** nothing (`UrlFetcher` has no cache).
- **Unit 11, `http-docs` (#70):** it documents the cache from this unit's KDoc, README row and CHANGELOG entry, and the logging above for operators.

## 5. Where the design refines the parent documents

None of these changes the unit's scope, a seam or its tier; each makes [`../architecture.md`](../architecture.md) §6.4 concrete against the code as built,
and each is a dated correction (2026-11-03) there, at §6.4 or in §7.4's row. [`plan.md`](plan.md) §6.11 gives the reasons.

- **Statistics:** no `recordStats()`, and one `evictionListener` that counts size removals only, where §6.4 has `recordStats()` and no
  listeners: Caffeine's `evictionCount` also counts expiry and failure removals, and varies with callback order. The cache keeps its four counts itself.
- **Outcomes:** a fatal `Error` is carried by value like every failure, never as an exceptional completion; a loader's own cancellation is an
  `IllegalStateException` failure; a load coroutine that fails with an `Exception` (a rejecting dispatcher) is a failed outcome; a completed entry of zero
  lifetime is treated as absent.
- **`close()`:** it sets the closed flag, cancels the scope, completes every future still in the map itself and clears the map.
- **Weights:** `Found`'s weight is at least 1, and a value heavier than `maxWeight` is never stored.
- **The call:** `getOrLoad` checks the caller's cancellation first, a negative `maxAge` throws `IllegalArgumentException`, and the overload without `maxAge`
  has a body that passes `Duration.INFINITE`.
- **The load scope** carries a `CoroutineExceptionHandler` that logs a failure outside the load's body once at `WARN`; the load's body, `close()`'s sweep
  with the call's re-check of the flag, and the job's completion handler each complete the shared future, and the first wins.
- **The load-end line** is written once, by the completion that wins; a stored-kind result whose future an `invalidate` removed during the load is logged
  `not stored: removed before the load ended` ([`final-implementation.md`](final-implementation.md) D4).
- **`ResponseCache.Disabled.close()`** does nothing.
- **The stack-level research's `startLoad` sketch** ([`../research/response-cache.md`](../research/response-cache.md)) is replaced by the total
  load body and its completers ([`research/README.md`](research/README.md), "Where the sources disagree").

## 6. Neighbouring issues

- **#58** (CI and repository practices): its CI would be the first place these tests run on every change. The Level 4c failure-identity check
  needs at least two cores to open the window it guards; on one core it passes without exercising it (§8).
- **[WebTools#87](https://github.com/SpartanLaboratories/WebTools/issues/87)**, the `UrlParseLinearTimeNonFunctionalTest` flake under concurrent level
  tasks: four more Level 4c classes now run beside it; it did not show in this unit's runs.
- **The IDE checks of #62 to #65** are still open; this unit's join them, at the next IntelliJ session (§8).

## 7. Follow-ups this unit leaves

Each is still open; [`plan.md`](plan.md) §13 names them and §4 holds them out of this unit.

- **`cache-stale-while-revalidate`:** `staleFor` on the `CachePolicy` builder, additive (architecture §4 lists it as a non-goal of #55).
- **`cache-non-jvm-default`:** a cache default for non-JVM targets beyond `ResponseCache.Disabled`.
- **`cache-joined-and-gauges`:** a count of joined calls, and size and weight gauges, in `CacheStats`, each additive through a new constructor of the
  class.
- **`cancelled-loader-failure-leaf`:** a typed failure leaf for a loader's own cancellation; the failure model is #62's, and a leaf is additive in a feature
  release, with the `IllegalStateException` then its `cause` or replaced.
- **`cache-invalidate-endpoint`:** invalidating every key of one endpoint.
- **`cache-fake-in-fixtures`:** a cache fake in the published fixtures, if a consumer needs one beyond `CaffeineResponseCache` on `VirtualClock`.
- **Seam graduation:** `ResponseCache` moves from Experimental to `@SupportedExtension` in a feature release once BotTools' adapter has built
  against it; the promotion is recorded in `CHANGELOG.md`.

## 8. Known gaps

- **The insert-then-check has no test that fails without it.** With the closed-flag re-check of `startLoad` removed, the close-race test passed in 5 of 5
  runs (P4, [`final-implementation.md`](final-implementation.md) §4). The reading recorded with P4, not instrumented, is that once `close()` has cancelled
  the load scope a `launch` completes cancelled without running its body, and the job's completion handler then settles the future as closed, so removing
  the re-check alone opens no hang for the test to see. Its first half, that a load coroutine cancelled before it runs never enters its body, is what the
  close-before-start case of `CaffeineResponseCacheCloseComponentTest` shows; the second half, that the handler completes the future for a `launch` into a
  scope already cancelled, rests on the coroutine library's source and its own tests ([`research/load-lifecycle.md`](research/load-lifecycle.md) F2), not
  on a run here. The re-check settles the future before any coroutine exists, so with it a waiter's release does not wait for the load dispatcher to run a
  cancelled task. No deterministic seam pauses a call between its insert and its re-check ([`plan.md`](plan.md) §9, "What cannot be tested automatically").
- **The close-versus-start window** rests on the insert-then-check, the job's completion handler and weakly consistent iteration of the cache's map; only the
  probabilistic close-race test probes it, and no run of it hung ([`plan.md`](plan.md) §3, Assumption 3).
- **The failure-identity check's reach depends on the host.** P3 shows it detects the missing removals on the 32-core build host with JDK 23.0.2, in 5 of 5
  runs. On a single-core host, or a JDK whose `CompletableFuture` dependent order differs from the one read in JDK 11.0.25's source, it can pass without
  having opened the window.
- **Caffeine behaviour read from source and not separated by a run** ([`plan.md`](plan.md) §3, Assumptions 1 and 2): that a zero `Expiry` result expires a
  just-completed entry at once, where the waiters' removal by identity does the same work and no test tells the two apart; and that Caffeine never cancels a
  future it holds, where no run showed one cancelled and the waiter's fall-through for that case is pinned only by cancelling the future from the test
  ([`final-implementation.md`](final-implementation.md) D3).
- **The path of a rejected dispatch** ([`plan.md`](plan.md) §3, Assumption 8): its outcome is pinned (a failure value, one `WARN`, no hang, nothing reaching
  `runTest`), but which completer delivers it, the call's `catch` around `launch` as read in the source or the job's completion handler, no test tells apart:
  both build the same entry.
- **The residue of a cancelled waiter** (one callback and one `CompletableDeferred` on the shared future until the load ends) is inferred, not
  measured.
- **The gate on implementers has no automated check.** The ABI dump does not record `@SubclassOptInRequired`, so dropping it from `ResponseCache` passes
  `apiCheck`; P1 showed the gate once, by hand (architecture §13; [`../consequences.md`](../consequences.md) §7). `sealed` on `Lookup` is held by the Level 1
  consumer's `when` expression with no `else`.
- **Measured only on the build host,** Windows 11 with JDK 23.0.2; Linux, macOS and other JDKs wait for #58's CI.
- **IDE behaviour is untested:** completion of `CaffeineResponseCache { }`, the opt-in error on an implementer of `ResponseCache` with its quick fix, the
  `@WebToolsDsl` scoping of the two builders, and Quick Documentation of the new types ([`final-implementation.md`](final-implementation.md) §5). Owner:
  the maintainer; trigger: the next IntelliJ session, together with the open IDE items of #62 to #65
  ([`../65-okhttp-transport/consequences.md`](../65-okhttp-transport/consequences.md) §8).

## 9. The plan's assumptions, as built

No assumption of [`plan.md`](plan.md) §3 failed. As far as this build shows:

1. **A zero `Expiry` result expires a just-completed entry at once:** a completed zero-lifetime entry is never served and nothing is held for its key once
   its waiters return (`CaffeineResponseCacheZeroTtlComponentTest`, `CaffeineResponseCacheFailureComponentTest`); Caffeine's own expiry is not separated from
   the waiters' removal (§8).
2. **Caffeine never cancels a future it holds:** no Level 2 or Level 4c run showed a cancelled future while the cache was open, and the waiters of a future
   that `invalidate` or `invalidateAll` removed in flight receive that load's value (`CaffeineResponseCacheInvalidationComponentTest`), so the extra round
   the assumption names for the opposite case is not built.
3. **The insert-then-check closes the close/start race:** no hang was observed, with the re-check or without it (P4); the close-race test cannot tell the
   re-check from the job's completion handler (§8), and `close()` keeps its one sweep.
4. **`runTest` 1.10.2 and 1.11.0 behave alike:** only 1.10.2 ran. Every Level 2 class closes its caches, with `use` or, in the three classes that test
   `close()`, directly.
5. **The failure-identity race can fire on the test host:** it fires on the 32-core host, where P3's mutation failed the check in 5 of 5 runs.
6. **No JUnit parallel execution is configured:** the module has no `junit-platform.properties`, and the classes that throw an `Error` through the cache
   complete their `runTest` normally.
7. **`kotlin.AutoCloseable.use` is available:** the tests compile and use it on the cache.
8. **A rejecting `loadDispatcher`** gives the outcome the assumption guards (`CaffeineResponseCacheRejectedLoadComponentTest`: a failure value, one `WARN`
   per rejected dispatch, no hang, nothing reaching `runTest`, and for a dispatch that throws an `Error` the same instance rethrown); which completer
   delivers it is not observed (§8).
